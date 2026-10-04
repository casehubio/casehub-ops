# PoolNodeSpec + Pool Lifecycle via Desiredstate Reconciliation

**Issue:** casehubio/casehub-ops#116
**Date:** 2026-10-04
**Status:** Draft

## Summary

Add pool as the 7th node type in the deployment domain, following the established
AgentNodeSpec + AgentProvisionHandler pattern exactly. Pool lifecycle (create, update,
destroy) is managed through desiredstate reconciliation, with drift detection and
human approval gates for destructive operations.

## Architecture

### New Components

| Component | Module | Package | Role |
|-----------|--------|---------|------|
| `PoolNodeSpec` | `api/` | `io.casehub.ops.api.deployment` | Record implementing `DeploymentNodeSpec`. Declares desired pool configuration. |
| `PoolScalingSpec` | `api/` | `io.casehub.ops.api.deployment` | Nested record for scaling config (type, targetFillRatio, cooldown, scaleInCooldown). |
| `WorkingDirPolicy` | `api/` | `io.casehub.ops.api.deployment` | Enum: EXCLUSIVE, SHARED_READ, BRANCH_ISOLATED. Mirrors claudony's enum — no shared dependency. |
| `EvictionStrategy` | `api/` | `io.casehub.ops.api.deployment` | Enum: MEMORY_WEIGHTED, LRU. Mirrors claudony's enum. |
| `PoolProvisionHandler` | `deployment/` | `io.casehub.ops.deployment.handler` | Provisions/deprovisions pools via `PoolOperations` interface. |
| `PoolOperations` | `deployment/` | `io.casehub.ops.deployment.handler` | Inner interface on `PoolProvisionHandler`. CRUD for pools. |
| `PoolDriftChecker` | `deployment/` | `io.casehub.ops.deployment.drift` | Implements `NodeDriftChecker`. Compares desired vs. actual pool config. |

### Wiring Changes to Existing SPI Quad

1. **`DeploymentNodeSpec`** — add `PoolNodeSpec` to the sealed `permits` list.
2. **`DeploymentGoals`** — add `List<GoalEntry<PoolNodeSpec>> pools` field.
3. **`DeploymentGoalCompiler.compile()`** — add `compileEntries(goals.pools(), nodes, dependencies)`.
4. **`DeploymentActualStateAdapter.handledTypes()`** — add `NodeType.of("pool")`.
5. **`DeploymentNodeProvisioner`** — inject `PoolProvisionHandler`, add `case PoolNodeSpec` to
   `doProvision()`/`doDeprovision()` switch expressions, add `NodeType.of("pool")` to `handledTypes()`.

## PoolNodeSpec Shape

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record PoolNodeSpec(
        String agentId,
        String backend,
        int minActive,
        int maxActive,
        String workingDir,
        WorkingDirPolicy workingDirPolicy,
        PoolScalingSpec scaling,
        EvictionStrategy eviction
) implements DeploymentNodeSpec {

    public PoolNodeSpec {
        if (agentId == null || agentId.isBlank())
            throw new IllegalArgumentException("agentId is required");
        if (backend == null || backend.isBlank())
            throw new IllegalArgumentException("backend is required");
        if (maxActive < 1)
            throw new IllegalArgumentException("maxActive must be >= 1");
        if (maxActive < minActive)
            throw new IllegalArgumentException("maxActive must be >= minActive");
        if (eviction == null) eviction = EvictionStrategy.MEMORY_WEIGHTED;
    }

    @Override
    public String nodeId() {
        return agentId + "-pool";
    }

    @Override
    public NodeType nodeType() {
        return NodeType.of("pool");
    }
}
```

### Nested Types

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record PoolScalingSpec(
        String type,
        Double targetFillRatio,
        String cooldown,
        String scaleInCooldown
) {
    public PoolScalingSpec {
        if (type == null || type.isBlank()) type = "none";
    }
}
```

