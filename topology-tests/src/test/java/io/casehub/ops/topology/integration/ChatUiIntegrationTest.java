package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateEventTypes;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.MergedEventSource;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.runtime.DefaultActualStateAdapterRouter;
import io.casehub.desiredstate.runtime.DefaultNodeProvisionerRouter;
import io.casehub.desiredstate.runtime.FaultPolicyEngine;
import io.casehub.desiredstate.runtime.NoOpHumanNodeHandler;
import io.casehub.desiredstate.runtime.NoOpPendingApprovalHandler;
import io.casehub.desiredstate.runtime.NodeStepExecutor;
import io.casehub.desiredstate.runtime.ReconciliationLoop;
import io.casehub.desiredstate.runtime.SimpleTransitionExecutor;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.api.SpecHashStore;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.claudony.ClaudonyNodeProvisioner;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
import io.casehub.ops.testing.audit.AuditAssertions;
import io.casehub.ops.testing.audit.AuditCapture;
import io.casehub.ops.testing.audit.CapturingTransitionExecutor;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class ChatUiIntegrationTest extends TopologyTestBase {

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void deploysChatViewerWithQhorusDependency() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/09-chat-ui/09-chat-ui.yaml", DEPLOYMENT_REGISTRY);

        assertThat(graph.nodes()).hasSize(7);
        assertThat(graph.nodes().get(NodeId.of("chat-viewer")))
                .as("chat-viewer node must exist")
                .isNotNull();

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);

        deployAllNodes(graph, target);

        NodeStatus chatViewerStatus = target.status("chat-viewer");
        assertThat(chatViewerStatus)
                .as("chat-viewer should be registered after deployment")
                .isNotEqualTo(NodeStatus.ABSENT);

        CaseHubApplicationSpec chatSpec = extractAppSpec(graph.nodes().get(NodeId.of("chat-viewer")));
        assertThat(chatSpec.config())
                .containsEntry("chat.mode", "conversation-viewer")
                .containsEntry("chat.session-source", "qhorus")
                .containsEntry("chat.channel-type", "direct");
    }

    @Test
    void chatViewerDependsOnQhorus() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/09-chat-ui/09-chat-ui.yaml", DEPLOYMENT_REGISTRY);

        DesiredNode chatViewer = graph.nodes().get(NodeId.of("chat-viewer"));
        assertThat(chatViewer).isNotNull();

        assertThat(graph.dependenciesOf(NodeId.of("chat-viewer")))
                .as("chat-viewer must depend on qhorus")
                .contains(NodeId.of("qhorus"));

        DesiredNode qhorus = graph.nodes().get(NodeId.of("qhorus"));
        assertThat(qhorus).as("qhorus node must exist").isNotNull();
    }

    @Test
    void sessionEndpointResponds() throws IOException, InterruptedException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/09-chat-ui/09-chat-ui.yaml", DEPLOYMENT_REGISTRY);

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);
        deployAllNodes(graph, target);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(claudonyUrl + "/api/claudony/sessions"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("Session endpoint should respond (200 OK or 401 if auth required)")
                .isIn(200, 401);
    }

    @Test
    void auditRecordsChatUiDeployment() throws Exception {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/09-chat-ui/09-chat-ui.yaml", DEPLOYMENT_REGISTRY);

        ObjectMapper            mapper        = new ObjectMapper().registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient client        = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        var                     specHashStore = new SpecHashStore();
        var                     provisioner   = new ClaudonyNodeProvisioner(client, specHashStore);
        var                     actualAdapter = new ClaudonyActualStateAdapter(client, specHashStore);
        var                     auditCapture  = new AuditCapture();

        var planner           = new TransitionPlanner();
        var provisionerRouter = new DefaultNodeProvisionerRouter(List.of(provisioner));
        var nodeExecutor = new NodeStepExecutor(
                provisionerRouter,
                new NoOpHumanNodeHandler(),
                new NoOpPendingApprovalHandler(),
                (step, tid) -> new StepOutcome.Succeeded());
        var               executor          = new SimpleTransitionExecutor(nodeExecutor);
        var               capturingExecutor = new CapturingTransitionExecutor(executor, auditCapture);
        var               actualRouter      = new DefaultActualStateAdapterRouter(List.of(actualAdapter));
        MergedEventSource emptySource       = () -> Multi.createFrom().nothing();

        ReconciliationLoop loop = ReconciliationLoop.builder(
                                                            planner, capturingExecutor, actualRouter,
                                                            new FaultPolicyEngine(List.of()), emptySource)
                                                    .cloudEventSink(auditCapture::recordCloudEvent)
                                                    .debounceWindow(Duration.ofMillis(50))
                                                    .resyncInterval(Duration.ofMinutes(30))
                                                    .build();

        try {
            loop.start("l09-audit", graph);
            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertAllSucceeded(auditCapture);
        } finally {
            loop.stop("l09-audit");
            loop.shutdown();
        }
    }
}
