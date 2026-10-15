package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
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
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class FleetScalingIntegrationTest extends TopologyTestBase {

    private static final Map<String, String> DEPLOYMENT_REGISTRY =
            buildTypeRegistryFrom(InfraNodeSpec.class, DeploymentNodeSpec.class);

    private static final List<String> FLEET_APP_IDS =
            List.of("my-llm-analyst", "my-llm-coder", "my-llm-assistant");

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void deploysThreeFleetInstancesViaRealClaudony() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);

        assertThat(graph.nodes()).hasSize(6);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient client = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(client);

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

    private CaseHubApplicationSpec extractAppSpec(DesiredNode node) {
        if (node.spec() instanceof DeploymentApplicationNodeSpec dns) {
            return dns.spec();
        }
        return (CaseHubApplicationSpec) node.spec();
    }
}
