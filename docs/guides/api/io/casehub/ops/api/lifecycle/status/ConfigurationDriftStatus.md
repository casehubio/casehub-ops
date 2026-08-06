# io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus

**Package:** `io.casehub.ops.api.lifecycle.status`

**Kind:** `enum`

## Fields

### `label` (`java.lang.String`)

### `severity` (`io.casehub.ops.api.lifecycle.Severity`)

## Enum Constants

### `DRIFTED` (`io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus`)

### `IN_SYNC` (`io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus`)

### `RECONCILIATION_FAILED` (`io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus`)

### `RECONCILING` (`io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus`)

## Constructors

### `private ConfigurationDriftStatus(io.casehub.ops.api.lifecycle.Severity severity, java.lang.String label)`

#### Parameters

- `severity` (`io.casehub.ops.api.lifecycle.Severity`)
- `label` (`java.lang.String`)

## Methods

### `public boolean isTerminal()`

### `public java.lang.String label()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public static io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus[] values()`
