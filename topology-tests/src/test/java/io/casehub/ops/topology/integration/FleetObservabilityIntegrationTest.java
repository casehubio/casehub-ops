package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
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
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class FleetObservabilityIntegrationTest extends TopologyTestBase {

    private static final List<String> FLEET_APP_IDS =
            List.of("my-llm-analyst", "my-llm-coder", "my-llm-assistant");

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void healthyFleetReportsAllProbesHealthy() throws IOException {
        ClaudonyDeploymentTarget target = deployFleet();

        for (String appId : FLEET_APP_IDS) {
            HealthCheckResult result = target.healthCheck(appId);
            assertThat(result)
                    .as("Health check for %s", appId)
                    .isInstanceOf(HealthCheckResult.Healthy.class);

            HealthCheckResult.Healthy healthy = (HealthCheckResult.Healthy) result;
            assertThat(healthy.instanceId()).isEqualTo(appId);
        }

        List<FleetLlmExerciser.LlmResponse> responses =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS, appId -> {
                    HttpClaudonyFleetClient fc = createClient();
                    return fc.getApp(appId).orElseThrow().config();
                });
        FleetLlmExerciser.assertFleetOperational(responses);
    }

    @Test
    void degradationDetectedViaHealthProbe() throws IOException {
        HttpClaudonyFleetClient client = createClient();
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(client);
        deployFleetWith(target);

        client.suspendApp("my-llm-analyst");

        HealthCheckResult analystHealth = target.healthCheck("my-llm-analyst");
        assertThat(analystHealth)
                .as("Suspended instance reports unhealthy")
                .isInstanceOf(HealthCheckResult.Unhealthy.class);
        assertThat(((HealthCheckResult.Unhealthy) analystHealth).reason())
                .isEqualTo("Suspended");

        assertThat(target.status("my-llm-analyst"))
                .as("Suspended maps to SUSPENDED status, not ABSENT")
                .isEqualTo(NodeStatus.SUSPENDED);

        for (String appId : List.of("my-llm-coder", "my-llm-assistant")) {
            assertThat(target.healthCheck(appId))
                    .as("Unaffected instance %s still healthy", appId)
                    .isInstanceOf(HealthCheckResult.Healthy.class);
            assertThat(target.status(appId))
                    .isEqualTo(NodeStatus.PRESENT);
        }

        List<FleetLlmExerciser.LlmResponse> healthyResponses =
                FleetLlmExerciser.exerciseFleet(
                        List.of("my-llm-coder", "my-llm-assistant"), appId ->
                                client.getApp(appId).orElseThrow().config());
        for (FleetLlmExerciser.LlmResponse r : healthyResponses) {
            FleetLlmExerciser.assertDomainRelevant(r);
        }
    }

    @Test
    void recoveryRestoresHealthyState() throws IOException {
        HttpClaudonyFleetClient client = createClient();
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(client);
        deployFleetWith(target);

        client.suspendApp("my-llm-analyst");
        assertThat(target.healthCheck("my-llm-analyst"))
                .isInstanceOf(HealthCheckResult.Unhealthy.class);
        assertThat(target.status("my-llm-analyst"))
                .isEqualTo(NodeStatus.SUSPENDED);

        client.resumeApp("my-llm-analyst");

        HealthCheckResult afterRecovery = target.healthCheck("my-llm-analyst");
        assertThat(afterRecovery)
                .as("Recovered instance reports healthy")
                .isInstanceOf(HealthCheckResult.Healthy.class);
        assertThat(target.status("my-llm-analyst"))
                .as("Recovered instance status is PRESENT")
                .isEqualTo(NodeStatus.PRESENT);

        FleetLlmExerciser.LlmResponse recoveredLlm =
                FleetLlmExerciser.exercise("my-llm-analyst",
                        createClient().getApp("my-llm-analyst").orElseThrow().config());
        FleetLlmExerciser.assertDomainRelevant(recoveredLlm);
    }

    @Test
    void absentInstanceReportsUnhealthyWithDiagnostic() throws IOException {
        deployFleet();
        HttpClaudonyFleetClient client = createClient();
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(client);

        HealthCheckResult result = target.healthCheck("nonexistent-app");
        assertThat(result)
                .as("Unknown instance reports unhealthy with diagnostic")
                .isInstanceOf(HealthCheckResult.Unhealthy.class);

        HealthCheckResult.Unhealthy unhealthy = (HealthCheckResult.Unhealthy) result;
        assertThat(unhealthy.reason()).contains("not found");
        assertThat(target.status("nonexistent-app"))
                .isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void fullSignalChain_healthyThroughDegradationToRecovery() throws IOException {
        HttpClaudonyFleetClient client = createClient();
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(client);
        deployFleetWith(target);

        for (String appId : FLEET_APP_IDS) {
            assertThat(target.healthCheck(appId)).isInstanceOf(HealthCheckResult.Healthy.class);
            assertThat(target.status(appId)).isEqualTo(NodeStatus.PRESENT);
        }

        client.suspendApp("my-llm-coder");

        assertThat(target.status("my-llm-coder")).isEqualTo(NodeStatus.SUSPENDED);
        HealthCheckResult coderHealth = target.healthCheck("my-llm-coder");
        assertThat(coderHealth).isInstanceOf(HealthCheckResult.Unhealthy.class);
        assertThat(((HealthCheckResult.Unhealthy) coderHealth).reason()).isEqualTo("Suspended");

        assertThat(target.status("my-llm-analyst")).isEqualTo(NodeStatus.PRESENT);
        assertThat(target.status("my-llm-assistant")).isEqualTo(NodeStatus.PRESENT);

        target.undeploy("my-llm-coder");
        assertThat(target.status("my-llm-coder")).isEqualTo(NodeStatus.ABSENT);
        assertThat(target.healthCheck("my-llm-coder"))
                .isInstanceOf(HealthCheckResult.Unhealthy.class);

        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/08-fleet-observability/08-fleet-observability.yaml", DEPLOYMENT_REGISTRY);
        DesiredNode coderNode = graph.nodes().entrySet().stream()
                .filter(e -> e.getKey().value().equals("my-llm.coder"))
                .map(Map.Entry::getValue)
                .findFirst().orElseThrow();

        CaseHubApplicationSpec coderSpec = extractAppSpec(coderNode);
        DeployResult redeployResult = target.deploy(
                coderSpec.image(),
                new DeployConfig(coderSpec.applicationId(), coderSpec.replicas(),
                        coderSpec.config(), Map.of()));
        assertThat(redeployResult).isInstanceOf(DeployResult.Success.class);

        assertThat(target.healthCheck("my-llm-coder"))
                .as("Redeployed instance is healthy again")
                .isInstanceOf(HealthCheckResult.Healthy.class);
        assertThat(target.status("my-llm-coder"))
                .isEqualTo(NodeStatus.PRESENT);

        for (String appId : FLEET_APP_IDS) {
            assertThat(target.healthCheck(appId)).isInstanceOf(HealthCheckResult.Healthy.class);
            assertThat(target.status(appId)).isEqualTo(NodeStatus.PRESENT);
        }

        List<FleetLlmExerciser.LlmResponse> recovered =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS, appId -> {
                    HttpClaudonyFleetClient fc = createClient();
                    return fc.getApp(appId).orElseThrow().config();
                });
        FleetLlmExerciser.assertFleetOperational(recovered);
    }

    private ClaudonyDeploymentTarget deployFleet() throws IOException {
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(createClient());
        deployFleetWith(target);
        return target;
    }

    private void deployFleetWith(ClaudonyDeploymentTarget target) throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/08-fleet-observability/08-fleet-observability.yaml", DEPLOYMENT_REGISTRY);
        deployAllNodes(graph, target);
    }

    private HttpClaudonyFleetClient createClient() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new HttpClaudonyFleetClient(claudonyUrl, mapper);
    }


}
