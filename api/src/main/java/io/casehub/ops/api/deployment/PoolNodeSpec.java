package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.NodeTypeId;

@JsonIgnoreProperties(ignoreUnknown = true)
@NodeTypeId("pool")
public record PoolNodeSpec(
        String applicationId,
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
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("applicationId is required");
        }
        if (agentId == null || agentId.isBlank()) {throw new IllegalArgumentException("agentId is required");}
        if (backend == null || backend.isBlank()) {throw new IllegalArgumentException("backend is required");}
        if (maxActive < 1) {throw new IllegalArgumentException("maxActive must be >= 1");}
        if (maxActive < minActive) {throw new IllegalArgumentException("maxActive must be >= minActive");}
        if (eviction == null) {eviction = EvictionStrategy.MEMORY_WEIGHTED;}
    }

    @Override
    public String nodeId() {
        return applicationId + ":" + agentId + "-pool";
    }

    @Override
    public NodeType nodeType() {
        return NodeType.of("pool");
    }
}
