package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NodeTemplate(
        String nodeId,
        String nodeType,
        Map<String, Object> spec
) {
    public NodeTemplate {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(nodeType, "nodeType");
        Objects.requireNonNull(spec, "spec");
        if (nodeType.contains("${")) {
            throw new IllegalArgumentException(
                "nodeType must not contain placeholders: " + nodeType);
        }
        spec = Map.copyOf(spec);
    }
}
