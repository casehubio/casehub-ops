package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateEventTypes;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.MergedEventSource;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.runtime.DefaultActualStateAdapterRouter;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.DefaultNodeProvisionerRouter;
import io.casehub.desiredstate.runtime.FaultPolicyEngine;
import io.casehub.desiredstate.runtime.NoOpHumanNodeHandler;
import io.casehub.desiredstate.runtime.NoOpPendingApprovalHandler;
import io.casehub.desiredstate.runtime.NodeStepExecutor;
import io.casehub.desiredstate.runtime.ReconciliationLoop;
import io.casehub.desiredstate.runtime.SimpleTransitionExecutor;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.api.SpecHashStore;
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
import io.casehub.ops.claudony.ClaudonyFaultPolicy;
import io.casehub.ops.claudony.ClaudonyNodeProvisioner;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
import io.casehub.ops.testing.audit.AuditAssertions;
import io.casehub.ops.testing.audit.AuditCapture;
import io.casehub.ops.testing.audit.CapturingTransitionExecutor;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import io.casehub.ras.api.ActiveSituation;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class AdhocLlmDeployerIntegrationTest extends TopologyTestBase {

    private static final String TENANT = "l12-test";
    private static final String YAML_PATH =
            "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer.yaml";

    private static final List<SpecialistProfile> CATALOG = List.of(
            new SpecialistProfile("product-x-specialist", "specialist",
                    "product-x", "expert", "kb-product-x"),
            new SpecialistProfile("product-y-specialist", "specialist",
                    "product-y", "expert", "kb-product-y"),
            new SpecialistProfile("escalation-specialist", "escalation",
                    "incident-response", "coordinator", "kb-escalation"));

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    private ReconciliationLoop loop;
    private AuditCapture auditCapture;
    private FailableClaudonyProvisioner provisioner;
    private HttpClaudonyFleetClient fleetClient;
    private ClaudonyActualStateAdapter actualAdapter;
    private AdhocLlmDeployerRecompiler recompiler;
    private DesiredStateGraph graph;
    private DefaultDesiredStateGraphFactory graphFactory;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        fleetClient = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        var specHashStore = new SpecHashStore();
        var realProvisioner = new ClaudonyNodeProvisioner(fleetClient, specHashStore);
        provisioner = new FailableClaudonyProvisioner(realProvisioner);
        actualAdapter = new ClaudonyActualStateAdapter(fleetClient, specHashStore);
        graphFactory = new DefaultDesiredStateGraphFactory();

        auditCapture = new AuditCapture();

        DeploymentDecisionFunction domainMatch =
                (situation, catalog) -> catalog.stream()
                        .filter(p -> situation.situationId().contains(p.domain()))
                        .map(SpecialistProfile::name)
                        .findFirst();
        recompiler = new AdhocLlmDeployerRecompiler(CATALOG, domainMatch, 3);

        var faultEngine = new FaultPolicyEngine(List.of(new ClaudonyFaultPolicy()));
        var planner = new TransitionPlanner();
        var provisionerRouter = new DefaultNodeProvisionerRouter(List.of(provisioner));
        var nodeExecutor = new NodeStepExecutor(
                provisionerRouter,
                new NoOpHumanNodeHandler(),
                new NoOpPendingApprovalHandler(),
                (step, tid) -> new StepOutcome.Succeeded());
        var executor = new SimpleTransitionExecutor(nodeExecutor);
        var capturingExecutor = new CapturingTransitionExecutor(executor, auditCapture);
        var actualRouter = new DefaultActualStateAdapterRouter(List.of(actualAdapter));
        MergedEventSource emptySource = () -> Multi.createFrom().nothing();

        loop = ReconciliationLoop.builder(planner, capturingExecutor,
                        actualRouter, faultEngine, emptySource)
                .cloudEventSink(auditCapture::recordCloudEvent)
                .debounceWindow(Duration.ofMillis(50))
                .resyncInterval(Duration.ofMinutes(30))
                .build();

        var registry = buildTypeRegistryFrom(ClaudonyNodeSpec.class);
        graph = compileSingleGraph(YAML_PATH, registry);
    }

    @AfterEach
    void tearDown() {
        if (loop != null) {
            loop.stop(TENANT);
            loop.shutdown();
        }
    }

    @Test
    void fullLifecycle_deployAndRemoveSpecialist() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture,
                DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        var situation = new ActiveSituation("product-x-surge", "_singleton", TENANT,
                0.9, Map.of(), Instant.now(), Instant.now(), 0);
        var result = recompiler.recompile(TENANT, graph,
                actualAdapter.readActual(graph, TENANT), situation, graphFactory);
        assertThat(result).isPresent();
        var expandedGraph = ((CompilationResult.SingleGraph) result.get()).graph();

        auditCapture.clear();
        loop.updateDesired(TENANT, expandedGraph);
        loop.requestReconciliation(TENANT);
        awaitReconciliationCycles(2);

        var actual = actualAdapter.readActual(expandedGraph, TENANT);
        assertThat(actual.statuses().get(NodeId.of("specialist-product-x-specialist")))
                .isEqualTo(NodeStatus.PRESENT);

        var resolved = recompiler.situationResolved(TENANT, "product-x-surge",
                expandedGraph, actual, graphFactory);
        assertThat(resolved).isPresent();
        var shrunkGraph = ((CompilationResult.SingleGraph) resolved.get()).graph();

        auditCapture.clear();
        loop.updateDesired(TENANT, shrunkGraph);
        loop.requestReconciliation(TENANT);
        awaitReconciliationCycles(2);

        assertThat(shrunkGraph.nodes())
                .doesNotContainKey(NodeId.of("specialist-product-x-specialist"));
    }

    @Test
    void concurrentAlerts_deployMultipleSpecialists() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture,
                DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        var sit1 = new ActiveSituation("product-x-surge", "_singleton", TENANT,
                0.9, Map.of(), Instant.now(), Instant.now(), 0);
        var sit2 = new ActiveSituation("product-y-surge", "_singleton", TENANT,
                0.8, Map.of(), Instant.now(), Instant.now(), 0);

        var actual = actualAdapter.readActual(graph, TENANT);
        var result1 = recompiler.recompile(TENANT, graph, actual, sit1, graphFactory);
        assertThat(result1).isPresent();
        var graphWith1 = ((CompilationResult.SingleGraph) result1.get()).graph();

        var result2 = recompiler.recompile(TENANT, graphWith1, actual, sit2, graphFactory);
        assertThat(result2).isPresent();
        var graphWith2 = ((CompilationResult.SingleGraph) result2.get()).graph();

        auditCapture.clear();
        loop.updateDesired(TENANT, graphWith2);
        loop.requestReconciliation(TENANT);
        awaitReconciliationCycles(2);

        var actualAfter = actualAdapter.readActual(graphWith2, TENANT);
        assertThat(actualAfter.statuses().get(NodeId.of("specialist-product-x-specialist")))
                .isEqualTo(NodeStatus.PRESENT);
        assertThat(actualAfter.statuses().get(NodeId.of("specialist-product-y-specialist")))
                .isEqualTo(NodeStatus.PRESENT);

        var res1 = recompiler.situationResolved(TENANT, "product-x-surge",
                graphWith2, actualAfter, graphFactory);
        var graphMinus1 = ((CompilationResult.SingleGraph) res1.get()).graph();
        var res2 = recompiler.situationResolved(TENANT, "product-y-surge",
                graphMinus1, actualAfter, graphFactory);
        var finalGraph = ((CompilationResult.SingleGraph) res2.get()).graph();

        auditCapture.clear();
        loop.updateDesired(TENANT, finalGraph);
        loop.requestReconciliation(TENANT);
        awaitReconciliationCycles(2);

        assertThat(finalGraph.nodes()).hasSize(4);
    }

    @Test
    void rejection_noDeploymentOnLowSeverity() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture,
                DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        DeploymentDecisionFunction rejectLow =
                (situation, catalog) -> situation.confidence() < 0.5
                        ? Optional.empty()
                        : catalog.stream()
                                .filter(p -> situation.situationId().contains(p.domain()))
                                .map(SpecialistProfile::name)
                                .findFirst();
        var selectiveRecompiler = new AdhocLlmDeployerRecompiler(CATALOG, rejectLow, 3);

        var lowSeverity = new ActiveSituation("product-x-minor", "_singleton", TENANT,
                0.3, Map.of(), Instant.now(), Instant.now(), 0);
        var result = selectiveRecompiler.recompile(TENANT, graph,
                actualAdapter.readActual(graph, TENANT), lowSeverity, graphFactory);

        assertThat(result).isEmpty();
        assertThat(graph.nodes()).hasSize(4);
    }

    @Test
    void healthFailure_redeploysSpecialist() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture,
                DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        var situation = new ActiveSituation("product-x-surge", "_singleton", TENANT,
                0.9, Map.of(), Instant.now(), Instant.now(), 0);
        var actual = actualAdapter.readActual(graph, TENANT);
        var result = recompiler.recompile(TENANT, graph, actual, situation, graphFactory);
        var expandedGraph = ((CompilationResult.SingleGraph) result.get()).graph();

        auditCapture.clear();
        loop.updateDesired(TENANT, expandedGraph);
        loop.requestReconciliation(TENANT);
        awaitReconciliationCycles(2);

        String specialistId = "specialist-product-x-specialist";
        provisioner.failNode(specialistId, 1);

        auditCapture.clear();
        loop.requestReconciliation(TENANT);
        awaitReconciliationCycles(3);

        var actualAfter = actualAdapter.readActual(expandedGraph, TENANT);
        assertThat(actualAfter.statuses().get(NodeId.of(specialistId)))
                .isEqualTo(NodeStatus.PRESENT);

        AuditAssertions.assertCloudEventEmitted(auditCapture,
                DesiredStateEventTypes.NODE_FAULTED);
    }

    private void awaitReconciliationCycles(int targetCycles) throws Exception {
        int baseline = auditCapture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        int target = baseline + targetCycles;
        long deadline = System.currentTimeMillis() + 30_000;
        while (auditCapture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED) < target) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(
                        "Expected " + targetCycles + " reconciliation cycles within 30s. " +
                        "Completed: " + (auditCapture.cloudEventCount(
                                DesiredStateEventTypes.RECONCILIATION_COMPLETED) - baseline) +
                        ". Events captured: " + auditCapture.cloudEvents().size());
            }
            loop.requestReconciliation(TENANT);
            TimeUnit.MILLISECONDS.sleep(200);
        }
    }
}
