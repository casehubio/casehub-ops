package io.casehub.ops.deployment.handler;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.ops.api.deployment.PoolNodeSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class PoolProvisionHandler {

    public interface PoolOperations {
        Optional<PoolInfo> getPool(String name);
        void createPool(PoolCreateRequest request);
        void updatePool(String name, PoolUpdateRequest request);
        void destroyPool(String name);
    }

    public record PoolInfo(
            String name, String status,
            int minActive, int maxActive,
            String scalingType, Double targetFillRatio,
            String cooldown, String scaleInCooldown,
            String eviction
    ) {}

    public record PoolCreateRequest(
            String agentId, String backend,
            int minActive, int maxActive,
            String workingDir, String workingDirPolicy,
            String scalingType, Double targetFillRatio,
            String cooldown, String scaleInCooldown,
            String eviction
    ) {}

    public record PoolUpdateRequest(
            Integer minActive, Integer maxActive,
            String scalingType, Double targetFillRatio,
            String cooldown, String scaleInCooldown,
            String eviction
    ) {}

    private final PoolOperations ops;

    @Inject
    public PoolProvisionHandler(PoolOperations ops) {
        this.ops = ops;
    }

    public ProvisionResult provision(PoolNodeSpec spec, ProvisionContext context) {
        var existing = ops.getPool(spec.agentId());
        if (existing.isEmpty()) {
            ops.createPool(toCreateRequest(spec));
            return new ProvisionResult.Success();
        }
        if (configDiffers(spec, existing.get())) {
            ops.updatePool(spec.agentId(), toUpdateRequest(spec));
        }
        return new ProvisionResult.Success();
    }

    public DeprovisionResult deprovision(PoolNodeSpec spec, DeprovisionContext context) {
        var existing = ops.getPool(spec.agentId());
        if (existing.isPresent()) {
            ops.destroyPool(spec.agentId());
        }
        return new DeprovisionResult.Success();
    }

    private boolean configDiffers(PoolNodeSpec spec, PoolInfo actual) {
        if (spec.minActive() != actual.minActive()) return true;
        if (spec.maxActive() != actual.maxActive()) return true;
        var scalingType = spec.scaling() != null ? spec.scaling().type() : "none";
        if (!Objects.equals(scalingType, actual.scalingType())) return true;
        var targetFillRatio = spec.scaling() != null ? spec.scaling().targetFillRatio() : null;
        if (!Objects.equals(targetFillRatio, actual.targetFillRatio())) return true;
        var eviction = spec.eviction() != null ? spec.eviction().name() : "MEMORY_WEIGHTED";
        return !Objects.equals(eviction, actual.eviction());
    }

    private PoolCreateRequest toCreateRequest(PoolNodeSpec spec) {
        return new PoolCreateRequest(
                spec.agentId(), spec.backend(),
                spec.minActive(), spec.maxActive(),
                spec.workingDir(),
                spec.workingDirPolicy() != null ? spec.workingDirPolicy().name() : null,
                spec.scaling() != null ? spec.scaling().type() : "none",
                spec.scaling() != null ? spec.scaling().targetFillRatio() : null,
                spec.scaling() != null ? spec.scaling().cooldown() : null,
                spec.scaling() != null ? spec.scaling().scaleInCooldown() : null,
                spec.eviction() != null ? spec.eviction().name() : "MEMORY_WEIGHTED");
    }

    private PoolUpdateRequest toUpdateRequest(PoolNodeSpec spec) {
        return new PoolUpdateRequest(
                spec.minActive(), spec.maxActive(),
                spec.scaling() != null ? spec.scaling().type() : "none",
                spec.scaling() != null ? spec.scaling().targetFillRatio() : null,
                spec.scaling() != null ? spec.scaling().cooldown() : null,
                spec.scaling() != null ? spec.scaling().scaleInCooldown() : null,
                spec.eviction() != null ? spec.eviction().name() : "MEMORY_WEIGHTED");
    }
}
