# io.casehub.ops.api.lifecycle.status.MaintenanceStatus

**Package:** `io.casehub.ops.api.lifecycle.status`

**Kind:** `enum`

## Fields

### `label` (`java.lang.String`)

### `severity` (`io.casehub.ops.api.lifecycle.Severity`)

## Enum Constants

### `FAILED` (`io.casehub.ops.api.lifecycle.status.MaintenanceStatus`)

### `IN_PROGRESS` (`io.casehub.ops.api.lifecycle.status.MaintenanceStatus`)

### `NO_ACTIVITY` (`io.casehub.ops.api.lifecycle.status.MaintenanceStatus`)

### `OVERDUE` (`io.casehub.ops.api.lifecycle.status.MaintenanceStatus`)

### `SCHEDULED` (`io.casehub.ops.api.lifecycle.status.MaintenanceStatus`)

## Constructors

### `private MaintenanceStatus(io.casehub.ops.api.lifecycle.Severity severity, java.lang.String label)`

#### Parameters

- `severity` (`io.casehub.ops.api.lifecycle.Severity`)
- `label` (`java.lang.String`)

## Methods

### `public boolean isTerminal()`

### `public java.lang.String label()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public static io.casehub.ops.api.lifecycle.status.MaintenanceStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ops.api.lifecycle.status.MaintenanceStatus[] values()`
