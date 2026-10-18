package io.casehub.ops.topology.integration;

import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class TopologyMutationsIntegrationTest extends TopologyTestBase {

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void prunesRemovedNodeBeforeGrowingNewNode() throws IOException {
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        DesiredStateGraph mutatedGraph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", DEPLOYMENT_REGISTRY);

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);

        deployAllNodes(initialGraph, target);

        assertThat(target.status("my-llm-assistant"))
                .as("Assistant present before mutation")
                .isEqualTo(NodeStatus.PRESENT);
        assertThat(target.status("my-llm-researcher"))
                .as("Researcher absent before mutation")
                .isEqualTo(NodeStatus.ABSENT);

        Set<NodeId> initialNodeIds = initialGraph.nodes().keySet();
        Set<NodeId> mutatedNodeIds = mutatedGraph.nodes().keySet();

        Set<NodeId> removedNodes = new LinkedHashSet<>(initialNodeIds);
        removedNodes.removeAll(mutatedNodeIds);

        Set<NodeId> addedNodes = new LinkedHashSet<>(mutatedNodeIds);
        addedNodes.removeAll(initialNodeIds);

        Set<String> removedAppIds = removedNodes.stream()
                .map(id -> extractAppSpec(initialGraph.nodes().get(id)).applicationId())
                .collect(Collectors.toSet());
        Set<String> addedAppIds = addedNodes.stream()
                .map(id -> extractAppSpec(mutatedGraph.nodes().get(id)).applicationId())
                .collect(Collectors.toSet());

        assertThat(removedAppIds).containsExactly("my-llm-assistant");
        assertThat(addedAppIds).containsExactly("my-llm-researcher");

        for (NodeId removedId : removedNodes) {
            String appId = extractAppSpec(initialGraph.nodes().get(removedId)).applicationId();
            DeployResult result = target.undeploy(appId);
            assertThat(result)
                    .as("Prune %s", appId)
                    .isInstanceOf(DeployResult.Success.class);
        }

        assertThat(target.status("my-llm-assistant"))
                .as("Assistant absent after prune")
                .isEqualTo(NodeStatus.ABSENT);

        for (NodeId addedId : addedNodes) {
            CaseHubApplicationSpec appSpec = extractAppSpec(mutatedGraph.nodes().get(addedId));
            DeployResult result = target.deploy(
                    appSpec.image(),
                    new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                            appSpec.config(), Map.of()));
            assertThat(result)
                    .as("Grow %s", appSpec.applicationId())
                    .isInstanceOf(DeployResult.Success.class);
        }

        assertThat(target.status("my-llm-analyst"))
                .as("Analyst still present (unchanged)")
                .isEqualTo(NodeStatus.PRESENT);
        assertThat(target.status("my-llm-coder"))
                .as("Coder still present (unchanged)")
                .isEqualTo(NodeStatus.PRESENT);
        assertThat(target.status("my-llm-researcher"))
                .as("Researcher now present (added)")
                .isEqualTo(NodeStatus.PRESENT);
        assertThat(target.status("my-llm-assistant"))
                .as("Assistant now absent (removed)")
                .isEqualTo(NodeStatus.ABSENT);

        List<String> mutatedFleetAppIds = List.of("my-llm-analyst", "my-llm-coder", "my-llm-researcher");
        List<FleetLlmExerciser.LlmResponse> postMutation =
                FleetLlmExerciser.exerciseFleet(mutatedFleetAppIds, appId -> {
                    DesiredNode node = mutatedGraph.nodes().entrySet().stream()
                            .filter(e -> extractAppSpec(e.getValue()).applicationId().equals(appId))
                            .map(Map.Entry::getValue)
                            .findFirst().orElseThrow();
                    return extractAppSpec(node).config();
                });
        FleetLlmExerciser.assertFleetOperational(postMutation);
    }

    @Test
    void graphDiffDetectsAdditionsAndRemovals() throws IOException {
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        DesiredStateGraph mutatedGraph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", DEPLOYMENT_REGISTRY);

        assertThat(initialGraph.nodes()).hasSameSizeAs(mutatedGraph.nodes());

        Set<String> initialFleetIds = initialGraph.nodes().keySet().stream()
                .map(NodeId::value)
                .filter(id -> id.startsWith("my-llm."))
                .collect(Collectors.toSet());
        Set<String> mutatedFleetIds = mutatedGraph.nodes().keySet().stream()
                .map(NodeId::value)
                .filter(id -> id.startsWith("my-llm."))
                .collect(Collectors.toSet());

        Set<String> removed = new LinkedHashSet<>(initialFleetIds);
        removed.removeAll(mutatedFleetIds);
        assertThat(removed).containsExactly("my-llm.assistant");

        Set<String> added = new LinkedHashSet<>(mutatedFleetIds);
        added.removeAll(initialFleetIds);
        assertThat(added).containsExactly("my-llm.researcher");

        Set<String> retained = new LinkedHashSet<>(initialFleetIds);
        retained.retainAll(mutatedFleetIds);
        assertThat(retained).containsExactlyInAnyOrder("my-llm.analyst", "my-llm.coder");

        CaseHubApplicationSpec researcherSpec = extractAppSpec(
                mutatedGraph.nodes().get(NodeId.of("my-llm.researcher")));
        FleetLlmExerciser.LlmResponse researcherLlm =
                FleetLlmExerciser.exercise("my-llm-researcher", researcherSpec.config());
        FleetLlmExerciser.assertDomainRelevant(researcherLlm);
        assertThat(researcherLlm.domain()).isEqualTo("knowledge-discovery");
    }


}
