package io.casehub.ops.api.infra;

import io.casehub.ops.api.infra.types.Labels;

import java.util.List;
import java.util.Objects;

public record LoadBalancerSpec(
        String name,
        String namespace,
        LoadBalancerType type,
        String healthCheckPath,
        int healthCheckPort,
        List<String> targetServices,
        Labels labels) implements InfraNodeSpec {

    public LoadBalancerSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(namespace, "namespace");
        if (type == null) type = LoadBalancerType.APPLICATION;
        if (healthCheckPath == null) healthCheckPath = "/health";
        if (targetServices == null) targetServices = List.of();
        if (labels == null) labels = Labels.empty();
    }

    @Override
    public String resourceType() { return "load_balancer"; }
}
