package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("reconciliation")
class SingleServiceReconciliationTest extends ReconciliationTestBase {

    @Test
    void ghostBlog_plansAllNodesFromEmpty() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        TransitionPlan plan = planFromEmpty(graph);
        assertThat(plan.additions()).hasSize(3);
        assertThat(plan.removals()).isEmpty();
    }

    @Test
    void ghostBlog_namespacePrecedesDeployment() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "blog-namespace", "ghost");
    }

    @Test
    void ghostBlog_deploymentPrecedesService() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "ghost", "ghost-service");
    }

    @Test
    void ghostBlog_executionSucceeds() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        TransitionPlan plan = planFromEmpty(graph);
        TransitionResult result = executeTransition(plan, "test");
        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }

    @Test
    void ghostBlog_driftDetection() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        Map<NodeId, NodeStatus> statuses = Map.of(
                NodeId.of("blog-namespace"), NodeStatus.PRESENT,
                NodeId.of("ghost"), NodeStatus.PRESENT,
                NodeId.of("ghost-service"), NodeStatus.ABSENT);
        TransitionPlan plan = planWithActual(graph, new ActualState(statuses));
        assertThat(plan.additions()).hasSize(1);
        assertThat(plan.additions().get(0).node().id()).isEqualTo(NodeId.of("ghost-service"));
        assertThat(plan.removals()).isEmpty();
    }
}
