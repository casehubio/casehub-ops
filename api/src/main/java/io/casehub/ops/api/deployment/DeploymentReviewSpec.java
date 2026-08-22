package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;

import java.util.Objects;

public record DeploymentReviewSpec(NodeId faultedNode, String reason) implements NodeSpec {
    public DeploymentReviewSpec {
        Objects.requireNonNull(faultedNode, "faultedNode");
        Objects.requireNonNull(reason, "reason");
    }

    public NodeType nodeType() { return NodeType.of("deployment-review"); }
}
