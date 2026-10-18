package io.casehub.ops.topology.deployment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("reconciliation")
class DeploymentTargetIntegrationTest extends TopologyTestBase {

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

        claudonyTarget = createClaudonyTarget(wireMock.baseUrl());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void helloLlm_deploysAllAppsViaClaudony() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);

        deployAllNodes(graph, claudonyTarget);

        wireMock.verify(4, postRequestedFor(urlEqualTo("/api/claudony/apps/")));
    }

    @Test
    void helloLlm_allAppsReportHealthy() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);

        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            NodeStatus status = claudonyTarget.status(appSpec.applicationId());
            assertThat(status).isEqualTo(NodeStatus.PRESENT);
        }
    }


}
