# io.casehub.ops.api.lifecycle.ServiceHealth

**Package:** `io.casehub.ops.api.lifecycle`

**Kind:** `record`

## Fields

### `dimensions` (`java.util.Map<io.casehub.ops.api.lifecycle.DimensionType,io.casehub.ops.api.lifecycle.DimensionStatus>`)

### `overallSeverity` (`io.casehub.ops.api.lifecycle.Severity`)

### `serviceId` (`java.lang.String`)

### `serviceName` (`java.lang.String`)

## Record Components

### `dimensions` (`java.util.Map<io.casehub.ops.api.lifecycle.DimensionType,io.casehub.ops.api.lifecycle.DimensionStatus>`)

### `overallSeverity` (`io.casehub.ops.api.lifecycle.Severity`)

### `serviceId` (`java.lang.String`)

### `serviceName` (`java.lang.String`)

## Constructors

### `public ServiceHealth(java.lang.String serviceId, java.lang.String serviceName, java.util.Map<io.casehub.ops.api.lifecycle.DimensionType,io.casehub.ops.api.lifecycle.DimensionStatus> dimensions, io.casehub.ops.api.lifecycle.Severity overallSeverity)`

#### Parameters

- `serviceId` (`java.lang.String`)
- `serviceName` (`java.lang.String`)
- `dimensions` (`java.util.Map<io.casehub.ops.api.lifecycle.DimensionType,io.casehub.ops.api.lifecycle.DimensionStatus>`)
- `overallSeverity` (`io.casehub.ops.api.lifecycle.Severity`)

## Methods

### `public java.util.Map<io.casehub.ops.api.lifecycle.DimensionType,io.casehub.ops.api.lifecycle.DimensionStatus> dimensions()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.ops.api.lifecycle.Severity overallSeverity()`

### `public java.lang.String serviceId()`

### `public java.lang.String serviceName()`

### `public final java.lang.String toString()`
