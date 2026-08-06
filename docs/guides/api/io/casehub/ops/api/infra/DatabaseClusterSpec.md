# io.casehub.ops.api.infra.DatabaseClusterSpec

**Package:** `io.casehub.ops.api.infra`

**Kind:** `record`

## Fields

### `backup` (`io.casehub.ops.api.infra.types.BackupConfig`)

### `engine` (`io.casehub.ops.api.infra.types.DatabaseEngine`)

### `region` (`java.lang.String`)

### `size` (`io.casehub.ops.api.infra.types.ClusterSize`)

### `version` (`java.lang.String`)

## Record Components

### `backup` (`io.casehub.ops.api.infra.types.BackupConfig`)

### `engine` (`io.casehub.ops.api.infra.types.DatabaseEngine`)

### `region` (`java.lang.String`)

### `size` (`io.casehub.ops.api.infra.types.ClusterSize`)

### `version` (`java.lang.String`)

## Constructors

### `public DatabaseClusterSpec(io.casehub.ops.api.infra.types.DatabaseEngine engine, java.lang.String version, io.casehub.ops.api.infra.types.ClusterSize size, java.lang.String region, io.casehub.ops.api.infra.types.BackupConfig backup)`

#### Parameters

- `engine` (`io.casehub.ops.api.infra.types.DatabaseEngine`)
- `version` (`java.lang.String`)
- `size` (`io.casehub.ops.api.infra.types.ClusterSize`)
- `region` (`java.lang.String`)
- `backup` (`io.casehub.ops.api.infra.types.BackupConfig`)

## Methods

### `public io.casehub.ops.api.infra.types.BackupConfig backup()`

### `public io.casehub.ops.api.infra.types.DatabaseEngine engine()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String region()`

### `public java.lang.String resourceType()`

### `public io.casehub.ops.api.infra.types.ClusterSize size()`

### `public final java.lang.String toString()`

### `public java.lang.String version()`
