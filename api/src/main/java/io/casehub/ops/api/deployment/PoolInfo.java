package io.casehub.ops.api.deployment;

public record PoolInfo(
        String applicationId,
        String name,
        String status,
        int minActive,
        int maxActive,
        String scalingType,
        Double targetFillRatio,
        String cooldown,
        String scaleInCooldown,
        String eviction
) {}
