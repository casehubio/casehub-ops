package io.casehub.ops.service.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ras.api.ActiveSituation;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.AdaptationActionSpec;
import io.casehub.ops.api.deployment.AdaptationRuleSpec;
import io.casehub.ops.api.deployment.LlmDecision;
import io.casehub.ops.api.deployment.LlmEvaluatedTrigger;
import io.casehub.ops.api.deployment.PatternTrigger;
import io.casehub.ops.api.deployment.AgentNodeSpec;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ops.api.deployment.GoalEntry;
import io.casehub.desiredstate.api.GoalCompiler;
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
    private final GoalCompiler<DeploymentGoals> compiler = (goals, f) -> {
        var nodes = new java.util.ArrayList<io.casehub.desiredstate.api.DesiredNode>();
        var deps = new java.util.ArrayList<io.casehub.desiredstate.api.Dependency>();
        for (var e : goals.agents()) { nodes.add(new io.casehub.desiredstate.api.DesiredNode(io.casehub.desiredstate.api.NodeId.of(e.spec().nodeId()), e.spec(), io.casehub.desiredstate.api.HumanGating.NONE)); }
        return io.casehub.desiredstate.api.CompilationResult.single(f.of(nodes, deps));
    };

    private AdaptationRule  scaleRule;
    private DeploymentGoals goals;

    @BeforeEach
    void setUp() {
        var trigger = new PatternTrigger("volatility-spike", 0.7, 0.5, Duration.ofMinutes(5));
        var scaleAction = new AdaptationActionSpec.ScaleActionSpec("risk-agent", 1, 5);
        var ruleSpec = new AdaptationRuleSpec("scale-risk", trigger, List.of(scaleAction));

        goals = new DeploymentGoals(
            List.of(new GoalEntry<>(new AgentNodeSpec("risk-agent", "Risk Monitor",
                "worker", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null), null)),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(),
            List.of(),
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

    // --- LLM trigger tests ---

    private AdaptationRule createLlmRule(String name) {
        var trigger = new LlmEvaluatedTrigger(
                "deployer", "catalog", 0.5, Duration.ofMinutes(1), Duration.ofSeconds(30));
        var scaleAction = new AdaptationActionSpec.ScaleActionSpec("risk-agent", 1, 5);
        var ruleSpec = new AdaptationRuleSpec(name, trigger, List.of(scaleAction));
        return AdaptationRule.fromSpecs(List.of(ruleSpec), compiler, mapper, factory).get(0);
    }

    @Test
    void allQualifyingSituationsReturnsMatchingSituations() {
        var llmRule = createLlmRule("llm-rule");
        var state = new TenantAdaptationState(goals, List.of(llmRule));

        var high = new ActiveSituation("surge-1", "k1", "t1", 0.8, Map.of(), Instant.now(), Instant.now(), 0);
        var medium = new ActiveSituation("surge-2", "k2", "t1", 0.6, Map.of(), Instant.now(), Instant.now(), 0);
        var low = new ActiveSituation("minor", "k3", "t1", 0.3, Map.of(), Instant.now(), Instant.now(), 0);

        state.updateSituation(high);
        state.updateSituation(medium);
        state.updateSituation(low);

        List<ActiveSituation> qualifying = state.allQualifyingSituations(llmRule);
        assertThat(qualifying).hasSize(2);
        assertThat(qualifying).extracting(ActiveSituation::situationId)
                .containsExactlyInAnyOrder("surge-1", "surge-2");
    }

    @Test
    void cacheLlmDecisionAndRetrieve() {
        var llmRule = createLlmRule("llm-rule");
        var state = new TenantAdaptationState(goals, List.of(llmRule));

        var decision = new LlmDecision("deploy", Map.of("name", "specialist-x"));
        state.cacheLlmDecision("llm-rule", "surge-1", decision);

        Optional<LlmDecision> cached = state.getCachedDecision("llm-rule", "surge-1");
        assertThat(cached).isPresent();
        assertThat(cached.get().isDeploy()).isTrue();
        assertThat(cached.get().selectedCatalogEntry().get("name")).isEqualTo("specialist-x");
    }

    @Test
    void getCachedDecisionsReturnsAllForRule() {
        var llmRule = createLlmRule("llm-rule");
        var state = new TenantAdaptationState(goals, List.of(llmRule));

        state.cacheLlmDecision("llm-rule", "surge-1", new LlmDecision("deploy", Map.of("name", "a")));
        state.cacheLlmDecision("llm-rule", "surge-2", new LlmDecision("deploy", Map.of("name", "b")));

        Map<String, LlmDecision> all = state.getCachedDecisions("llm-rule");
        assertThat(all).hasSize(2);
        assertThat(all).containsKeys("surge-1", "surge-2");
    }

    @Test
    void rejectDecisionInvalidatedOnConfidenceChange() {
        var llmRule = createLlmRule("llm-rule");
        var state = new TenantAdaptationState(goals, List.of(llmRule));

        var sit = new ActiveSituation("surge-1", "k1", "t1", 0.6, Map.of(), Instant.now(), Instant.now(), 0);
        state.updateSituation(sit);
        state.cacheLlmDecision("llm-rule", "surge-1", new LlmDecision("reject", null));

        var updated = new ActiveSituation("surge-1", "k1", "t1", 0.95, Map.of(), Instant.now(), Instant.now(), 1);
        state.updateSituation(updated);

        assertThat(state.getCachedDecision("llm-rule", "surge-1")).isEmpty();
    }

    @Test
    void deployDecisionRetainedOnConfidenceChange() {
        var llmRule = createLlmRule("llm-rule");
        var state = new TenantAdaptationState(goals, List.of(llmRule));

        var sit = new ActiveSituation("surge-1", "k1", "t1", 0.6, Map.of(), Instant.now(), Instant.now(), 0);
        state.updateSituation(sit);
        state.cacheLlmDecision("llm-rule", "surge-1", new LlmDecision("deploy", Map.of("name", "x")));

        var updated = new ActiveSituation("surge-1", "k1", "t1", 0.95, Map.of(), Instant.now(), Instant.now(), 1);
        state.updateSituation(updated);

        assertThat(state.getCachedDecision("llm-rule", "surge-1")).isPresent();
        assertThat(state.getCachedDecision("llm-rule", "surge-1").get().isDeploy()).isTrue();
    }

    @Test
    void clearSituationRemovesCachedDecision() {
        var llmRule = createLlmRule("llm-rule");
        var state = new TenantAdaptationState(goals, List.of(llmRule));

        var sit = new ActiveSituation("surge-1", "k1", "t1", 0.8, Map.of(), Instant.now(), Instant.now(), 0);
        state.updateSituation(sit);
        state.cacheLlmDecision("llm-rule", "surge-1", new LlmDecision("deploy", Map.of("name", "x")));

        state.clearSituation("surge-1");

        assertThat(state.getCachedDecision("llm-rule", "surge-1")).isEmpty();
    }

    @Test
    void shouldActivateUsesCompositeKeyForLlmTrigger() {
        var trigger = new LlmEvaluatedTrigger(
                "deployer", "catalog", 0.5, Duration.ofHours(1), Duration.ofSeconds(30));
        var scaleAction = new AdaptationActionSpec.ScaleActionSpec("risk-agent", 1, 5);
        var ruleSpec = new AdaptationRuleSpec("llm-cooldown", trigger, List.of(scaleAction));
        var llmRule = AdaptationRule.fromSpecs(List.of(ruleSpec), compiler, mapper, factory).get(0);
        var state = new TenantAdaptationState(goals, List.of(llmRule));

        var sit1 = new ActiveSituation("surge-1", "k1", "t1", 0.8, Map.of(), Instant.now(), Instant.now(), 0);
        var sit2 = new ActiveSituation("surge-2", "k2", "t1", 0.9, Map.of(), Instant.now(), Instant.now(), 0);

        state.updateSituation(sit1);
        state.updateSituation(sit2);

        assertThat(state.shouldActivate(llmRule, sit1)).isTrue();
        assertThat(state.shouldActivate(llmRule, sit2)).isTrue();
    }

    @Test
    void baseGraphStoredAndRetrieved() {
        var llmRule = createLlmRule("llm-rule");
        DesiredStateGraph graph = factory.of(List.of(), List.of());
        var state = new TenantAdaptationState(graph, List.of(llmRule), Map.of());

        assertThat(state.baseGraph()).isSameAs(graph);
    }
}
