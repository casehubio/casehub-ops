package io.casehub.ops.deployment.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ops.deployment.DeploymentGoalCompiler;
import io.casehub.ops.deployment.DeploymentGoalLoader;
import io.casehub.ras.api.ActiveSituation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveSituationRecompilerIntegrationTest {

    private final DesiredStateGraphFactory graphFactory = new DefaultDesiredStateGraphFactory();
    private final ActualState emptyActual = new ActualState(Map.of());

    private DeploymentAdaptiveSituationRecompiler recompiler;
    private DeploymentGoalCompiler compiler;
    private DeploymentGoals goals;

    @BeforeEach
    void setUp() {
        compiler = new DeploymentGoalCompiler();
        recompiler = new DeploymentAdaptiveSituationRecompiler();
        recompiler.compiler = compiler;
        recompiler.mapper = new ObjectMapper();

        var loader = new DeploymentGoalLoader();
        goals = loader.load("test-fsitrading-deployment.yaml");

        recompiler.register("t1", goals,
                Map.of("fsitrading.volatility-spike", Duration.ofMinutes(30),
                        "fsitrading.market-anomaly", Duration.ofMinutes(15),
                        "fsitrading.active-breach", Duration.ofHours(2)),
                graphFactory);
    }

    @Test
    void recompileReturnsEmptyForUnregisteredTenant() {
        var situation = activeSituation("fsitrading.volatility-spike", 0.85);
        var base = compileBase();
        var result = recompiler.recompile("unknown", base, emptyActual, situation, graphFactory);
        assertThat(result).isEmpty();
    }

    @Test
    void volatilitySpikeScalesRiskAgent() {
        var situation = activeSituation("fsitrading.volatility-spike", 0.85);
        var base = compileBase();

        var result = recompiler.recompile("t1", base, emptyActual, situation, graphFactory);

        assertThat(result).isPresent();
        var adapted = ((CompilationResult.SingleGraph) result.get()).graph();
        assertThat(adapted.nodes()).containsKey(NodeId.of("risk-agent"));
        assertThat(adapted.nodes()).containsKey(NodeId.of("risk-agent~2"));
    }

    @Test
    void multipleSituationsComposeCorrectly() {
        var base = compileBase();

        var volatility = activeSituation("fsitrading.volatility-spike", 0.85);
        var result1 = recompiler.recompile("t1", base, emptyActual, volatility, graphFactory);
        assertThat(result1).isPresent();
        var afterScale = ((CompilationResult.SingleGraph) result1.get()).graph();
        assertThat(afterScale.nodes()).containsKey(NodeId.of("risk-agent~2"));

        var anomaly = activeSituation("fsitrading.market-anomaly", 0.7);
        var result2 = recompiler.recompile("t1", afterScale, emptyActual, anomaly, graphFactory);
        assertThat(result2).isPresent();
        var afterBoth = ((CompilationResult.SingleGraph) result2.get()).graph();
        assertThat(afterBoth.nodes()).containsKey(NodeId.of("risk-agent~2"));
        assertThat(afterBoth.nodes()).containsKey(NodeId.of("trade-execution"));
    }

    @Test
    void unrelatedSituationReturnsEmpty() {
        var base = compileBase();
        var unrelated = activeSituation("unrelated.situation", 0.9);

        var result = recompiler.recompile("t1", base, emptyActual, unrelated, graphFactory);
        assertThat(result).isEmpty();
    }

    @Test
    void belowMinConfidenceDoesNotTrigger() {
        var base = compileBase();
        var lowConfidence = activeSituation("fsitrading.volatility-spike", 0.3);

        var result = recompiler.recompile("t1", base, emptyActual, lowConfidence, graphFactory);
        assertThat(result).isEmpty();
    }

    private DesiredStateGraph compileBase() {
        var result = compiler.compile(goals, graphFactory);
        return ((CompilationResult.SingleGraph) result).graph();
    }

    private static ActiveSituation activeSituation(String situationId, double confidence) {
        return new ActiveSituation(situationId, "key1", "t1",
                confidence, Map.of(), Instant.now(), Instant.now(), 1);
    }
}
