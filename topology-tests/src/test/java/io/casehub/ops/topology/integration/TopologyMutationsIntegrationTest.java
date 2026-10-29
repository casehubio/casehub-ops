package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateEventTypes;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.GraphMutation;
import io.casehub.desiredstate.api.MergedEventSource;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
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
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class TopologyMutationsIntegrationTest extends TopologyTestBase {

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void prunesRemovedNodeBeforeGrowingNewNode() throws IOException {
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        DesiredStateGraph mutatedGraph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", DEPLOYMENT_REGISTRY);

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);

        deployAllNodes(initialGraph, target);

        assertThat(target.status("my-llm-assistant"))
                .as("Assistant present before mutation")
                .isEqualTo(NodeStatus.PRESENT);
        assertThat(target.status("my-llm-researcher"))
                .as("Researcher absent before mutation")
                .isEqualTo(NodeStatus.ABSENT);

        Set<NodeId> initialNodeIds = initialGraph.nodes().keySet();
        Set<NodeId> mutatedNodeIds = mutatedGraph.nodes().keySet();

        Set<NodeId> removedNodes = new LinkedHashSet<>(initialNodeIds);
        removedNodes.removeAll(mutatedNodeIds);

        Set<NodeId> addedNodes = new LinkedHashSet<>(mutatedNodeIds);
        addedNodes.removeAll(initialNodeIds);

        Set<String> removedAppIds = removedNodes.stream()
                .map(id -> extractAppSpec(initialGraph.nodes().get(id)).applicationId())
                .collect(Collectors.toSet());
        Set<String> addedAppIds = addedNodes.stream()
                .map(id -> extractAppSpec(mutatedGraph.nodes().get(id)).applicationId())
                .collect(Collectors.toSet());

        assertThat(removedAppIds).containsExactly("my-llm-assistant");
        assertThat(addedAppIds).containsExactly("my-llm-researcher");

        for (NodeId removedId : removedNodes) {
            String appId = extractAppSpec(initialGraph.nodes().get(removedId)).applicationId();
            DeployResult result = target.undeploy(appId);
            assertThat(result)
                    .as("Prune %s", appId)
                    .isInstanceOf(DeployResult.Success.class);
        }

        assertThat(target.status("my-llm-assistant"))
                .as("Assistant absent after prune")
                .isEqualTo(NodeStatus.ABSENT);

        for (NodeId addedId : addedNodes) {
            CaseHubApplicationSpec appSpec = extractAppSpec(mutatedGraph.nodes().get(addedId));
            DeployResult result = target.deploy(
                    appSpec.image(),
                    new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                            appSpec.config(), Map.of()));
            assertThat(result)
                    .as("Grow %s", appSpec.applicationId())
                    .isInstanceOf(DeployResult.Success.class);
        }

        assertThat(target.status("my-llm-analyst"))
                .as("Analyst still present (unchanged)")
                .isEqualTo(NodeStatus.PRESENT);
        assertThat(target.status("my-llm-coder"))
                .as("Coder still present (unchanged)")
                .isEqualTo(NodeStatus.PRESENT);
        assertThat(target.status("my-llm-researcher"))
                .as("Researcher now present (added)")
                .isEqualTo(NodeStatus.PRESENT);
        assertThat(target.status("my-llm-assistant"))
                .as("Assistant now absent (removed)")
                .isEqualTo(NodeStatus.ABSENT);

        List<String> mutatedFleetAppIds = List.of("my-llm-analyst", "my-llm-coder", "my-llm-researcher");
        List<LlmResponse> postMutation =
                FleetLlmExerciser.exerciseFleet(mutatedFleetAppIds, appId -> {
                    DesiredNode node = mutatedGraph.nodes().entrySet().stream()
                            .filter(e -> extractAppSpec(e.getValue()).applicationId().equals(appId))
                            .map(Map.Entry::getValue)
                            .findFirst().orElseThrow();
                    return extractAppSpec(node).config();
                });
        postMutation.forEach(FleetAssertions::assertResponseWellFormed);
        FleetAssertions.assertRouteConsistent(postMutation);
    }

    @Test
    void graphDiffDetectsAdditionsAndRemovals() throws IOException {
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        DesiredStateGraph mutatedGraph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", DEPLOYMENT_REGISTRY);

        var mutations = GraphDiff.computeMutations(initialGraph, mutatedGraph);

        List<String> added = mutations.stream()
                                      .filter(m -> m instanceof GraphMutation.AddNode<?>)
                                      .map(GraphMutation::targetNodeId)
                                      .toList();
        List<String> removed = mutations.stream()
                                        .filter(m -> m instanceof GraphMutation.RemoveNode<?>)
                                        .map(GraphMutation::targetNodeId)
                                        .toList();

        assertThat(removed).containsExactly("my-llm.assistant");
        assertThat(added).containsExactly("my-llm.researcher");

        assertThat(mutations.stream().filter(m -> m instanceof GraphMutation.UpdateNode<?>).count())
                .as("No nodes updated (analyst and coder unchanged)").isZero();

        CaseHubApplicationSpec researcherSpec = extractAppSpec(
                mutatedGraph.nodes().get(NodeId.of("my-llm.researcher")));
        LlmResponse researcherLlm =
                FleetLlmExerciser.exercise("my-llm-researcher", researcherSpec.config());
        FleetAssertions.assertResponseWellFormed(researcherLlm);
        assertThat(researcherLlm.domain()).isEqualTo("knowledge-discovery");
    }

    @Test
    void auditRecordsMutationOrdering() throws Exception {
        var registry = buildTypeRegistryFrom(ClaudonyNodeSpec.class);
        DesiredStateGraph initialGraph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", registry);
        DesiredStateGraph mutatedGraph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", registry);

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
            loop.start("l07-audit", initialGraph);
            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertAllSucceeded(auditCapture);

            auditCapture.clear();
            loop.updateDesired("l07-audit", mutatedGraph);
            loop.requestReconciliation("l07-audit");
            awaitReconciliation(auditCapture, loop, "l07-audit");

            AuditAssertions.assertNoFailures(auditCapture);
            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);

            String completedJson = lastCloudEventData(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            assertThat(completedJson)
                    .as("Reconciliation should record at least one removal")
                    .doesNotContain("\"removalsCount\":0");
            assertThat(completedJson)
                    .as("Reconciliation should record at least one addition")
                    .doesNotContain("\"additionsCount\":0");

            assertThat(auditCapture.succeededNodes())
                    .as("Mutation transition should have succeeded nodes")
                    .isNotEmpty();
        } finally {
            loop.stop("l07-audit");
            loop.shutdown();
        }
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
