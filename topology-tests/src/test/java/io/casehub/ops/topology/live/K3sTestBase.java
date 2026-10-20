package io.casehub.ops.topology.live;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionResult;
import io.casehub.desiredstate.runtime.DefaultNodeProvisionerRouter;
import io.casehub.desiredstate.runtime.NoOpHumanNodeHandler;
import io.casehub.desiredstate.runtime.NoOpPendingApprovalHandler;
import io.casehub.desiredstate.runtime.NodeStepExecutor;
import io.casehub.desiredstate.runtime.SimpleTransitionExecutor;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infra-live")
abstract class K3sTestBase extends TopologyTestBase {

    static K3sContainer k3s;
    static KubernetesClient k8sClient;

    @BeforeAll
    static void startK3s() {
        k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.31.2-k3s1"))
                      .withCommand("server", "--disable=traefik");
        k3s.start();

        k8sClient = new KubernetesClientBuilder()
                            .withConfig(Config.fromKubeconfig(k3s.getKubeConfigYaml()))
                            .build();
    }

    @AfterAll
    static void stopK3s() {
        if (k8sClient != null) k8sClient.close();
        if (k3s != null) k3s.stop();
    }

    protected TransitionResult planAndExecute(DesiredStateGraph graph) {
        return planAndExecute(graph, null);
    }

    protected TransitionResult planAndExecute(DesiredStateGraph graph,
                                               String imageOverride) {
        var provisioner = new Fabric8NodeProvisioner(k8sClient, imageOverride);
        var planner = new TransitionPlanner();
        var plan = planner.plan(graph, new ActualState(Map.of()));

        var router = new DefaultNodeProvisionerRouter(List.of(provisioner));
        var nodeExecutor = new NodeStepExecutor(
                router,
                new NoOpHumanNodeHandler(),
                new NoOpPendingApprovalHandler(),
                (step, tid) -> new StepOutcome.Succeeded());
        var executor = new SimpleTransitionExecutor(nodeExecutor);
        return executor.execute(plan, "live-test");
    }

    protected void awaitDeploymentReady(String namespace, String name,
                                         Duration timeout) {
        k8sClient.apps().deployments()
                .inNamespace(namespace).withName(name)
                .waitUntilReady(timeout.toSeconds(), TimeUnit.SECONDS);
    }

    protected void assertNamespaceExists(String name) {
        assertThat(k8sClient.namespaces().withName(name).get())
                .as("Namespace '%s' should exist", name)
                .isNotNull();
    }

    protected void assertDeploymentExists(String namespace, String name) {
        Deployment dep = k8sClient.apps().deployments()
                .inNamespace(namespace).withName(name).get();
        assertThat(dep)
                .as("Deployment '%s/%s' should exist", namespace, name)
                .isNotNull();
    }

    protected void assertServiceExists(String namespace, String name) {
        assertThat(k8sClient.services()
                .inNamespace(namespace).withName(name).get())
                .as("Service '%s/%s' should exist", namespace, name)
                .isNotNull();
    }

    protected static void assertAllSucceeded(TransitionResult result) {
        assertThat(result.outcomes().values())
                .as("All transition steps should succeed")
                .allMatch(o -> o instanceof StepOutcome.Succeeded
                        || o instanceof StepOutcome.AlreadyConverged);
    }
}
