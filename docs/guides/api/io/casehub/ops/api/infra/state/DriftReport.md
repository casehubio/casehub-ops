# io.casehub.ops.api.infra.state.DriftReport

**Package:** `io.casehub.ops.api.infra.state`

**Kind:** `record`

## Fields

### `backendId` (`java.lang.String`)

### `detectedAt` (`java.time.Instant`)

### `drifted` (`boolean`)

### `drifts` (`java.util.List<io.casehub.ops.api.infra.state.DriftedField>`)

### `nodeId` (`NodeId`)

## Record Components

### `backendId` (`java.lang.String`)

### `detectedAt` (`java.time.Instant`)

### `drifted` (`boolean`)

### `drifts` (`java.util.List<io.casehub.ops.api.infra.state.DriftedField>`)

### `nodeId` (`NodeId`)

## Constructors

### `public DriftReport(NodeId nodeId, boolean drifted, java.util.List<io.casehub.ops.api.infra.state.DriftedField> drifts, java.time.Instant detectedAt, java.lang.String backendId)`

#### Parameters

- `nodeId` (`NodeId`)
- `drifted` (`boolean`)
- `drifts` (`java.util.List<io.casehub.ops.api.infra.state.DriftedField>`)
- `detectedAt` (`java.time.Instant`)
- `backendId` (`java.lang.String`)

## Methods

### `public java.lang.String backendId()`

### `public java.time.Instant detectedAt()`

### `public boolean drifted()`

### `public java.util.List<io.casehub.ops.api.infra.state.DriftedField> drifts()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public NodeId nodeId()`

### `public final java.lang.String toString()`
