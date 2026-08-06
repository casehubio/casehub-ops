# io.casehub.ops.api.infra.ComputeInstanceSpec

**Package:** `io.casehub.ops.api.infra`

**Kind:** `record`

## Fields

### `imageId` (`java.lang.String`)

### `instanceType` (`io.casehub.ops.api.infra.types.InstanceType`)

### `network` (`io.casehub.ops.api.infra.types.NetworkConfig`)

### `provider` (`io.casehub.ops.api.infra.types.CloudProvider`)

### `region` (`java.lang.String`)

## Record Components

### `imageId` (`java.lang.String`)

### `instanceType` (`io.casehub.ops.api.infra.types.InstanceType`)

### `network` (`io.casehub.ops.api.infra.types.NetworkConfig`)

### `provider` (`io.casehub.ops.api.infra.types.CloudProvider`)

### `region` (`java.lang.String`)

## Constructors

### `public ComputeInstanceSpec(io.casehub.ops.api.infra.types.CloudProvider provider, java.lang.String region, io.casehub.ops.api.infra.types.InstanceType instanceType, java.lang.String imageId, io.casehub.ops.api.infra.types.NetworkConfig network)`

#### Parameters

- `provider` (`io.casehub.ops.api.infra.types.CloudProvider`)
- `region` (`java.lang.String`)
- `instanceType` (`io.casehub.ops.api.infra.types.InstanceType`)
- `imageId` (`java.lang.String`)
- `network` (`io.casehub.ops.api.infra.types.NetworkConfig`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String imageId()`

### `public io.casehub.ops.api.infra.types.InstanceType instanceType()`

### `public io.casehub.ops.api.infra.types.NetworkConfig network()`

### `public io.casehub.ops.api.infra.types.CloudProvider provider()`

### `public java.lang.String region()`

### `public java.lang.String resourceType()`

### `public final java.lang.String toString()`
