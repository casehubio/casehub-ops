# io.casehub.ops.api.compliance.ComplianceControlSpec

**Package:** `io.casehub.ops.api.compliance`

**Kind:** `record`

## Fields

### `controlId` (`java.lang.String`)

### `controlType` (`java.lang.String`)

### `description` (`java.lang.String`)

### `evidenceMaxAgeDays` (`int`)

### `frameworks` (`java.util.List<io.casehub.ops.api.compliance.FrameworkMapping>`)

### `properties` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `requiresHumanReview` (`boolean`)

### `strategy` (`java.lang.String`)

### `title` (`java.lang.String`)

## Record Components

### `controlId` (`java.lang.String`)

### `controlType` (`java.lang.String`)

### `description` (`java.lang.String`)

### `evidenceMaxAgeDays` (`int`)

### `frameworks` (`java.util.List<io.casehub.ops.api.compliance.FrameworkMapping>`)

### `properties` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `requiresHumanReview` (`boolean`)

### `strategy` (`java.lang.String`)

### `title` (`java.lang.String`)

## Constructors

### `public ComplianceControlSpec(java.lang.String controlId, java.lang.String controlType, java.lang.String strategy, java.lang.String title, java.lang.String description, java.util.List<io.casehub.ops.api.compliance.FrameworkMapping> frameworks, int evidenceMaxAgeDays, boolean requiresHumanReview, java.util.Map<java.lang.String,java.lang.Object> properties)`

#### Parameters

- `controlId` (`java.lang.String`)
- `controlType` (`java.lang.String`)
- `strategy` (`java.lang.String`)
- `title` (`java.lang.String`)
- `description` (`java.lang.String`)
- `frameworks` (`java.util.List<io.casehub.ops.api.compliance.FrameworkMapping>`)
- `evidenceMaxAgeDays` (`int`)
- `requiresHumanReview` (`boolean`)
- `properties` (`java.util.Map<java.lang.String,java.lang.Object>`)

## Methods

### `public java.lang.String controlId()`

### `public java.lang.String controlType()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public int evidenceMaxAgeDays()`

### `public java.util.List<io.casehub.ops.api.compliance.FrameworkMapping> frameworks()`

### `public final int hashCode()`

### `public java.util.Map<java.lang.String,java.lang.Object> properties()`

### `public boolean requiresHuman()`

### `public boolean requiresHumanReview()`

### `public java.lang.String strategy()`

### `public java.lang.String title()`

### `public final java.lang.String toString()`
