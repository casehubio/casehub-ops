---
layout: post
title: "Closing the Audit Loop on L05–L08"
date: 2026-10-22
entry_type: note
subtype: diary
projects: [casehubio/casehub-ops]
tags: [testing, audit, reconciliation, desired-state]
---

# Closing the Audit Loop on L05–L08

Issue #171 built the audit test infrastructure — `AuditCapture`, `CapturingTransitionExecutor`, `AuditAssertions`. Three classes that capture `TransitionResult`s and `CloudEvent`s during reconciliation and let you assert on them. Issue #172 is the payoff: wire that infrastructure into the integration tests at Levels 05 through 08.

L08 (fleet observability) was straightforward. It already had a `ReconciliationLoop` with a `cloudEventSink` piping into an ad-hoc `CopyOnWriteArrayList<CloudEvent>`. Swap the list for `AuditCapture`, wrap `SimpleTransitionExecutor` with `CapturingTransitionExecutor`, replace the private helper methods with `AuditAssertions` calls. The test got shorter and more expressive — `AuditAssertions.assertCloudEventEmitted(capture, NODE_FAULTED)` reads better than four lines of stream filtering.

L05–L07 were the interesting problem. These tests deploy via `ClaudonyDeploymentTarget` directly — they call `target.deploy()`, `target.suspend()`, `target.undeploy()` without going through the reconciliation runtime. No `TransitionExecutor`, no `ReconciliationLoop`, no CloudEvents. The audit infrastructure has nothing to intercept.

The question was whether to refactor them to use the full runtime stack or find another way. I kept the existing direct-target tests untouched — they're valuable for validating the deployment target API itself — and added new test methods that run the same scenarios through a `ReconciliationLoop`.

For config drift (L05), the loop starts with the initial graph, then `updateDesired()` swaps in the drifted graph. The reconciliation cycle detects the drift and emits `NODE_DRIFTED`. For mutations (L07), the same pattern — `updateDesired()` with the mutated graph, then assert on `RECONCILIATION_COMPLETED` recording additions and removals.

Lifecycle (L06) was the subtlest. The `ReconciliationLoop` doesn't have explicit suspend/resume commands. Instead, `DesiredNode` has a `targetStatus` field — `ACTIVE` or `SUSPENDED`. To drive suspend through the loop, I built a modified graph where every node's `targetStatus` is `SUSPENDED`, called `updateDesired()`, and let the planner produce suspension steps. The `RECONCILIATION_COMPLETED` event carries `suspensionsCount` and `resumptionsCount` — exactly the fields I needed to assert on. Same pattern for resume: swap `targetStatus` back to `ACTIVE`, reconcile, check the counts.

The `awaitReconciliation` helper ended up duplicated across three test classes. Extracting it to `TopologyTestBase` would have required heavy runtime imports in the base class. The duplication is a conscious trade-off — three identical 10-line methods are easier to maintain than polluting the base class with dependencies most tests don't need.

What this opens up: L05–L07 now have a second testing dimension. The direct-target tests verify that `ClaudonyDeploymentTarget` works correctly at the API level. The audit tests verify that when those operations run through the reconciliation runtime, the correct events flow through the system. Both matter — API correctness and system-level observability are different failure modes.
