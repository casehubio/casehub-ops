package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.claudony.ClaudonyAppInfo;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class ConfigDriftIntegrationTest extends TopologyTestBase {

    private static final List<String> FLEET_APP_IDS =
            List.of("my-llm-analyst", "my-llm-coder", "my-llm-assistant");

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void detectsConfigDriftAndReconcilesDriftedNodeOnly() throws IOException {
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        DesiredStateGraph driftedGraph = compileSingleGraph(
                "topologies/deployment/05-config-drift.yaml", DEPLOYMENT_REGISTRY);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient client = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(client);

        deployAllNodes(initialGraph, target);

        ClaudonyAppInfo analystBefore = client.getApp("my-llm-analyst").orElseThrow();
        assertThat(analystBefore.config())
                .containsEntry("llm.capability", "deep")
                .containsEntry("llm.domain", "analysis");

        FleetLlmExerciser.LlmResponse analystBeforeLlm =
                FleetLlmExerciser.exercise("my-llm-analyst", analystBefore.config());
        FleetLlmExerciser.assertDomainRelevant(analystBeforeLlm);
        assertThat(analystBeforeLlm.domain()).isEqualTo("analysis");

        List<String> driftedAppIds = new ArrayList<>();
        for (Map.Entry<NodeId, DesiredNode> entry : driftedGraph.nodes().entrySet()) {
            DesiredNode initialNode = initialGraph.nodes().get(entry.getKey());
            if (initialNode == null) continue;

            CaseHubApplicationSpec driftedSpec = extractAppSpec(entry.getValue());
            CaseHubApplicationSpec initialSpec = extractAppSpec(initialNode);
            if (!driftedSpec.config().equals(initialSpec.config())) {
                driftedAppIds.add(driftedSpec.applicationId());
            }
        }

        assertThat(driftedAppIds)
                .as("Only analyst should have drifted config")
                .containsExactly("my-llm-analyst");

        for (Map.Entry<NodeId, DesiredNode> entry : driftedGraph.nodes().entrySet()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(entry.getValue());
            if (driftedAppIds.contains(appSpec.applicationId())) {
                DeployResult result = target.deploy(
                        appSpec.image(),
                        new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                                appSpec.config(), Map.of()));
                assertThat(result)
                        .as("Drift reconcile %s", appSpec.applicationId())
                        .isInstanceOf(DeployResult.Success.class);
            }
        }

        ClaudonyAppInfo analystAfter = client.getApp("my-llm-analyst").orElseThrow();
        assertThat(analystAfter.config())
                .containsEntry("llm.capability", "analytical")
                .containsEntry("llm.domain", "data-analysis");

        FleetLlmExerciser.LlmResponse analystAfterLlm =
                FleetLlmExerciser.exercise("my-llm-analyst", analystAfter.config());
        FleetLlmExerciser.assertDomainRelevant(analystAfterLlm);
        assertThat(analystAfterLlm.domain()).isEqualTo("data-analysis");

        List<FleetLlmExerciser.LlmResponse> allResponses =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS,
                        appId -> client.getApp(appId).orElseThrow().config());
        FleetLlmExerciser.assertFleetOperational(allResponses);

        ClaudonyAppInfo coder = client.getApp("my-llm-coder").orElseThrow();
        assertThat(coder.config()).containsEntry("llm.capability", "fast");

        ClaudonyAppInfo assistant = client.getApp("my-llm-assistant").orElseThrow();
        assertThat(assistant.config()).containsEntry("llm.capability", "balanced");
    }

    @Test
    void graphDiffIdentifiesOnlyChangedNodes() throws IOException {
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        DesiredStateGraph driftedGraph = compileSingleGraph(
                "topologies/deployment/05-config-drift.yaml", DEPLOYMENT_REGISTRY);

        assertThat(initialGraph.nodes()).hasSameSizeAs(driftedGraph.nodes());

        List<String> changedNodeIds = new ArrayList<>();
        for (Map.Entry<NodeId, DesiredNode> entry : driftedGraph.nodes().entrySet()) {
            DesiredNode initialNode = initialGraph.nodes().get(entry.getKey());
            assertThat(initialNode)
                    .as("Node %s exists in both graphs", entry.getKey().value())
                    .isNotNull();

            CaseHubApplicationSpec driftedSpec = extractAppSpec(entry.getValue());
            CaseHubApplicationSpec initialSpec = extractAppSpec(initialNode);
            if (!driftedSpec.config().equals(initialSpec.config())) {
                changedNodeIds.add(entry.getKey().value());
            }
        }

        assertThat(changedNodeIds)
                .as("Only the analyst fleet member should have changed config")
                .containsExactly("my-llm.analyst");

        CaseHubApplicationSpec initialAnalyst = extractAppSpec(
                initialGraph.nodes().get(NodeId.of("my-llm.analyst")));
        CaseHubApplicationSpec driftedAnalyst = extractAppSpec(
                driftedGraph.nodes().get(NodeId.of("my-llm.analyst")));

        FleetLlmExerciser.LlmResponse beforeDrift =
                FleetLlmExerciser.exercise("analyst-before", initialAnalyst.config());
        FleetLlmExerciser.LlmResponse afterDrift =
                FleetLlmExerciser.exercise("analyst-after", driftedAnalyst.config());

        FleetLlmExerciser.assertSubstantive(beforeDrift);
        FleetLlmExerciser.assertSubstantive(afterDrift);
        assertThat(beforeDrift.domain()).isEqualTo("analysis");
        assertThat(afterDrift.domain()).isEqualTo("data-analysis");
    }

}