```java
public enum WorkingDirPolicy {
    EXCLUSIVE,
    SHARED_READ,
    BRANCH_ISOLATED
}
```

```java
public enum EvictionStrategy {
    MEMORY_WEIGHTED,
    LRU
}
```

### Node ID Convention

`nodeId()` returns `agentId + "-pool"` to avoid collision with the `AgentNodeSpec` whose
`nodeId()` returns the bare `agentId`. This enables the dependency chain:
`agentId` (agent) → `agentId-pool` (pool) → channel nodes.

## PoolProvisionHandler

Follows the `ChannelProvisionHandler` pattern — inner `PoolOperations` interface abstracts
the external service boundary.

```java
@ApplicationScoped
public class PoolProvisionHandler {

    public interface PoolOperations {
        Optional<PoolInfo> getPool(String name);
        void createPool(PoolCreateRequest request);
        void updatePool(String name, PoolUpdateRequest request);
        void destroyPool(String name);
    }

    private final PoolOperations ops;

    @Inject
    public PoolProvisionHandler(PoolOperations ops) {
        this.ops = ops;
    }

    public ProvisionResult provision(PoolNodeSpec spec, ProvisionContext context) { ... }
    public DeprovisionResult deprovision(PoolNodeSpec spec, DeprovisionContext context) { ... }
}
```

### PoolOperations DTOs

```java
public record PoolInfo(
        String name, String status,
        int minActive, int maxActive,
        String scalingType, Double targetFillRatio,
        String cooldown, String scaleInCooldown,
        String eviction
)

public record PoolCreateRequest(
        String agentId, String backend,
        int minActive, int maxActive,
        String workingDir, String workingDirPolicy,
        String scalingType, Double targetFillRatio,
        String cooldown, String scaleInCooldown,
        String eviction
)
```

