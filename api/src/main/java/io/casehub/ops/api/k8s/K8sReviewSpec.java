package io.casehub.ops.api.k8s;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;

import java.util.Objects;

public record K8sReviewSpec(NodeId faultedNode, String reason) implements NodeSpec {
    public K8sReviewSpec {
        Objects.requireNonNull(faultedNode, "faultedNode");
        Objects.requireNonNull(reason, "reason");
    }
}
