package io.casehub.ops.service.adaptation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ops.api.deployment.LlmDecision;
import io.casehub.ops.api.deployment.LlmEvaluatedTrigger;
import io.casehub.ras.api.ActiveSituation;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TenantAdaptationState {

    private final DeploymentGoals      goals;
    private final DesiredStateGraph    baseGraph;
    private final List<AdaptationRule> rules;
    private final Map<String, Boolean> activePerRule = new HashMap<>();
    private final Map<String, Instant> lastChangePerRule = new HashMap<>();
    private final Map<String, ActiveSituation> trackedSituations = new HashMap<>();
    private final Map<String, Duration> situationClearanceWindows;
    private final Map<String, Map<String, LlmDecision>> llmDecisionCache = new HashMap<>();

    public TenantAdaptationState(DeploymentGoals goals, List<AdaptationRule> rules,
                          Map<String, Duration> situationClearanceWindows) {
        this.goals = Objects.requireNonNull(goals, "goals");
        this.baseGraph = null;
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        this.situationClearanceWindows = Map.copyOf(
            Objects.requireNonNull(situationClearanceWindows, "situationClearanceWindows"));
    }

    public TenantAdaptationState(DeploymentGoals goals, List<AdaptationRule> rules) {
        this(goals, rules, Map.of());
    }

    public TenantAdaptationState(DesiredStateGraph baseGraph, List<AdaptationRule> rules,
                          Map<String, Duration> situationClearanceWindows) {
        this.goals = null;
        this.baseGraph = Objects.requireNonNull(baseGraph, "baseGraph");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        this.situationClearanceWindows = Map.copyOf(
            Objects.requireNonNull(situationClearanceWindows, "situationClearanceWindows"));
    }

    public DeploymentGoals goals() {
        return goals;
    }

    public DesiredStateGraph baseGraph() {
        return baseGraph;
    }

    public List<AdaptationRule> rules() {
        return rules;
    }

    public void updateSituation(ActiveSituation situation) {
        ActiveSituation previous = trackedSituations.get(situation.situationId());
        trackedSituations.put(situation.situationId(), situation);
        if (previous != null && previous.confidence() != situation.confidence()) {
            for (var ruleCache : llmDecisionCache.values()) {
                LlmDecision cached = ruleCache.get(situation.situationId());
                if (cached != null && cached.isReject()) {
                    ruleCache.remove(situation.situationId());
                }
            }
        }
    }

    public Optional<ActiveSituation> activeSituationFor(AdaptationRule rule) {
        if (rule.trigger() instanceof io.casehub.ops.api.deployment.PatternTrigger pt) {
            return Optional.ofNullable(trackedSituations.get(pt.situation()));
        }
        throw new UnsupportedOperationException(
                "Use allQualifyingSituations() for LLM triggers");
    }

    public List<ActiveSituation> allQualifyingSituations(AdaptationRule rule) {
        if (!(rule.trigger() instanceof LlmEvaluatedTrigger lt)) {
            throw new UnsupportedOperationException(
                    "allQualifyingSituations() is only for LLM triggers");
        }
        return trackedSituations.values().stream()
                .filter(s -> s.confidence() >= lt.minConfidence())
                .toList();
    }

    public void cacheLlmDecision(String ruleName, String situationId, LlmDecision decision) {
        llmDecisionCache.computeIfAbsent(ruleName, k -> new HashMap<>())
                .put(situationId, decision);
    }

    public Optional<LlmDecision> getCachedDecision(String ruleName, String situationId) {
        Map<String, LlmDecision> ruleCache = llmDecisionCache.get(ruleName);
        if (ruleCache == null) return Optional.empty();
        return Optional.ofNullable(ruleCache.get(situationId));
    }

    public Map<String, LlmDecision> getCachedDecisions(String ruleName) {
        Map<String, LlmDecision> ruleCache = llmDecisionCache.get(ruleName);
        return ruleCache != null ? Map.copyOf(ruleCache) : Map.of();
    }

    public boolean clearSituation(String situationId) {
        ActiveSituation removed = trackedSituations.remove(situationId);
        if (removed == null) return false;
        for (AdaptationRule rule : rules) {
            if (rule.trigger() instanceof io.casehub.ops.api.deployment.PatternTrigger pt && pt.situation().equals(situationId)) {
                activePerRule.put(rule.name(), false);
                lastChangePerRule.put(rule.name(), Instant.now());
            }
        }
        for (var ruleCache : llmDecisionCache.values()) {
            ruleCache.remove(situationId);
        }
        return true;
    }

    public boolean shouldActivate(AdaptationRule rule, ActiveSituation situation) {
        if (rule.trigger() instanceof io.casehub.ops.api.deployment.LlmEvaluatedTrigger lt) {
            String compositeKey = rule.name() + ":" + situation.situationId();
            boolean currentlyActive = activePerRule.getOrDefault(compositeKey, false);
            boolean shouldBeActive = situation.confidence() >= lt.minConfidence();

            if (shouldBeActive != currentlyActive) {
                Duration cooldownDuration = lt.effectiveCooldown();
                if (!cooldownDuration.isZero()) {
                    Instant lastChange = lastChangePerRule.get(compositeKey);
                    if (lastChange != null) {
                        Duration elapsed = Duration.between(lastChange, Instant.now());
                        if (elapsed.compareTo(cooldownDuration) < 0) {
                            return currentlyActive;
                        }
                    }
                }
                lastChangePerRule.put(compositeKey, Instant.now());
                activePerRule.put(compositeKey, shouldBeActive);
            }
            return shouldBeActive;
        }

        if (rule.trigger() instanceof io.casehub.ops.api.deployment.PatternTrigger pt) {
            boolean currentlyActive = activePerRule.getOrDefault(rule.name(), false);
            double threshold = currentlyActive ? pt.effectiveDeactivateBelow() : pt.minConfidence();
            Duration cooldownDuration = pt.effectiveCooldown();

            boolean shouldBeActive = situation.confidence() >= threshold;

            if (shouldBeActive != currentlyActive) {
                if (!cooldownDuration.isZero()) {
                    Instant lastChange = lastChangePerRule.get(rule.name());
                    if (lastChange != null) {
                        Duration elapsed = Duration.between(lastChange, Instant.now());
                        if (elapsed.compareTo(cooldownDuration) < 0) {
                            return currentlyActive;
                        }
                    }
                }
                lastChangePerRule.put(rule.name(), Instant.now());
                activePerRule.put(rule.name(), shouldBeActive);
            }
            return shouldBeActive;
        }

        return false;
    }


    public boolean isRuleActive(String ruleName) {
        return activePerRule.getOrDefault(ruleName, false);
    }


    public Optional<ActiveSituation> getTrackedSituation(String situationId) {
        return Optional.ofNullable(trackedSituations.get(situationId));
    }

    public List<ActiveSituation> trackedSituationSnapshot() {
        return List.copyOf(trackedSituations.values());
    }


    public void clearAbsentSituations() {
        Instant now = Instant.now();
        var iter = trackedSituations.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            String sitId = entry.getKey();
            ActiveSituation sit = entry.getValue();
            Duration clearanceWindow = situationClearanceWindows.getOrDefault(
                sitId, Duration.ofMinutes(5));
            if (Duration.between(sit.lastSignal(), now).compareTo(clearanceWindow) > 0) {
                for (AdaptationRule rule : rules) {
                    if (rule.trigger() instanceof io.casehub.ops.api.deployment.PatternTrigger pt && pt.situation().equals(sitId)) {
                        Boolean wasActive = activePerRule.get(rule.name());
                        if (wasActive != null && wasActive) {
                            Duration cd = pt.effectiveCooldown();
                            if (!cd.isZero()) {
                                Instant lastChange = lastChangePerRule.get(rule.name());
                                if (lastChange != null &&
                                    Duration.between(lastChange, now).compareTo(cd) < 0) {
                                    continue;
                                }
                            }
                            activePerRule.put(rule.name(), false);
                            lastChangePerRule.put(rule.name(), now);
                        }
                    }
                }
                for (var ruleCache : llmDecisionCache.values()) {
                    ruleCache.remove(sitId);
                }
                iter.remove();
            }
        }
    }
}
