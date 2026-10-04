package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PoolScalingSpec(
        String type,
        Double targetFillRatio,
        String cooldown,
        String scaleInCooldown
) {
    public PoolScalingSpec {
        if (type == null || type.isBlank()) type = "none";
    }
}
