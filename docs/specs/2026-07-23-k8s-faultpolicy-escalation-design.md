# K8s-Aware FaultPolicy — Reusable Threshold Escalation Component

**Issue:** casehubio/casehub-ops#45
**Date:** 2026-07-23
**Status:** Implemented

## Problem

`KubernetesFaultPolicy` is a stub — returns `List.of()` for all faults. K8s resources
that persistently fail to provision are retried indefinitely with no escalation path.
The same escalation pattern (count faults per node, escalate at threshold) is already
implemented in `IoTFaultPolicy` and the desiredstate example `ProvisionEscalationFaultPolicy`,
but as inline logic with no reuse.

This spec addresses a subset of issue #45's vision. #45 envisions operational data
including "failure counts, retry history" to distinguish transient from permanent failures.
This design provides failure counts via an in-memory counter, using count-as-proxy for the
transient/permanent distinction. Retry history and restart-resilient counting are deferred
(casehubio/casehub-desiredstate#85).

## Decision

Extract a reusable `ThresholdFaultPolicy` into casehub-desiredstate's runtime package.
Domain modules in casehub-ops (and any future desiredstate consumer) delegate to it with
domain-specific configuration.

**Why desiredstate, not platform-api or casehub-ops/api:**
- The component implements `FaultPolicy` and returns `GraphMutation` — both desiredstate API types.
  Placing it in platform-api would create a circular dependency (desiredstate → platform-api → desiredstate).
- Placing it in casehub-ops/api limits availability to ops domain modules only.
- desiredstate owns the SPI and already contains reusable policy implementations (`CbrFaultPolicy`,
  `FaultPolicyEngine`).

**Why not workers-k8s:** workers-k8s handles K8s Job execution faults (retry with backoff for
worker tasks). `KubernetesFaultPolicy` handles K8s infrastructure provisioning faults (desired-state
graph mutations). Different concerns at different layers — no overlap.

**Why "ThresholdFaultPolicy", not "EscalatingFaultPolicy":** The desiredstate ARC42STORIES
and multiple specs describe "three-tier fault escalation (auto-retry → AI review → human review)"
as a core architectural pattern. `ProvisionEscalationFaultPolicy` implements this full model.
This component supports a single tier — count faults, fire one action at threshold. Naming it
`EscalatingFaultPolicy` would create a false architectural claim. `ThresholdFaultPolicy`
accurately describes the behavior: count-based threshold with configurable action. Multi-tier
escalation support is tracked as casehubio/casehub-desiredstate#86.

**Other stub fault policy candidates:** Three of five domain fault policies in casehub-ops
are stubs: `DeploymentFaultPolicy`, `InfraFaultPolicy`, and `ComplianceFaultPolicy`.
`InfraFaultPolicy` is a strong candidate — its Javadoc notes the lack of transient/permanent
distinction, which count-as-proxy addresses (casehubio/casehub-ops#64).
`DeploymentFaultPolicy` requires evaluation — deployment nodes are one-shot registrations
where repeated failures typically indicate misconfiguration (casehubio/casehub-ops#65).
`ComplianceFaultPolicy` should remain no-op — compliance drift is handled by evidence-based
drift detection in the ActualStateAdapter, not by fault policy mutations.

## Architecture

### Approach: composition with builder configuration

Consumers wrap a `ThresholdFaultPolicy` instance configured via builder. No inheritance —
domain fault policies remain `implements FaultPolicy` and delegate internally.

### New types in casehub-desiredstate

**Package:** `io.casehub.desiredstate.api` (moved from `runtime` during implementation —
ThresholdFaultPolicy has zero runtime dependencies; placing it in runtime forced consumers
to take a compile dep on the full runtime module)

#### ThresholdFaultPolicy

```java
public class ThresholdFaultPolicy implements FaultPolicy {

    private final Set<FaultType> faultTypes;
    private final Set<NodeType> nodeTypes;      // empty = match all
    private final Set<NodeType> ignoreTypes;    // regress guard — never escalate these
    private final int threshold;
    private final EscalationAction action;
    private final ConcurrentHashMap<NodeId, Integer> faultCounts = new ConcurrentHashMap<>();

    private ThresholdFaultPolicy(Builder builder) { ... }

    public static Builder builder() { return new Builder(); }

    @Override
    public List<GraphMutation> onFault(String tenancyId, FaultEvent event,
                                        DesiredStateGraph current, ActualState actual) {
        // 1. Look up node: DesiredNode node = current.nodes().get(event.node())
        // 2. Regress guard: if node != null and node.type() is in ignoreTypes, return empty
        // 3. Filter: event.type() must be in faultTypes
        // 4. Absent guard: if node == null, return empty (faulted node no longer in desired state)
        // 5. Filter: if nodeTypes is non-empty, node.type() must be in nodeTypes
        // 6. Count: faultCounts.merge(event.node(), 1, Integer::sum)
        // 7. Threshold: if count < threshold, return empty
        // 8. Delegate: return action.escalate(tenancyId, event, current, actual)
    }

    public static class Builder {
        public Builder faultTypes(Set<FaultType> faultTypes) { ... }
        public Builder nodeTypes(Set<NodeType> nodeTypes) { ... }
        public Builder ignoreTypes(Set<NodeType> ignoreTypes) { ... }
        public Builder threshold(int threshold) { ... }
        public Builder action(EscalationAction action) { ... }
        public ThresholdFaultPolicy build() { ... }
    }
}
```

**Step ordering rationale:** The regress guard (step 2) runs before the fault type filter
(step 3) so that faults on review nodes are never counted, regardless of fault type. The
absent guard (step 4) runs after the fault type filter because irrelevant fault types should
be filtered cheaply before the node type check. This matches `IoTFaultPolicy`'s existing order.

**Fault count state:** In-memory `ConcurrentHashMap`, not persisted. Resets on restart.
Acceptable because the reconciliation loop re-encounters the same failures and re-counts.

Fault counts accumulate monotonically — there is no reset on node recovery. If a node fails
twice, recovers, then fails once more, its count is 3. This means a previously-failing node
escalates immediately on recurrence. This is intentional for safety: intermittent failures
that cross the threshold warrant human attention regardless of interim recovery. This is the
same behavior as the existing `IoTFaultPolicy` being refactored.

Counts are not evicted when nodes are removed from the desired-state graph. At the expected
scale (10–200 nodes per tenant per ARC42STORIES §L2), memory growth is negligible. Eviction
and persistence are tracked as casehubio/casehub-desiredstate#85.

**Builder validation:** `faultTypes` and `action` are required (NPE on build if missing).
`threshold` defaults to 3. `nodeTypes` defaults to empty (match all). `ignoreTypes` defaults to empty.

#### EscalationAction

```java
@FunctionalInterface
public interface EscalationAction {
    List<GraphMutation> escalate(String tenancyId, FaultEvent event,
                                  DesiredStateGraph current, ActualState actual);

    static EscalationAction addReviewNode(NodeType reviewType, ReviewSpecFactory specFactory) {
        return (tenancyId, event, current, actual) -> {
            NodeId reviewId = NodeId.of("review-" + event.node().value());
            if (current.nodes().containsKey(reviewId)) {
                return List.of();
            }
            return List.of(new GraphMutation.AddNode(
                new DesiredNode(reviewId, reviewType, specFactory.create(event, current), HumanGating.ALL)));
        };
    }
}
```

`EscalationAction.escalate` receives `ActualState` alongside the other `FaultPolicy.onFault`
parameters. `ThresholdFaultPolicy.onFault` already receives `actual` — passing it through costs
nothing and avoids narrowing the interface for a reusable component. Custom actions that need to
inspect infrastructure state (e.g., checking actual K8s pod status before deciding escalation
strategy) can do so without a breaking API change.

`addReviewNode` captures two pieces of boilerplate every consumer would otherwise repeat:
- Duplicate guard (don't add a review node that already exists)
- `HumanGating.ALL` convention for review nodes

#### ReviewSpecFactory

```java
@FunctionalInterface
public interface ReviewSpecFactory {
    NodeSpec create(FaultEvent event, DesiredStateGraph current);
}
```

The factory receives both the `FaultEvent` and the `DesiredStateGraph` so review specs can
include information from the graph — e.g., the faulted node's type (`current.nodes().get(event.node()).type()`)
or its spec details. Since `addReviewNode` already has `current` in scope, passing it through
costs nothing. Without graph access, review specs are limited to information on the `FaultEvent`
alone (node ID, fault type, detail string), which forces operators to manually look up what
failed. Adding `current` later would be a breaking change on this `@FunctionalInterface`.

### API hardening in casehub-desiredstate

`FaultEvent`'s compact constructor validates `node` and `type` but not `detail` — it can be
null. `K8sReviewSpec` and `IoTReviewSpec` both require non-null `reason`, sourced from
`event.detail()`. A provisioner reporting `PROVISION_FAILED` with null detail would produce a
valid `FaultEvent` that NPEs deep in the escalation chain at review spec construction.

Fix at the source: add `Objects.requireNonNull(detail, "FaultEvent.detail must not be null")`
to `FaultEvent`'s compact constructor. A fault without an explanation is incomplete data — it
should fail loud at creation, not silently at escalation time. All existing `FaultEvent`
construction sites in `ReconciliationLoop` provide non-null detail (hardcoded strings for drift
events, `StepOutcome.Failed.reason()` / `StepOutcome.Rejected.reason()` for execution faults).

For defense in depth, also add `Objects.requireNonNull(reason, ...)` to `StepOutcome.Failed`,
`StepOutcome.Rejected`, and `StepOutcome.Skipped` compact constructors. `Failed` and `Rejected`
feed directly into `FaultEvent.detail` via `ReconciliationLoop.faultFeedback()` — a provisioner
returning `Failed(null)` is a bug in the reporter that should surface immediately, not propagate
through the fault pipeline. `Skipped` does not feed into `FaultEvent`, but carries the same
`String reason` field — a skip without an explanation is incomplete data by the same principle.
All three reason-carrying variants get the same treatment. `StepOutcome.Succeeded` has no fields
and no invariants to enforce.

### FaultPolicyEngine interaction

`FaultPolicyEngine.evaluate()` iterates all CDI-discovered `FaultPolicy` beans and merges their
mutations, throwing `ConflictingMutationException` if two policies propose different mutations for
the same `NodeId`. Both `KubernetesFaultPolicy` and `CbrFaultPolicy` are `@ApplicationScoped` and
fire on the same fault events.

No conflicts arise in practice: `CbrFaultPolicy` proposes structural graph mutations derived from
retrieved similar configurations — these target existing node IDs from the graph topology.
`KubernetesFaultPolicy` (via `ThresholdFaultPolicy`) proposes `AddNode` for review nodes with a
distinct ID scheme (`review-{nodeId}`). The CBR adaptation operates on the existing graph, never
generating review-prefixed node IDs.

### Changes in casehub-ops

#### IoTFaultPolicy — refactored to delegate

```java
@ApplicationScoped
public class IoTFaultPolicy implements FaultPolicy {

    private static final NodeType DEVICE_CONFIG = NodeType.of("device-config");
    private static final NodeType IOT_REVIEW    = NodeType.of("iot-review");

    private final ThresholdFaultPolicy delegate = ThresholdFaultPolicy.builder()
            .faultTypes(Set.of(FaultType.PROVISION_FAILED))
            .nodeTypes(Set.of(DEVICE_CONFIG))
            .ignoreTypes(Set.of(IOT_REVIEW))
            .threshold(3)
            .action(EscalationAction.addReviewNode(IOT_REVIEW,
                    (event, current) -> new IoTReviewSpec(event.node(), event.detail())))
            .build();

    @Override
    public List<GraphMutation> onFault(String tenancyId, FaultEvent event,
                                        DesiredStateGraph current, ActualState actual) {
        return delegate.onFault(tenancyId, event, current, actual);
    }
}
```

Behaviour is identical to the current inline implementation. Existing `IoTFaultPolicyTest`
validates the refactor without modification.

#### KubernetesFaultPolicy — new implementation

```java
@ApplicationScoped
public class KubernetesFaultPolicy implements FaultPolicy {

    private static final NodeType K8S_REVIEW = NodeType.of("k8s-review");

    private final ThresholdFaultPolicy delegate = ThresholdFaultPolicy.builder()
            .faultTypes(Set.of(FaultType.PROVISION_FAILED))
            .nodeTypes(Set.of(
                    ApplicationNodeTypes.K8S_NAMESPACE,
                    ApplicationNodeTypes.K8S_DEPLOYMENT,
                    ApplicationNodeTypes.K8S_SERVICE,
                    ApplicationNodeTypes.K8S_INGRESS,
                    ApplicationNodeTypes.K8S_CONFIGMAP))
            .ignoreTypes(Set.of(K8S_REVIEW))
            .threshold(3)
            .action(EscalationAction.addReviewNode(K8S_REVIEW,
                    (event, current) -> new K8sReviewSpec(event.node(), event.detail())))
            .build();

    @Override
    public List<GraphMutation> onFault(String tenancyId, FaultEvent event,
                                        DesiredStateGraph current, ActualState actual) {
        return delegate.onFault(tenancyId, event, current, actual);
    }
}
```

**Fault type:** `PROVISION_FAILED` only. `NODE_DEGRADED` (drift) is handled by the
reconciliation loop's re-provisioning. `DEPROVISION_FAILED` is a cleanup concern.

**Threshold:** 3 (same as IoT). Trivial to change per-domain via the builder.

#### New types

**K8sReviewSpec** — `io.casehub.ops.api.k8s`

```java
public record K8sReviewSpec(NodeId faultedNode, String reason) implements NodeSpec {
    public K8sReviewSpec {
        Objects.requireNonNull(faultedNode, "faultedNode");
        Objects.requireNonNull(reason, "reason");
    }
}
```

Same shape and field names as `IoTReviewSpec`. Carries the faulted node ID and failure reason
for operator review. Placed in `api.k8s` following the convention established by
`IoTReviewSpec` in `api.iot` — `NodeSpec` implementations are API-level contracts.
`HumanGating` is set on the `DesiredNode` (via `addReviewNode`), not on the spec — consistent
with `IoTReviewSpec` which inherits the default `NodeSpec.humanGating() → NONE`.

## Testing

### ThresholdFaultPolicyTest (desiredstate)

Unit tests for the reusable component. Plain JUnit, no CDI:

- Below threshold — returns empty
- At threshold — delegates to action
- Above threshold — delegates again
- Wrong fault type — returns empty
- Wrong node type — returns empty
- Ignore types (regress guard) — returns empty
- Empty nodeTypes — matches all
- Multiple nodes tracked independently
- Node absent from graph — returns empty (null guard)
- Node fails, recovers, fails again — count persists, escalates at cumulative threshold
- Concurrent fault events for same node — ConcurrentHashMap merge correctness

### IoTFaultPolicyTest (casehub-ops) — unchanged

Existing tests validate the refactor. No modifications needed. If these pass,
the extraction preserved behaviour.

### KubernetesFaultPolicyTest (casehub-ops) — new

Mirrors IoTFaultPolicyTest structure:

- Below threshold (1st, 2nd failure) — returns empty
- At threshold (3rd failure) — returns `AddNode` with `k8s-review` type
- Review node already exists — returns empty
- Faulted node is `k8s-review` — returns empty (regress guard)
- Non-PROVISION_FAILED fault type — returns empty
- Non-K8s node type — returns empty

## Cross-Repo Execution Order

1. **casehub-desiredstate:** Add non-null validation to `FaultEvent.detail`,
   `StepOutcome.Failed.reason`, `StepOutcome.Rejected.reason`, and
   `StepOutcome.Skipped.reason`. Create
   `ThresholdFaultPolicy`, `EscalationAction`, `ReviewSpecFactory`, and
   `ThresholdFaultPolicyTest`. Build, test, deploy snapshot.
2. **casehub-ops:** Update desiredstate dependency version. Refactor `IoTFaultPolicy`,
   verify existing tests pass. Create `K8sReviewSpec` in `api/k8s/`. Implement
   `KubernetesFaultPolicy` and add `KubernetesFaultPolicyTest`. Build, test.

## Out of Scope

- Persisted fault counts (restart-resilient counting) — casehubio/casehub-desiredstate#85
- Multi-tier escalation (AI review → human review) — casehubio/casehub-desiredstate#86
- Dependency-aware graph mutations — casehubio/casehub-desiredstate#87
- `NODE_DEGRADED` / `DEPROVISION_FAILED` responses — no graph mutation warranted
- InfraFaultPolicy adoption — casehubio/casehub-ops#64
- DeploymentFaultPolicy evaluation — casehubio/casehub-ops#65
