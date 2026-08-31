package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.types.Labels;
import io.casehub.ops.api.infra.types.ServiceType;

import java.util.Objects;

@NodeTypeId("k8s_service")
public record K8sServiceSpec(
        String namespace,
        String name,
        int port,
        int targetPort,
        ServiceType serviceType,
        Labels labels,
        Labels selector) implements InfraNodeSpec {

    public K8sServiceSpec {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        if (serviceType == null) {serviceType = ServiceType.CLUSTER_IP;}
        if (labels == null) {labels = Labels.empty();}
        if (selector == null) {selector = Labels.empty();}
    }

    @Override
    public String resourceType() {
        return "k8s_service";
    }
}
