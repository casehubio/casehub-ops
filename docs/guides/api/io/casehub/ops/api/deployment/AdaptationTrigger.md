# io.casehub.ops.api.deployment.AdaptationTrigger

**Package:** `io.casehub.ops.api.deployment`

**Kind:** `record`

## Fields

### `cooldown` (`java.time.Duration`)

### `deactivateBelow` (`java.lang.Double`)

### `minConfidence` (`double`)

### `situation` (`java.lang.String`)

## Record Components

### `cooldown` (`java.time.Duration`)

### `deactivateBelow` (`java.lang.Double`)

### `minConfidence` (`double`)

### `situation` (`java.lang.String`)

## Constructors

### `public AdaptationTrigger(java.lang.String situation, double minConfidence, java.lang.Double deactivateBelow, java.time.Duration cooldown)`

#### Parameters

- `situation` (`java.lang.String`)
- `minConfidence` (`double`)
- `deactivateBelow` (`java.lang.Double`)
- `cooldown` (`java.time.Duration`)

## Methods

### `public java.time.Duration cooldown()`

### `public java.lang.Double deactivateBelow()`

### `public java.time.Duration effectiveCooldown()`

### `public double effectiveDeactivateBelow()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public double minConfidence()`

### `public java.lang.String situation()`

### `public final java.lang.String toString()`
