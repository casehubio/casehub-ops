package io.casehub.ops.topology.integration;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionResult;
import io.casehub.desiredstate.runtime.DefaultNodeProvisionerRouter;
import io.casehub.desiredstate.runtime.NoOpHumanNodeHandler;
import io.casehub.desiredstate.runtime.NoOpPendingApprovalHandler;
import io.casehub.desiredstate.runtime.NodeStepExecutor;
import io.casehub.desiredstate.runtime.SimpleTransitionExecutor;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class HelloLlmIntegrationTest extends TopologyTestBase {

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void registersAppsViaRealClaudony() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);

        deployAllNodes(graph, target);

        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            NodeStatus status = target.status(appSpec.applicationId());
            assertThat(status)
                    .as("Status of %s after registration", appSpec.applicationId())
                    .isNotEqualTo(NodeStatus.ABSENT);
        }
    }

    @Test
    void registersAppsViaTransitionPlanner() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);

        ClaudonyDeploymentTarget target      = createClaudonyTarget(claudonyUrl);
        var                      provisioner = new ClaudonyNodeProvisioner(target);
        var                      planner     = new TransitionPlanner();
        var                      plan        = planner.plan(graph, new ActualState(Map.of()));

        assertThat(plan.flatAdditions())
                .as("Plan should have additions for all 4 nodes")
                .hasSize(4);

        var router = new DefaultNodeProvisionerRouter(List.of(provisioner));
        var nodeExecutor = new NodeStepExecutor(
                router,
                new NoOpHumanNodeHandler(),
                new NoOpPendingApprovalHandler(),
                (step, tid) -> new StepOutcome.Succeeded());
        var              executor = new SimpleTransitionExecutor(nodeExecutor);
        TransitionResult result   = executor.execute(plan, "integration-test");

        assertThat(result.outcomes().values())
                .as("All transition steps should succeed")
                .allMatch(o -> o instanceof StepOutcome.Succeeded);

        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            NodeStatus             status  = target.status(appSpec.applicationId());
            assertThat(status)
                    .as("Status of %s after plan execution", appSpec.applicationId())
                    .isNotEqualTo(NodeStatus.ABSENT);
        }
    }


}
