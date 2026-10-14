package io.casehub.ops.claudony;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudonyAppSpec(
        String name,
        String namespace,
        String displayName,
        String description,
        String image,
        Map<String, String> config,
        List<PoolSpec> pools
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PoolSpec(String agentId, int minActive, int maxActive) {}
}
