package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.types.Labels;

import java.util.Objects;

@NodeTypeId("k8s_namespace")
public record K8sNamespaceSpec(String name, Labels labels) implements InfraNodeSpec {

    public K8sNamespaceSpec {
        Objects.requireNonNull(name, "name");
        if (labels == null) labels = Labels.empty();
    }

    @Override
    public String resourceType() {
        return "k8s_namespace";
    }
}
