package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.casehub.desiredstate.api.NodeType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PoolNodeSpec(
        String agentId,
        String backend,
        int minActive,
        int maxActive,
        String workingDir,
        WorkingDirPolicy workingDirPolicy,
        PoolScalingSpec scaling,
        EvictionStrategy eviction
) implements DeploymentNodeSpec {

    public PoolNodeSpec {
        if (agentId == null || agentId.isBlank())
            throw new IllegalArgumentException("agentId is required");
        if (backend == null || backend.isBlank())
            throw new IllegalArgumentException("backend is required");
        if (maxActive < 1)
            throw new IllegalArgumentException("maxActive must be >= 1");
        if (maxActive < minActive)
            throw new IllegalArgumentException("maxActive must be >= minActive");
        if (eviction == null) eviction = EvictionStrategy.MEMORY_WEIGHTED;
    }

    @Override
    public String nodeId() {
        return agentId + "-pool";
    }

    @Override
    public NodeType nodeType() {
        return NodeType.of("pool");
    }
}
