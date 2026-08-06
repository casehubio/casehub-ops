# io.casehub.ops.api.lifecycle.status.SecurityStatus

**Package:** `io.casehub.ops.api.lifecycle.status`

**Kind:** `enum`

## Fields

### `label` (`java.lang.String`)

### `severity` (`io.casehub.ops.api.lifecycle.Severity`)

## Enum Constants

### `BREACH_DETECTED` (`io.casehub.ops.api.lifecycle.status.SecurityStatus`)

### `CLEAR` (`io.casehub.ops.api.lifecycle.status.SecurityStatus`)

### `INVESTIGATING` (`io.casehub.ops.api.lifecycle.status.SecurityStatus`)

### `PATCHING` (`io.casehub.ops.api.lifecycle.status.SecurityStatus`)

### `VULNERABILITY_DETECTED` (`io.casehub.ops.api.lifecycle.status.SecurityStatus`)

## Constructors

### `private SecurityStatus(io.casehub.ops.api.lifecycle.Severity severity, java.lang.String label)`

#### Parameters

- `severity` (`io.casehub.ops.api.lifecycle.Severity`)
- `label` (`java.lang.String`)

## Methods

### `public boolean isTerminal()`

### `public java.lang.String label()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public static io.casehub.ops.api.lifecycle.status.SecurityStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ops.api.lifecycle.status.SecurityStatus[] values()`
