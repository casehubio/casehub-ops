package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.types.Labels;

import java.util.Map;
import java.util.Objects;

@NodeTypeId("k8s_configmap")
public record K8sConfigMapSpec(
        String namespace,
        String name,
        Map<String, String> data,
        Labels labels) implements InfraNodeSpec {

    public K8sConfigMapSpec {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        if (data == null) {data = Map.of();}
        data = Map.copyOf(data);
        if (labels == null) {labels = Labels.empty();}
    }

    @Override
    public String resourceType() {
        return "k8s_configmap";
    }
}
