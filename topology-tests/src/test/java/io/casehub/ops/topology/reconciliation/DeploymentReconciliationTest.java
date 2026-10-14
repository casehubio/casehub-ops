package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;
import io.casehub.desiredstate.api.StepOutcome;
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
    void helloLlm_plansThreeAdditions() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        assertThat(plan.flatAdditions()).hasSize(3);
        assertThat(plan.flatRemovals()).isEmpty();
    }

    @Test
    void helloLlm_eidosBeforeEngine() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "eidos", "engine");
    }

    @Test
    void helloLlm_engineBeforeLlm() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "engine", "my-llm");
    }

    @Test
    void helloLlm_executesSuccessfully() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        TransitionResult result = executeTransition(plan, "test-tenant");
        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }

    @Test
    void helloLlm_idempotentAfterExecution() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        TransitionPlan plan = planFromEmpty(graph);
        executeTransition(plan, "test-tenant");

        actualAdapter.setAllPresent(graph);
        TransitionPlan replan = planWithActual(graph,
                actualAdapter.readActual(graph, "test-tenant"));
        assertThat(replan.flatAdditions()).isEmpty();
        assertThat(replan.flatRemovals()).isEmpty();
    }
}
