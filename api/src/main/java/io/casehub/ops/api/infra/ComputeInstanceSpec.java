package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.types.CloudProvider;
import io.casehub.ops.api.infra.types.InstanceType;
import io.casehub.ops.api.infra.types.NetworkConfig;

import java.util.Objects;

@NodeTypeId("compute_instance")
public record ComputeInstanceSpec(
        CloudProvider provider,
        String region,
        InstanceType instanceType,
        String imageId,
        NetworkConfig network) implements InfraNodeSpec {

    public ComputeInstanceSpec {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(instanceType, "instanceType");
        Objects.requireNonNull(imageId, "imageId");
        Objects.requireNonNull(network, "network");
    }

    @Override
    public String resourceType() {
        return "compute_instance";
    }
}
