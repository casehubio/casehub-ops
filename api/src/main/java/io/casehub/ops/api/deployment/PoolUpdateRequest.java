package io.casehub.ops.api.deployment;

public record PoolUpdateRequest(
        Integer minActive,
        Integer maxActive,
        String scalingType,
        Double targetFillRatio,
        String cooldown,
        String scaleInCooldown,
        String eviction
) {}
