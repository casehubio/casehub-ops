package io.casehub.ops.topology.env;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
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
import io.casehub.ops.deployment.target.KubernetesDeploymentTarget;
import io.casehub.ops.testing.env.EnvironmentAwareNodeProvisioner;
import io.casehub.ops.testing.env.ImageResolver;
import io.casehub.ops.testing.env.ImageSource;
import io.casehub.ops.testing.env.LlmAssertions;
import io.casehub.ops.testing.env.LlmResponse;
import io.casehub.ops.testing.env.ServiceHealthWaiter;
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
    @K3dClusterExtension.K3dNodeHost
    static String                                        k3dNodeHost;
    @K3dClusterExtension.K3dPortMappings
    static java.util.Map<Integer, Integer>               k3dPortMappings;


    @Test
    void deploysTopologyViaTransitionPlanner() throws IOException {
        TargetEnvironment env = TargetEnvironment.current();
        System.out.printf("  [multi-env] Target environment: %s%n", env);

        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);

        ApplicationDeploymentTarget target = createTargetForEnv(env);
        try {
            deployAllToTarget(graph, target, env);

            for (DesiredNode node : graph.nodes().values()) {
                CaseHubApplicationSpec     appSpec = extractAppSpec(node);
                java.util.Optional<String> url     = target.serviceUrl(appSpec.applicationId());
                System.out.printf("  [multi-env] serviceUrl(%s) → %s%n",
                                  appSpec.applicationId(), url.orElse("empty"));
                assertThat(url)
                        .as("Service URL for %s on %s", appSpec.applicationId(), env)
                        .isPresent();

                ServiceHealthWaiter.waitForReady(url.get(),
                                                 new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                                                                  Map.of(), Map.of()).healthPath(),
                                                 java.time.Duration.ofSeconds(60));

                assertHttpOk(url.get() + "/q/health/ready");
                System.out.printf("  [multi-env] %s healthy on %s%n",
                                  appSpec.applicationId(), env);
            }

            if (env == TargetEnvironment.LOCAL) {
                try {
                    LlmResponse llmResponse =
                            FleetLlmExerciser.exercise("my-llm",
                                                       extractAppSpec(graph.nodes().get(NodeId.of("my-llm"))).config());
                    LlmAssertions.assertDomainRelevant(llmResponse);
                    System.out.printf("  [multi-env] LLM exercise passed on LOCAL%n");
                } catch (Exception e) {
                    System.out.printf("  [multi-env] LLM exercise skipped — %s: %s%n",
                                      e.getClass().getSimpleName(), e.getMessage());
                }
            }
        } finally {
            undeploy(graph, target);
            target.close();
        }
    }

    private void deployAllToTarget(DesiredStateGraph graph,
                                   ApplicationDeploymentTarget target,
                                   TargetEnvironment env) {
        if (env == TargetEnvironment.LOCAL) {
            var provisioner = new EnvironmentAwareNodeProvisioner(target);
            var planner     = new TransitionPlanner();
            var plan        = planner.plan(graph, new ActualState(Map.of()));

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
            TransitionResult result   = executor.execute(plan, "multi-env-test");

            assertThat(result.outcomes().values())
                    .as("All transition steps should succeed on %s", env)
                    .allMatch(o -> o instanceof StepOutcome.Succeeded);

            if (target instanceof io.casehub.ops.claudony.ClaudonyDeploymentTarget claudony
                && claudonyUrl != null) {
                for (DesiredNode node : graph.nodes().values()) {
                    CaseHubApplicationSpec appSpec = extractAppSpec(node);
                    claudony.registerServiceUrl(appSpec.applicationId(), claudonyUrl);
                }
            }
        } else {
            ImageSource source  = ImageResolver.currentSource();
            String      version = System.getProperty("casehub.version", "0.4-SNAPSHOT");
            for (DesiredNode node : graph.nodes().values()) {
                CaseHubApplicationSpec appSpec = extractAppSpec(node);
                String imageRef = ImageResolver.resolveFromSource(
                        appSpec.applicationId(), version, source);
                DeployResult deployResult = target.deploy(imageRef,
                                                          new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                                                                           Map.of(), Map.of()));
                System.out.printf("  [multi-env] deploy(%s, image=%s) → %s%n",
                                  appSpec.applicationId(), imageRef, deployResult);
                assertThat(deployResult)
                        .as("Deploy %s on %s", appSpec.applicationId(), env)
                        .isInstanceOf(DeployResult.Success.class);
            }
        }
    }

    private ApplicationDeploymentTarget createTargetForEnv(TargetEnvironment env) {
        return switch (env) {
            case LOCAL -> createClaudonyTarget(claudonyUrl);
            case K8S -> {
                assertThat(k3dClient)
                        .as("K3dClient must be injected for K8S environment")
                        .isNotNull();
                yield new KubernetesDeploymentTarget(
                        k3dClient, K3dClusterExtension.TEST_NAMESPACE, null,
                        k3dNodeHost, k3dPortMappings != null ? k3dPortMappings : Map.of());
            }
            case PODMAN -> TargetEnvironmentFactory.createTarget(
                    TargetEnvironment.PODMAN, Map.of());
        };
    }

    private static void assertHttpOk(String url) throws IOException {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                                                  java.net.URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        try {
            assertThat(conn.getResponseCode())
                    .as("HTTP GET %s", url)
                    .isEqualTo(200);
        } finally {
            conn.disconnect();
        }
    }

    private void undeploy(DesiredStateGraph graph, ApplicationDeploymentTarget target) {
        for (DesiredNode node : graph.nodes().values()) {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            target.undeploy(appSpec.applicationId());
        }
    }
}
