# io.casehub.ops.api.infra.task.ProvisionOutcome

**Package:** `io.casehub.ops.api.infra.task`

**Kind:** `record`

## Fields

### `artifact` (`io.casehub.ops.api.infra.task.ExecutionArtifact`)

### `executionLog` (`java.lang.String`)

### `resultState` (`io.casehub.ops.api.infra.state.ResourceState`)

### `success` (`boolean`)

## Record Components

### `artifact` (`io.casehub.ops.api.infra.task.ExecutionArtifact`)

### `executionLog` (`java.lang.String`)

### `resultState` (`io.casehub.ops.api.infra.state.ResourceState`)

### `success` (`boolean`)

## Constructors

### `public ProvisionOutcome(boolean success, io.casehub.ops.api.infra.state.ResourceState resultState, java.lang.String executionLog, io.casehub.ops.api.infra.task.ExecutionArtifact artifact)`

#### Parameters

- `success` (`boolean`)
- `resultState` (`io.casehub.ops.api.infra.state.ResourceState`)
- `executionLog` (`java.lang.String`)
- `artifact` (`io.casehub.ops.api.infra.task.ExecutionArtifact`)

## Methods

### `public io.casehub.ops.api.infra.task.ExecutionArtifact artifact()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String executionLog()`

### `public final int hashCode()`

### `public io.casehub.ops.api.infra.state.ResourceState resultState()`

### `public boolean success()`

### `public final java.lang.String toString()`
