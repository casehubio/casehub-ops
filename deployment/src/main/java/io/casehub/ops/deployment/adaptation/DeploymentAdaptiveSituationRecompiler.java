package io.casehub.ops.deployment.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.SituationRecompiler;
import io.casehub.ops.api.deployment.AdaptationSnapshotProvider;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ops.deployment.DeploymentGoalCompiler;
import io.casehub.ops.service.adaptation.AdaptationRule;
import io.casehub.ops.service.adaptation.LlmTriggerEvaluator;
import io.casehub.ops.service.adaptation.TenantAdaptationState;
import io.casehub.ras.api.ActiveSituation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class DeploymentAdaptiveSituationRecompiler implements SituationRecompiler, AdaptationSnapshotProvider {

    private static final Logger                                                             LOG                = Logger.getLogger(
            DeploymentAdaptiveSituationRecompiler.class.getName());
    static final         Map<String, Class<? extends io.casehub.desiredstate.api.NodeSpec>> NODE_TYPE_REGISTRY = Map.of(
            "agent", io.casehub.ops.api.deployment.AgentNodeSpec.class,
            "channel", io.casehub.ops.api.deployment.ChannelNodeSpec.class,
            "case_type", io.casehub.ops.api.deployment.CaseTypeNodeSpec.class,
            "trust_policy", io.casehub.ops.api.deployment.TrustPolicyNodeSpec.class,
            "endpoint", io.casehub.ops.api.deployment.EndpointNodeSpec.class,
            "detection", io.casehub.ops.api.deployment.DetectionNodeSpec.class,
            "pool", io.casehub.ops.api.deployment.PoolNodeSpec.class
                                                                                                                       );


    @Inject
    DeploymentGoalCompiler compiler;
    @Inject
    ObjectMapper           mapper;
    @Inject
    MeterRegistry          meterRegistry;

    private final ConcurrentHashMap<String, TenantAdaptationState> tenantStates =
            new ConcurrentHashMap<>();
    private       LlmTriggerEvaluator                              llmEvaluator;

    public void setLlmEvaluator(LlmTriggerEvaluator evaluator) {
        this.llmEvaluator = evaluator;
    }


    @Override
    public int priority() {
        return 100;
    }

    public void register(String tenancyId, DeploymentGoals goals,
                         Map<String, Duration> situationClearanceWindows,
                         DesiredStateGraphFactory factory) {
        List<AdaptationRule> rules = AdaptationRule.fromSpecs(
                goals.adaptations(), compiler, mapper, factory, NODE_TYPE_REGISTRY);
        var state = new TenantAdaptationState(goals, rules, situationClearanceWindows);
        tenantStates.put(tenancyId, state);
    }

    public void register(String tenancyId, DesiredStateGraph baseGraph,
                         List<AdaptationRule> rules,
                         Map<String, Duration> situationClearanceWindows) {
        var state = new TenantAdaptationState(baseGraph, rules, situationClearanceWindows);
        tenantStates.put(tenancyId, state);
    }


    @Override
    public Optional<CompilationResult> recompile(
            String tenancyId,
            DesiredStateGraph currentGraph,
            ActualState actualState,
            ActiveSituation situation,
            DesiredStateGraphFactory factory) {
        TenantAdaptationState state = tenantStates.get(tenancyId);
        if (state == null || state.rules().isEmpty()) {
            return Optional.empty();
        }

        // Phase 1: Snapshot — identify uncached LLM evaluations needed
        List<LlmEvalRequest> evalRequests;
        synchronized (state) {
            state.updateSituation(situation);
            evalRequests = collectLlmEvalRequests(state);
        }

        // Phase 2: Evaluate — call LLM outside lock
        for (LlmEvalRequest req : evalRequests) {
            if (llmEvaluator != null) {
                Optional<io.casehub.ops.api.deployment.LlmDecision> decision =
                        llmEvaluator.evaluate(req.trigger, req.situation, req.catalogEntries);
                if (decision.isPresent()) {
                    synchronized (state) {
                        state.cacheLlmDecision(req.ruleName, req.situation.situationId(), decision.get());
                    }
                }
            }
        }

        // Phase 3: Apply — rebuild adapted graph inside lock
        synchronized (state) {
            DesiredStateGraph base          = resolveBaseGraph(state, factory);
            DesiredStateGraph adapted       = base;
            Set<NodeId>       modifiedNodes = new HashSet<>();

            for (AdaptationRule rule : state.rules()) {
                if (rule.trigger() instanceof io.casehub.ops.api.deployment.PatternTrigger) {
                    Optional<ActiveSituation> match = state.activeSituationFor(rule);
                    if (match.isPresent() && state.shouldActivate(rule, match.get())) {
                        Set<NodeId> targets = rule.targetNodeIds(base);
                        logConflicts(tenancyId, rule, targets, modifiedNodes);
                        adapted = rule.apply(adapted, match.get());
                        modifiedNodes.addAll(targets);
                    }
                } else if (rule.trigger() instanceof io.casehub.ops.api.deployment.LlmEvaluatedTrigger) {
                    Map<String, io.casehub.ops.api.deployment.LlmDecision> cached =
                            state.getCachedDecisions(rule.name());
                    for (var entry : cached.entrySet()) {
                        if (entry.getValue().isDeploy()) {
                            ActiveSituation sit = findTrackedSituation(state, entry.getKey());
                            if (sit != null) {
                                adapted = rule.apply(adapted, sit, entry.getValue().selectedCatalogEntry());
                            }
                        }
                    }
                }
            }

            state.clearAbsentSituations();

            if (graphsEqual(adapted, base)) {
                return Optional.empty();
            }
            return Optional.of(CompilationResult.single(adapted));
        }
    }

    @Override
    public Optional<CompilationResult> situationResolved(
            String tenancyId,
            String situationId,
            DesiredStateGraph currentGraph,
            ActualState actualState,
            DesiredStateGraphFactory factory) {
        TenantAdaptationState state = tenantStates.get(tenancyId);
        if (state == null) {
            return Optional.empty();
        }

        synchronized (state) {
            boolean hadSituation = state.clearSituation(situationId);
            if (!hadSituation) {
                return Optional.empty();
            }

            DesiredStateGraph base    = resolveBaseGraph(state, factory);
            DesiredStateGraph adapted = base;

            for (AdaptationRule rule : state.rules()) {
                if (rule.trigger() instanceof io.casehub.ops.api.deployment.PatternTrigger) {
                    Optional<ActiveSituation> match = state.activeSituationFor(rule);
                    if (match.isPresent() && state.shouldActivate(rule, match.get())) {
                        adapted = rule.apply(adapted, match.get());
                    }
                } else if (rule.trigger() instanceof io.casehub.ops.api.deployment.LlmEvaluatedTrigger) {
                    Map<String, io.casehub.ops.api.deployment.LlmDecision> cached =
                            state.getCachedDecisions(rule.name());
                    for (var entry : cached.entrySet()) {
                        if (entry.getValue().isDeploy()) {
                            ActiveSituation sit = findTrackedSituation(state, entry.getKey());
                            if (sit != null) {
                                adapted = rule.apply(adapted, sit, entry.getValue().selectedCatalogEntry());
                            }
                        }
                    }
                }
            }

            if (graphsEqual(adapted, currentGraph)) {
                return Optional.empty();
            }
            return Optional.of(CompilationResult.single(adapted));
        }
    }

    @Override
    public AdaptationSnapshot getAdaptationSnapshot(String tenancyId) {
        TenantAdaptationState state = tenantStates.get(tenancyId);
        if (state == null) {
            return new AdaptationSnapshot(List.of(), List.of());
        }

        synchronized (state) {
            List<AdaptationSnapshotProvider.ActiveRuleInfo> ruleInfos =
                    state.rules().stream()
                         .map(rule -> {
                             if (rule.trigger() instanceof io.casehub.ops.api.deployment.PatternTrigger pt) {
                                 return new AdaptationSnapshotProvider.ActiveRuleInfo(
                                         rule.name(), "pattern",
                                         List.of(pt.situation()),
                                         state.isRuleActive(rule.name()));
                             } else {
                                 List<String> situationIds = state.getCachedDecisions(rule.name())
                                                                  .entrySet().stream()
                                                                  .filter(e -> e.getValue().isDeploy())
                                                                  .map(Map.Entry::getKey)
                                                                  .toList();
                                 return new AdaptationSnapshotProvider.ActiveRuleInfo(
                                         rule.name(), "llm-evaluated",
                                         situationIds,
                                         !situationIds.isEmpty());
                             }
                         })
                         .toList();

            List<AdaptationSnapshotProvider.TrackedSituationInfo> sitInfos =
                    state.trackedSituationSnapshot().stream()
                         .map(sit -> new AdaptationSnapshotProvider.TrackedSituationInfo(
                                 sit.situationId(), sit.confidence(),
                                 sit.since(), sit.lastSignal()))
                         .toList();

            return new AdaptationSnapshot(ruleInfos, sitInfos);
        }
    }

    private static boolean graphsEqual(DesiredStateGraph a, DesiredStateGraph b) {
        if (a == b) {return true;}
        if (a == null || b == null) {return false;}
        return a.nodes().equals(b.nodes()) && a.dependencies().equals(b.dependencies());
    }

    private record LlmEvalRequest(
            String ruleName,
            io.casehub.ops.api.deployment.LlmEvaluatedTrigger trigger,
            ActiveSituation situation,
            List<Map<String, String>> catalogEntries) {}

    private List<LlmEvalRequest> collectLlmEvalRequests(TenantAdaptationState state) {
        List<LlmEvalRequest> requests = new java.util.ArrayList<>();
        for (AdaptationRule rule : state.rules()) {
            if (rule.trigger() instanceof io.casehub.ops.api.deployment.LlmEvaluatedTrigger lt) {
                List<ActiveSituation> qualifying = state.allQualifyingSituations(rule);
                for (ActiveSituation sit : qualifying) {
                    if (state.getCachedDecision(rule.name(), sit.situationId()).isEmpty()
                        && state.shouldActivate(rule, sit)) {
                        requests.add(new LlmEvalRequest(
                                rule.name(), lt, sit, rule.catalogEntries()));
                    }
                }
            }
        }
        return requests;
    }

    private DesiredStateGraph resolveBaseGraph(TenantAdaptationState state, DesiredStateGraphFactory factory) {
        if (state.baseGraph() != null) {
            return state.baseGraph();
        }
        CompilationResult baseResult = compiler.compile(state.goals(), factory);
        return ((CompilationResult.SingleGraph) baseResult).graph();
    }

    private void logConflicts(String tenancyId, AdaptationRule rule,
                              Set<NodeId> targets, Set<NodeId> modifiedNodes) {
        for (NodeId t : targets) {
            if (modifiedNodes.contains(t)) {
                LOG.warning(String.format(
                        "Conflict: rule '%s' modifies '%s' already modified by earlier rule",
                        rule.name(), t.value()));
                meterRegistry.counter("desiredstate.adaptation.conflict.total",
                                      Tags.of("tenancy_id", tenancyId, "rule_name", rule.name(), "node_id", t.value()))
                             .increment();
            }
        }
    }

    private static ActiveSituation findTrackedSituation(TenantAdaptationState state, String situationId) {
        return state.trackedSituationSnapshot().stream()
                    .filter(s -> s.situationId().equals(situationId))
                    .findFirst()
                    .orElse(null);
    }


}
