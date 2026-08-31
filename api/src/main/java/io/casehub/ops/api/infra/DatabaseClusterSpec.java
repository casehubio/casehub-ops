package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.types.BackupConfig;
import io.casehub.ops.api.infra.types.ClusterSize;
import io.casehub.ops.api.infra.types.DatabaseEngine;

import java.util.Objects;

@NodeTypeId("database_cluster")
public record DatabaseClusterSpec(
        DatabaseEngine engine,
        String version,
        ClusterSize size,
        String region,
        BackupConfig backup) implements InfraNodeSpec {

    public DatabaseClusterSpec {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(backup, "backup");
    }

    @Override
    public String resourceType() {
        return "database_cluster";
    }
}
