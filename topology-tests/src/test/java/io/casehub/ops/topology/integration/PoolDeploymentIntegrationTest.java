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
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
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
class PoolDeploymentIntegrationTest extends TopologyTestBase {

    private static final String TENANT = "l10-test";
    private static final String YAML_PATH =
            "topologies/deployment/10-pool-deployment/10-pool-deployment.yaml";

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
    void poolConvergesToDesiredState() {
        loop.start(TENANT, graph);

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        AuditAssertions.assertNoCloudEventsOfType(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
        AuditAssertions.assertAllSucceeded(auditCapture);

        var poolNode = findNodeContaining("support-pool");
        assertThat(poolNode).as("pool node must exist in graph").isNotNull();

        var actual = actualAdapter.readActual(graph, TENANT);
        assertThat(actual.statuses().get(NodeId.of(poolNode)))
                .as("pool should be PRESENT after convergence")
                .isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void poolScalesUpOnDesiredStateChange() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        var scaledUpPool = new ClaudonyPoolNodeSpec("support-app", "support-agent", 5, 10);
        DesiredStateGraph scaledGraph = replacePoolSpec(graph, scaledUpPool);

        auditCapture.clear();
        loop.updateDesired(TENANT, scaledGraph);
        loop.requestReconciliation(TENANT);
        awaitNextCycle();

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        AuditAssertions.assertNoFailures(auditCapture);

        var appInfo = fleetClient.getApp("support-app");
        assertThat(appInfo).isPresent();
        var pool = appInfo.get().pools().stream()
                .filter(p -> "support-agent".equals(p.agentId()))
                .findFirst();
        assertThat(pool).as("pool entry must exist after scale-up").isPresent();
        assertThat(pool.get().minActive()).as("minActive after scale-up").isEqualTo(5);
        assertThat(pool.get().maxActive()).as("maxActive after scale-up").isEqualTo(10);
    }

    @Test
    void poolScalesDownOnDesiredStateChange() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        var scaledDownPool = new ClaudonyPoolNodeSpec("support-app", "support-agent", 1, 4);
        DesiredStateGraph scaledGraph = replacePoolSpec(graph, scaledDownPool);

        auditCapture.clear();
        loop.updateDesired(TENANT, scaledGraph);
        loop.requestReconciliation(TENANT);
        awaitNextCycle();

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        AuditAssertions.assertNoFailures(auditCapture);

        var appInfo = fleetClient.getApp("support-app");
        assertThat(appInfo).isPresent();
        var pool = appInfo.get().pools().stream()
                .filter(p -> "support-agent".equals(p.agentId()))
                .findFirst();
        assertThat(pool).as("pool entry must exist after scale-down").isPresent();
        assertThat(pool.get().minActive()).as("minActive after scale-down").isEqualTo(1);
        assertThat(pool.get().maxActive()).as("maxActive after scale-down").isEqualTo(4);
    }

    @Test
    void poolReplacesUnhealthyInstance() throws Exception {
        loop.start(TENANT, graph);
        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        var appInfo = fleetClient.getApp("support-app");
        assertThat(appInfo).isPresent();
        assertThat(appInfo.get().pools().stream().anyMatch(p -> "support-agent".equals(p.agentId())))
                .as("pool should exist before removal")
                .isTrue();

        var updatedPools = appInfo.get().pools().stream()
                .filter(p -> !"support-agent".equals(p.agentId()))
                .toList();
        var cleared = new io.casehub.ops.claudony.ClaudonyAppSpec(
                appInfo.get().name(), appInfo.get().namespace(),
                appInfo.get().displayName(), appInfo.get().description(),
                appInfo.get().image(), appInfo.get().config(),
                updatedPools.stream()
                        .map(p -> new io.casehub.ops.claudony.ClaudonyAppSpec.PoolSpec(
                                p.agentId(), p.minActive(), p.maxActive()))
                        .toList());
        fleetClient.updateApp("support-app", cleared);

        var actualBefore = actualAdapter.readActual(graph, TENANT);
        String poolNodeId = findNodeContaining("support-pool");
        assertThat(actualBefore.statuses().get(NodeId.of(poolNodeId)))
                .as("pool should be ABSENT after removal")
                .isEqualTo(NodeStatus.ABSENT);

        auditCapture.clear();
        loop.requestReconciliation(TENANT);
        awaitNextCycle();

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);

        var actualAfter = actualAdapter.readActual(graph, TENANT);
        assertThat(actualAfter.statuses().get(NodeId.of(poolNodeId)))
                .as("pool should be PRESENT after reconciliation recovery")
                .isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void auditRecordsPoolOperations() {
        loop.start(TENANT, graph);

        AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        AuditAssertions.assertAllSucceeded(auditCapture);

        assertThat(auditCapture.cloudEvents())
                .as("audit trail should contain reconciliation events")
                .isNotEmpty();
    }

    private DesiredStateGraph replacePoolSpec(DesiredStateGraph original,
                                               ClaudonyPoolNodeSpec newPoolSpec) {
        DesiredStateGraph result = original;
        for (var entry : original.nodes().entrySet()) {
            if (entry.getValue().spec() instanceof ClaudonyPoolNodeSpec) {
                var replacement = new DesiredNode(
                        entry.getKey(),
                        newPoolSpec,
                        entry.getValue().humanGating());
                result = result.withNode(replacement);
            }
        }
        return result;
    }

    private void awaitNextCycle() throws Exception {
        int baseline = auditCapture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED);
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

    private String findNodeContaining(String substring) {
        return graph.nodes().keySet().stream()
                .map(NodeId::value)
                .filter(id -> id.contains(substring))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No node ID containing '" + substring + "' in graph"));
    }
}
