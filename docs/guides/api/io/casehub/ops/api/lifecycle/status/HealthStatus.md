# io.casehub.ops.api.lifecycle.status.HealthStatus

**Package:** `io.casehub.ops.api.lifecycle.status`

**Kind:** `enum`

## Fields

### `label` (`java.lang.String`)

### `severity` (`io.casehub.ops.api.lifecycle.Severity`)

## Enum Constants

### `DEGRADED` (`io.casehub.ops.api.lifecycle.status.HealthStatus`)

### `DOWN` (`io.casehub.ops.api.lifecycle.status.HealthStatus`)

### `HEALTHY` (`io.casehub.ops.api.lifecycle.status.HealthStatus`)

### `INVESTIGATING` (`io.casehub.ops.api.lifecycle.status.HealthStatus`)

### `REMEDIATING` (`io.casehub.ops.api.lifecycle.status.HealthStatus`)

## Constructors

### `private HealthStatus(io.casehub.ops.api.lifecycle.Severity severity, java.lang.String label)`

#### Parameters

- `severity` (`io.casehub.ops.api.lifecycle.Severity`)
- `label` (`java.lang.String`)

## Methods

### `public boolean isTerminal()`

### `public java.lang.String label()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public static io.casehub.ops.api.lifecycle.status.HealthStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ops.api.lifecycle.status.HealthStatus[] values()`
