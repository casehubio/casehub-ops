package io.casehub.ops.deployment.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.AdaptationActionSpec;
import io.casehub.ops.api.deployment.AdaptationRuleSpec;
import io.casehub.ops.api.deployment.AdaptationTrigger;
import io.casehub.ops.api.deployment.AgentNodeSpec;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ops.api.deployment.GoalEntry;
import io.casehub.ops.api.deployment.TrustPolicyNodeSpec;
import io.casehub.ops.deployment.DeploymentGoalCompiler;
import io.casehub.ras.api.ActiveSituation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentAdaptiveSituationRecompilerTest {

    private final DesiredStateGraphFactory graphFactory = new DefaultDesiredStateGraphFactory();
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentGoalCompiler compiler = new DeploymentGoalCompiler();
    private final ActualState emptyActual = new ActualState(Map.of());

    private DeploymentAdaptiveSituationRecompiler recompiler;
    private DeploymentGoals goalsWithAdaptations;
    private DesiredStateGraph baseGraph;

    @BeforeEach
    void setUp() {
        recompiler = new DeploymentAdaptiveSituationRecompiler();
        recompiler.compiler = compiler;
        recompiler.mapper = mapper;

        var scaleTrigger = new AdaptationTrigger("volatility-spike", 0.7, 0.5, Duration.ofMinutes(5));
        var scaleAction = new AdaptationActionSpec.ScaleActionSpec("risk-agent", 1, 5);
        var scaleRule = new AdaptationRuleSpec("scale-risk", scaleTrigger, List.of(scaleAction));

        var updateTrigger = new AdaptationTrigger("market-anomaly", 0.6, null, null);
        var updateAction = new AdaptationActionSpec.UpdateActionSpec("trade-execution", "trust",
            Map.of("threshold", 0.9));
        var updateRule = new AdaptationRuleSpec("tighten-trust", updateTrigger, List.of(updateAction));

        goalsWithAdaptations = new DeploymentGoals(
            List.of(new GoalEntry<>(new AgentNodeSpec("risk-agent", "Risk Monitor",
                "worker", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null), null)),
            List.of(), List.of(),
            List.of(new GoalEntry<>(new TrustPolicyNodeSpec("trade-execution", 0.7, 5,
                0.05, 0.5, Map.of(), false), null)),
            List.of(), List.of(),
            List.of(scaleRule, updateRule));

        baseGraph = extractGraph(compiler.compile(goalsWithAdaptations, graphFactory));
    }

    @Test
    void recompileReturnsEmptyForUnregisteredTenant() {
        var situation = new ActiveSituation("volatility-spike", "k1", "unknown",
            0.85, Map.of(), Instant.now(), Instant.now(), 1);

        var result = recompiler.recompile("unknown", baseGraph, emptyActual,
            situation, graphFactory);

        assertThat(result).isEmpty();
    }

    @Test
    void recompileReturnsEmptyForUnrelatedSituation() {
        recompiler.register("t1", goalsWithAdaptations,
            Map.of("volatility-spike", Duration.ofMinutes(30)), graphFactory);

        var situation = new ActiveSituation("unrelated", "k1", "t1",
            0.9, Map.of(), Instant.now(), Instant.now(), 1);

        var result = recompiler.recompile("t1", baseGraph, emptyActual,
            situation, graphFactory);

        assertThat(result).isEmpty();
    }

    @Test
    void recompileScalesRiskAgentOnVolatilitySpike() {
        recompiler.register("t1", goalsWithAdaptations,
            Map.of("volatility-spike", Duration.ofMinutes(30)), graphFactory);

        var situation = new ActiveSituation("volatility-spike", "k1", "t1",
            1.0, Map.of(), Instant.now(), Instant.now(), 1);

        var result = recompiler.recompile("t1", baseGraph, emptyActual,
            situation, graphFactory);

        assertThat(result).isPresent();
        var adapted = extractGraph(result.get());
        assertThat(adapted.nodes()).containsKey(NodeId.of("risk-agent"));
        assertThat(adapted.nodes()).containsKey(NodeId.of("risk-agent~2"));
        assertThat(adapted.nodes()).containsKey(NodeId.of("risk-agent~3"));
        assertThat(adapted.nodes()).containsKey(NodeId.of("risk-agent~4"));
        assertThat(adapted.nodes()).containsKey(NodeId.of("risk-agent~5"));
    }

    @Test
    void recompileComposesMultipleActiveSituations() {
        recompiler.register("t1", goalsWithAdaptations,
            Map.of("volatility-spike", Duration.ofMinutes(30),
                   "market-anomaly", Duration.ofMinutes(15)), graphFactory);

        var volatility = new ActiveSituation("volatility-spike", "k1", "t1",
            1.0, Map.of(), Instant.now(), Instant.now(), 1);
        recompiler.recompile("t1", baseGraph, emptyActual, volatility, graphFactory);

        var anomaly = new ActiveSituation("market-anomaly", "k2", "t1",
            0.7, Map.of(), Instant.now(), Instant.now(), 1);
        var result = recompiler.recompile("t1", baseGraph, emptyActual,
            anomaly, graphFactory);

        assertThat(result).isPresent();
        var adapted = extractGraph(result.get());
        assertThat(adapted.nodes()).containsKey(NodeId.of("risk-agent~2"));
        assertThat(adapted.nodes()).containsKey(NodeId.of("trade-execution"));
    }

    @Test
    void recompileReturnsEmptyWhenGraphUnchanged() {
        var goalsNoAdaptations = new DeploymentGoals(
            List.of(new GoalEntry<>(new AgentNodeSpec("risk-agent", "Risk Monitor",
                "worker", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null), null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        recompiler.register("t1", goalsNoAdaptations, Map.of(), graphFactory);

        var situation = new ActiveSituation("unrelated", "k1", "t1",
            0.9, Map.of(), Instant.now(), Instant.now(), 1);
        var base = extractGraph(compiler.compile(goalsNoAdaptations, graphFactory));
        var result = recompiler.recompile("t1", base, emptyActual,
            situation, graphFactory);

        assertThat(result).isEmpty();
    }

    @Test
    void priorityIs100() {
        assertThat(recompiler.priority()).isEqualTo(100);
    }

    private DesiredStateGraph extractGraph(CompilationResult result) {
        if (result instanceof CompilationResult.SingleGraph single) {
            return single.graph();
        }
        throw new AssertionError("Expected CompilationResult.Single, got: " + result.getClass());
    }
}
