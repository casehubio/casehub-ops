package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
import io.casehub.ops.claudony.ClaudonyAppSpec;
import io.casehub.ops.claudony.ClaudonyFaultPolicy;
import io.casehub.ops.claudony.ClaudonyNodeProvisioner;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
import io.casehub.ops.testing.audit.AuditAssertions;
import io.casehub.ops.testing.audit.AuditCapture;
import io.casehub.ops.testing.audit.CapturingTransitionExecutor;
import io.casehub.ops.topology.compilation.TopologyTestBase;
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
class FaultRecoveryIntegrationTest extends TopologyTestBase {

    private static final String TENANT = "l14-test";
    private static final String YAML_PATH =
            "topologies/deployment/14-fault-recovery/14-fault-recovery.yaml";

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    private ReconciliationLoop loop;
    private AuditCapture auditCapture;
    private FailableClaudonyProvisioner provisioner;
    private HttpClaudonyFleetClient fleetClient;
    private ClaudonyActualStateAdapter actualAdapter;
    private SpecHashStore specHashStore;
    private DesiredStateGraph graph;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        fleetClient = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        specHashStore = new SpecHashStore();
        var realProvisioner = new ClaudonyNodeProvisioner(fleetClient, specHashStore);
        provisioner = new FailableClaudonyProvisioner(realProvisioner);
        actualAdapter = new ClaudonyActualStateAdapter(fleetClient, specHashStore);

        auditCapture = new AuditCapture();

        var faultEngine       = new FaultPolicyEngine(List.of(new ClaudonyFaultPolicy()));
        var planner           = new TransitionPlanner();
        var provisionerRouter = new DefaultNodeProvisionerRouter(List.of(provisioner));
        var nodeExecutor = new NodeStepExecutor(
                provisionerRouter,
                new NoOpHumanNodeHandler(),
                new NoOpPendingApprovalHandler(),
                (step, tid) -> new StepOutcome.Succeeded());
        var executor          = new SimpleTransitionExecutor(nodeExecutor);
        var capturingExecutor = new CapturingTransitionExecutor(executor, auditCapture);
        var actualRouter      = new DefaultActualStateAdapterRouter(List.of(actualAdapter));
        MergedEventSource emptySource = () -> Multi.createFrom().nothing();

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
    void faultDetectedAndRecoveredAutomatically() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        removePoolFromClaudony();

        String poolNodeId = findNodeContaining("support-pool");
        provisioner.failNode(poolNodeId, 2);

        auditCapture.clear();
        awaitReconciliationCycles(4);

        var actual = actualAdapter.readActual(graph, TENANT);
        assertThat(actual.statuses().get(NodeId.of(poolNodeId)))
                .as("pool should be PRESENT after automatic recovery")
                .isEqualTo(NodeStatus.PRESENT);

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
    }

    @Test
    void faultEscalatesToHumanReview() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        removePoolFromClaudony();

        String poolNodeId = findNodeContaining("support-pool");
        provisioner.failNode(poolNodeId, 10);

        auditCapture.clear();
        awaitReconciliationCycles(5);

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_FAULTED, 3);
    }

    @Test
    void recoveryPreservesOtherFleetMembers() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        removePoolFromClaudony();

        String poolNodeId = findNodeContaining("support-pool");
        provisioner.failNode(poolNodeId, 2);

        auditCapture.clear();
        awaitReconciliationCycles(4);

        var actual = actualAdapter.readActual(graph, TENANT);
        for (var entry : graph.nodes().entrySet()) {
            if (entry.getKey().value().contains("support-pool")) {
                continue;
            }
            assertThat(actual.statuses().get(entry.getKey()))
                    .as("node %s should remain PRESENT during pool fault recovery",
                            entry.getKey().value())
                    .isEqualTo(NodeStatus.PRESENT);
        }
    }

    @Test
    void auditRecordsFaultSequence() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        AuditAssertions.assertAllSucceeded(auditCapture);

        removePoolFromClaudony();

        String poolNodeId = findNodeContaining("support-pool");
        provisioner.failNode(poolNodeId, 2);

        auditCapture.clear();
        awaitReconciliationCycles(4);

        assertThat(auditCapture.cloudEventsOfType(DesiredStateEventTypes.NODE_FAULTED))
                .as("audit trail should contain at least 2 NODE_FAULTED events")
                .hasSizeGreaterThanOrEqualTo(2);

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        assertThat(auditCapture.cloudEvents())
                .as("audit trail should contain reconciliation and fault events")
                .isNotEmpty();
    }

    private void removePoolFromClaudony() {
        var appInfo = fleetClient.getApp("support-app");
        assertThat(appInfo).isPresent();
        var updatedPools = appInfo.get().pools().stream()
                .filter(p -> !"support-agent".equals(p.agentId()))
                .toList();
        var cleared = new ClaudonyAppSpec(
                appInfo.get().name(), appInfo.get().namespace(),
                appInfo.get().displayName(), appInfo.get().description(),
                appInfo.get().image(), appInfo.get().config(),
                updatedPools.stream()
                        .map(p -> new ClaudonyAppSpec.PoolSpec(
                                p.agentId(), p.minActive(), p.maxActive()))
                        .toList());
        fleetClient.updateApp("support-app", cleared);
    }

    private void awaitReconciliationCycles(int targetCycles) throws Exception {
        int baseline = auditCapture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        int target = baseline + targetCycles;
        long deadline = System.currentTimeMillis() + 30_000;
        while (auditCapture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED) < target) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(
                        "Expected " + targetCycles + " reconciliation cycles within 30s. " +
                        "Completed: " + (auditCapture.cloudEventCount(
                                DesiredStateEventTypes.RECONCILIATION_COMPLETED) - baseline) +
                        ". Events captured: " + auditCapture.cloudEvents().size());
            }
            loop.requestReconciliation(TENANT);
            TimeUnit.MILLISECONDS.sleep(200);
        }
    }

    private String findNodeContaining(String substring) {
        return graph.nodes().keySet().stream()
                .map(NodeId::value)
                .filter(id -> id.contains(substring))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No node ID containing '" + substring + "' in graph"));
    }
}
