package io.casehub.ops.topology.integration;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import io.casehub.ras.api.ActiveSituation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdhocLlmDeployerRecompilerTest extends TopologyTestBase {

    private static final String YAML_PATH =
            "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer.yaml";

    private DesiredStateGraph baseGraph;
    private DesiredStateGraphFactory factory;
    private ActualState actualState;

    @BeforeEach
    void setUp() throws IOException {
        var registry = buildTypeRegistryFrom(ClaudonyNodeSpec.class);
        baseGraph   = compileSingleGraph(YAML_PATH, registry);
        factory     = new DefaultDesiredStateGraphFactory();
        actualState = new ActualState(
                Map.of(
                        NodeId.of("manifest"), NodeStatus.PRESENT,
                        NodeId.of("eidos"), NodeStatus.PRESENT,
                        NodeId.of("engine"), NodeStatus.PRESENT,
                        NodeId.of("deployer"), NodeStatus.PRESENT
                      ));
    }

    @Test
    void recompile_addSpecialistOnTriggered() {
        var catalog = List.of(
                new SpecialistProfile("product-x-specialist", "specialist",
                        "product-x", "expert", "kb-product-x"));
        DeploymentDecisionFunction alwaysDeploy =
                (situation, cat) -> Optional.of(cat.get(0).name());
        var recompiler = new AdhocLlmDeployerRecompiler(catalog, alwaysDeploy, 3);

        var situation = new ActiveSituation("sit-1", "_singleton", "test",
                0.9, Map.of(), Instant.now(), Instant.now(), 0);

        Optional<CompilationResult> result = recompiler.recompile(
                "tenant-1", baseGraph, actualState, situation, factory);

        assertThat(result).isPresent();
        var newGraph = ((CompilationResult.SingleGraph) result.get()).graph();
        assertThat(newGraph.nodes()).containsKey(NodeId.of("specialist-product-x-specialist"));
        var spec = (ClaudonyAppNodeSpec) newGraph.nodes()
                .get(NodeId.of("specialist-product-x-specialist")).spec();
        assertThat(spec.config()).containsEntry("llm.domain", "product-x");
        assertThat(spec.config()).containsEntry("llm.deployed-by", "deployer");
        assertThat(spec.config()).containsEntry("llm.source-situation", "sit-1");
    }

    @Test
    void situationResolved_removesSpecialist() {
        var catalog = List.of(
                new SpecialistProfile("product-x-specialist", "specialist",
                        "product-x", "expert", "kb-product-x"));
        DeploymentDecisionFunction alwaysDeploy =
                (situation, cat) -> Optional.of(cat.get(0).name());
        var recompiler = new AdhocLlmDeployerRecompiler(catalog, alwaysDeploy, 3);

        var situation = new ActiveSituation("sit-1", "_singleton", "test",
                0.9, Map.of(), Instant.now(), Instant.now(), 0);
        var result = recompiler.recompile("tenant-1", baseGraph, actualState,
                situation, factory);
        assertThat(result).isPresent();
        var graphWithSpecialist = ((CompilationResult.SingleGraph) result.get()).graph();

        var resolved = recompiler.situationResolved("tenant-1", "sit-1",
                graphWithSpecialist, actualState, factory);

        assertThat(resolved).isPresent();
        var finalGraph = ((CompilationResult.SingleGraph) resolved.get()).graph();
        assertThat(finalGraph.nodes()).doesNotContainKey(
                NodeId.of("specialist-product-x-specialist"));
    }

    @Test
    void recompile_rejectionWhenDecisionEmpty() {
        var catalog = List.of(
                new SpecialistProfile("product-x-specialist", "specialist",
                        "product-x", "expert", "kb-product-x"));
        DeploymentDecisionFunction neverDeploy = (situation, cat) -> Optional.empty();
        var recompiler = new AdhocLlmDeployerRecompiler(catalog, neverDeploy, 3);

        var situation = new ActiveSituation("sit-1", "_singleton", "test",
                0.3, Map.of(), Instant.now(), Instant.now(), 0);
        var result = recompiler.recompile("tenant-1", baseGraph, actualState,
                situation, factory);

        assertThat(result).isEmpty();
    }

    @Test
    void recompile_concurrentSituationsAddMultipleSpecialists() {
        var catalog = List.of(
                new SpecialistProfile("product-x-specialist", "specialist",
                        "product-x", "expert", "kb-product-x"),
                new SpecialistProfile("product-y-specialist", "specialist",
                        "product-y", "expert", "kb-product-y"));
        DeploymentDecisionFunction domainMatch =
                (situation, cat) -> cat.stream()
                        .filter(p -> situation.situationId().contains(p.domain()))
                        .map(SpecialistProfile::name)
                        .findFirst();
        var recompiler = new AdhocLlmDeployerRecompiler(catalog, domainMatch, 3);

        var sit1 = new ActiveSituation("product-x-surge", "_singleton", "test",
                0.9, Map.of(), Instant.now(), Instant.now(), 0);
        var sit2 = new ActiveSituation("product-y-surge", "_singleton", "test",
                0.8, Map.of(), Instant.now(), Instant.now(), 0);

        var result1 = recompiler.recompile("tenant-1", baseGraph, actualState,
                sit1, factory);
        assertThat(result1).isPresent();
        var graphAfterFirst = ((CompilationResult.SingleGraph) result1.get()).graph();

        var result2 = recompiler.recompile("tenant-1", graphAfterFirst, actualState,
                sit2, factory);
        assertThat(result2).isPresent();
        var graphAfterBoth = ((CompilationResult.SingleGraph) result2.get()).graph();

        assertThat(graphAfterBoth.nodes()).containsKey(
                NodeId.of("specialist-product-x-specialist"));
        assertThat(graphAfterBoth.nodes()).containsKey(
                NodeId.of("specialist-product-y-specialist"));
        assertThat(recompiler.activeMappings()).hasSize(2);
    }

    @Test
    void circuitBreaker_escalatesAfterMaxRedeploys() {
        var catalog = List.of(
                new SpecialistProfile("product-x-specialist", "specialist",
                        "product-x", "expert", "kb-product-x"));
        var recompiler = new AdhocLlmDeployerRecompiler(catalog,
                (s, c) -> Optional.of(c.get(0).name()), 3);

        String specialistId = "specialist-product-x-specialist";
        assertThat(recompiler.shouldEscalate(specialistId)).isFalse();

        recompiler.recordRedeploy(specialistId);
        recompiler.recordRedeploy(specialistId);
        assertThat(recompiler.shouldEscalate(specialistId)).isFalse();

        recompiler.recordRedeploy(specialistId);
        assertThat(recompiler.shouldEscalate(specialistId)).isTrue();
    }

    @Test
    void recompile_idempotentWhenSpecialistAlreadyExists() {
        var catalog = List.of(
                new SpecialistProfile("product-x-specialist", "specialist",
                        "product-x", "expert", "kb-product-x"));
        var recompiler = new AdhocLlmDeployerRecompiler(catalog,
                (s, c) -> Optional.of(c.get(0).name()), 3);

        var situation = new ActiveSituation("sit-1", "_singleton", "test",
                0.9, Map.of(), Instant.now(), Instant.now(), 0);

        var result1 = recompiler.recompile("tenant-1", baseGraph, actualState,
                situation, factory);
        assertThat(result1).isPresent();
        var graphWithSpecialist = ((CompilationResult.SingleGraph) result1.get()).graph();

        var result2 = recompiler.recompile("tenant-1", graphWithSpecialist, actualState,
                situation, factory);
        assertThat(result2).isEmpty();
    }
}
