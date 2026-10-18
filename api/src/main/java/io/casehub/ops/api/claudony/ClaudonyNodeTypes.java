package io.casehub.ops.api.claudony;

import io.casehub.desiredstate.api.NodeType;

public final class ClaudonyNodeTypes {
    public static final NodeType APP = NodeType.of("claudony_app");
    public static final NodeType POOL = NodeType.of("claudony_pool");
    private ClaudonyNodeTypes() {}
}
