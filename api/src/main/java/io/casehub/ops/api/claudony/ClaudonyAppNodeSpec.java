package io.casehub.ops.api.claudony;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.NodeTypeId;

import java.util.Map;

@NodeTypeId("claudony_app")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudonyAppNodeSpec(
    String name,
    String displayName,
    String description,
    String image,
    Map<String, String> config
) implements ClaudonyNodeSpec {
    public ClaudonyAppNodeSpec {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        config = config != null ? Map.copyOf(config) : Map.of();
    }
    @Override public NodeType nodeType() { return ClaudonyNodeTypes.APP; }
    @Override public String nodeId() { return "claudony_app:" + name; }
}
