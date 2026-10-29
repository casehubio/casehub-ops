package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateEventTypes;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.MergedEventSource;
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
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
import io.casehub.ops.testing.audit.AuditAssertions;
import io.casehub.ops.testing.audit.AuditCapture;
import io.casehub.ops.testing.audit.CapturingTransitionExecutor;
import io.casehub.ops.testing.env.FleetAssertions;
import io.casehub.ops.testing.env.LlmResponse;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
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
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);

        assertThat(graph.nodes()).hasSize(6);

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);

        deployAllNodes(graph, target);

        for (String appId : FLEET_APP_IDS) {
            NodeStatus status = target.status(appId);
            assertThat(status)
                    .as("Status of %s after deployment", appId)
                    .isNotEqualTo(NodeStatus.ABSENT);
        }

        List<LlmResponse> responses =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS, appId -> {
                    DesiredNode node = graph.nodes().values().stream()
                            .filter(n -> extractAppSpec(n).applicationId().equals(appId))
                            .findFirst().orElseThrow();
                    return extractAppSpec(node).config();
                });
        responses.forEach(FleetAssertions::assertResponseWellFormed);
        FleetAssertions.assertRouteConsistent(responses);
    }

    @Test
    void fleetInstancesHaveDistinctApplicationIds() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);

        List<String> llmAppIds = graph.nodes().entrySet().stream()
                .filter(e -> e.getKey().value().startsWith("my-llm."))
                .map(e -> extractAppSpec(e.getValue()).applicationId())
                .toList();

        assertThat(llmAppIds)
                .containsExactlyInAnyOrderElementsOf(FLEET_APP_IDS);

        List<LlmResponse> responses =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS, appId -> {
                    DesiredNode node = graph.nodes().values().stream()
                            .filter(n -> extractAppSpec(n).applicationId().equals(appId))
                            .findFirst().orElseThrow();
                    return extractAppSpec(node).config();
                });
        responses.forEach(FleetAssertions::assertResponseWellFormed);
        FleetAssertions.assertRouteConsistent(responses);
    }

    @Test
    void auditRecordsFleetRegistrations() throws Exception {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);

        ObjectMapper            mapper        = new ObjectMapper().registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient client        = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        var                     specHashStore = new SpecHashStore();
        var                     provisioner   = new io.casehub.ops.claudony.ClaudonyNodeProvisioner(client, specHashStore);
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
            loop.start("l04-audit", graph);
            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertAllSucceeded(auditCapture);
        } finally {
            loop.stop("l04-audit");
            loop.shutdown();
        }
    }


}
