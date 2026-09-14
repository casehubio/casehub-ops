package io.casehub.ops.deployment.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.SituationRecompiler;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ops.deployment.DeploymentGoalCompiler;
import io.casehub.ras.api.ActiveSituation;
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
public class DeploymentAdaptiveSituationRecompiler implements SituationRecompiler {

    private static final Logger LOG = Logger.getLogger(
        DeploymentAdaptiveSituationRecompiler.class.getName());

    @Inject DeploymentGoalCompiler compiler;
    @Inject ObjectMapper mapper;

    private final ConcurrentHashMap<String, TenantAdaptationState> tenantStates =
        new ConcurrentHashMap<>();

    @Override
    public int priority() {
        return 100;
    }

    public void register(String tenancyId, DeploymentGoals goals,
                         Map<String, Duration> situationClearanceWindows,
                         DesiredStateGraphFactory factory) {
        List<AdaptationRule> rules = AdaptationRule.fromSpecs(
            goals.adaptations(), compiler, mapper, factory);
        var state = new TenantAdaptationState(goals, rules, situationClearanceWindows);
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

        synchronized (state) {
            state.updateSituation(situation);

            CompilationResult baseResult = compiler.compile(state.goals(), factory);
            DesiredStateGraph base = ((CompilationResult.SingleGraph) baseResult).graph();
            DesiredStateGraph adapted = base;
            Set<NodeId> modifiedNodes = new HashSet<>();

            for (AdaptationRule rule : state.rules()) {
                Optional<ActiveSituation> match = state.activeSituationFor(rule);
                if (match.isPresent() && state.shouldActivate(rule, match.get())) {
                    Set<NodeId> targets = rule.targetNodeIds(base);
                    for (NodeId t : targets) {
                        if (modifiedNodes.contains(t)) {
                            LOG.warning(String.format(
                                "Conflict: rule '%s' modifies '%s' "
                                    + "already modified by earlier rule",
                                rule.name(), t.value()));
                        }
                    }
                    adapted = rule.apply(adapted, match.get());
                    modifiedNodes.addAll(targets);
                }
            }

            state.clearAbsentSituations();

            if (graphsEqual(adapted, base)) {
                return Optional.empty();
            }
            return Optional.of(CompilationResult.single(adapted));
        }
    }

    private static boolean graphsEqual(DesiredStateGraph a, DesiredStateGraph b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.nodes().equals(b.nodes()) && a.dependencies().equals(b.dependencies());
    }
}
