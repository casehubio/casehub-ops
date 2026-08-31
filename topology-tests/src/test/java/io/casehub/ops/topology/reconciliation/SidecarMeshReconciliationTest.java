package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.OrderedStep;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("reconciliation")
class SidecarMeshReconciliationTest extends ReconciliationTestBase {

    private static final String EXEMPLAR = "topologies/sidecar-mesh/lb-cluster-logistics.yaml";

    @Test
    void logistics_plansAllNodesFromEmpty() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        assertThat(plan.additions()).isNotEmpty();
        assertThat(plan.removals()).isEmpty();
    }

    @Test
    void logistics_namespacePrecedesDeployments() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        for (OrderedStep step : plan.additions()) {
            if (step.node().type().equals(NodeType.of("k8s_deployment"))) {
                assertOrderedBefore(plan, "logistics-ns", step.node().id().value());
            }
        }
    }

    @Test
    void logistics_meshControlPlanePresentInGraph() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        boolean hasMeshControlPlane = graph.nodes().values().stream()
                .anyMatch(n -> n.type().equals(NodeType.of("mesh_control_plane")));
        assertThat(hasMeshControlPlane).isTrue();
    }

    @Test
    void logistics_driftOnFleetApi() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        Map<NodeId, NodeStatus> statuses = new HashMap<>();
        graph.nodes().keySet().forEach(id -> statuses.put(id, NodeStatus.PRESENT));
        statuses.put(NodeId.of("fleet-api"), NodeStatus.ABSENT);
        TransitionPlan plan = planWithActual(graph, new ActualState(statuses));
        assertThat(plan.additions()).hasSize(1);
        assertThat(plan.additions().get(0).node().id()).isEqualTo(NodeId.of("fleet-api"));
    }

    @Test
    void logistics_executionSucceeds() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        TransitionResult result = executeTransition(plan, "test");
        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }
}
