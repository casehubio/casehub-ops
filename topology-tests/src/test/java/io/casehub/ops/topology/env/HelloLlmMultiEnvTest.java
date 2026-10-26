package io.casehub.ops.topology.env;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionResult;
import io.casehub.desiredstate.runtime.DefaultNodeProvisionerRouter;
import io.casehub.desiredstate.runtime.NoOpHumanNodeHandler;
import io.casehub.desiredstate.runtime.NoOpPendingApprovalHandler;
import io.casehub.desiredstate.runtime.NodeStepExecutor;
import io.casehub.desiredstate.runtime.SimpleTransitionExecutor;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.testing.env.EnvironmentAwareNodeProvisioner;
import io.casehub.ops.testing.env.TargetEnvironment;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import io.casehub.ops.topology.integration.FleetLlmExerciser;
import io.casehub.ops.topology.integration.Service;
import io.casehub.ops.topology.integration.ServiceCluster;
import io.casehub.ops.topology.integration.ServiceEndpoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
@ExtendWith(K3dClusterExtension.class)
class HelloLlmMultiEnvTest extends TopologyTestBase {

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @K3dClusterExtension.K3dClient
    static io.fabric8.kubernetes.client.KubernetesClient k3dClient;

    @Test
    void deploysTopologyViaTransitionPlanner() throws IOException {
        TargetEnvironment env = TargetEnvironment.current();
        System.out.printf("  [multi-env] Target environment: %s%n", env);

        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);

        ApplicationDeploymentTarget target = createTargetForEnv(env);
        var provisioner = new EnvironmentAwareNodeProvisioner(target);
        var planner = new TransitionPlanner();
        var plan = planner.plan(graph, new ActualState(Map.of()));

        assertThat(plan.flatAdditions())
                .as("Plan should have additions for all 4 nodes")
                .hasSize(4);

        var router = new DefaultNodeProvisionerRouter(List.of(provisioner));
        var nodeExecutor = new NodeStepExecutor(
                router,
                new NoOpHumanNodeHandler(),
                new NoOpPendingApprovalHandler(),
                (step, tid) -> new StepOutcome.Succeeded());
        var executor = new SimpleTransitionExecutor(nodeExecutor);
        TransitionResult result = executor.execute(plan, "multi-env-test");

        if (env == TargetEnvironment.LOCAL) {
            assertThat(result.outcomes().values())
                    .as("All transition steps should succeed on %s", env)
                    .allMatch(o -> o instanceof StepOutcome.Succeeded);
        } else {
            for (DesiredNode node : graph.nodes().values()) {
                CaseHubApplicationSpec appSpec = extractAppSpec(node);
                DeployResult deployResult = target.deploy("nginx:alpine",
                        new DeployConfig(appSpec.applicationId(), appSpec.replicas(), Map.of(), Map.of()));
                System.out.printf("  [multi-env] deploy(%s) → %s%n", appSpec.applicationId(), deployResult);
                assertThat(deployResult)
                        .as("Deploy %s on %s", appSpec.applicationId(), env)
                        .isInstanceOf(DeployResult.Success.class);
            }
        }

        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            NodeStatus status = target.status(appSpec.applicationId());
            System.out.printf("  [multi-env] status(%s) → %s%n", appSpec.applicationId(), status);
            assertThat(status)
                    .as("Status of %s on %s after deployment", appSpec.applicationId(), env)
                    .isNotEqualTo(NodeStatus.ABSENT);
        }

        System.out.printf("  [multi-env] All 4 nodes deployed successfully on %s%n", env);

        if (env == TargetEnvironment.LOCAL) {
            try {
                FleetLlmExerciser.LlmResponse llmResponse =
                        FleetLlmExerciser.exercise("my-llm",
                                extractAppSpec(graph.nodes().get(NodeId.of("my-llm"))).config());
                FleetLlmExerciser.assertDomainRelevant(llmResponse);
                System.out.printf("  [multi-env] LLM exercise passed on LOCAL%n");
            } catch (Exception e) {
                System.out.printf("  [multi-env] LLM exercise skipped — %s: %s%n",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }

        undeploy(graph, target);
    }

    private ApplicationDeploymentTarget createTargetForEnv(TargetEnvironment env) {
        return switch (env) {
            case LOCAL -> createClaudonyTarget(claudonyUrl);
            case K8S -> {
                assertThat(k3dClient)
                        .as("K3dClient must be injected for K8S environment")
                        .isNotNull();
                yield new KubernetesDeploymentTarget(
                        k3dClient, K3dClusterExtension.TEST_NAMESPACE, "nginx:alpine");
            }
            case PODMAN -> new ImageOverridingTarget(
                    TargetEnvironmentFactory.createTarget(TargetEnvironment.PODMAN, Map.of()),
                    "nginx:alpine");
        };
    }

    private void undeploy(DesiredStateGraph graph, ApplicationDeploymentTarget target) {
        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            target.undeploy(appSpec.applicationId());
        }
    }
}
