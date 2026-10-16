package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec;
import io.casehub.ops.api.deployment.DeploymentNodeSpec;
import io.casehub.ops.api.infra.InfraNodeSpec;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@OllamaAvailable
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class LifecycleIntegrationTest extends TopologyTestBase {

    private static final Map<String, String> DEPLOYMENT_REGISTRY =
            buildTypeRegistryFrom(InfraNodeSpec.class, DeploymentNodeSpec.class);

    private static final List<String> FLEET_APP_IDS =
            List.of("my-llm-analyst", "my-llm-coder", "my-llm-assistant");

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void suspendAndResumeFleetWithStatusTransitions() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/06-lifecycle.yaml", DEPLOYMENT_REGISTRY);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient client = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(client);

        assertThat(target.supportsStatefulLifecycle())
                .as("Claudony target supports stateful lifecycle")
                .isTrue();

        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            DeployResult result = target.deploy(
                    appSpec.image(),
                    new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                            appSpec.config(), Map.of()));
            assertThat(result)
                    .as("Deploy %s", appSpec.applicationId())
                    .isInstanceOf(DeployResult.Success.class);
        }

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
                "topologies/deployment/06-lifecycle.yaml", DEPLOYMENT_REGISTRY);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient client = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(client);

        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            target.deploy(appSpec.image(),
                    new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                            appSpec.config(), Map.of()));
        }

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

    private CaseHubApplicationSpec extractAppSpec(DesiredNode node) {
        if (node.spec() instanceof DeploymentApplicationNodeSpec dns) {
            return dns.spec();
        }
        return (CaseHubApplicationSpec) node.spec();
    }
}
