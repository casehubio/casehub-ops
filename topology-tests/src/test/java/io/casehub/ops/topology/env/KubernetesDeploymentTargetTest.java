package io.casehub.ops.topology.env;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.deployment.target.KubernetesDeploymentTarget;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class KubernetesDeploymentTargetTest {

    KubernetesClient           client;
    KubernetesDeploymentTarget target;

    @BeforeEach
    void setUp() {
        client.namespaces().resource(
            new NamespaceBuilder().withNewMetadata().withName("test").endMetadata().build()
        ).create();
        target = new KubernetesDeploymentTarget(client, "test", null);
    }

    @Test
    void targetId_returnsKubernetes() {
        assertThat(target.targetId()).isEqualTo("kubernetes");
    }

    @Test
    void deploy_createsDeploymentAndService() {
        var config = new DeployConfig("my-app", 1, Map.of(), Map.of());

        var result = target.deploy("casehubio/my-app:latest", config);

        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(client.apps().deployments().inNamespace("test").withName("my-app").get())
            .isNotNull();
        assertThat(client.services().inNamespace("test").withName("my-app").get())
            .isNotNull();
    }

    @Test
    void undeploy_removesResources() {
        var config = new DeployConfig("my-app", 1, Map.of(), Map.of());
        target.deploy("casehubio/my-app:latest", config);

        var result = target.undeploy("my-app");

        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(client.apps().deployments().inNamespace("test").withName("my-app").get())
            .isNull();
    }

    @Test
    void status_returnsAbsentForUnknownApp() {
        assertThat(target.status("nonexistent")).isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void deploy_usesImageOverride() {
        target = new KubernetesDeploymentTarget(client, "test", "override:latest");
        var config = new DeployConfig("my-app", 1, Map.of(), Map.of());

        target.deploy("original:latest", config);

        var dep = client.apps().deployments().inNamespace("test").withName("my-app").get();
        assertThat(dep.getSpec().getTemplate().getSpec().getContainers().get(0).getImage())
            .isEqualTo("override:latest");
    }

    @Test
    void deploy_passesEnvVars() {
        var config = new DeployConfig("my-app", 1, Map.of(), Map.of("MY_VAR", "my-value"));

        target.deploy("casehubio/my-app:latest", config);

        var dep = client.apps().deployments().inNamespace("test").withName("my-app").get();
        var envVars = dep.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertThat(envVars).anyMatch(e -> "MY_VAR".equals(e.getName()) && "my-value".equals(e.getValue()));
    }

    @Test
    void deploy_setsReplicaCount() {
        var config = new DeployConfig("my-app", 3, Map.of(), Map.of());

        target.deploy("casehubio/my-app:latest", config);

        var dep = client.apps().deployments().inNamespace("test").withName("my-app").get();
        assertThat(dep.getSpec().getReplicas()).isEqualTo(3);
    }

    @Test
    void serviceUrl_returnsEmptyBeforeDeploy() {
        assertThat(target.serviceUrl("nonexistent")).isEmpty();
    }

    @Test
    void close_succeedsWithNoPortForwards() {
        target.close();
    }
}
