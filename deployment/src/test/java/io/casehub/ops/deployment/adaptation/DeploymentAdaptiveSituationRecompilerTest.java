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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

    private SimpleMeterRegistry meterRegistry;
    private DeploymentAdaptiveSituationRecompiler recompiler;
    private DeploymentGoals goalsWithAdaptations;
    private DesiredStateGraph baseGraph;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        recompiler = new DeploymentAdaptiveSituationRecompiler();
        recompiler.compiler = compiler;
        recompiler.mapper = mapper;
        recompiler.meterRegistry = meterRegistry;

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
            List.of(),
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
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

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

    @Test
    void situationResolvedClearsSituationAndRecompilesFromBase() {
        recompiler.register("t1", goalsWithAdaptations,
                            Map.of("volatility-spike", Duration.ofMinutes(30),
                                   "market-anomaly", Duration.ofMinutes(15)), graphFactory);

        var volatility = new ActiveSituation("volatility-spike", "k1", "t1",
                                             1.0, Map.of(), Instant.now(), Instant.now(), 1);
        recompiler.recompile("t1", baseGraph, emptyActual, volatility, graphFactory);

        var anomaly = new ActiveSituation("market-anomaly", "k2", "t1",
                                          0.7, Map.of(), Instant.now(), Instant.now(), 1);
        var adapted = extractGraph(recompiler.recompile("t1", baseGraph, emptyActual,
                                                        anomaly, graphFactory).orElseThrow());

        assertThat(adapted.nodes()).containsKey(NodeId.of("risk-agent~2"));
        assertThat(adapted.nodes()).containsKey(NodeId.of("trade-execution"));

        var afterResolve = recompiler.situationResolved("t1", "volatility-spike",
                                                        adapted, emptyActual, graphFactory);

        assertThat(afterResolve).isPresent();
        var resolved = extractGraph(afterResolve.get());
        assertThat(resolved.nodes()).doesNotContainKey(NodeId.of("risk-agent~2"));
        assertThat(resolved.nodes()).containsKey(NodeId.of("trade-execution"));
    }

    @Test
    void situationResolvedReturnsEmptyForUnknownSituation() {
        recompiler.register("t1", goalsWithAdaptations,
                            Map.of("volatility-spike", Duration.ofMinutes(30)), graphFactory);

        var result = recompiler.situationResolved("t1", "never-tracked",
                                                  baseGraph, emptyActual, graphFactory);

        assertThat(result).isEmpty();
    }

    @Test
    void situationResolvedReturnsEmptyForUnregisteredTenant() {
        var result = recompiler.situationResolved("unknown", "volatility-spike",
                                                  baseGraph, emptyActual, graphFactory);

        assertThat(result).isEmpty();
    }


    @Test
    void recompileIncrementsConflictCounterWhenRulesOverlap() {
        var trigger     = new AdaptationTrigger("volatility-spike", 0.7, null, null);
        var scaleAction = new AdaptationActionSpec.ScaleActionSpec("risk-agent", 1, 3);
        var rule1       = new AdaptationRuleSpec("scale-risk", trigger, List.of(scaleAction));

        var updateAction = new AdaptationActionSpec.UpdateActionSpec("risk-agent", null,
                                                                     Map.of("name", "Updated Risk Monitor"));
        var rule2 = new AdaptationRuleSpec("update-risk", trigger, List.of(updateAction));

        var goals = new DeploymentGoals(
                List.of(new GoalEntry<>(new AgentNodeSpec("risk-agent", "Risk Monitor",
                                                          "worker", null, null, null, null, null, null, null,
                                                          null, null, null, null, null, null, null, null, null), null)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                List.of(rule1, rule2));

        recompiler.register("t1", goals,
                            Map.of("volatility-spike", Duration.ofMinutes(30)), graphFactory);

        var base = extractGraph(compiler.compile(goals, graphFactory));
        var situation = new ActiveSituation("volatility-spike", "k1", "t1",
                                            1.0, Map.of(), Instant.now(), Instant.now(), 1);

        recompiler.recompile("t1", base, emptyActual, situation, graphFactory);

        var counter = meterRegistry.find("desiredstate.adaptation.conflict.total")
                                   .tag("tenancy_id", "t1")
                                   .tag("rule_name", "update-risk")
                                   .tag("node_id", "risk-agent")
                                   .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    private DesiredStateGraph extractGraph(CompilationResult result) {
        if (result instanceof CompilationResult.SingleGraph single) {
            return single.graph();
        }
        throw new AssertionError("Expected CompilationResult.Single, got: " + result.getClass());
    }
}
