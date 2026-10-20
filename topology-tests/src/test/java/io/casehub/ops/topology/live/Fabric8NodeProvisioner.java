package io.casehub.ops.topology.live;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.NodeProvisioner;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.ops.api.infra.InfraDesiredNodeSpec;
import io.casehub.ops.api.infra.InfraNodeSpec;
import io.casehub.ops.api.infra.K8sDeploymentSpec;
import io.casehub.ops.api.infra.K8sNamespaceSpec;
import io.casehub.ops.api.infra.K8sServiceSpec;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

class Fabric8NodeProvisioner implements NodeProvisioner {

    private final KubernetesClient client;
    private final String imageOverride;

    Fabric8NodeProvisioner(KubernetesClient client, String imageOverride) {
        this.client = client;
        this.imageOverride = imageOverride;
    }

    @Override
    public Set<NodeType> handledTypes() {
        return Set.of(
                NodeType.of("k8s_namespace"),
                NodeType.of("k8s_deployment"),
                NodeType.of("k8s_service"));
    }

    @Override
    public Duration resyncInterval() {
        return Duration.ofMinutes(5);
    }

    @Override
    public ProvisionResult provision(DesiredNode node, ProvisionContext context) {
        try {
            InfraNodeSpec spec = extractSpec(node);
            switch (spec) {
                case K8sNamespaceSpec ns -> applyNamespace(ns);
                case K8sDeploymentSpec dep -> applyDeployment(dep);
                case K8sServiceSpec svc -> applyService(svc);
                default -> {
                    return new ProvisionResult.Failed(
                            "Unsupported spec type: " + spec.getClass().getSimpleName());
                }
            }
            return new ProvisionResult.Success();
        } catch (Exception e) {
            return new ProvisionResult.Failed(e.getMessage());
        }
    }

    @Override
    public DeprovisionResult deprovision(DesiredNode node, DeprovisionContext context) {
        try {
            InfraNodeSpec spec = extractSpec(node);
            switch (spec) {
                case K8sNamespaceSpec ns ->
                        client.namespaces().withName(ns.name()).delete();
                case K8sDeploymentSpec dep ->
                        client.apps().deployments()
                                .inNamespace(dep.namespace()).withName(dep.name()).delete();
                case K8sServiceSpec svc ->
                        client.services()
                                .inNamespace(svc.namespace()).withName(svc.name()).delete();
                default -> {
                    return new DeprovisionResult.Failed(
                            "Unsupported spec type: " + spec.getClass().getSimpleName());
                }
            }
            return new DeprovisionResult.Success();
        } catch (Exception e) {
            return new DeprovisionResult.Failed(e.getMessage());
        }
    }

    private InfraNodeSpec extractSpec(DesiredNode node) {
        if (node.spec() instanceof InfraDesiredNodeSpec wrapper) {
            return wrapper.resourceSpec();
        }
        return (InfraNodeSpec) node.spec();
    }

    private void applyNamespace(K8sNamespaceSpec spec) {
        var ns = new NamespaceBuilder()
                .withNewMetadata()
                .withName(spec.name())
                .withLabels(spec.labels().values())
                .endMetadata()
                .build();
        client.resource(ns).createOr(NonDeletingOperation::update);
    }

    private void applyDeployment(K8sDeploymentSpec spec) {
        String image = imageOverride != null ? imageOverride : spec.image();
        var container = new ContainerBuilder()
                .withName(spec.name())
                .withImage(image)
                .withResources(new ResourceRequirementsBuilder()
                        .addToRequests("cpu", new Quantity(spec.resources().cpuRequest()))
                        .addToRequests("memory", new Quantity(spec.resources().memoryRequest()))
                        .addToLimits("cpu", new Quantity(spec.resources().cpuLimit()))
                        .addToLimits("memory", new Quantity(spec.resources().memoryLimit()))
                        .build())
                .withPorts(spec.ports().stream()
                        .map(p -> new ContainerPortBuilder()
                                .withContainerPort(p.containerPort())
                                .withProtocol(p.protocol())
                                .build())
                        .toList())
                .withEnv(spec.env().entrySet().stream()
                        .map(e -> new EnvVarBuilder()
                                .withName(e.getKey())
                                .withValue(e.getValue())
                                .build())
                        .toList())
                .build();

        var dep = new DeploymentBuilder()
                .withNewMetadata()
                .withName(spec.name())
                .withNamespace(spec.namespace())
                .withLabels(spec.labels().values())
                .endMetadata()
                .withNewSpec()
                .withReplicas(spec.replicas())
                .withNewSelector()
                .withMatchLabels(Map.of("app", spec.name()))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(Map.of("app", spec.name()))
                .endMetadata()
                .withNewSpec()
                .withContainers(container)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        client.resource(dep).createOr(NonDeletingOperation::update);
    }

    private void applyService(K8sServiceSpec spec) {
        String k8sType = switch (spec.serviceType()) {
            case CLUSTER_IP -> "ClusterIP";
            case NODE_PORT -> "NodePort";
            case LOAD_BALANCER -> "LoadBalancer";
        };
        var svc = new ServiceBuilder()
                .withNewMetadata()
                .withName(spec.name())
                .withNamespace(spec.namespace())
                .withLabels(spec.labels().values())
                .endMetadata()
                .withNewSpec()
                .withType(k8sType)
                .withSelector(spec.selector().values())
                .withPorts(new ServicePortBuilder()
                        .withPort(spec.port())
                        .withTargetPort(new IntOrString(spec.targetPort()))
                        .withProtocol("TCP")
                        .build())
                .endSpec()
                .build();
        client.resource(svc).createOr(NonDeletingOperation::update);
    }
}
