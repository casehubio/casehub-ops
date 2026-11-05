package io.casehub.ops.api.deployment;

public record PoolCreateRequest(
        String applicationId,
        String agentId,
        String backend,
        int minActive,
        int maxActive,
        String workingDir,
        String workingDirPolicy,
        String scalingType,
        Double targetFillRatio,
        String cooldown,
        String scaleInCooldown,
        String eviction
) {}
