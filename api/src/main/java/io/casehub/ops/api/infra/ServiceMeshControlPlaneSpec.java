package io.casehub.ops.api.infra;

import io.casehub.ops.api.infra.types.Labels;

import java.util.Objects;

public record ServiceMeshControlPlaneSpec(
        String namespace,
        String image,
        int replicas,
        Labels labels) implements InfraNodeSpec {

    public ServiceMeshControlPlaneSpec {
        Objects.requireNonNull(namespace, "namespace");
        if (image == null) image = "istio/pilot:latest";
        if (labels == null) labels = Labels.empty();
    }

    @Override
    public String resourceType() { return "mesh_control_plane"; }
}
