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
class FaultRecoveryAcceptanceTest extends TopologyTestBase {

    private static final String TENANT = "l14-acceptance";
    private static final String YAML_PATH =
            "topologies/deployment/14-fault-recovery/14-fault-recovery.yaml";

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void faultRecoveryReplacesAndVerifiesRecoveredInstance() throws Exception {
        System.out.println("\n═══ Level 14: Fault Recovery ═══");
        System.out.println("Deploying topology with fault recovery...");

        ObjectMapper            mapper        = new ObjectMapper().registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient fleetClient   = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        var                     specHashStore = new SpecHashStore();
        var                     realProvisioner = new ClaudonyNodeProvisioner(fleetClient, specHashStore);
        var                     provisioner   = new FailableClaudonyProvisioner(realProvisioner);
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
            AuditAssertions.assertAuditTrailCoversGraph(auditCapture, graph);
            AuditAssertions.assertCloudEventsHaveAttribution(auditCapture, TENANT);
            AuditAssertions.assertReconciliationSummaryConsistent(auditCapture, graph);
            System.out.printf("✓ Topology deployed — %d nodes provisioned%n", graph.nodes().size());

            System.out.println("\nVerifying initial fleet health...");
            var initialActual = actualAdapter.readActual(graph, TENANT);
            for (var entry : graph.nodes().entrySet()) {
                assertThat(initialActual.statuses().get(entry.getKey()))
                        .as("Node %s should be PRESENT after initial deployment",
                                entry.getKey().value())
                        .isEqualTo(NodeStatus.PRESENT);
                System.out.printf("  ✓ %s — PRESENT%n", entry.getKey().value());
            }

            System.out.println("\nInjecting fault — removing pool from claudony...");
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

            String poolNodeId = findNodeIdContaining(graph, "support-pool");
            provisioner.failNode(poolNodeId, 2);
            System.out.printf("  ✓ Fault injected on %s (2 failures before recovery)%n", poolNodeId);

            System.out.println("\nWaiting for fault detection and recovery...");
            auditCapture.clear();
            awaitReconciliationCycles(loop, auditCapture, 4);

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
            System.out.println("  ✓ Fault detected — NODE_FAULTED event emitted");

            int faultCount = auditCapture.cloudEventsOfType(DesiredStateEventTypes.NODE_FAULTED).size();
            System.out.printf("  ✓ %d fault events recorded in audit trail%n", faultCount);

            System.out.println("\nVerifying recovery...");
            var recoveredActual = actualAdapter.readActual(graph, TENANT);
            assertThat(recoveredActual.statuses().get(NodeId.of(poolNodeId)))
                    .as("Pool should be PRESENT after automatic recovery")
                    .isEqualTo(NodeStatus.PRESENT);
            System.out.printf("  ✓ %s — recovered to PRESENT%n", poolNodeId);

            System.out.println("\nVerifying service continuity — other nodes unaffected...");
            for (var entry : graph.nodes().entrySet()) {
                if (entry.getKey().value().contains("support-pool")) continue;
                assertThat(recoveredActual.statuses().get(entry.getKey()))
                        .as("Node %s should remain PRESENT during pool fault recovery",
                                entry.getKey().value())
                        .isEqualTo(NodeStatus.PRESENT);
                System.out.printf("  ✓ %s — still PRESENT (unaffected)%n",
                        entry.getKey().value());
            }

            System.out.println("\nVerifying audit trail...");
            assertThat(auditCapture.cloudEventsOfType(DesiredStateEventTypes.NODE_FAULTED))
                    .as("Audit trail should contain fault events")
                    .hasSizeGreaterThanOrEqualTo(2);
            AuditAssertions.assertCloudEventEmitted(auditCapture,
                    DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            System.out.println("  ✓ Audit trail: fault events → reconciliation → recovery");
            System.out.println("  ✓ Full lifecycle: deploy → fault → detect → recover → verify");

        } finally {
            loop.stop(TENANT);
            loop.shutdown();
        }

        System.out.println("\n═══ Level 14: PASSED ═══\n");
    }

    private static void awaitReconciliationCycles(ReconciliationLoop loop,
                                                   AuditCapture auditCapture,
                                                   int targetCycles) throws Exception {
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

    private static String findNodeIdContaining(DesiredStateGraph graph, String substring) {
        return graph.nodes().keySet().stream()
                .map(NodeId::value)
                .filter(id -> id.contains(substring))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No node ID containing '" + substring + "' in graph"));
    }
}
