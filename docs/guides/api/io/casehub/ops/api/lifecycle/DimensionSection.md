# io.casehub.ops.api.lifecycle.DimensionSection

**Package:** `io.casehub.ops.api.lifecycle`

**Kind:** `class`

## Fields

### `lastUpdated` (`java.time.Instant`)

### `prefix` (`java.lang.String`)

### `reader` (`io.casehub.ops.api.lifecycle.DimensionSection.ContextReader`)

### `type` (`io.casehub.ops.api.lifecycle.DimensionType`)

### `writer` (`io.casehub.ops.api.lifecycle.DimensionSection.ContextWriter`)

## Constructors

### `public DimensionSection(io.casehub.ops.api.lifecycle.DimensionType type, io.casehub.ops.api.lifecycle.DimensionSection.ContextWriter writer, io.casehub.ops.api.lifecycle.DimensionSection.ContextReader reader)`

#### Parameters

- `type` (`io.casehub.ops.api.lifecycle.DimensionType`)
- `writer` (`io.casehub.ops.api.lifecycle.DimensionSection.ContextWriter`)
- `reader` (`io.casehub.ops.api.lifecycle.DimensionSection.ContextReader`)

## Methods

### `public T get(java.lang.String key, java.lang.Class<T> type)`

#### Parameters

- `key` (`java.lang.String`)
- `type` (`java.lang.Class<T>`)

### `public java.time.Instant lastUpdated()`

### `public void put(java.lang.String key, java.lang.Object value)`

#### Parameters

- `key` (`java.lang.String`)
- `value` (`java.lang.Object`)

### `public io.casehub.ops.api.lifecycle.DimensionType type()`
