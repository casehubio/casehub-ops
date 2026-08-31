package io.casehub.ops.topology.live;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.ops.topology.reconciliation.ReconciliationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infra-live")
@KubernetesAvailable
class LiveDeploymentTest extends ReconciliationTestBase {

    @Test
    void singleServiceBlog_plansCorrectly() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        TransitionPlan plan = planFromEmpty(graph);
        assertThat(plan.additions()).hasSize(3);
        assertOrderedBefore(plan, "blog-namespace", "ghost");
    }
}
