# io.casehub.ops.api.lifecycle.status.DecommissionStatus

**Package:** `io.casehub.ops.api.lifecycle.status`

**Kind:** `enum`

## Fields

### `label` (`java.lang.String`)

### `severity` (`io.casehub.ops.api.lifecycle.Severity`)

## Enum Constants

### `BLOCKED` (`io.casehub.ops.api.lifecycle.status.DecommissionStatus`)

### `COMPLETED` (`io.casehub.ops.api.lifecycle.status.DecommissionStatus`)

### `IN_PROGRESS` (`io.casehub.ops.api.lifecycle.status.DecommissionStatus`)

### `NOT_PLANNED` (`io.casehub.ops.api.lifecycle.status.DecommissionStatus`)

### `SCHEDULED` (`io.casehub.ops.api.lifecycle.status.DecommissionStatus`)

## Constructors

### `private DecommissionStatus(io.casehub.ops.api.lifecycle.Severity severity, java.lang.String label)`

#### Parameters

- `severity` (`io.casehub.ops.api.lifecycle.Severity`)
- `label` (`java.lang.String`)

## Methods

### `public boolean isTerminal()`

### `public java.lang.String label()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public static io.casehub.ops.api.lifecycle.status.DecommissionStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ops.api.lifecycle.status.DecommissionStatus[] values()`
