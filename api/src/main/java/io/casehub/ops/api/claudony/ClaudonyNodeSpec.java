package io.casehub.ops.api.claudony;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;

public sealed interface ClaudonyNodeSpec extends NodeSpec
    permits ClaudonyAppNodeSpec, ClaudonyPoolNodeSpec {
    String nodeId();
    @Override NodeType nodeType();
}
