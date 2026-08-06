# io.casehub.ops.api.lifecycle.ServiceCaseContext

**Package:** `io.casehub.ops.api.lifecycle`

**Kind:** `class`

## Fields

### `category` (`io.casehub.ops.api.lifecycle.ManagedServiceCategory`)

### `deployedAt` (`java.time.Instant`)

### `dimensions` (`java.util.Map<io.casehub.ops.api.lifecycle.DimensionType,io.casehub.ops.api.lifecycle.OperationalDimension>`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `serviceId` (`java.lang.String`)

### `serviceName` (`java.lang.String`)

## Constructors

### `private ServiceCaseContext(java.lang.String serviceId, java.lang.String serviceName, io.casehub.ops.api.lifecycle.ManagedServiceCategory category, java.util.Map<io.casehub.ops.api.lifecycle.DimensionType,io.casehub.ops.api.lifecycle.OperationalDimension> dimensions, java.time.Instant deployedAt, java.util.Map<java.lang.String,java.lang.Object> metadata)`

#### Parameters

- `serviceId` (`java.lang.String`)
- `serviceName` (`java.lang.String`)
- `category` (`io.casehub.ops.api.lifecycle.ManagedServiceCategory`)
- `dimensions` (`java.util.Map<io.casehub.ops.api.lifecycle.DimensionType,io.casehub.ops.api.lifecycle.OperationalDimension>`)
- `deployedAt` (`java.time.Instant`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.Object>`)

## Methods

### `public io.casehub.ops.api.lifecycle.ManagedServiceCategory category()`

### `public static io.casehub.ops.api.lifecycle.ServiceCaseContext create(java.lang.String serviceId, java.lang.String serviceName, io.casehub.ops.api.lifecycle.ManagedServiceCategory category, java.util.Map<java.lang.String,java.lang.Object> metadata, io.casehub.ops.api.lifecycle.DimensionSection.ContextWriter writer, io.casehub.ops.api.lifecycle.DimensionSection.ContextReader reader)`

#### Parameters

- `serviceId` (`java.lang.String`)
- `serviceName` (`java.lang.String`)
- `category` (`io.casehub.ops.api.lifecycle.ManagedServiceCategory`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.Object>`)
- `writer` (`io.casehub.ops.api.lifecycle.DimensionSection.ContextWriter`)
- `reader` (`io.casehub.ops.api.lifecycle.DimensionSection.ContextReader`)

### `private static io.casehub.ops.api.lifecycle.DimensionStatus defaultStatus(io.casehub.ops.api.lifecycle.DimensionType type)`

#### Parameters

- `type` (`io.casehub.ops.api.lifecycle.DimensionType`)

### `public java.time.Instant deployedAt()`

### `public java.util.Map<io.casehub.ops.api.lifecycle.DimensionType,io.casehub.ops.api.lifecycle.OperationalDimension> dimensions()`

### `public java.util.Map<java.lang.String,java.lang.Object> metadata()`

### `public java.lang.String serviceId()`

### `public java.lang.String serviceName()`

### `public io.casehub.ops.api.lifecycle.ServiceHealth toServiceHealth()`
