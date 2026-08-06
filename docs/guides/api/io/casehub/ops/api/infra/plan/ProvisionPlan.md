# io.casehub.ops.api.infra.plan.ProvisionPlan

**Package:** `io.casehub.ops.api.infra.plan`

**Kind:** `record`

## Fields

### `changes` (`java.util.List<io.casehub.ops.api.infra.plan.PlannedChange>`)

### `nodeId` (`NodeId`)

### `risk` (`io.casehub.ops.api.approval.RiskClassification`)

### `summary` (`java.lang.String`)

### `toolDetail` (`io.casehub.ops.api.infra.plan.ToolPlanDetail`)

## Record Components

### `changes` (`java.util.List<io.casehub.ops.api.infra.plan.PlannedChange>`)

### `nodeId` (`NodeId`)

### `risk` (`io.casehub.ops.api.approval.RiskClassification`)

### `summary` (`java.lang.String`)

### `toolDetail` (`io.casehub.ops.api.infra.plan.ToolPlanDetail`)

## Constructors

### `public ProvisionPlan(NodeId nodeId, java.util.List<io.casehub.ops.api.infra.plan.PlannedChange> changes, io.casehub.ops.api.approval.RiskClassification risk, java.lang.String summary, io.casehub.ops.api.infra.plan.ToolPlanDetail toolDetail)`

#### Parameters

- `nodeId` (`NodeId`)
- `changes` (`java.util.List<io.casehub.ops.api.infra.plan.PlannedChange>`)
- `risk` (`io.casehub.ops.api.approval.RiskClassification`)
- `summary` (`java.lang.String`)
- `toolDetail` (`io.casehub.ops.api.infra.plan.ToolPlanDetail`)

## Methods

### `public java.util.List<io.casehub.ops.api.infra.plan.PlannedChange> changes()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public NodeId nodeId()`

### `public io.casehub.ops.api.approval.RiskClassification risk()`

### `public java.lang.String summary()`

### `public final java.lang.String toString()`

### `public io.casehub.ops.api.infra.plan.ToolPlanDetail toolDetail()`
