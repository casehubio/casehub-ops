package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;
import io.casehub.ops.api.deployment.DeploymentNodeSpec;
import io.casehub.ops.api.infra.InfraNodeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("reconciliation")
class DeploymentReconciliationTest extends ReconciliationTestBase {

    private static final Map<String, String> DEPLOYMENT_REGISTRY =
            buildTypeRegistryFrom(InfraNodeSpec.class, DeploymentNodeSpec.class);

    @BeforeEach
    void setUp() {
        Set<NodeType> types = new HashSet<>();
        types.add(NodeType.of("casehub_application"));
        provisioner = new FailableNodeProvisioner();
        provisioner.setHandledTypes(types);
        planner = new io.casehub.desiredstate.runtime.TransitionPlanner();
        actualAdapter = new io.casehub.desiredstate.testing.MockActualStateAdapter();
        actualAdapter.setHandledTypes(types);
    }

    @Test
    void helloLlm_plansOneAddition() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        assertThat(plan.flatAdditions()).hasSize(1);
        assertThat(plan.flatRemovals()).isEmpty();
    }

    @Test
    void helloLlm_executesSuccessfully() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        TransitionResult result = executeTransition(plan, "test-tenant");
        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }

    @Test
    void helloLlm_idempotentAfterExecution() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        executeTransition(plan, "test-tenant");

        actualAdapter.setAllPresent(graph);
        TransitionPlan replan = planWithActual(graph,
                actualAdapter.readActual(graph, "test-tenant"));
        assertThat(replan.flatAdditions()).isEmpty();
        assertThat(replan.flatRemovals()).isEmpty();
    }

    // ── Level 02: Accountability ──────────────────────────────────────────

    @Test
    void accountability_plansTwoAdditions() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        assertThat(plan.flatAdditions()).hasSize(2);
        assertThat(plan.flatRemovals()).isEmpty();
    }

    @Test
    void accountability_platformBeforeLedger() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "platform", "ledger");
    }

    @Test
    void accountability_executesSuccessfully() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        TransitionResult result = executeTransition(plan, "test-tenant");
        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }

    @Test
    void accountability_idempotentAfterExecution() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        executeTransition(plan, "test-tenant");

        actualAdapter.setAllPresent(graph);
        TransitionPlan replan = planWithActual(graph,
                actualAdapter.readActual(graph, "test-tenant"));
        assertThat(replan.flatAdditions()).isEmpty();
        assertThat(replan.flatRemovals()).isEmpty();
    }
// --- Level 03: Case Management ---

    @Test
    void caseManagement_plansThreeAdditions() throws IOException {
        var graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        var plan = planFromEmpty(graph);

        assertThat(plan.flatAdditions()).hasSize(3);
        assertThat(plan.flatRemovals()).isEmpty();
    }

    @Test
    void caseManagement_platformBeforeLedgerBeforeQhorus() throws IOException {
        var graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        var plan = planFromEmpty(graph);

        assertOrderedBefore(plan, "platform", "ledger");
        assertOrderedBefore(plan, "ledger", "qhorus");
    }

    @Test
    void caseManagement_executesSuccessfully() throws IOException {
        var graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        var plan   = planFromEmpty(graph);
        var result = executeTransition(plan, "test-tenant");

        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }

    @Test
    void caseManagement_idempotentAfterExecution() throws IOException {
        var graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        var plan = planFromEmpty(graph);
        executeTransition(plan, "test-tenant");

        actualAdapter.setAllPresent(graph);
        var replan = planWithActual(graph,
                                    actualAdapter.readActual(graph, "test-tenant"));

        assertThat(replan.flatAdditions()).isEmpty();
        assertThat(replan.flatRemovals()).isEmpty();
    }

// ── Level 15: Governed Interaction ────────────────────────────────────

    @Test
    void governedInteraction_compilesWithChannelProtocols() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/15-governed-interaction/15-governed-interaction.yaml",
                DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(4);
        var channelNode = graph.nodes().get(io.casehub.desiredstate.api.NodeId.of("design-review"));
        assertThat(channelNode.spec()).isInstanceOf(io.casehub.ops.api.deployment.ChannelNodeSpec.class);
        var channelSpec = (io.casehub.ops.api.deployment.ChannelNodeSpec) channelNode.spec();
        assertThat(channelSpec.protocols()).containsExactly("NEGOTIATION");
        assertThat(channelSpec.protocolParticipants()).containsExactly("coordinator", "reviewer-1", "reviewer-2");
        assertThat(channelSpec.policyOverrides()).containsEntry("negotiation.acceptance", "majority");
        assertThat(channelSpec.policyOverrides()).containsEntry("negotiation.max-rounds", "3");
    }

    @Test
    void governedInteraction_channelDependsOnAgents() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/15-governed-interaction/15-governed-interaction.yaml",
                DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "coordinator", "design-review");
        assertOrderedBefore(plan, "reviewer-1", "design-review");
        assertOrderedBefore(plan, "reviewer-2", "design-review");
    }

    @Test
    void governedInteraction_executesSuccessfully() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/15-governed-interaction/15-governed-interaction.yaml",
                DEPLOYMENT_REGISTRY);
        TransitionPlan   plan   = planFromEmpty(graph);
        TransitionResult result = executeTransition(plan, "test-tenant");
        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }

}
