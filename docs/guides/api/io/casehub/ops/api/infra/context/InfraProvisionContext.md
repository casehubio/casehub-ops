# io.casehub.ops.api.infra.context.InfraProvisionContext

**Package:** `io.casehub.ops.api.infra.context`

**Kind:** `record`

## Fields

### `action` (`io.casehub.ops.api.infra.context.ProvisionAction`)

### `approvedPlan` (`io.casehub.ops.api.infra.plan.ProvisionPlan`)

### `nodeId` (`NodeId`)

### `phase` (`io.casehub.ops.api.infra.context.ProvisionPhase`)

### `requestedAt` (`java.time.Instant`)

### `tenancyId` (`java.lang.String`)

### `thresholds` (`io.casehub.ops.api.approval.ApprovalThresholds`)

## Record Components

### `action` (`io.casehub.ops.api.infra.context.ProvisionAction`)

### `approvedPlan` (`io.casehub.ops.api.infra.plan.ProvisionPlan`)

### `nodeId` (`NodeId`)

### `phase` (`io.casehub.ops.api.infra.context.ProvisionPhase`)

### `requestedAt` (`java.time.Instant`)

### `tenancyId` (`java.lang.String`)

### `thresholds` (`io.casehub.ops.api.approval.ApprovalThresholds`)

## Constructors

### `public InfraProvisionContext(NodeId nodeId, java.lang.String tenancyId, io.casehub.ops.api.infra.context.ProvisionPhase phase, io.casehub.ops.api.infra.context.ProvisionAction action, io.casehub.ops.api.infra.plan.ProvisionPlan approvedPlan, io.casehub.ops.api.approval.ApprovalThresholds thresholds, java.time.Instant requestedAt)`

#### Parameters

- `nodeId` (`NodeId`)
- `tenancyId` (`java.lang.String`)
- `phase` (`io.casehub.ops.api.infra.context.ProvisionPhase`)
- `action` (`io.casehub.ops.api.infra.context.ProvisionAction`)
- `approvedPlan` (`io.casehub.ops.api.infra.plan.ProvisionPlan`)
- `thresholds` (`io.casehub.ops.api.approval.ApprovalThresholds`)
- `requestedAt` (`java.time.Instant`)

## Methods

### `public io.casehub.ops.api.infra.context.ProvisionAction action()`

### `public io.casehub.ops.api.infra.plan.ProvisionPlan approvedPlan()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public NodeId nodeId()`

### `public io.casehub.ops.api.infra.context.ProvisionPhase phase()`

### `public java.time.Instant requestedAt()`

### `public java.lang.String tenancyId()`

### `public io.casehub.ops.api.approval.ApprovalThresholds thresholds()`

### `public final java.lang.String toString()`
