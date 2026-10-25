package io.casehub.ops.topology.integration;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.Dependency;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.SituationRecompiler;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ras.api.ActiveSituation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class AdhocLlmDeployerRecompiler implements SituationRecompiler {

    private final List<SpecialistProfile> catalog;
    private final DeploymentDecisionFunction decisionFunction;
    private final int maxRedeploys;
    private final Map<String, String> situationToSpecialist = new ConcurrentHashMap<>();
    private final Map<String, Integer> redeployCount = new ConcurrentHashMap<>();

    AdhocLlmDeployerRecompiler(List<SpecialistProfile> catalog,
                               DeploymentDecisionFunction decisionFunction,
                               int maxRedeploys) {
        this.catalog = List.copyOf(catalog);
        this.decisionFunction = decisionFunction;
        this.maxRedeploys = maxRedeploys;
    }

    @Override
    public int priority() {
        // SituationRecompilerDispatchCore dispatches sequentially per tenant — no synchronization needed
        return 50;
    }

    @Override
    public Optional<CompilationResult> recompile(
            String tenancyId, DesiredStateGraph current,
            ActualState actual, ActiveSituation situation,
            DesiredStateGraphFactory factory) {

        Optional<String> decision = decisionFunction.decide(situation, catalog);
        if (decision.isEmpty()) {
            return Optional.empty();
        }

        String specialistName = decision.get();
        SpecialistProfile profile = catalog.stream()
                .filter(p -> p.name().equals(specialistName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown specialist: " + specialistName));

        NodeId specialistId = NodeId.of("specialist-" + profile.name());

        if (current.nodes().containsKey(specialistId)) {
            return Optional.empty();
        }

        var spec = new ClaudonyAppNodeSpec(
                "specialist-" + profile.name(),
                profile.name().replace("-", " "),
                "Ad-hoc specialist for " + profile.domain(),
                "casehubio/my-llm-app:latest",
                Map.of(
                        "llm.agent-id", "specialist-" + profile.name(),
                        "llm.name", profile.name().replace("-", " "),
                        "llm.role", profile.role(),
                        "llm.domain", profile.domain(),
                        "llm.archetype", profile.archetype(),
                        "llm.knowledge-base", profile.knowledgeBase(),
                        "llm.identity-source", "eidos",
                        "llm.deployed-by", "deployer",
                        "llm.source-situation", situation.situationId()
                ));

        var node = new DesiredNode(specialistId, spec, HumanGating.NONE);
        DesiredStateGraph newGraph = current
                .withNode(node)
                .withDependency(new Dependency(specialistId, NodeId.of("engine")));

        situationToSpecialist.put(situation.situationId(), specialistId.value());
        return Optional.of(CompilationResult.single(newGraph));
    }

    @Override
    public Optional<CompilationResult> situationResolved(
            String tenancyId, String situationId,
            DesiredStateGraph currentGraph, ActualState actualState,
            DesiredStateGraphFactory factory) {

        String specialistNodeId = situationToSpecialist.remove(situationId);
        if (specialistNodeId == null) {
            return Optional.empty();
        }

        NodeId nodeId = NodeId.of(specialistNodeId);
        if (!currentGraph.nodes().containsKey(nodeId)) {
            return Optional.empty();
        }

        DesiredStateGraph newGraph = currentGraph.withoutNode(nodeId);
        redeployCount.remove(specialistNodeId);
        return Optional.of(CompilationResult.single(newGraph));
    }

    boolean shouldEscalate(String specialistNodeId) {
        return redeployCount.getOrDefault(specialistNodeId, 0) >= maxRedeploys;
    }

    void recordRedeploy(String specialistNodeId) {
        redeployCount.merge(specialistNodeId, 1, Integer::sum);
    }

    Map<String, String> activeMappings() {
        return Map.copyOf(situationToSpecialist);
    }
}
