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
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
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
import java.util.List;


@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class AccountabilityAcceptanceTest extends TopologyTestBase {

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void accountabilityLedgerRecordsWorkAttribution() throws Exception {
        System.out.println("\n═══ Level 02: Accountability ═══");
        System.out.println("Deploying topology with audit trail...");

        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);

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
            loop.start("l02-accountability", graph);

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertAllSucceeded(auditCapture);
            System.out.printf("✓ Topology deployed — %d nodes provisioned%n", graph.nodes().size());

            System.out.println("\nVerifying accountability trail completeness...");
            AuditAssertions.assertAuditTrailCoversGraph(auditCapture, graph);
            System.out.printf("  ✓ Audit trail covers all %d graph nodes%n", graph.nodes().size());

            System.out.println("\nVerifying CloudEvent attribution...");
            AuditAssertions.assertCloudEventsHaveAttribution(auditCapture, "l02-accountability");
            System.out.printf("  ✓ All %d CloudEvents have source, type, timestamp, and tenancyId%n",
                    auditCapture.cloudEvents().size());

            System.out.println("\nVerifying reconciliation summary integrity...");
            AuditAssertions.assertReconciliationSummaryConsistent(auditCapture, graph);
            System.out.println("  ✓ Reconciliation summary matches graph: correct nodeCount, zero faults");

            System.out.println("\nExercising fleet...");
            DesiredNode llmNode = graph.nodes().get(NodeId.of("my-llm"));
            CaseHubApplicationSpec llmSpec = extractAppSpec(llmNode);
            LlmResponse response =
                    FleetLlmExerciser.exercise(llmSpec.applicationId(), llmSpec.config());
            FleetAssertions.assertResponseWellFormed(response);
            System.out.printf("  ✓ %s responded (%s/%s, %d chars)%n",
                    response.appId(), response.backendKey(), response.instanceId(),
                    response.content().length());

            System.out.println("\nVerifying work attribution chain...");
            AuditAssertions.assertCloudEventForNode(
                    auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED, "my-llm");
            System.out.println("  ✓ Reconciliation event traces to my-llm provisioning");
            System.out.printf("  ✓ Accountability chain verified: %d CloudEvents, %d node outcomes%n",
                    auditCapture.cloudEvents().size(), auditCapture.allOutcomes().size());

        } finally {
            loop.stop("l02-accountability");
            loop.shutdown();
        }

        System.out.println("\n═══ Level 02: PASSED ═══\n");
    }
}
