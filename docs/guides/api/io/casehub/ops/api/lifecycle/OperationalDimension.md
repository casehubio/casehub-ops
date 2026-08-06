# io.casehub.ops.api.lifecycle.OperationalDimension

**Package:** `io.casehub.ops.api.lifecycle`

**Kind:** `class`

## Fields

### `activeResponses` (`java.util.List<io.casehub.ops.api.lifecycle.CaseRef>`)

### `section` (`io.casehub.ops.api.lifecycle.DimensionSection`)

### `status` (`io.casehub.ops.api.lifecycle.DimensionStatus`)

### `subscriptions` (`java.util.List<io.casehub.ops.api.lifecycle.GanglionBinding>`)

### `type` (`io.casehub.ops.api.lifecycle.DimensionType`)

## Constructors

### `public OperationalDimension(io.casehub.ops.api.lifecycle.DimensionType type, io.casehub.ops.api.lifecycle.DimensionStatus status, io.casehub.ops.api.lifecycle.DimensionSection section, java.util.List<io.casehub.ops.api.lifecycle.GanglionBinding> subscriptions)`

#### Parameters

- `type` (`io.casehub.ops.api.lifecycle.DimensionType`)
- `status` (`io.casehub.ops.api.lifecycle.DimensionStatus`)
- `section` (`io.casehub.ops.api.lifecycle.DimensionSection`)
- `subscriptions` (`java.util.List<io.casehub.ops.api.lifecycle.GanglionBinding>`)

## Methods

### `public java.util.List<io.casehub.ops.api.lifecycle.CaseRef> activeResponses()`

### `public void addResponse(io.casehub.ops.api.lifecycle.CaseRef ref)`

#### Parameters

- `ref` (`io.casehub.ops.api.lifecycle.CaseRef`)

### `public void removeResponse(java.util.UUID caseId)`

#### Parameters

- `caseId` (`java.util.UUID`)

### `public io.casehub.ops.api.lifecycle.DimensionSection section()`

### `public io.casehub.ops.api.lifecycle.Severity severity()`

### `public io.casehub.ops.api.lifecycle.DimensionStatus status()`

### `public java.util.List<io.casehub.ops.api.lifecycle.GanglionBinding> subscriptions()`

### `public io.casehub.ops.api.lifecycle.DimensionType type()`

### `public void updateStatus(io.casehub.ops.api.lifecycle.DimensionStatus newStatus)`

#### Parameters

- `newStatus` (`io.casehub.ops.api.lifecycle.DimensionStatus`)
