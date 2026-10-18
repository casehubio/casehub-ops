package io.casehub.ops.topology.integration;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class FleetScalingIntegrationTest extends TopologyTestBase {

    private static final List<String> FLEET_APP_IDS =
            List.of("my-llm-analyst", "my-llm-coder", "my-llm-assistant");

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void deploysThreeFleetInstancesViaRealClaudony() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);

        assertThat(graph.nodes()).hasSize(6);

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);

        deployAllNodes(graph, target);

        for (String appId : FLEET_APP_IDS) {
            NodeStatus status = target.status(appId);
            assertThat(status)
                    .as("Status of %s after deployment", appId)
                    .isNotEqualTo(NodeStatus.ABSENT);
        }
    }

    @Test
    void fleetInstancesHaveDistinctApplicationIds() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);

        List<String> llmAppIds = graph.nodes().entrySet().stream()
                .filter(e -> e.getKey().value().startsWith("my-llm."))
                .map(e -> extractAppSpec(e.getValue()).applicationId())
                .toList();

        assertThat(llmAppIds)
                .containsExactlyInAnyOrderElementsOf(FLEET_APP_IDS);
    }


}
