package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;

import java.util.Objects;

public record InfraReviewSpec(NodeId faultedNode, String reason) implements NodeSpec {
    public InfraReviewSpec {
        Objects.requireNonNull(faultedNode, "faultedNode");
        Objects.requireNonNull(reason, "reason");
    }

    public NodeType nodeType() { return NodeType.of("infra-review"); }
}
