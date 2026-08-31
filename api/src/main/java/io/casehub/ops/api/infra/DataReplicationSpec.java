package io.casehub.ops.api.infra;

import java.util.Objects;

public record DataReplicationSpec(
        String sourceCluster,
        String targetCluster,
        String sourceService,
        ReplicationMode mode,
        int lagToleranceSeconds) implements InfraNodeSpec {

    public DataReplicationSpec {
        Objects.requireNonNull(sourceCluster, "sourceCluster");
        Objects.requireNonNull(targetCluster, "targetCluster");
        if (mode == null) mode = ReplicationMode.ASYNC;
    }

    @Override
    public String resourceType() { return "data_replication"; }
}
