package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;

public record DeploymentApplicationNodeSpec(
    CaseHubApplicationSpec spec,
    String deploymentTargetId,
    String existingUrl
) implements NodeSpec {

    public String nodeId() { return spec.nodeId(); }

    @Override
    public NodeType nodeType() { return spec.nodeType(); }
}
