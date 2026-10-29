package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateEventTypes;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.MergedEventSource;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TargetStatus;
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
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.claudony.ClaudonyNodeProvisioner;
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
import java.util.concurrent.TimeUnit;

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
        List<LlmResponse> preSuspend =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS,
                        appId -> fleetClient.getApp(appId).orElseThrow().config());
        preSuspend.forEach(FleetAssertions::assertResponseWellFormed);
        FleetAssertions.assertRouteConsistent(preSuspend);

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

        List<LlmResponse> postResume =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS,
                        appId -> fleetClient.getApp(appId).orElseThrow().config());
        postResume.forEach(FleetAssertions::assertResponseWellFormed);
        FleetAssertions.assertRouteConsistent(postResume);
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
        LlmResponse coderResponse =
                FleetLlmExerciser.exercise("my-llm-coder",
                        fleetClient2.getApp("my-llm-coder").orElseThrow().config());
        FleetAssertions.assertResponseWellFormed(coderResponse);
    }

    @Test
    void auditRecordsLifecycleTransitions() throws Exception {
        var registry = buildTypeRegistryFrom(ClaudonyNodeSpec.class);
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/06-lifecycle/06-lifecycle.yaml", registry);

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
            loop.start("l06-audit", graph);
            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertAllSucceeded(auditCapture);
            AuditAssertions.assertAuditTrailCoversGraph(auditCapture, graph);
            AuditAssertions.assertCloudEventsHaveAttribution(auditCapture, "l06-audit");
            AuditAssertions.assertReconciliationSummaryConsistent(auditCapture, graph);

            DesiredStateGraph suspendedGraph = withTargetStatus(graph, TargetStatus.SUSPENDED);
            auditCapture.clear();
            loop.updateDesired("l06-audit", suspendedGraph);
            loop.requestReconciliation("l06-audit");
            awaitReconciliation(auditCapture, loop, "l06-audit");

            AuditAssertions.assertNoFailures(auditCapture);
            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            String completedJson = lastCloudEventData(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            assertThat(completedJson)
                    .as("Reconciliation should record suspensions")
                    .contains("\"suspensionsCount\":" + FLEET_APP_IDS.size());

            DesiredStateGraph resumedGraph = withTargetStatus(suspendedGraph, TargetStatus.ACTIVE);
            auditCapture.clear();
            loop.updateDesired("l06-audit", resumedGraph);
            loop.requestReconciliation("l06-audit");
            awaitReconciliation(auditCapture, loop, "l06-audit");

            AuditAssertions.assertNoFailures(auditCapture);
            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            String resumedJson = lastCloudEventData(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            assertThat(resumedJson)
                    .as("Reconciliation should record resumptions")
                    .contains("\"resumptionsCount\":" + FLEET_APP_IDS.size());
        } finally {
            loop.stop("l06-audit");
            loop.shutdown();
        }
    }

    private static DesiredStateGraph withTargetStatus(DesiredStateGraph graph, TargetStatus status) {
        DesiredStateGraph result = graph;
        for (DesiredNode node : graph.nodes().values()) {
            result = result.withNode(
                    new DesiredNode(node.id(), node.spec(), node.humanGating(), node.hooks(), status));
        }
        return result;
    }

    private static String lastCloudEventData(AuditCapture capture, String eventType) {
        var events = capture.cloudEventsOfType(eventType);
        assertThat(events).as("Expected at least one event of type '%s'", eventType).isNotEmpty();
        var last = events.get(events.size() - 1);
        assertThat(last.getData()).as("CloudEvent data should not be null").isNotNull();
        return new String(last.getData().toBytes());
    }

    private static void awaitReconciliation(AuditCapture capture, ReconciliationLoop loop,
                                            String tenantId) throws Exception {
        int  baseline = capture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        long deadline = System.currentTimeMillis() + 15_000;
        while (capture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED) <= baseline) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(
                        "Reconciliation cycle did not complete within 15s. Events: " + capture.cloudEvents().size());
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }


}
