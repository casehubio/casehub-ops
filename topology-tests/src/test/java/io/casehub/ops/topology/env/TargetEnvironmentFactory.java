package io.casehub.ops.topology.env;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
import io.casehub.ops.container.PodmanDeploymentTarget;
import io.casehub.ops.container.podman.PodmanClient;
import io.casehub.ops.testing.env.EnvironmentAwareNodeProvisioner;
import io.casehub.ops.testing.env.TargetEnvironment;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.WebClient;

import java.util.Map;

public class TargetEnvironmentFactory {

    public static ApplicationDeploymentTarget createTarget(
            TargetEnvironment env, Map<String, String> endpoints) {
        return switch (env) {
            case LOCAL -> createClaudonyTarget(
                endpoints.getOrDefault("claudony", "http://localhost:8080"));
            case K8S -> createK8sTarget(endpoints);
            case PODMAN -> createPodmanTarget();
        };
    }

    public static EnvironmentAwareNodeProvisioner createProvisioner(
            TargetEnvironment env, Map<String, String> endpoints) {
        return new EnvironmentAwareNodeProvisioner(createTarget(env, endpoints));
    }

    private static ClaudonyDeploymentTarget createClaudonyTarget(String url) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        HttpClaudonyFleetClient client = new HttpClaudonyFleetClient(url, mapper);
        return new ClaudonyDeploymentTarget(client);
    }

    private static KubernetesDeploymentTarget createK8sTarget(Map<String, String> endpoints) {
        io.fabric8.kubernetes.client.KubernetesClient client =
            new io.fabric8.kubernetes.client.KubernetesClientBuilder().build();
        String namespace = endpoints.getOrDefault("k8s-namespace", "casehub-test");
        String imageOverride = endpoints.get("k8s-image-override");
        return new KubernetesDeploymentTarget(client, namespace, imageOverride);
    }

    private static PodmanDeploymentTarget createPodmanTarget() {
        String socketPath = System.getProperty(
            "casehub.container.podman.socket", "/var/run/podman/podman.sock");
        Vertx vertx = Vertx.vertx();
        WebClient webClient = WebClient.create(vertx);
        PodmanClient podmanClient = new PodmanClient(webClient,
            SocketAddress.domainSocketAddress(socketPath));
        return new PodmanDeploymentTarget(podmanClient);
    }
}
