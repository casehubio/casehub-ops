# io.casehub.ops.api.approval.ApprovalPlan

**Package:** `io.casehub.ops.api.approval`

**Kind:** `record`

## Fields

### `action` (`StepAction`)

### `detail` (`io.casehub.ops.api.approval.PlanDetail`)

### `nodeId` (`NodeId`)

### `originalSpec` (`NodeSpec`)

### `risk` (`io.casehub.ops.api.approval.RiskClassification`)

### `summary` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

## Record Components

### `action` (`StepAction`)

### `detail` (`io.casehub.ops.api.approval.PlanDetail`)

### `nodeId` (`NodeId`)

### `originalSpec` (`NodeSpec`)

### `risk` (`io.casehub.ops.api.approval.RiskClassification`)

### `summary` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

## Constructors

### `public ApprovalPlan(NodeId nodeId, StepAction action, io.casehub.ops.api.approval.RiskClassification risk, java.lang.String summary, java.lang.String tenancyId, NodeSpec originalSpec, io.casehub.ops.api.approval.PlanDetail detail)`

#### Parameters

- `nodeId` (`NodeId`)
- `action` (`StepAction`)
- `risk` (`io.casehub.ops.api.approval.RiskClassification`)
- `summary` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `originalSpec` (`NodeSpec`)
- `detail` (`io.casehub.ops.api.approval.PlanDetail`)

## Methods

### `public StepAction action()`

### `public io.casehub.ops.api.approval.PlanDetail detail()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public NodeId nodeId()`

### `public NodeSpec originalSpec()`

### `public io.casehub.ops.api.approval.RiskClassification risk()`

### `public java.lang.String summary()`

### `public java.lang.String tenancyId()`

### `public final java.lang.String toString()`
