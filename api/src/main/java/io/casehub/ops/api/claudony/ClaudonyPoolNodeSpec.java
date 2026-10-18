package io.casehub.ops.api.claudony;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.NodeTypeId;

@NodeTypeId("claudony_pool")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudonyPoolNodeSpec(
    String appName,
    String agentId,
    int minActive,
    int maxActive
) implements ClaudonyNodeSpec {
    public ClaudonyPoolNodeSpec {
        if (appName == null || appName.isBlank()) throw new IllegalArgumentException("appName required");
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("agentId required");
    }
    @Override public NodeType nodeType() { return ClaudonyNodeTypes.POOL; }
    @Override public String nodeId() { return "claudony_pool:" + appName + ":" + agentId; }
}
