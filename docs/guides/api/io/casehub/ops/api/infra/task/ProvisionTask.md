# io.casehub.ops.api.infra.task.ProvisionTask

**Package:** `io.casehub.ops.api.infra.task`

**Kind:** `record`

## Fields

### `action` (`io.casehub.ops.api.infra.task.TaskAction`)

### `currentState` (`io.casehub.ops.api.infra.state.ResourceState`)

### `nodeId` (`NodeId`)

### `spec` (`io.casehub.ops.api.infra.InfraNodeSpec`)

## Record Components

### `action` (`io.casehub.ops.api.infra.task.TaskAction`)

### `currentState` (`io.casehub.ops.api.infra.state.ResourceState`)

### `nodeId` (`NodeId`)

### `spec` (`io.casehub.ops.api.infra.InfraNodeSpec`)

## Constructors

### `public ProvisionTask(NodeId nodeId, io.casehub.ops.api.infra.InfraNodeSpec spec, io.casehub.ops.api.infra.task.TaskAction action, io.casehub.ops.api.infra.state.ResourceState currentState)`

#### Parameters

- `nodeId` (`NodeId`)
- `spec` (`io.casehub.ops.api.infra.InfraNodeSpec`)
- `action` (`io.casehub.ops.api.infra.task.TaskAction`)
- `currentState` (`io.casehub.ops.api.infra.state.ResourceState`)

## Methods

### `public io.casehub.ops.api.infra.task.TaskAction action()`

### `public io.casehub.ops.api.infra.state.ResourceState currentState()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public NodeId nodeId()`

### `public io.casehub.ops.api.infra.InfraNodeSpec spec()`

### `public final java.lang.String toString()`
