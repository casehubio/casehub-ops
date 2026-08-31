package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.types.IngressRule;
import io.casehub.ops.api.infra.types.Labels;

import java.util.List;
import java.util.Objects;

@NodeTypeId("k8s_ingress")
public record K8sIngressSpec(
        String namespace,
        String name,
        String host,
        List<IngressRule> rules,
        Labels labels) implements InfraNodeSpec {

    public K8sIngressSpec {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        if (rules == null) {rules = List.of();}
        rules = List.copyOf(rules);
        if (labels == null) {labels = Labels.empty();}
    }

    @Override
    public String resourceType() {
        return "k8s_ingress";
    }
}
