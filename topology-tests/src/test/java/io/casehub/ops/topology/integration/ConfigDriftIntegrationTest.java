package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateEventTypes;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.GraphMutation;
import io.casehub.desiredstate.api.MergedEventSource;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.runtime.DefaultActualStateAdapterRouter;
import io.casehub.desiredstate.runtime.DefaultNodeProvisionerRouter;
import io.casehub.desiredstate.runtime.FaultPolicyEngine;
import io.casehub.desiredstate.runtime.GraphDiff;
import io.casehub.desiredstate.runtime.NoOpHumanNodeHandler;
import io.casehub.desiredstate.runtime.NoOpPendingApprovalHandler;
import io.casehub.desiredstate.runtime.NodeStepExecutor;
import io.casehub.desiredstate.runtime.ReconciliationLoop;
import io.casehub.desiredstate.runtime.SimpleTransitionExecutor;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.api.SpecHashStore;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
import io.casehub.ops.claudony.ClaudonyAppInfo;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class ConfigDriftIntegrationTest extends TopologyTestBase {

    private static final List<String> FLEET_APP_IDS =
            List.of("my-llm-analyst", "my-llm-coder", "my-llm-assistant");

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void detectsConfigDriftAndReconcilesDriftedNodeOnly() throws IOException {
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        DesiredStateGraph driftedGraph = compileSingleGraph(
                "topologies/deployment/05-config-drift/05-config-drift.yaml", DEPLOYMENT_REGISTRY);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient client = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        ClaudonyDeploymentTarget target = new ClaudonyDeploymentTarget(client);

        deployAllNodes(initialGraph, target);

        ClaudonyAppInfo analystBefore = client.getApp("my-llm-analyst").orElseThrow();
        assertThat(analystBefore.config())
                .containsEntry("llm.capability", "deep")
                .containsEntry("llm.domain", "analysis");

        LlmResponse analystBeforeLlm =
                FleetLlmExerciser.exercise("my-llm-analyst", analystBefore.config());
        FleetAssertions.assertResponseWellFormed(analystBeforeLlm);
        assertThat(analystBeforeLlm.domain()).isEqualTo("analysis");

        var mutations = GraphDiff.computeMutations(initialGraph, driftedGraph);
        List<String> driftedAppIds = mutations.stream()
                .filter(m -> m instanceof GraphMutation.UpdateNode<?>)
                .map(m -> extractAppSpec(driftedGraph.nodes().get(NodeId.of(m.targetNodeId()))).applicationId())
                .toList();

        assertThat(driftedAppIds)
                .as("Only analyst should have drifted config")
                .containsExactly("my-llm-analyst");

        for (Map.Entry<NodeId, DesiredNode> entry : driftedGraph.nodes().entrySet()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(entry.getValue());
            if (driftedAppIds.contains(appSpec.applicationId())) {
                DeployResult result = target.deploy(
                        appSpec.image(),
                        new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                                appSpec.config(), Map.of()));
                assertThat(result)
                        .as("Drift reconcile %s", appSpec.applicationId())
                        .isInstanceOf(DeployResult.Success.class);
            }
        }

        ClaudonyAppInfo analystAfter = client.getApp("my-llm-analyst").orElseThrow();
        assertThat(analystAfter.config())
                .containsEntry("llm.capability", "analytical")
                .containsEntry("llm.domain", "data-analysis");

        LlmResponse analystAfterLlm =
                FleetLlmExerciser.exercise("my-llm-analyst", analystAfter.config());
        FleetAssertions.assertResponseWellFormed(analystAfterLlm);
        assertThat(analystAfterLlm.domain()).isEqualTo("data-analysis");

        List<LlmResponse> allResponses =
                FleetLlmExerciser.exerciseFleet(FLEET_APP_IDS,
                        appId -> client.getApp(appId).orElseThrow().config());
        allResponses.forEach(FleetAssertions::assertResponseWellFormed);
        FleetAssertions.assertRouteConsistent(allResponses);

        ClaudonyAppInfo coder = client.getApp("my-llm-coder").orElseThrow();
        assertThat(coder.config()).containsEntry("llm.capability", "fast");

        ClaudonyAppInfo assistant = client.getApp("my-llm-assistant").orElseThrow();
        assertThat(assistant.config()).containsEntry("llm.capability", "balanced");
    }

    @Test
    void graphDiffIdentifiesOnlyChangedNodes() throws IOException {
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        DesiredStateGraph driftedGraph = compileSingleGraph(
                "topologies/deployment/05-config-drift/05-config-drift.yaml", DEPLOYMENT_REGISTRY);

        var mutations = GraphDiff.computeMutations(initialGraph, driftedGraph);

        List<String> updatedNodeIds = mutations.stream()
                                               .filter(m -> m instanceof GraphMutation.UpdateNode<?>)
                                               .map(GraphMutation::targetNodeId)
                                               .toList();

        assertThat(updatedNodeIds)
                .as("Only the analyst fleet member should have changed config")
                .containsExactly("my-llm.analyst");

        assertThat(mutations.stream().filter(m -> m instanceof GraphMutation.AddNode<?>).count())
                .as("No nodes added").isZero();
        assertThat(mutations.stream().filter(m -> m instanceof GraphMutation.RemoveNode<?>).count())
                .as("No nodes removed").isZero();

        CaseHubApplicationSpec initialAnalyst = extractAppSpec(
                initialGraph.nodes().get(NodeId.of("my-llm.analyst")));
        CaseHubApplicationSpec driftedAnalyst = extractAppSpec(
                driftedGraph.nodes().get(NodeId.of("my-llm.analyst")));

        LlmResponse beforeDrift =
                FleetLlmExerciser.exercise("analyst-before", initialAnalyst.config());
        LlmResponse afterDrift =
                FleetLlmExerciser.exercise("analyst-after", driftedAnalyst.config());

        FleetAssertions.assertResponseWellFormed(beforeDrift);
        FleetAssertions.assertResponseWellFormed(afterDrift);
        assertThat(beforeDrift.domain()).isEqualTo("analysis");
        assertThat(afterDrift.domain()).isEqualTo("data-analysis");
    }

    @Test
    void auditRecordsDriftReconciliation() throws Exception {
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        DesiredStateGraph driftedGraph = compileSingleGraph(
                "topologies/deployment/05-config-drift/05-config-drift.yaml", DEPLOYMENT_REGISTRY);

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
            loop.start("l05-audit", initialGraph);
            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertAllSucceeded(auditCapture);
            AuditAssertions.assertAuditTrailCoversGraph(auditCapture, initialGraph);
            AuditAssertions.assertCloudEventsHaveAttribution(auditCapture, "l05-audit");
            AuditAssertions.assertReconciliationSummaryConsistent(auditCapture, initialGraph);

            auditCapture.clear();
            loop.updateDesired("l05-audit", driftedGraph);
            loop.requestReconciliation("l05-audit");
            awaitReconciliation(auditCapture, loop, "l05-audit");

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.NODE_DRIFTED);
            AuditAssertions.assertCloudEventForNode(auditCapture, DesiredStateEventTypes.NODE_DRIFTED, "my-llm.analyst");
            AuditAssertions.assertNoFailures(auditCapture);
            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        } finally {
            loop.stop("l05-audit");
            loop.shutdown();
        }
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
