package io.casehub.ops.api.infra;

import io.casehub.ops.api.infra.types.ResourceRequirements;

import io.casehub.desiredstate.api.NodeTypeId;

import java.util.Objects;

@NodeTypeId("sidecar_proxy")
public record SidecarProxySpec(
        String targetService,
        String image,
        ResourceRequirements resources) implements InfraNodeSpec {

    public SidecarProxySpec {
        Objects.requireNonNull(targetService, "targetService");
        if (image == null) image = "envoyproxy/envoy:latest";
    }

    @Override
    public String resourceType() { return "sidecar_proxy"; }
}
