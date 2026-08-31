package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("reconciliation")
class ReconciliationTestBaseVerificationTest extends ReconciliationTestBase {

    @Test
    void planFromEmpty_produces_additions_for_all_nodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        TransitionPlan plan = planFromEmpty(graph);
        assertThat(plan.additions()).hasSize(3);
        assertThat(plan.removals()).isEmpty();
    }

    @Test
    void assertOrderedBefore_passes_for_correct_ordering() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "blog-namespace", "ghost");
        assertOrderedBefore(plan, "ghost", "ghost-service");
    }

    @Test
    void executeTransition_records_provisioned_nodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        TransitionPlan plan = planFromEmpty(graph);
        TransitionResult result = executeTransition(plan, "test-tenant");
        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
        assertThat(provisioner.provisioned).hasSize(3);
    }
}
