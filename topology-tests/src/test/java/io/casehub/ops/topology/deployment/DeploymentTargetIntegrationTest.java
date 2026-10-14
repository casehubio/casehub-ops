package io.casehub.ops.topology.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("reconciliation")
class DeploymentTargetIntegrationTest extends TopologyTestBase {

    private static final Map<String, String> DEPLOYMENT_REGISTRY =
            buildTypeRegistryFrom(InfraNodeSpec.class, DeploymentNodeSpec.class);

    private WireMockServer wireMock;
    private ClaudonyDeploymentTarget claudonyTarget;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        ClaudonyWireMockSupport.stubRegisterApp(wireMock);
        ClaudonyWireMockSupport.stubGetAppHealthy(wireMock, "manifest");
        ClaudonyWireMockSupport.stubGetAppHealthy(wireMock, "eidos");
        ClaudonyWireMockSupport.stubGetAppHealthy(wireMock, "engine");
        ClaudonyWireMockSupport.stubGetAppHealthy(wireMock, "my-llm");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient fleetClient = new HttpClaudonyFleetClient(
                wireMock.baseUrl(), mapper);
        claudonyTarget = new ClaudonyDeploymentTarget(fleetClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void helloLlm_deploysAllAppsViaClaudony() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);

        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            DeployResult result = claudonyTarget.deploy(
                    appSpec.image(),
                    new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                            appSpec.config(), Map.of()));
            assertThat(result).isInstanceOf(DeployResult.Success.class);
        }

        wireMock.verify(4, postRequestedFor(urlEqualTo("/api/claudony/apps/")));
    }

    @Test
    void helloLlm_allAppsReportHealthy() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);

        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            NodeStatus status = claudonyTarget.status(appSpec.applicationId());
            assertThat(status).isEqualTo(NodeStatus.PRESENT);
        }
    }

    private CaseHubApplicationSpec extractAppSpec(DesiredNode node) {
        if (node.spec() instanceof DeploymentApplicationNodeSpec dns) {
            return dns.spec();
        }
        return (CaseHubApplicationSpec) node.spec();
    }
}
