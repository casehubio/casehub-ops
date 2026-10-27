package io.casehub.ops.deployment.target;

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

    private final KubernetesClient                client;
    private final String                          namespace;
    private final String                          imageOverride;
    private final String                          nodeHost;
    private final java.util.Map<Integer, Integer> portMappings;

    public KubernetesDeploymentTarget(KubernetesClient client,
                                      String namespace,
                                      String imageOverride) {
        this(client, namespace, imageOverride, null, Map.of());
    }

    public KubernetesDeploymentTarget(KubernetesClient client,
                                      String namespace,
                                      String imageOverride,
                                      String nodeHost,
                                      java.util.Map<Integer, Integer> portMappings) {
        this.client        = client;
        this.namespace     = namespace;
        this.imageOverride = imageOverride;
        this.nodeHost      = nodeHost;
        this.portMappings  = portMappings;
    }

    @Override
    public String targetId() {return "kubernetes";}

    @Override
    public DeployResult deploy(String imageRef, DeployConfig config) {
        try {
            String image = imageOverride != null ? imageOverride : imageRef;
            String name  = config.applicationId();

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
                                  .withType("NodePort")
                                  .withSelector(Map.of("app", name))
                                  .withPorts(new ServicePortBuilder()
                                                     .withPort(8080).withTargetPort(new IntOrString(8080))
                                                     .withNodePort(assignNodePort(name))
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
        if (dep == null) {return NodeStatus.ABSENT;}
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
        try {
            var svc = client.services().inNamespace(namespace)
                            .withName(instanceId).get();
            if (svc == null) {return java.util.Optional.empty();}
            var ports = svc.getSpec().getPorts();
            if (ports == null || ports.isEmpty()) {return java.util.Optional.empty();}
            int    nodePort = ports.get(0).getNodePort();
            int    hostPort = portMappings.getOrDefault(nodePort, nodePort);
            String host     = nodeHost != null ? nodeHost : "localhost";
            return java.util.Optional.of("http://" + host + ":" + hostPort);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public void close() {}

    public static int assignNodePort(String serviceName) {
        int hash = Math.abs(serviceName.hashCode());
        return 30080 + (hash % 20);
    }
}
