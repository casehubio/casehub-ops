package io.casehub.ops.api.claudony;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;

import java.util.Objects;

public record ClaudonyReviewSpec(NodeId faultedNode, String reason) implements NodeSpec {
    public ClaudonyReviewSpec {
        Objects.requireNonNull(faultedNode, "faultedNode");
        Objects.requireNonNull(reason, "reason");
    }
    @Override public NodeType nodeType() { return NodeType.of("claudony_review"); }
}
