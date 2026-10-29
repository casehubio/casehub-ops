package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateEventTypes;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.MergedEventSource;
import io.casehub.desiredstate.api.NodeId;
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
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
import io.casehub.ops.claudony.ClaudonyFaultPolicy;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class FleetObservabilityAcceptanceTest extends TopologyTestBase {

    private static final String TENANT = "l08-acceptance";
    private static final String YAML_PATH =
            "topologies/deployment/08-fleet-observability/08-fleet-observability.yaml";
    private static final List<String> FLEET_APP_IDS =
            List.of("my-llm-analyst", "my-llm-coder", "my-llm-assistant");

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void fleetObservabilityDetectsAndReportsHealth() throws Exception {
        System.out.println("\n═══ Level 08: Fleet Observability ═══");
        System.out.println("Deploying fleet topology with health monitoring...");

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
            AuditAssertions.assertNoCloudEventsOfType(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
            System.out.printf("✓ Topology deployed — %d nodes provisioned%n", graph.nodes().size());

            System.out.println("\nVerifying fleet health — all members operational...");
            for (String appId : FLEET_APP_IDS) {
                String nodeId = findNodeIdContaining(graph, appId.replace("my-llm-", "my-llm."));
                AuditAssertions.assertNodeSucceeded(auditCapture, nodeId);
                System.out.printf("  ✓ %s — healthy (provisioned and converged)%n", appId);
            }

            System.out.println("\nExercising fleet...");
            List<LlmResponse> responses = new ArrayList<>();
            for (String appId : FLEET_APP_IDS) {
                DesiredNode node = graph.nodes().values().stream()
                        .filter(n -> n.spec() instanceof ClaudonyAppNodeSpec spec
                                && spec.name().equals(appId))
                        .findFirst().orElseThrow();
                ClaudonyAppNodeSpec spec = (ClaudonyAppNodeSpec) node.spec();
                LlmResponse response =
                        FleetLlmExerciser.exercise(appId, spec.config());
                FleetAssertions.assertResponseWellFormed(response);
                responses.add(response);
                System.out.printf("  ✓ %s (%s) responded — %d chars%n",
                        appId, response.domain(), response.content().length());
            }
            FleetAssertions.assertRouteConsistent(responses);
            System.out.println("  ✓ Fleet responses routed consistently");

            System.out.println("\nInjecting fault — simulating LLM failure...");
            String faultNodeId = findNodeIdContaining(graph, "my-llm.analyst");
            fleetClient.deregisterApp("my-llm-analyst");
            provisioner.failNode(faultNodeId, 1);
            awaitNextCycle(loop, auditCapture);

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_FAULTED);
            AuditAssertions.assertCloudEventForNode(
                    auditCapture, DesiredStateEventTypes.NODE_FAULTED, faultNodeId);
            System.out.printf("  ✓ Fault detected for %s — NODE_FAULTED event emitted%n", faultNodeId);

            System.out.println("\nRecovering — clearing fault injection...");
            provisioner.clearFailures();
            awaitNextCycle(loop, auditCapture);

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_RECOVERED);
            System.out.println("  ✓ Recovery detected — NODE_RECOVERED event emitted");
            System.out.println("  ✓ Observability chain complete: healthy → faulted → recovered");

        } finally {
            loop.stop(TENANT);
            loop.shutdown();
        }

        System.out.println("\n═══ Level 08: PASSED ═══\n");
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
