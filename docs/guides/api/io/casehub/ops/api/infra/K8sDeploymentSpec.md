# io.casehub.ops.api.infra.K8sDeploymentSpec

**Package:** `io.casehub.ops.api.infra`

**Kind:** `record`

## Fields

### `env` (`java.util.Map<java.lang.String,java.lang.String>`)

### `healthCheck` (`java.util.Optional<io.casehub.ops.api.infra.types.HealthCheckSpec>`)

### `image` (`java.lang.String`)

### `labels` (`io.casehub.ops.api.infra.types.Labels`)

### `name` (`java.lang.String`)

### `namespace` (`java.lang.String`)

### `ports` (`java.util.List<io.casehub.ops.api.infra.types.PortMapping>`)

### `replicas` (`int`)

### `resources` (`io.casehub.ops.api.infra.types.ResourceRequirements`)

## Record Components

### `env` (`java.util.Map<java.lang.String,java.lang.String>`)

### `healthCheck` (`java.util.Optional<io.casehub.ops.api.infra.types.HealthCheckSpec>`)

### `image` (`java.lang.String`)

### `labels` (`io.casehub.ops.api.infra.types.Labels`)

### `name` (`java.lang.String`)

### `namespace` (`java.lang.String`)

### `ports` (`java.util.List<io.casehub.ops.api.infra.types.PortMapping>`)

### `replicas` (`int`)

### `resources` (`io.casehub.ops.api.infra.types.ResourceRequirements`)

## Constructors

### `public K8sDeploymentSpec(java.lang.String namespace, java.lang.String name, java.lang.String image, int replicas, io.casehub.ops.api.infra.types.ResourceRequirements resources, io.casehub.ops.api.infra.types.Labels labels)`

#### Parameters

- `namespace` (`java.lang.String`)
- `name` (`java.lang.String`)
- `image` (`java.lang.String`)
- `replicas` (`int`)
- `resources` (`io.casehub.ops.api.infra.types.ResourceRequirements`)
- `labels` (`io.casehub.ops.api.infra.types.Labels`)

### `public K8sDeploymentSpec(java.lang.String namespace, java.lang.String name, java.lang.String image, int replicas, io.casehub.ops.api.infra.types.ResourceRequirements resources, io.casehub.ops.api.infra.types.Labels labels, java.util.List<io.casehub.ops.api.infra.types.PortMapping> ports, java.util.Map<java.lang.String,java.lang.String> env, java.util.Optional<io.casehub.ops.api.infra.types.HealthCheckSpec> healthCheck)`

#### Parameters

- `namespace` (`java.lang.String`)
- `name` (`java.lang.String`)
- `image` (`java.lang.String`)
- `replicas` (`int`)
- `resources` (`io.casehub.ops.api.infra.types.ResourceRequirements`)
- `labels` (`io.casehub.ops.api.infra.types.Labels`)
- `ports` (`java.util.List<io.casehub.ops.api.infra.types.PortMapping>`)
- `env` (`java.util.Map<java.lang.String,java.lang.String>`)
- `healthCheck` (`java.util.Optional<io.casehub.ops.api.infra.types.HealthCheckSpec>`)

## Methods

### `public java.util.Map<java.lang.String,java.lang.String> env()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.Optional<io.casehub.ops.api.infra.types.HealthCheckSpec> healthCheck()`

### `public java.lang.String image()`

### `public io.casehub.ops.api.infra.types.Labels labels()`

### `public java.lang.String name()`

### `public java.lang.String namespace()`

### `public java.util.List<io.casehub.ops.api.infra.types.PortMapping> ports()`

### `public int replicas()`

### `public java.lang.String resourceType()`

### `public io.casehub.ops.api.infra.types.ResourceRequirements resources()`

### `public final java.lang.String toString()`