`PoolUpdateRequest` reuses the fields from `PoolCreateRequest` with nullable fields for
partial updates (same pattern as claudony's existing `PoolUpdateRequest`).

### Provision Logic

1. Call `ops.getPool(spec.agentId())`.
2. If absent: call `ops.createPool(...)` with all spec fields.
3. If present: compare config fields. If any differ, call `ops.updatePool(...)` with changed fields.
4. Return `ProvisionResult.Success()`.

### Deprovision Logic

1. Call `ops.getPool(spec.agentId())`.
2. If present: call `ops.destroyPool(spec.agentId())`.
3. Return `DeprovisionResult.Success()`.

## PoolDriftChecker

```java
@ApplicationScoped
public class PoolDriftChecker implements NodeDriftChecker {

    private final PoolProvisionHandler.PoolOperations ops;

    @Override
    public String nodeType() { return "pool"; }

    @Override
    public NodeStatus check(NodeSpec spec, String tenancyId) {
        if (!(spec instanceof PoolNodeSpec poolSpec)) return NodeStatus.UNKNOWN;

        var actual = ops.getPool(poolSpec.agentId());
        if (actual.isEmpty()) return NodeStatus.ABSENT;

        var pool = actual.get();
        if (capacityDrifted(poolSpec, pool)
                || scalingDrifted(poolSpec, pool)
                || evictionDrifted(poolSpec, pool)) {
            return NodeStatus.DRIFTED;
        }
        return NodeStatus.PRESENT;
    }
}
```

### Drift fields compared

| Field | Desired (PoolNodeSpec) | Actual (PoolInfo) |
|-------|----------------------|-------------------|
| minActive | `spec.minActive()` | `pool.minActive()` |
| maxActive | `spec.maxActive()` | `pool.maxActive()` |
| scaling.type | `spec.scaling().type()` | `pool.scalingType()` |
| scaling.targetFillRatio | `spec.scaling().targetFillRatio()` | `pool.targetFillRatio()` |
| eviction | `spec.eviction().name()` | `pool.eviction()` |

Budget is runtime-only — excluded from drift comparison.

## GoalCompiler Ordering

Ordering is declared in the YAML `dependsOn`, not hardcoded. The expected dependency chain:

```
agent-node (agentId) ← pool-node (agentId-pool) ← channel-nodes
```

The `DeploymentGoalCompiler` already compiles `dependsOn` into `Dependency` objects via
`compileEntries()`. No ordering logic changes needed — the runtime `TransitionPlanner`
handles topological ordering from the dependency graph.

## Approval Gates

Reuses existing `ApprovalEvaluator` + `InMemoryPlanStore`. The approval infrastructure is
idempotent — plans are re-derivable from desired vs. actual state on restart.

Pool-specific risk classifications:

| Operation | Risk | Gate |
|-----------|------|------|
| Create pool | Low | No approval needed |
| Update pool (capacity change) | Low | No approval needed |
| Update pool (scaling type change) | Low | No approval needed |
| Deprovision (destroy pool) | High | PendingApproval |
| Provision with minActive=0, maxActive=0 | High | PendingApproval (scale-to-zero) |

## PoolOperations Implementation (app module)

The `app/` module provides the real `PoolOperations` implementation that calls Claudony
via mcpDomain. This crosses the service boundary at `/api/claudony/pools`.

The implementation will use the platform's mcpDomain client infrastructure for REST calls.

**Prerequisite:** Claudony must expose createPool/destroyPool endpoints first
(separate issue in casehubio/claudony).

## YAML Example

```yaml
code-reviewer-pool:
  type: pool
  dependsOn: [code-reviewer]
  spec:
    agentId: code-reviewer
    backend: claudony
    minActive: 2
    maxActive: 8
    workingDir: ~/workspace/reviews
    workingDirPolicy: SHARED_READ
    scaling:
      type: target-tracking
      targetFillRatio: 0.7
      cooldown: 30s
    eviction: MEMORY_WEIGHTED
```

## Testing

| Test | Location | Coverage |
|------|----------|----------|
| `PoolNodeSpecTest` | `api/` | Validation, nodeId, nodeType, JSON round-trip |
| `PoolProvisionHandlerTest` | `deployment/` | Provision (create/update), deprovision, idempotency with stubbed PoolOperations |
| `PoolDriftCheckerTest` | `deployment/` | ABSENT/PRESENT/DRIFTED for each field, UNKNOWN for non-pool spec |
| `DeploymentGoalCompilerTest` | `deployment/` | Pool entries compiled into graph with correct dependencies |
| `DeploymentNodeProvisionerTest` | `deployment/` | Pool dispatch in switch expression |
| `DeploymentActualStateAdapterTest` | `deployment/` | Pool type handled |

## Out of Scope

- Claudony-side createPool/destroyPool endpoints (separate claudony issue)
- Budget configuration in PoolNodeSpec (runtime concern, not desired state)
- Step/demand-pressure/proactive scaling config fields in PoolScalingSpec (start with target-tracking, extend later)
- Pool-to-pool dependencies (single pool per agent for now)

## References

- `AgentNodeSpec.java` — pattern source for DeploymentNodeSpec record shape
- `AgentProvisionHandler.java` — pattern source for provision/deprovision handler
- `AgentDriftChecker.java` — pattern source for drift checking
- `ChannelProvisionHandler.java` — pattern source for PoolOperations interface (inner ChannelOperations)
- `DeploymentGoalCompiler.java` — wiring point for new node type
- `DeploymentActualStateAdapter.java` — wiring point for drift checker
- `DeploymentNodeProvisioner.java` — wiring point for provision dispatch
- `ClaudonyPoolApi.java` — REST API surface for pool operations
- `AgentPoolDefinition.java` — Claudony pool model (ScalingConfig, EvictionStrategy, WorkingDirPolicy)
- `PoolDetail.java` — Claudony response model for drift comparison
- `InMemoryPlanStore.java` — idempotent approval infrastructure
- casehubio/claudony#248 — parent epic (fleet script lifecycle)
