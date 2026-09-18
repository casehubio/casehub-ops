package io.casehub.ops.deployment.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ras.api.ActiveSituation;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.AdaptationActionSpec;
import io.casehub.ops.api.deployment.AdaptationRuleSpec;
import io.casehub.ops.api.deployment.AdaptationTrigger;
import io.casehub.ops.api.deployment.AgentNodeSpec;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ops.api.deployment.GoalEntry;
import io.casehub.ops.deployment.DeploymentGoalCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAdaptationStateTest {

    private final DesiredStateGraphFactory factory = new DefaultDesiredStateGraphFactory();
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentGoalCompiler compiler = new DeploymentGoalCompiler();

    private AdaptationRule scaleRule;
    private DeploymentGoals goals;

    @BeforeEach
    void setUp() {
        var trigger = new AdaptationTrigger("volatility-spike", 0.7, 0.5, Duration.ofMinutes(5));
        var scaleAction = new AdaptationActionSpec.ScaleActionSpec("risk-agent", 1, 5);
        var ruleSpec = new AdaptationRuleSpec("scale-risk", trigger, List.of(scaleAction));

        goals = new DeploymentGoals(
            List.of(new GoalEntry<>(new AgentNodeSpec("risk-agent", "Risk Monitor",
                "worker", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null), null)),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(ruleSpec));

        scaleRule = AdaptationRule.fromSpecs(List.of(ruleSpec), compiler, mapper, factory).get(0);
    }

    @Test
    void updateSituationTracksAndRetrievesBySituationId() {
        var state = new TenantAdaptationState(goals, List.of(scaleRule),
            Map.of("volatility-spike", Duration.ofMinutes(30)));
        var situation = new ActiveSituation("volatility-spike", "key1", "t1",
            0.85, Map.of(), Instant.now(), Instant.now(), 1);

        state.updateSituation(situation);

        Optional<ActiveSituation> found = state.activeSituationFor(scaleRule);
        assertThat(found).isPresent();
        assertThat(found.get().situationId()).isEqualTo("volatility-spike");
        assertThat(found.get().confidence()).isEqualTo(0.85);
    }

    @Test
    void activeSituationForReturnsEmptyWhenNoMatch() {
        var state = new TenantAdaptationState(goals, List.of(scaleRule),
            Map.of("volatility-spike", Duration.ofMinutes(30)));

        assertThat(state.activeSituationFor(scaleRule)).isEmpty();
    }

    @Test
    void updateSituationReplacesExisting() {
        var state = new TenantAdaptationState(goals, List.of(scaleRule),
            Map.of("volatility-spike", Duration.ofMinutes(30)));

        var first = new ActiveSituation("volatility-spike", "key1", "t1",
            0.75, Map.of(), Instant.now(), Instant.now(), 1);
        state.updateSituation(first);

        var second = new ActiveSituation("volatility-spike", "key1", "t1",
            0.95, Map.of(), Instant.now(), Instant.now(), 2);
        state.updateSituation(second);

        assertThat(state.activeSituationFor(scaleRule).get().confidence()).isEqualTo(0.95);
    }

    @Test
    void clearSituationRemovesTrackedSituationAndResetsRuleState() {
        var state = new TenantAdaptationState(goals, List.of(scaleRule),
            Map.of("volatility-spike", Duration.ofMinutes(30)));
        var situation = new ActiveSituation("volatility-spike", "key1", "t1",
            0.85, Map.of(), Instant.now(), Instant.now(), 1);

        state.updateSituation(situation);
        state.shouldActivate(scaleRule, situation);

        boolean cleared = state.clearSituation("volatility-spike");
        assertThat(cleared).isTrue();
        assertThat(state.activeSituationFor(scaleRule)).isEmpty();

        boolean clearedAgain = state.clearSituation("volatility-spike");
        assertThat(clearedAgain).isFalse();
    }

    @Test
    void clearAbsentSituationsRemovesStaleSituations() {
        var state = new TenantAdaptationState(goals, List.of(scaleRule),
            Map.of("volatility-spike", Duration.ofSeconds(1)));
        var stale = new ActiveSituation("volatility-spike", "key1", "t1",
            0.85, Map.of(), Instant.now().minusSeconds(10),
            Instant.now().minusSeconds(5), 1);

        state.updateSituation(stale);
        state.shouldActivate(scaleRule, stale);

        state.clearAbsentSituations();
        assertThat(state.activeSituationFor(scaleRule)).isEmpty();
    }

    @Test
    void clearAbsentSituationsPreservesFreshSituations() {
        var state = new TenantAdaptationState(goals, List.of(scaleRule),
            Map.of("volatility-spike", Duration.ofMinutes(30)));
        var fresh = new ActiveSituation("volatility-spike", "key1", "t1",
            0.85, Map.of(), Instant.now(), Instant.now(), 1);

        state.updateSituation(fresh);

        state.clearAbsentSituations();
        assertThat(state.activeSituationFor(scaleRule)).isPresent();
    }

    @Test
    void clearAbsentSituationsUsesDefaultWindowWhenNotConfigured() {
        var state = new TenantAdaptationState(goals, List.of(scaleRule), Map.of());
        var stale = new ActiveSituation("volatility-spike", "key1", "t1",
            0.85, Map.of(), Instant.now().minusSeconds(600),
            Instant.now().minusSeconds(400), 1);

        state.updateSituation(stale);
        state.shouldActivate(scaleRule, stale);

        state.clearAbsentSituations();
        assertThat(state.activeSituationFor(scaleRule)).isEmpty();
    }
}
