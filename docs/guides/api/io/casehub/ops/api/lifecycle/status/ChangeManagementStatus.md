# io.casehub.ops.api.lifecycle.status.ChangeManagementStatus

**Package:** `io.casehub.ops.api.lifecycle.status`

**Kind:** `enum`

## Fields

### `label` (`java.lang.String`)

### `severity` (`io.casehub.ops.api.lifecycle.Severity`)

## Enum Constants

### `CANARY` (`io.casehub.ops.api.lifecycle.status.ChangeManagementStatus`)

### `CHANGE_FAILED` (`io.casehub.ops.api.lifecycle.status.ChangeManagementStatus`)

### `NO_ACTIVITY` (`io.casehub.ops.api.lifecycle.status.ChangeManagementStatus`)

### `ROLLBACK` (`io.casehub.ops.api.lifecycle.status.ChangeManagementStatus`)

### `ROLLING_OUT` (`io.casehub.ops.api.lifecycle.status.ChangeManagementStatus`)

### `UPGRADE_AVAILABLE` (`io.casehub.ops.api.lifecycle.status.ChangeManagementStatus`)

## Constructors

### `private ChangeManagementStatus(io.casehub.ops.api.lifecycle.Severity severity, java.lang.String label)`

#### Parameters

- `severity` (`io.casehub.ops.api.lifecycle.Severity`)
- `label` (`java.lang.String`)

## Methods

### `public boolean isTerminal()`

### `public java.lang.String label()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public static io.casehub.ops.api.lifecycle.status.ChangeManagementStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ops.api.lifecycle.status.ChangeManagementStatus[] values()`
