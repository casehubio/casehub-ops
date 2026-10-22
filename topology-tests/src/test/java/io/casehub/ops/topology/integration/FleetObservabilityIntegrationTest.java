package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredStateEventTypes;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.MergedEventSource;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeType;
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
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
import io.casehub.ops.claudony.ClaudonyFaultPolicy;
import io.casehub.ops.claudony.ClaudonyNodeProvisioner;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
import io.casehub.ops.testing.audit.AuditAssertions;
import io.casehub.ops.testing.audit.AuditCapture;
import io.casehub.ops.testing.audit.CapturingTransitionExecutor;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class FleetObservabilityIntegrationTest extends TopologyTestBase {

    private static final String TENANT = "l08-test";
    private static final String YAML_PATH =
            "topologies/deployment/08-fleet-observability/08-fleet-observability.yaml";

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    private ReconciliationLoop loop;
    private AuditCapture auditCapture;
    private FailableClaudonyProvisioner provisioner;
    private HttpClaudonyFleetClient fleetClient;
    private DesiredStateGraph graph;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        fleetClient = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        var specHashStore   = new SpecHashStore();
        var realProvisioner = new ClaudonyNodeProvisioner(fleetClient, specHashStore);
        provisioner = new FailableClaudonyProvisioner(realProvisioner);
        var actualAdapter = new ClaudonyActualStateAdapter(fleetClient, specHashStore);

        auditCapture = new AuditCapture();

        var faultEngine       = new FaultPolicyEngine(List.of(new ClaudonyFaultPolicy()));
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

        loop = ReconciliationLoop.builder(planner, capturingExecutor, actualRouter, faultEngine, emptySource)
                                 .cloudEventSink(auditCapture::recordCloudEvent)
                                 .debounceWindow(Duration.ofMillis(50))
                                 .resyncInterval(Duration.ofMinutes(30))
                                 .build();

        var registry = buildTypeRegistryFrom(ClaudonyNodeSpec.class);
        graph = compileSingleGraph(YAML_PATH, registry);
    }

    @AfterEach
    void tearDown() {
        if (loop != null) {
            loop.stop(TENANT);
            loop.shutdown();
        }
    }

    @Test
    void reconciliationConvergesFleet() {
        loop.start(TENANT, graph);

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        AuditAssertions.assertNoCloudEventsOfType(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
        AuditAssertions.assertAllSucceeded(auditCapture);
    }

    @Test
    void provisionFailureEmitsFaultEvent() throws Exception {
        loop.start(TENANT, graph);
        auditCapture.clear();

        String targetNodeId = findNodeIdContaining("my-llm.analyst");
        fleetClient.deregisterApp("my-llm-analyst");
        provisioner.failNode(targetNodeId, 1);

        awaitNextCycle();

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        AuditAssertions.assertNodeFailed(auditCapture, targetNodeId);
    }

    @Test
    void recoveryEmitsRecoveredEvent() throws Exception {
        loop.start(TENANT, graph);

        String targetNodeId = findNodeIdContaining("my-llm.analyst");
        fleetClient.deregisterApp("my-llm-analyst");
        provisioner.failNode(targetNodeId, 1);
        awaitNextCycle();

        auditCapture.clear();
        provisioner.clearFailures();
        awaitNextCycle();

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_RECOVERED);
        AuditAssertions.assertNoCloudEventsOfType(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
    }

    @Test
    void faultPolicyProducesMutation() throws Exception {
        loop.start(TENANT, graph);

        String targetNodeId = findNodeIdContaining("my-llm.analyst");
        fleetClient.deregisterApp("my-llm-analyst");
        provisioner.failNode(targetNodeId, 3);

        awaitNextCycle();
        awaitNextCycle();
        awaitNextCycle();

        var desiredAfterMutation = loop.getDesired(TENANT);
        boolean hasReviewNode = desiredAfterMutation.nodes().values().stream()
                .anyMatch(n -> n.type().equals(NodeType.of("claudony_review")));

        assertThat(hasReviewNode)
                .as("ClaudonyFaultPolicy should add a claudony_review node after tier 3 threshold")
                .isTrue();
    }

    @Test
    void fullObservabilityChain() throws Exception {
        loop.start(TENANT, graph);

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        AuditAssertions.assertNoCloudEventsOfType(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
        AuditAssertions.assertAllSucceeded(auditCapture);

        String targetNodeId = findNodeIdContaining("my-llm.analyst");
        fleetClient.deregisterApp("my-llm-analyst");
        provisioner.failNode(targetNodeId, 1);
        awaitNextCycle();

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
        AuditAssertions.assertCloudEventForNode(auditCapture, DesiredStateEventTypes.NODE_FAULTED, targetNodeId);

        provisioner.clearFailures();
        awaitNextCycle();

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_RECOVERED);

        List<String> eventTypes = auditCapture.cloudEvents().stream()
                                              .map(CloudEvent::getType)
                                              .toList();

        assertThat(eventTypes)
                .as("Full chain should include completed, faulted, and recovered events")
                .contains(
                        DesiredStateEventTypes.RECONCILIATION_COMPLETED,
                        DesiredStateEventTypes.NODE_FAULTED,
                        DesiredStateEventTypes.NODE_RECOVERED);
    }

    private void awaitNextCycle() throws Exception {
        int baseline = auditCapture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        loop.requestReconciliation(TENANT);
        long deadline = System.currentTimeMillis() + 15_000;
        while (auditCapture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED) <= baseline) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(
                        "Reconciliation cycle did not complete within 15s. " +
                        "Events captured: " + auditCapture.cloudEvents().size());
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }

    private String findNodeIdContaining(String substring) {
        return graph.nodes().keySet().stream()
                .map(NodeId::value)
                .filter(id -> id.contains(substring))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No node ID containing '" + substring + "' in graph"));
    }
}
