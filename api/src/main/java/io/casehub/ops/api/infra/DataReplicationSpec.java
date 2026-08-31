package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;

import java.util.Objects;

@NodeTypeId("data_replication")
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
