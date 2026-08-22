package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;

public sealed interface DeploymentNodeSpec extends NodeSpec permits
                                                            AgentNodeSpec, ChannelNodeSpec, CaseTypeNodeSpec, TrustPolicyNodeSpec, EndpointNodeSpec, DetectionNodeSpec {
    String nodeId();

    @Override
    NodeType nodeType();
}
