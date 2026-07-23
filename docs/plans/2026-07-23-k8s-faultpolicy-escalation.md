# ThresholdFaultPolicy Escalation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #45 — feat: K8s-aware FaultPolicy responses — graph mutations for persistent failures
**Issue group:** #45

**Goal:** Extract a reusable `ThresholdFaultPolicy` into casehub-desiredstate, harden the FaultEvent API,
refactor IoTFaultPolicy to delegate, and implement KubernetesFaultPolicy with K8s-aware escalation.

**Architecture:** Cross-repo. ThresholdFaultPolicy + EscalationAction + ReviewSpecFactory live in
`io.casehub.desiredstate.runtime` (desiredstate repo). Domain fault policies in casehub-ops delegate
to ThresholdFaultPolicy via builder configuration — composition, no inheritance.

**Tech Stack:** Java 22, JUnit 5, AssertJ, casehub-desiredstate 0.2-SNAPSHOT, casehub-ops

## Global Constraints

- All new types in desiredstate go in the `runtime` module (`io.casehub.desiredstate.runtime`)
  except `EscalationAction` and `ReviewSpecFactory` which go in the `api` module
  (`io.casehub.desiredstate.api`) since they are consumer-facing interfaces
- `K8sReviewSpec` goes in `io.casehub.ops.api.k8s` following `IoTReviewSpec` in `io.casehub.ops.api.iot`
- No CDI annotations on ThresholdFaultPolicy — it's instantiated by consumers via builder
- Tests use `DefaultDesiredStateGraphFactory` for graph construction
- Tests use AssertJ (`assertThat`) matching existing test style in both repos
- Use IntelliJ MCP (`ide_create_file`, `ide_edit_member`, `ide_insert_member`, `ide_replace_member`)
  for all source file operations. Use `ide_refactor_rename` for renames. Never use bash for code files.
- desiredstate project_path: `/Users/mdproctor/claude/casehub/desiredstate`
- ops project_path: `/Users/mdproctor/claude/casehub/ops`

---

### Task 1: API hardening — FaultEvent and StepOutcome null validation (desiredstate)

