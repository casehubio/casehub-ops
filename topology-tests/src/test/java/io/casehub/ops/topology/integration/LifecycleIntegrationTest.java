package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
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
class LifecycleIntegrationTest extends TopologyTestBase {

    private static final List<String> FLEET_APP_IDS =
            List.of("my-llm-analyst", "my-llm-coder", "my-llm-assistant");

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void suspendAndResumeFleetWithStatusTransitions() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/06-lifecycle/06-lifecycle.yaml", DEPLOYMENT_REGISTRY);

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);

        assertThat(target.supportsStatefulLifecycle())
                .as("Claudony target supports stateful lifecycle")
                .isTrue();

        deployAllNodes(graph, target);

        for (String appId : FLEET_APP_IDS) {
            assertThat(target.status(appId))
                    .as("Status of %s after deployment", appId)
                    .isEqualTo(NodeStatus.PRESENT);
        }

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient fleetClient = new HttpClaudonyFleetClient(claudonyUrl, om);
        List<FleetLlmExerciser.LlmResponse> preSuspend =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS,
                        appId -> fleetClient.getApp(appId).orElseThrow().config());
        FleetLlmExerciser.assertFleetOperational(preSuspend);

        for (String appId : FLEET_APP_IDS) {
            DeployResult result = target.suspend(appId);
            assertThat(result)
                    .as("Suspend %s", appId)
                    .isInstanceOf(DeployResult.Success.class);
        }

        for (String appId : FLEET_APP_IDS) {
            assertThat(target.status(appId))
                    .as("Status of %s after suspend", appId)
                    .isEqualTo(NodeStatus.SUSPENDED);
        }

        for (String appId : FLEET_APP_IDS) {
            DeployResult result = target.resume(appId);
            assertThat(result)
                    .as("Resume %s", appId)
                    .isInstanceOf(DeployResult.Success.class);
        }

        for (String appId : FLEET_APP_IDS) {
            assertThat(target.status(appId))
                    .as("Status of %s after resume", appId)
                    .isEqualTo(NodeStatus.PRESENT);
        }

        List<FleetLlmExerciser.LlmResponse> postResume =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS,
                        appId -> fleetClient.getApp(appId).orElseThrow().config());
        FleetLlmExerciser.assertFleetOperational(postResume);
    }

    @Test
    void suspendedInstanceIsDistinguishableFromAbsent() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/06-lifecycle/06-lifecycle.yaml", DEPLOYMENT_REGISTRY);

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);

        deployAllNodes(graph, target);

        target.suspend("my-llm-analyst");

        assertThat(target.status("my-llm-analyst"))
                .as("Suspended instance has SUSPENDED status, not ABSENT")
                .isEqualTo(NodeStatus.SUSPENDED);

        assertThat(target.status("nonexistent-app"))
                .as("Unknown instance is ABSENT")
                .isEqualTo(NodeStatus.ABSENT);

        assertThat(target.status("my-llm-coder"))
                .as("Running instance is PRESENT")
                .isEqualTo(NodeStatus.PRESENT);

        ObjectMapper om2 = new ObjectMapper();
        om2.registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient fleetClient2 = new HttpClaudonyFleetClient(claudonyUrl, om2);
        FleetLlmExerciser.LlmResponse coderResponse =
                FleetLlmExerciser.exercise("my-llm-coder",
                        fleetClient2.getApp("my-llm-coder").orElseThrow().config());
        FleetLlmExerciser.assertDomainRelevant(coderResponse);
    }


}
