# io.casehub.ops.api.lifecycle.status.ScalingStatus

**Package:** `io.casehub.ops.api.lifecycle.status`

**Kind:** `enum`

## Fields

### `label` (`java.lang.String`)

### `severity` (`io.casehub.ops.api.lifecycle.Severity`)

## Enum Constants

### `OPTIMAL` (`io.casehub.ops.api.lifecycle.status.ScalingStatus`)

### `OVER_PROVISIONED` (`io.casehub.ops.api.lifecycle.status.ScalingStatus`)

### `SCALE_FAILED` (`io.casehub.ops.api.lifecycle.status.ScalingStatus`)

### `SCALING` (`io.casehub.ops.api.lifecycle.status.ScalingStatus`)

### `UNDER_PROVISIONED` (`io.casehub.ops.api.lifecycle.status.ScalingStatus`)

## Constructors

### `private ScalingStatus(io.casehub.ops.api.lifecycle.Severity severity, java.lang.String label)`

#### Parameters

- `severity` (`io.casehub.ops.api.lifecycle.Severity`)
- `label` (`java.lang.String`)

## Methods

### `public boolean isTerminal()`

### `public java.lang.String label()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public static io.casehub.ops.api.lifecycle.status.ScalingStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ops.api.lifecycle.status.ScalingStatus[] values()`
