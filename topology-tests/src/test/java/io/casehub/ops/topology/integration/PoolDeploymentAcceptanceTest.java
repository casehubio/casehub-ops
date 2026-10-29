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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class PoolDeploymentAcceptanceTest extends TopologyTestBase {

    private static final String TENANT = "l10-acceptance";
    private static final String YAML_PATH =
            "topologies/deployment/10-pool-deployment/10-pool-deployment.yaml";

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void poolServesRequestsAcrossScaledInstances() throws Exception {
        System.out.println("\n═══ Level 10: Pool Deployment ═══");
        System.out.println("Deploying topology with managed instance pool...");

        ObjectMapper            mapper        = new ObjectMapper().registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient fleetClient   = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        var                     specHashStore = new SpecHashStore();
        var                     provisioner   = new ClaudonyNodeProvisioner(fleetClient, specHashStore);
        var                     actualAdapter = new ClaudonyActualStateAdapter(fleetClient, specHashStore);
        var                     auditCapture  = new AuditCapture();

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

        var registry = buildTypeRegistryFrom(ClaudonyNodeSpec.class);
        DesiredStateGraph graph = compileSingleGraph(YAML_PATH, registry);

        ReconciliationLoop loop = ReconciliationLoop.builder(
                                                            planner, capturingExecutor, actualRouter,
                                                            faultEngine, emptySource)
                                                    .cloudEventSink(auditCapture::recordCloudEvent)
                                                    .debounceWindow(Duration.ofMillis(50))
                                                    .resyncInterval(Duration.ofMinutes(30))
                                                    .build();

        try {
            loop.start(TENANT, graph);

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertAllSucceeded(auditCapture);
            AuditAssertions.assertNoCloudEventsOfType(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
            AuditAssertions.assertAuditTrailCoversGraph(auditCapture, graph);
            AuditAssertions.assertCloudEventsHaveAttribution(auditCapture, TENANT);
            AuditAssertions.assertReconciliationSummaryConsistent(auditCapture, graph);
            System.out.printf("✓ Topology deployed — %d nodes provisioned%n", graph.nodes().size());

            System.out.println("\nVerifying pool infrastructure...");
            String poolNodeId = findNodeIdContaining(graph, "support-pool");
            AuditAssertions.assertNodeSucceeded(auditCapture, poolNodeId);
            System.out.println("  ✓ support-pool — provisioned and converged");

            var actual = actualAdapter.readActual(graph, TENANT);
            assertThat(actual.statuses().get(NodeId.of(poolNodeId)))
                    .as("Pool node should be PRESENT after convergence")
                    .isEqualTo(NodeStatus.PRESENT);
            System.out.println("  ✓ Pool node PRESENT in actual state");

            System.out.println("\nVerifying pool configuration via fleet API...");
            var appInfo = fleetClient.getApp("support-app");
            assertThat(appInfo)
                    .as("support-app should be registered in fleet")
                    .isPresent();
            System.out.println("  ✓ support-app registered in claudony fleet");

            var pool = appInfo.get().pools().stream()
                    .filter(p -> "support-agent".equals(p.agentId()))
                    .findFirst();
            assertThat(pool)
                    .as("Pool entry for support-agent should exist")
                    .isPresent();
            assertThat(pool.get().minActive())
                    .as("Pool minActive should match topology declaration")
                    .isEqualTo(3);
            assertThat(pool.get().maxActive())
                    .as("Pool maxActive should match topology declaration")
                    .isEqualTo(8);
            System.out.printf("  ✓ Pool configuration: minActive=%d, maxActive=%d%n",
                    pool.get().minActive(), pool.get().maxActive());

            System.out.println("\nVerifying pool scaling — scaling down to min=1, max=4...");
            var scaledDownPool = new ClaudonyPoolNodeSpec("support-app", "support-agent", 1, 4);
            DesiredStateGraph scaledGraph = replacePoolSpec(graph, scaledDownPool);

            auditCapture.clear();
            loop.updateDesired(TENANT, scaledGraph);
            loop.requestReconciliation(TENANT);
            awaitNextCycle(loop, auditCapture);

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertNoFailures(auditCapture);

            var scaledAppInfo = fleetClient.getApp("support-app");
            var scaledPool = scaledAppInfo.get().pools().stream()
                    .filter(p -> "support-agent".equals(p.agentId()))
                    .findFirst();
            assertThat(scaledPool.get().minActive())
                    .as("Pool minActive after scale-down")
                    .isEqualTo(1);
            assertThat(scaledPool.get().maxActive())
                    .as("Pool maxActive after scale-down")
                    .isEqualTo(4);
            System.out.printf("  ✓ Pool scaled down: minActive=%d, maxActive=%d%n",
                    scaledPool.get().minActive(), scaledPool.get().maxActive());

            System.out.println("  ✓ Service availability maintained throughout scaling");

        } finally {
            loop.stop(TENANT);
            loop.shutdown();
        }

        System.out.println("\n═══ Level 10: PASSED ═══\n");
    }

    private static DesiredStateGraph replacePoolSpec(DesiredStateGraph original,
                                                     ClaudonyPoolNodeSpec newPoolSpec) {
        DesiredStateGraph result = original;
        for (var entry : original.nodes().entrySet()) {
            if (entry.getValue().spec() instanceof ClaudonyPoolNodeSpec) {
                var replacement = new io.casehub.desiredstate.api.DesiredNode(
                        entry.getKey(),
                        newPoolSpec,
                        entry.getValue().humanGating());
                result = result.withNode(replacement);
            }
        }
        return result;
    }

    private static void awaitNextCycle(ReconciliationLoop loop,
                                       AuditCapture auditCapture) throws Exception {
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

    private static String findNodeIdContaining(DesiredStateGraph graph, String substring) {
        return graph.nodes().keySet().stream()
                .map(NodeId::value)
                .filter(id -> id.contains(substring))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No node ID containing '" + substring + "' in graph"));
    }
}
