package io.casehub.ops.topology.env;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;

import java.util.Map;

public class KubernetesDeploymentTarget implements ApplicationDeploymentTarget {

    private final KubernetesClient client;
    private final String namespace;
    private final String imageOverride;
    private final Map<String, io.fabric8.kubernetes.client.LocalPortForward> portForwards = new java.util.HashMap<>();


    public KubernetesDeploymentTarget(KubernetesClient client,
                                       String namespace,
                                       String imageOverride) {
        this.client = client;
        this.namespace = namespace;
        this.imageOverride = imageOverride;
    }

    @Override
    public String targetId() { return "kubernetes"; }

    @Override
    public DeployResult deploy(String imageRef, DeployConfig config) {
        try {
            String image = imageOverride != null ? imageOverride : imageRef;
            String name = config.applicationId();

            var container = new ContainerBuilder()
                .withName(name)
                .withImage(image)
                .withEnv(config.env().entrySet().stream()
                    .map(e -> new EnvVarBuilder()
                        .withName(e.getKey()).withValue(e.getValue()).build())
                    .toList())
                .build();

            var deployment = new DeploymentBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace)
                    .withLabels(Map.of("app", name)).endMetadata()
                .withNewSpec()
                    .withReplicas(config.replicas())
                    .withNewSelector().withMatchLabels(Map.of("app", name)).endSelector()
                    .withNewTemplate()
                        .withNewMetadata().withLabels(Map.of("app", name)).endMetadata()
                        .withNewSpec().withContainers(container).endSpec()
                    .endTemplate()
                .endSpec()
                .build();
            client.resource(deployment).createOr(NonDeletingOperation::update);

            var service = new ServiceBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withNewSpec()
                    .withSelector(Map.of("app", name))
                    .withPorts(new ServicePortBuilder()
                        .withPort(8080).withTargetPort(new IntOrString(8080))
                        .withProtocol("TCP").build())
                .endSpec()
                .build();
            client.resource(service).createOr(NonDeletingOperation::update);

            return new DeployResult.Success(name);
        } catch (Exception e) {
            return new DeployResult.Failed(e.getMessage());
        }
    }

    @Override
    public DeployResult undeploy(String instanceId) {
        try {
            client.apps().deployments().inNamespace(namespace)
                .withName(instanceId).delete();
            client.services().inNamespace(namespace)
                .withName(instanceId).delete();
            return new DeployResult.Success(instanceId);
        } catch (Exception e) {
            return new DeployResult.Failed(e.getMessage());
        }
    }

    @Override
    public NodeStatus status(String instanceId) {
        Deployment dep = client.apps().deployments().inNamespace(namespace)
            .withName(instanceId).get();
        if (dep == null) return NodeStatus.ABSENT;
        var depStatus = dep.getStatus();
        if (depStatus != null && depStatus.getReadyReplicas() != null
                && depStatus.getReadyReplicas() > 0) {
            return NodeStatus.PRESENT;
        }
        return NodeStatus.UNKNOWN;
    }

    @Override
    public HealthCheckResult healthCheck(String instanceId) {
        NodeStatus s = status(instanceId);
        return s == NodeStatus.PRESENT
            ? new HealthCheckResult.Healthy(instanceId)
            : new HealthCheckResult.Unhealthy(instanceId, "Status: " + s);
    }

    @Override
    public java.util.Optional<String> serviceUrl(String instanceId) {
        io.fabric8.kubernetes.client.LocalPortForward existing = portForwards.get(instanceId);
        if (existing != null) {
            return java.util.Optional.of("http://localhost:" + existing.getLocalPort());
        }
        try {
            io.fabric8.kubernetes.client.LocalPortForward pf = client.services()
                                                                     .inNamespace(namespace)
                                                                     .withName(instanceId)
                                                                     .portForward(8080);
            portForwards.put(instanceId, pf);
            return java.util.Optional.of("http://localhost:" + pf.getLocalPort());
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public void close() {
        for (io.fabric8.kubernetes.client.LocalPortForward pf : portForwards.values()) {
            try {pf.close();} catch (java.io.IOException ignored) {}
        }
        portForwards.clear();
    }
}
