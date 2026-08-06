# io.casehub.ops.api.infra.K8sIngressSpec

**Package:** `io.casehub.ops.api.infra`

**Kind:** `record`

## Fields

### `host` (`java.lang.String`)

### `labels` (`io.casehub.ops.api.infra.types.Labels`)

### `name` (`java.lang.String`)

### `namespace` (`java.lang.String`)

### `rules` (`java.util.List<io.casehub.ops.api.infra.types.IngressRule>`)

## Record Components

### `host` (`java.lang.String`)

### `labels` (`io.casehub.ops.api.infra.types.Labels`)

### `name` (`java.lang.String`)

### `namespace` (`java.lang.String`)

### `rules` (`java.util.List<io.casehub.ops.api.infra.types.IngressRule>`)

## Constructors

### `public K8sIngressSpec(java.lang.String namespace, java.lang.String name, java.lang.String host, java.util.List<io.casehub.ops.api.infra.types.IngressRule> rules, io.casehub.ops.api.infra.types.Labels labels)`

#### Parameters

- `namespace` (`java.lang.String`)
- `name` (`java.lang.String`)
- `host` (`java.lang.String`)
- `rules` (`java.util.List<io.casehub.ops.api.infra.types.IngressRule>`)
- `labels` (`io.casehub.ops.api.infra.types.Labels`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String host()`

### `public io.casehub.ops.api.infra.types.Labels labels()`

### `public java.lang.String name()`

### `public java.lang.String namespace()`

### `public java.lang.String resourceType()`

### `public java.util.List<io.casehub.ops.api.infra.types.IngressRule> rules()`

### `public final java.lang.String toString()`
