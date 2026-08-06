# io.casehub.ops.api.lifecycle.status.ProblemManagementStatus

**Package:** `io.casehub.ops.api.lifecycle.status`

**Kind:** `enum`

## Fields

### `label` (`java.lang.String`)

### `severity` (`io.casehub.ops.api.lifecycle.Severity`)

## Enum Constants

### `INVESTIGATING` (`io.casehub.ops.api.lifecycle.status.ProblemManagementStatus`)

### `NO_KNOWN_PROBLEMS` (`io.casehub.ops.api.lifecycle.status.ProblemManagementStatus`)

### `PATTERN_DETECTED` (`io.casehub.ops.api.lifecycle.status.ProblemManagementStatus`)

### `ROOT_CAUSE_FIXED` (`io.casehub.ops.api.lifecycle.status.ProblemManagementStatus`)

### `WORKAROUND_APPLIED` (`io.casehub.ops.api.lifecycle.status.ProblemManagementStatus`)

## Constructors

### `private ProblemManagementStatus(io.casehub.ops.api.lifecycle.Severity severity, java.lang.String label)`

#### Parameters

- `severity` (`io.casehub.ops.api.lifecycle.Severity`)
- `label` (`java.lang.String`)

## Methods

### `public boolean isTerminal()`

### `public java.lang.String label()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public static io.casehub.ops.api.lifecycle.status.ProblemManagementStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ops.api.lifecycle.status.ProblemManagementStatus[] values()`
