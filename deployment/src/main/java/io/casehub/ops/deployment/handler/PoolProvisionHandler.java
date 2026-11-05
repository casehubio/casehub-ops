package io.casehub.ops.deployment.handler;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.ops.api.deployment.PoolCreateRequest;
import io.casehub.ops.api.deployment.PoolInfo;
import io.casehub.ops.api.deployment.PoolNodeSpec;
import io.casehub.ops.api.deployment.PoolOperations;
import io.casehub.ops.api.deployment.PoolUpdateRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class PoolProvisionHandler {

    private final Map<String, PoolOperations> backends;

    @Inject
    public PoolProvisionHandler(@Any Instance<PoolOperations> allBackends) {
        this.backends = allBackends.stream()
                                   .collect(Collectors.toMap(PoolOperations::backendId, b -> b));
    }

    public PoolProvisionHandler(List<PoolOperations> backends) {
        this.backends = backends.stream()
                                .collect(Collectors.toMap(PoolOperations::backendId, b -> b));
    }

    public ProvisionResult provision(PoolNodeSpec spec, ProvisionContext context) {
        var ops = backends.get(spec.backend());
        if (ops == null) {
            return new ProvisionResult.Failed("No PoolOperations for backend: " + spec.backend());
        }
        var existing = ops.getPool(spec.applicationId(), spec.agentId());
        if (existing.isEmpty()) {
            ops.createPool(toCreateRequest(spec));
            return new ProvisionResult.Success();
        }
        if (configDiffers(spec, existing.get())) {
            ops.updatePool(spec.applicationId(), spec.agentId(), toUpdateRequest(spec));
        }
        return new ProvisionResult.Success();
    }

    public DeprovisionResult deprovision(PoolNodeSpec spec, DeprovisionContext context) {
        var ops = backends.get(spec.backend());
        if (ops == null) {
            return new DeprovisionResult.Failed("No PoolOperations for backend: " + spec.backend());
        }
        var existing = ops.getPool(spec.applicationId(), spec.agentId());
        if (existing.isPresent()) {
            ops.destroyPool(spec.applicationId(), spec.agentId());
        }
        return new DeprovisionResult.Success();
    }

    private boolean configDiffers(PoolNodeSpec spec, PoolInfo actual) {
        if (spec.minActive() != actual.minActive()) {return true;}
        if (spec.maxActive() != actual.maxActive()) {return true;}
        var scalingType = spec.scaling() != null ? spec.scaling().type() : "none";
        if (!Objects.equals(scalingType, actual.scalingType())) {return true;}
        var targetFillRatio = spec.scaling() != null ? spec.scaling().targetFillRatio() : null;
        if (!Objects.equals(targetFillRatio, actual.targetFillRatio())) {return true;}
        var eviction = spec.eviction() != null ? spec.eviction().name() : "MEMORY_WEIGHTED";
        return !Objects.equals(eviction, actual.eviction());
    }

    private PoolCreateRequest toCreateRequest(PoolNodeSpec spec) {
        return new PoolCreateRequest(
                spec.applicationId(),
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
