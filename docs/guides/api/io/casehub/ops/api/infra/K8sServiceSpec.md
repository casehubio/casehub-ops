# io.casehub.ops.api.infra.K8sServiceSpec

**Package:** `io.casehub.ops.api.infra`

**Kind:** `record`

## Fields

### `labels` (`io.casehub.ops.api.infra.types.Labels`)

### `name` (`java.lang.String`)

### `namespace` (`java.lang.String`)

### `port` (`int`)

### `selector` (`io.casehub.ops.api.infra.types.Labels`)

### `serviceType` (`io.casehub.ops.api.infra.types.ServiceType`)

### `targetPort` (`int`)

## Record Components

### `labels` (`io.casehub.ops.api.infra.types.Labels`)

### `name` (`java.lang.String`)

### `namespace` (`java.lang.String`)

### `port` (`int`)

### `selector` (`io.casehub.ops.api.infra.types.Labels`)

### `serviceType` (`io.casehub.ops.api.infra.types.ServiceType`)

### `targetPort` (`int`)

## Constructors

### `public K8sServiceSpec(java.lang.String namespace, java.lang.String name, int port, int targetPort, io.casehub.ops.api.infra.types.ServiceType serviceType, io.casehub.ops.api.infra.types.Labels labels, io.casehub.ops.api.infra.types.Labels selector)`

#### Parameters

- `namespace` (`java.lang.String`)
- `name` (`java.lang.String`)
- `port` (`int`)
- `targetPort` (`int`)
- `serviceType` (`io.casehub.ops.api.infra.types.ServiceType`)
- `labels` (`io.casehub.ops.api.infra.types.Labels`)
- `selector` (`io.casehub.ops.api.infra.types.Labels`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.ops.api.infra.types.Labels labels()`

### `public java.lang.String name()`

### `public java.lang.String namespace()`

### `public int port()`

### `public java.lang.String resourceType()`

### `public io.casehub.ops.api.infra.types.Labels selector()`

### `public io.casehub.ops.api.infra.types.ServiceType serviceType()`

### `public int targetPort()`

### `public final java.lang.String toString()`
