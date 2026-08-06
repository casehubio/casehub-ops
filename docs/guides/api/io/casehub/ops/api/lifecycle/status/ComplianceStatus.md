# io.casehub.ops.api.lifecycle.status.ComplianceStatus

**Package:** `io.casehub.ops.api.lifecycle.status`

**Kind:** `enum`

## Fields

### `label` (`java.lang.String`)

### `severity` (`io.casehub.ops.api.lifecycle.Severity`)

## Enum Constants

### `COMPLIANT` (`io.casehub.ops.api.lifecycle.status.ComplianceStatus`)

### `NON_COMPLIANT` (`io.casehub.ops.api.lifecycle.status.ComplianceStatus`)

### `REMEDIATING` (`io.casehub.ops.api.lifecycle.status.ComplianceStatus`)

### `STALE_EVIDENCE` (`io.casehub.ops.api.lifecycle.status.ComplianceStatus`)

### `UNAVAILABLE` (`io.casehub.ops.api.lifecycle.status.ComplianceStatus`)

## Constructors

### `private ComplianceStatus(io.casehub.ops.api.lifecycle.Severity severity, java.lang.String label)`

#### Parameters

- `severity` (`io.casehub.ops.api.lifecycle.Severity`)
- `label` (`java.lang.String`)

## Methods

### `public boolean isTerminal()`

### `public java.lang.String label()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public static io.casehub.ops.api.lifecycle.status.ComplianceStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ops.api.lifecycle.status.ComplianceStatus[] values()`
