package io.casehub.ops.claudony;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.casehub.platform.api.registry.HealthStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudonyAppInfo(
        String name,
        String namespace,
        String displayName,
        String description,
        String image,
        Map<String, String> config,
        HealthStatus health,
        Instant registeredAt,
        List<AppPoolInfo> pools
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AppPoolInfo(String agentId, int minActive, int maxActive) {}
}
