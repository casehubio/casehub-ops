package io.casehub.ops.api.infra;

import java.util.Objects;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;

public record InfraDesiredNodeSpec(InfraNodeSpec resourceSpec, String backendId) implements NodeSpec {

    public InfraDesiredNodeSpec {
        Objects.requireNonNull(resourceSpec, "resourceSpec");
        Objects.requireNonNull(backendId, "backendId");
    }

    public NodeType nodeType() { return NodeType.of(resourceSpec.resourceType()); }
}
