# io.casehub.ops.api.deployment.TrustPolicyNodeSpec

**Package:** `io.casehub.ops.api.deployment`

**Kind:** `record`

## Fields

### `blendFactor` (`double`)

### `bootstrapEscalationRequired` (`boolean`)

### `borderlineMargin` (`double`)

### `capability` (`java.lang.String`)

### `minimumObservations` (`int`)

### `qualityFloors` (`java.util.Map<java.lang.String,java.lang.Double>`)

### `threshold` (`double`)

## Record Components

### `blendFactor` (`double`)

### `bootstrapEscalationRequired` (`boolean`)

### `borderlineMargin` (`double`)

### `capability` (`java.lang.String`)

### `minimumObservations` (`int`)

### `qualityFloors` (`java.util.Map<java.lang.String,java.lang.Double>`)

### `threshold` (`double`)

## Constructors

### `public TrustPolicyNodeSpec(java.lang.String capability, double threshold, int minimumObservations, double borderlineMargin, double blendFactor, java.util.Map<java.lang.String,java.lang.Double> qualityFloors, boolean bootstrapEscalationRequired)`

#### Parameters

- `capability` (`java.lang.String`)
- `threshold` (`double`)
- `minimumObservations` (`int`)
- `borderlineMargin` (`double`)
- `blendFactor` (`double`)
- `qualityFloors` (`java.util.Map<java.lang.String,java.lang.Double>`)
- `bootstrapEscalationRequired` (`boolean`)

## Methods

### `public double blendFactor()`

### `public boolean bootstrapEscalationRequired()`

### `public double borderlineMargin()`

### `public java.lang.String capability()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int minimumObservations()`

### `public java.lang.String nodeId()`

### `public java.lang.String nodeType()`

### `public java.util.Map<java.lang.String,java.lang.Double> qualityFloors()`

### `public double threshold()`

### `public final java.lang.String toString()`