**Files:**
- Modify: `api/src/main/java/io/casehub/desiredstate/api/FaultEvent.java`
- Modify: `api/src/main/java/io/casehub/desiredstate/api/StepOutcome.java`
- Test: `api/src/test/java/io/casehub/desiredstate/api/FaultEventValidationTest.java`
- Test: `api/src/test/java/io/casehub/desiredstate/api/StepOutcomeValidationTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `FaultEvent` now rejects null `detail`; `StepOutcome.Failed`, `.Rejected`, `.Skipped`
  now reject null `reason`. All existing construction sites already pass non-null values.

- [ ] **Step 1: Write failing test for FaultEvent null detail**

Create `api/src/test/java/io/casehub/desiredstate/api/FaultEventValidationTest.java`:

```java
package io.casehub.desiredstate.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaultEventValidationTest {

    @Test
    void nullDetail_throwsNPE() {
        assertThatThrownBy(() -> new FaultEvent(NodeId.of("n1"), FaultType.PROVISION_FAILED, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("detail");
    }

    @Test
    void validConstruction_succeeds() {
        var event = new FaultEvent(NodeId.of("n1"), FaultType.PROVISION_FAILED, "reason");
        assertThat(event.detail()).isEqualTo("reason");
    }
}
```

Add missing import: `import static org.assertj.core.api.Assertions.assertThat;`

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl api test -Dtest=FaultEventValidationTest -o --batch-mode` in the desiredstate repo.
Expected: `nullDetail_throwsNPE` FAILS (no validation on detail yet).

- [ ] **Step 3: Add null validation to FaultEvent.detail**

Use `ide_edit_member` to replace the `FaultEvent` compact constructor. The full record becomes:

```java
public record FaultEvent(NodeId node, FaultType type, String detail) {
    public FaultEvent {
        Objects.requireNonNull(node, "FaultEvent.node must not be null");
        Objects.requireNonNull(type, "FaultEvent.type must not be null");
        Objects.requireNonNull(detail, "FaultEvent.detail must not be null");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl api test -Dtest=FaultEventValidationTest -o --batch-mode`
Expected: PASS

- [ ] **Step 5: Write failing tests for StepOutcome null reason**

Create `api/src/test/java/io/casehub/desiredstate/api/StepOutcomeValidationTest.java`:

```java
package io.casehub.desiredstate.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepOutcomeValidationTest {

    @Test
    void failed_nullReason_throwsNPE() {
        assertThatThrownBy(() -> new StepOutcome.Failed(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void rejected_nullReason_throwsNPE() {
        assertThatThrownBy(() -> new StepOutcome.Rejected(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void skipped_nullReason_throwsNPE() {
        assertThatThrownBy(() -> new StepOutcome.Skipped(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn -pl api test -Dtest=StepOutcomeValidationTest -o --batch-mode`
Expected: All 3 FAIL.

- [ ] **Step 7: Add null validation to StepOutcome records**

Use `ide_edit_member` on `StepOutcome.java`. Replace the sealed interface with:

```java
public sealed interface StepOutcome {
    record Succeeded() implements StepOutcome {}
    record Failed(String reason) implements StepOutcome {
        public Failed { java.util.Objects.requireNonNull(reason, "StepOutcome.Failed.reason must not be null"); }
    }
    record Skipped(String reason) implements StepOutcome {
        public Skipped { java.util.Objects.requireNonNull(reason, "StepOutcome.Skipped.reason must not be null"); }
    }
    record Rejected(String reason) implements StepOutcome {
        public Rejected { java.util.Objects.requireNonNull(reason, "StepOutcome.Rejected.reason must not be null"); }
    }
}
```

- [ ] **Step 8: Run all tests to verify nothing breaks**

Run: `mvn -pl api test -o --batch-mode`
Expected: All pass. Existing construction sites already pass non-null values.

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/desiredstate add api/src/main/java/io/casehub/desiredstate/api/FaultEvent.java api/src/main/java/io/casehub/desiredstate/api/StepOutcome.java api/src/test/java/io/casehub/desiredstate/api/FaultEventValidationTest.java api/src/test/java/io/casehub/desiredstate/api/StepOutcomeValidationTest.java
git -C /Users/mdproctor/claude/casehub/desiredstate commit -m "fix: add null validation to FaultEvent.detail and StepOutcome reason fields

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: ThresholdFaultPolicy + EscalationAction + ReviewSpecFactory (desiredstate)

**Files:**
- Create: `api/src/main/java/io/casehub/desiredstate/api/EscalationAction.java`
- Create: `api/src/main/java/io/casehub/desiredstate/api/ReviewSpecFactory.java`
- Create: `runtime/src/main/java/io/casehub/desiredstate/runtime/ThresholdFaultPolicy.java`
- Test: `runtime/src/test/java/io/casehub/desiredstate/runtime/ThresholdFaultPolicyTest.java`

**Interfaces:**
- Consumes: `FaultPolicy`, `FaultEvent`, `FaultType`, `GraphMutation`, `DesiredStateGraph`,
  `ActualState`, `DesiredNode`, `NodeId`, `NodeType`, `NodeSpec`, `HumanGating` (all existing API types)
- Produces:
  - `EscalationAction` — `@FunctionalInterface`, method: `List<GraphMutation> escalate(String tenancyId, FaultEvent event, DesiredStateGraph current, ActualState actual)`
  - `EscalationAction.addReviewNode(NodeType reviewType, ReviewSpecFactory specFactory)` — static factory
  - `ReviewSpecFactory` — `@FunctionalInterface`, method: `NodeSpec create(FaultEvent event, DesiredStateGraph current)`
  - `ThresholdFaultPolicy` — `implements FaultPolicy`, builder: `.faultTypes(Set<FaultType>)`, `.nodeTypes(Set<NodeType>)`, `.ignoreTypes(Set<NodeType>)`, `.threshold(int)`, `.action(EscalationAction)`, `.build()`

- [ ] **Step 1: Create EscalationAction interface**

Use `ide_create_file` in the desiredstate project:

File: `api/src/main/java/io/casehub/desiredstate/api/EscalationAction.java`

```java
package io.casehub.desiredstate.api;

import java.util.List;

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
                    new DesiredNode(reviewId, reviewType,
                                    specFactory.create(event, current), HumanGating.ALL)));
        };
    }
}
```

- [ ] **Step 2: Create ReviewSpecFactory interface**

Use `ide_create_file`:

File: `api/src/main/java/io/casehub/desiredstate/api/ReviewSpecFactory.java`

```java
package io.casehub.desiredstate.api;

@FunctionalInterface
public interface ReviewSpecFactory {
    NodeSpec create(FaultEvent event, DesiredStateGraph current);
}
```

- [ ] **Step 3: Write failing tests for ThresholdFaultPolicy**

Use `ide_create_file`:

File: `runtime/src/test/java/io/casehub/desiredstate/runtime/ThresholdFaultPolicyTest.java`

```java
package io.casehub.desiredstate.runtime;

import io.casehub.desiredstate.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThresholdFaultPolicyTest {

    private static final NodeType TARGET   = NodeType.of("target");
    private static final NodeType REVIEW   = NodeType.of("review");
    private static final NodeType OTHER    = NodeType.of("other");
    private static final ActualState EMPTY_ACTUAL = new ActualState(Map.of());

    private DefaultDesiredStateGraphFactory graphFactory;

    @BeforeEach
    void setUp() {
        graphFactory = new DefaultDesiredStateGraphFactory();
    }

    record TestReviewSpec(NodeId faultedNode, String reason) implements NodeSpec {}

    private ThresholdFaultPolicy policyWithThreshold(int threshold) {
        return ThresholdFaultPolicy.builder()
                .faultTypes(Set.of(FaultType.PROVISION_FAILED))
                .nodeTypes(Set.of(TARGET))
                .ignoreTypes(Set.of(REVIEW))
                .threshold(threshold)
                .action(EscalationAction.addReviewNode(REVIEW,
                        (event, current) -> new TestReviewSpec(event.node(), event.detail())))
                .build();
    }

    private DesiredStateGraph graphWith(String nodeId, NodeType type) {
        return graphFactory.of(
                List.of(new DesiredNode(NodeId.of(nodeId), type, new TestReviewSpec(NodeId.of(nodeId), "x"), HumanGating.NONE)),
                List.of());
    }

    @Test
    void belowThreshold_returnsEmpty() {
        var policy = policyWithThreshold(3);
        var graph = graphWith("n1", TARGET);
        var event = new FaultEvent(NodeId.of("n1"), FaultType.PROVISION_FAILED, "fail");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
    }

    @Test
    void atThreshold_delegatesToAction() {
        var policy = policyWithThreshold(3);
        var graph = graphWith("n1", TARGET);
        var event = new FaultEvent(NodeId.of("n1"), FaultType.PROVISION_FAILED, "fail");

        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        var mutations = policy.onFault("t1", event, graph, EMPTY_ACTUAL);

        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(GraphMutation.AddNode.class);
        var addNode = (GraphMutation.AddNode) mutations.getFirst();
        assertThat(addNode.node().id()).isEqualTo(NodeId.of("review-n1"));
        assertThat(addNode.node().type()).isEqualTo(REVIEW);
        assertThat(addNode.node().humanGating()).isEqualTo(HumanGating.ALL);
    }

    @Test
    void aboveThreshold_delegatesAgain() {
        var policy = policyWithThreshold(2);
        var graph = graphWith("n1", TARGET);
        var event = new FaultEvent(NodeId.of("n1"), FaultType.PROVISION_FAILED, "fail");

        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).hasSize(1);
        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).hasSize(1);
    }

    @Test
    void wrongFaultType_returnsEmpty() {
        var policy = policyWithThreshold(1);
        var graph = graphWith("n1", TARGET);
        var event = new FaultEvent(NodeId.of("n1"), FaultType.NODE_DEGRADED, "drift");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
    }

    @Test
    void wrongNodeType_returnsEmpty() {
        var policy = policyWithThreshold(1);
        var graph = graphWith("n1", OTHER);
        var event = new FaultEvent(NodeId.of("n1"), FaultType.PROVISION_FAILED, "fail");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
    }

    @Test
    void ignoreTypes_regressGuard_returnsEmpty() {
        var policy = policyWithThreshold(1);
        var graph = graphWith("review-n1", REVIEW);
        var event = new FaultEvent(NodeId.of("review-n1"), FaultType.PROVISION_FAILED, "fail");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
    }

    @Test
    void emptyNodeTypes_matchesAll() {
        var policy = ThresholdFaultPolicy.builder()
                .faultTypes(Set.of(FaultType.PROVISION_FAILED))
                .threshold(1)
                .action(EscalationAction.addReviewNode(REVIEW,
                        (event, current) -> new TestReviewSpec(event.node(), event.detail())))
                .build();
        var graph = graphWith("n1", OTHER);
        var event = new FaultEvent(NodeId.of("n1"), FaultType.PROVISION_FAILED, "fail");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).hasSize(1);
    }

    @Test
    void multipleNodesTrackedIndependently() {
        var policy = policyWithThreshold(2);
        var node1 = new DesiredNode(NodeId.of("a"), TARGET, new TestReviewSpec(NodeId.of("a"), "x"), HumanGating.NONE);
        var node2 = new DesiredNode(NodeId.of("b"), TARGET, new TestReviewSpec(NodeId.of("b"), "x"), HumanGating.NONE);
        var graph = graphFactory.of(List.of(node1, node2), List.of());

        var eventA = new FaultEvent(NodeId.of("a"), FaultType.PROVISION_FAILED, "fail");
        var eventB = new FaultEvent(NodeId.of("b"), FaultType.PROVISION_FAILED, "fail");

        policy.onFault("t1", eventA, graph, EMPTY_ACTUAL);
        assertThat(policy.onFault("t1", eventB, graph, EMPTY_ACTUAL)).isEmpty();
        assertThat(policy.onFault("t1", eventA, graph, EMPTY_ACTUAL)).hasSize(1);
    }

    @Test
    void nodeAbsentFromGraph_returnsEmpty() {
        var policy = policyWithThreshold(1);
        var graph = graphFactory.of(List.of(), List.of());
        var event = new FaultEvent(NodeId.of("gone"), FaultType.PROVISION_FAILED, "fail");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
    }

    @Test
    void faultCountPersistsAcrossRecovery() {
        var policy = policyWithThreshold(3);
        var graph = graphWith("n1", TARGET);
        var event = new FaultEvent(NodeId.of("n1"), FaultType.PROVISION_FAILED, "fail");

        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        // node "recovers" — count is still 2
        var mutations = policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        assertThat(mutations).hasSize(1);
    }

    @Test
    void builderRequiresFaultTypes() {
        assertThatThrownBy(() -> ThresholdFaultPolicy.builder()
                .threshold(1)
                .action((t, e, g, a) -> List.of())
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderRequiresAction() {
        assertThatThrownBy(() -> ThresholdFaultPolicy.builder()
                .faultTypes(Set.of(FaultType.PROVISION_FAILED))
                .threshold(1)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addReviewNode_duplicateGuard() {
        var policy = policyWithThreshold(1);
        var targetNode = new DesiredNode(NodeId.of("n1"), TARGET, new TestReviewSpec(NodeId.of("n1"), "x"), HumanGating.NONE);
        var reviewNode = new DesiredNode(NodeId.of("review-n1"), REVIEW, new TestReviewSpec(NodeId.of("n1"), "prior"), HumanGating.ALL);
        var graph = graphFactory.of(List.of(targetNode, reviewNode), List.of());
        var event = new FaultEvent(NodeId.of("n1"), FaultType.PROVISION_FAILED, "fail");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -pl runtime test -Dtest=ThresholdFaultPolicyTest -o --batch-mode`
Expected: FAIL — `ThresholdFaultPolicy` class does not exist.

- [ ] **Step 5: Implement ThresholdFaultPolicy**

Use `ide_create_file`:

File: `runtime/src/main/java/io/casehub/desiredstate/runtime/ThresholdFaultPolicy.java`

```java
package io.casehub.desiredstate.runtime;

import io.casehub.desiredstate.api.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ThresholdFaultPolicy implements FaultPolicy {

    private final Set<FaultType> faultTypes;
    private final Set<NodeType> nodeTypes;
    private final Set<NodeType> ignoreTypes;
    private final int threshold;
    private final EscalationAction action;
    private final ConcurrentHashMap<NodeId, Integer> faultCounts = new ConcurrentHashMap<>();

    private ThresholdFaultPolicy(Builder builder) {
        this.faultTypes = Set.copyOf(builder.faultTypes);
        this.nodeTypes = builder.nodeTypes == null ? Set.of() : Set.copyOf(builder.nodeTypes);
        this.ignoreTypes = builder.ignoreTypes == null ? Set.of() : Set.copyOf(builder.ignoreTypes);
        this.threshold = builder.threshold;
        this.action = builder.action;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<GraphMutation> onFault(String tenancyId, FaultEvent event,
                                        DesiredStateGraph current, ActualState actual) {
        DesiredNode node = current.nodes().get(event.node());

        if (node != null && ignoreTypes.contains(node.type())) {
            return List.of();
        }

        if (!faultTypes.contains(event.type())) {
            return List.of();
        }

        if (node == null) {
            return List.of();
        }

        if (!nodeTypes.isEmpty() && !nodeTypes.contains(node.type())) {
            return List.of();
        }

        int count = faultCounts.merge(event.node(), 1, Integer::sum);
        if (count < threshold) {
            return List.of();
        }

        return action.escalate(tenancyId, event, current, actual);
    }

    public static class Builder {
        private Set<FaultType> faultTypes;
        private Set<NodeType> nodeTypes;
        private Set<NodeType> ignoreTypes;
        private int threshold = 3;
        private EscalationAction action;

        public Builder faultTypes(Set<FaultType> faultTypes) { this.faultTypes = faultTypes; return this; }
        public Builder nodeTypes(Set<NodeType> nodeTypes) { this.nodeTypes = nodeTypes; return this; }
        public Builder ignoreTypes(Set<NodeType> ignoreTypes) { this.ignoreTypes = ignoreTypes; return this; }
        public Builder threshold(int threshold) { this.threshold = threshold; return this; }
        public Builder action(EscalationAction action) { this.action = action; return this; }

        public ThresholdFaultPolicy build() {
            Objects.requireNonNull(faultTypes, "faultTypes is required");
            Objects.requireNonNull(action, "action is required");
            return new ThresholdFaultPolicy(this);
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -pl runtime test -Dtest=ThresholdFaultPolicyTest -o --batch-mode`
Expected: All PASS.

- [ ] **Step 7: Run full desiredstate build**

Run: `mvn --batch-mode install` in the desiredstate repo.
Expected: All modules pass. This also installs the snapshot to local .m2.

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/desiredstate add api/src/main/java/io/casehub/desiredstate/api/EscalationAction.java api/src/main/java/io/casehub/desiredstate/api/ReviewSpecFactory.java runtime/src/main/java/io/casehub/desiredstate/runtime/ThresholdFaultPolicy.java runtime/src/test/java/io/casehub/desiredstate/runtime/ThresholdFaultPolicyTest.java
git -C /Users/mdproctor/claude/casehub/desiredstate commit -m "feat: ThresholdFaultPolicy — reusable count-based fault escalation

Composition-based FaultPolicy implementation with configurable fault type
filter, node type filter, regress guard, threshold, and escalation action.
EscalationAction.addReviewNode provides the common review-node pattern.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Refactor IoTFaultPolicy to delegate (casehub-ops)

**Files:**
- Modify: `iot/src/main/java/io/casehub/ops/iot/IoTFaultPolicy.java`
- Unchanged: `iot/src/test/java/io/casehub/ops/iot/IoTFaultPolicyTest.java` (validates refactor)

**Interfaces:**
- Consumes: `ThresholdFaultPolicy.builder()`, `EscalationAction.addReviewNode()` from Task 2
- Produces: Identical behavior to current IoTFaultPolicy (verified by existing test suite)

- [ ] **Step 1: Run existing IoTFaultPolicyTest to establish baseline**

Run: `mvn -pl iot test -Dtest=IoTFaultPolicyTest -o --batch-mode`
Expected: All PASS. This is the baseline — the refactor must not change this.

- [ ] **Step 2: Replace IoTFaultPolicy implementation**

Use `ide_edit_member` to replace the full class body. The complete file becomes:

```java
package io.casehub.ops.iot;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.ThresholdFaultPolicy;
import io.casehub.ops.api.iot.IoTReviewSpec;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class IoTFaultPolicy implements FaultPolicy {

    static final int ESCALATION_THRESHOLD = 3;

    private static final NodeType DEVICE_CONFIG = NodeType.of("device-config");
    private static final NodeType IOT_REVIEW    = NodeType.of("iot-review");

    private final ThresholdFaultPolicy delegate = ThresholdFaultPolicy.builder()
            .faultTypes(Set.of(FaultType.PROVISION_FAILED))
            .nodeTypes(Set.of(DEVICE_CONFIG))
            .ignoreTypes(Set.of(IOT_REVIEW))
            .threshold(ESCALATION_THRESHOLD)
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

- [ ] **Step 3: Run IoTFaultPolicyTest to verify refactor preserved behavior**

Run: `mvn -pl iot test -Dtest=IoTFaultPolicyTest -o --batch-mode`
Expected: All PASS — identical to baseline. If any test fails, the refactor broke behavior.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/ops add iot/src/main/java/io/casehub/ops/iot/IoTFaultPolicy.java
git -C /Users/mdproctor/claude/casehub/ops commit -m "refactor(#45): IoTFaultPolicy delegates to ThresholdFaultPolicy

Behavior unchanged — existing IoTFaultPolicyTest validates the extraction.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: K8sReviewSpec + KubernetesFaultPolicy + tests (casehub-ops)

**Files:**
- Create: `api/src/main/java/io/casehub/ops/api/k8s/K8sReviewSpec.java`
- Modify: `app/src/main/java/io/casehub/ops/app/k8s/KubernetesFaultPolicy.java`
- Create: `app/src/test/java/io/casehub/ops/app/k8s/KubernetesFaultPolicyTest.java`

**Interfaces:**
- Consumes: `ThresholdFaultPolicy.builder()`, `EscalationAction.addReviewNode()` from Task 2,
  `ApplicationNodeTypes.K8S_*` constants
- Produces: `K8sReviewSpec(NodeId faultedNode, String reason) implements NodeSpec`,
  `KubernetesFaultPolicy implements FaultPolicy` — escalates K8s PROVISION_FAILED at threshold 3

- [ ] **Step 1: Create K8sReviewSpec**

Use `ide_create_file` in the ops project:

File: `api/src/main/java/io/casehub/ops/api/k8s/K8sReviewSpec.java`

```java
package io.casehub.ops.api.k8s;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;

import java.util.Objects;

public record K8sReviewSpec(NodeId faultedNode, String reason) implements NodeSpec {
    public K8sReviewSpec {
        Objects.requireNonNull(faultedNode, "faultedNode");
        Objects.requireNonNull(reason, "reason");
    }
}
```

- [ ] **Step 2: Write failing KubernetesFaultPolicyTest**

Use `ide_create_file`:

File: `app/src/test/java/io/casehub/ops/app/k8s/KubernetesFaultPolicyTest.java`

```java
package io.casehub.ops.app.k8s;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.infra.InfraDesiredNodeSpec;
import io.casehub.ops.api.infra.InfraNodeSpec;
import io.casehub.ops.api.k8s.K8sReviewSpec;
import io.casehub.ops.app.goal.ApplicationNodeTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesFaultPolicyTest {

    private static final NodeType K8S_REVIEW = NodeType.of("k8s-review");
    private static final ActualState EMPTY_ACTUAL = new ActualState(Map.of());

    private final DefaultDesiredStateGraphFactory graphFactory = new DefaultDesiredStateGraphFactory();
    private KubernetesFaultPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new KubernetesFaultPolicy();
    }

    @Test
    void provisionFailed_belowThreshold_returnsEmpty() {
        var graph = graphWithK8sNode("deploy-1", ApplicationNodeTypes.K8S_DEPLOYMENT);
        var event = new FaultEvent(NodeId.of("deploy-1"), FaultType.PROVISION_FAILED, "image pull failed");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
    }

    @Test
    void provisionFailed_atThreshold_addsReviewNode() {
        var graph = graphWithK8sNode("deploy-1", ApplicationNodeTypes.K8S_DEPLOYMENT);
        var event = new FaultEvent(NodeId.of("deploy-1"), FaultType.PROVISION_FAILED, "image pull failed");

        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        var mutations = policy.onFault("t1", event, graph, EMPTY_ACTUAL);

        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(GraphMutation.AddNode.class);

        var addNode = (GraphMutation.AddNode) mutations.getFirst();
        assertThat(addNode.node().id()).isEqualTo(NodeId.of("review-deploy-1"));
        assertThat(addNode.node().type()).isEqualTo(K8S_REVIEW);
        assertThat(addNode.node().humanGating()).isEqualTo(HumanGating.ALL);
        assertThat(addNode.node().spec()).isInstanceOf(K8sReviewSpec.class);

        var spec = (K8sReviewSpec) addNode.node().spec();
        assertThat(spec.faultedNode()).isEqualTo(NodeId.of("deploy-1"));
        assertThat(spec.reason()).isEqualTo("image pull failed");
    }

    @Test
    void provisionFailed_reviewAlreadyExists_returnsEmpty() {
        var deployNode = new DesiredNode(NodeId.of("deploy-1"), ApplicationNodeTypes.K8S_DEPLOYMENT,
                                         testSpec(), HumanGating.NONE);
        var reviewNode = new DesiredNode(NodeId.of("review-deploy-1"), K8S_REVIEW,
                                         new K8sReviewSpec(NodeId.of("deploy-1"), "prior"), HumanGating.ALL);
        var graph = graphFactory.of(List.of(deployNode, reviewNode), List.of());
        var event = new FaultEvent(NodeId.of("deploy-1"), FaultType.PROVISION_FAILED, "still failing");

        for (int i = 0; i < 10; i++) {
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        }
    }

    @Test
    void provisionFailed_onReviewNode_returnsEmpty() {
        var reviewNode = new DesiredNode(NodeId.of("review-deploy-1"), K8S_REVIEW,
                                         new K8sReviewSpec(NodeId.of("deploy-1"), "test"), HumanGating.ALL);
        var graph = graphFactory.of(List.of(reviewNode), List.of());
        var event = new FaultEvent(NodeId.of("review-deploy-1"), FaultType.PROVISION_FAILED, "failed");

        for (int i = 0; i < 10; i++) {
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        }
    }

    @Test
    void nonProvisionFaultType_returnsEmpty() {
        var graph = graphWithK8sNode("deploy-1", ApplicationNodeTypes.K8S_DEPLOYMENT);
        for (FaultType type : List.of(FaultType.NODE_DEGRADED, FaultType.NODE_DESTROYED,
                                      FaultType.DEPROVISION_FAILED, FaultType.DEPENDENCY_UNAVAILABLE,
                                      FaultType.HUMAN_NODE_TIMEOUT, FaultType.APPROVAL_REJECTED)) {
            var event = new FaultEvent(NodeId.of("deploy-1"), type, "test");
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL))
                    .as("FaultType %s should return empty", type)
                    .isEmpty();
        }
    }

    @Test
    void nonK8sNodeType_returnsEmpty() {
        var otherNode = new DesiredNode(NodeId.of("other-1"), NodeType.of("something-else"),
                                        testSpec(), HumanGating.NONE);
        var graph = graphFactory.of(List.of(otherNode), List.of());
        var event = new FaultEvent(NodeId.of("other-1"), FaultType.PROVISION_FAILED, "failed");

        for (int i = 0; i < 10; i++) {
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        }
    }

    @Test
    void allK8sNodeTypes_escalate() {
        for (NodeType k8sType : List.of(ApplicationNodeTypes.K8S_NAMESPACE,
                                         ApplicationNodeTypes.K8S_DEPLOYMENT,
                                         ApplicationNodeTypes.K8S_SERVICE,
                                         ApplicationNodeTypes.K8S_INGRESS,
                                         ApplicationNodeTypes.K8S_CONFIGMAP)) {
            var freshPolicy = new KubernetesFaultPolicy();
            var node = new DesiredNode(NodeId.of("res-1"), k8sType, testSpec(), HumanGating.NONE);
            var graph = graphFactory.of(List.of(node), List.of());
            var event = new FaultEvent(NodeId.of("res-1"), FaultType.PROVISION_FAILED, "fail");

            freshPolicy.onFault("t1", event, graph, EMPTY_ACTUAL);
            freshPolicy.onFault("t1", event, graph, EMPTY_ACTUAL);
            assertThat(freshPolicy.onFault("t1", event, graph, EMPTY_ACTUAL))
                    .as("K8s node type %s should escalate at threshold", k8sType)
                    .hasSize(1);
        }
    }

    private DesiredStateGraph graphWithK8sNode(String nodeId, NodeType type) {
        var node = new DesiredNode(NodeId.of(nodeId), type, testSpec(), HumanGating.NONE);
        return graphFactory.of(List.of(node), List.of());
    }

    private NodeSpec testSpec() {
        return new NodeSpec() {};
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -pl app test -Dtest=KubernetesFaultPolicyTest -o --batch-mode`
Expected: FAIL — KubernetesFaultPolicy still returns `List.of()`.

- [ ] **Step 4: Implement KubernetesFaultPolicy**

Use `ide_edit_member` to replace the full `KubernetesFaultPolicy` class. The complete file:

```java
package io.casehub.ops.app.k8s;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.ThresholdFaultPolicy;
import io.casehub.ops.api.k8s.K8sReviewSpec;
import io.casehub.ops.app.goal.ApplicationNodeTypes;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Set;

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

- [ ] **Step 5: Run KubernetesFaultPolicyTest to verify it passes**

Run: `mvn -pl app test -Dtest=KubernetesFaultPolicyTest -o --batch-mode`
Expected: All PASS.

- [ ] **Step 6: Run full ops build**

Run: `mvn --batch-mode -o install` in the ops repo.
Expected: All modules pass — including IoTFaultPolicyTest from Task 3.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/ops add api/src/main/java/io/casehub/ops/api/k8s/K8sReviewSpec.java app/src/main/java/io/casehub/ops/app/k8s/KubernetesFaultPolicy.java app/src/test/java/io/casehub/ops/app/k8s/KubernetesFaultPolicyTest.java
git -C /Users/mdproctor/claude/casehub/ops commit -m "feat(#45): K8s-aware FaultPolicy with threshold escalation

KubernetesFaultPolicy delegates to ThresholdFaultPolicy — escalates
PROVISION_FAILED at threshold 3 by adding a k8s-review human node.
K8sReviewSpec follows IoTReviewSpec convention in api.k8s.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```
