# io.casehub.ops.api.approval.InMemoryPlanStore

**Package:** `io.casehub.ops.api.approval`

**Kind:** `class`

## Fields

### `plans` (`java.util.concurrent.ConcurrentHashMap<java.lang.String,io.casehub.ops.api.approval.ApprovalPlan>`)

## Constructors

### `public InMemoryPlanStore()`

## Methods

### `public void remove(java.lang.String planReference)`

#### Parameters

- `planReference` (`java.lang.String`)

### `public java.util.Optional<io.casehub.ops.api.approval.ApprovalPlan> retrieve(java.lang.String planReference)`

#### Parameters

- `planReference` (`java.lang.String`)

### `public java.lang.String store(io.casehub.ops.api.approval.ApprovalPlan plan)`

#### Parameters

- `plan` (`io.casehub.ops.api.approval.ApprovalPlan`)
