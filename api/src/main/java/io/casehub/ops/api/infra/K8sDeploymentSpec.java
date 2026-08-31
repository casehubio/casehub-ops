package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.types.HealthCheckSpec;
import io.casehub.ops.api.infra.types.Labels;
import io.casehub.ops.api.infra.types.PortMapping;
import io.casehub.ops.api.infra.types.ResourceRequirements;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@NodeTypeId("k8s_deployment")
public record K8sDeploymentSpec(
        String namespace,
        String name,
        String image,
        int replicas,
        ResourceRequirements resources,
        Labels labels,
        List<PortMapping> ports,
        Map<String, String> env,
        Optional<HealthCheckSpec> healthCheck) implements InfraNodeSpec {

    public K8sDeploymentSpec {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(image, "image");
        if (resources == null) {resources = new ResourceRequirements("100m", "500m", "128Mi", "512Mi");}
        if (labels == null) {labels = Labels.empty();}
        if (ports == null) {ports = List.of();}
        ports = List.copyOf(ports);
        if (env == null) {env = Map.of();}
        env = Map.copyOf(env);
        if (healthCheck == null) {healthCheck = Optional.empty();}
    }

    public K8sDeploymentSpec(String namespace, String name, String image, int replicas,
                             ResourceRequirements resources, Labels labels) {
        this(namespace, name, image, replicas, resources, labels,
             List.of(), Map.of(), Optional.empty());
    }

    @Override
    public String resourceType() {
        return "k8s_deployment";
    }
}
