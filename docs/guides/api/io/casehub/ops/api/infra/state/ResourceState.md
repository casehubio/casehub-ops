# io.casehub.ops.api.infra.state.ResourceState

**Package:** `io.casehub.ops.api.infra.state`

**Kind:** `record`

## Fields

### `attributes` (`JsonNode`)

### `lastObserved` (`java.time.Instant`)

### `nodeId` (`NodeId`)

### `outputs` (`io.casehub.ops.api.infra.state.ResourceOutputs`)

### `resourceType` (`java.lang.String`)

### `status` (`io.casehub.ops.api.infra.state.ResourceStatus`)

## Record Components

### `attributes` (`JsonNode`)

### `lastObserved` (`java.time.Instant`)

### `nodeId` (`NodeId`)

### `outputs` (`io.casehub.ops.api.infra.state.ResourceOutputs`)

### `resourceType` (`java.lang.String`)

### `status` (`io.casehub.ops.api.infra.state.ResourceStatus`)

## Constructors

### `public ResourceState(NodeId nodeId, java.lang.String resourceType, io.casehub.ops.api.infra.state.ResourceStatus status, java.time.Instant lastObserved, JsonNode attributes, io.casehub.ops.api.infra.state.ResourceOutputs outputs)`

#### Parameters

- `nodeId` (`NodeId`)
- `resourceType` (`java.lang.String`)
- `status` (`io.casehub.ops.api.infra.state.ResourceStatus`)
- `lastObserved` (`java.time.Instant`)
- `attributes` (`JsonNode`)
- `outputs` (`io.casehub.ops.api.infra.state.ResourceOutputs`)

## Methods

### `public JsonNode attributes()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.time.Instant lastObserved()`

### `public NodeId nodeId()`

### `public io.casehub.ops.api.infra.state.ResourceOutputs outputs()`

### `public java.lang.String resourceType()`

### `public io.casehub.ops.api.infra.state.ResourceStatus status()`

### `public final java.lang.String toString()`
