# Incident Response Child Case — Design Spec

Issue: #34
Epic: #29 (Service lifecycle management)
Branch: `issue-34-incident-response-child-case`

## Summary

Replace the `StubChildCaseDescriptor` for `ops:incident-response` with a full
`IncidentResponseCaseDescriptor` implementing a three-phase worker chain: assess
incident → remediate (restart/rollback/scale) → verify convergence, with an
escalation branch for incidents that can't be auto-remediated.

Follows the single-attempt + escalate model established by
`DriftRemediationCaseDescriptor`. All remediation actions flow through the
desired-state model — the reconciliation loop handles actual K8s mutations.

## Case Definition

**Case identity:** `ops:incident-response` v1.0

**Entry points (unchanged — bindings already exist):**

| Source | Binding | Trigger | Input mapping |
|--------|---------|---------|---------------|
| `ApplicationCaseDescriptor` | `on-incident-detected` | `.incidentDetected` | `.incidentData` |
| `ServiceCaseDescriptor` | `health:incident-response` | `.health.serviceDown` | `.health.incidentData` |

### Capabilities

| Capability | Purpose |
|------------|---------|
| `assess-incident` | Classify incident type and decide remediation action |
| `remediate-incident` | Execute the chosen remediation via desired-state |
| `verify-remediation` | Track node convergence to confirm resolution |
| `escalate-incident` | Produce summary for human review |

### Workers

| Worker | Capability | Input type | Output |
|--------|-----------|------------|--------|
| `incident-assess-worker` | `assess-incident` | `Map` (raw incident data) | `.incidentAssessment` or `.escalationRequired` |
| `incident-remediate-worker` | `remediate-incident` | `Map` (assessment) | `.remediationExecuted` |
| `incident-verify-worker` | `verify-remediation` | `Map` (execution result) | (registers with `NodeConvergenceTracker`) |
| `incident-escalate-worker` | `escalate-incident` | `Map` (assessment + diagnostics) | `.incidentStatus = "escalated"` |

### Bindings (context change chaining)

| Binding name | Trigger | Target capability |
|-------------|---------|-------------------|
| `on-assessment-complete` | `.incidentAssessment` | `remediate-incident` |
| `on-remediation-executed` | `.remediationExecuted` | `verify-remediation` |
| `on-escalation-required` | `.escalationRequired` | `escalate-incident` |

### Completion

```
.incidentStatus == "resolved" || .incidentStatus == "escalated"
```

`NodeConvergenceTracker` signals `.incidentStatus = "resolved"` when all affected
nodes recover. The escalate worker writes `.incidentStatus = "escalated"`.

**Worker failure handling:** The remediate worker catches lifecycle service
exceptions and writes `.escalationRequired` (with the error as detail) rather than
returning `WorkerResult.failed()`. This ensures the case always reaches a terminal
state via the escalation path. Only input validation failures (null/missing fields
in the assess worker) use `WorkerResult.failed()` — those represent malformed
signals, not operational failures.

### Dependencies

`IncidentResponseCaseDescriptor.build(ApplicationLifecycleService, NodeConvergenceTracker)`
— same injection pattern as `ScalingEventCaseDescriptor`.

## Input Data Shape

Both entry points provide the same contract:

```json
{
  "serviceId":      "order-api",
  "applicationId":  "uuid-string",
  "tenancyId":      "tenant-1",
  "incidentType":   "SERVICE_DOWN",
  "detectedAt":     "2026-08-03T14:30:00Z",
  "diagnostics": {
    "podStatus":    "CrashLoopBackOff",
    "restartCount": 5,
    "lastExitCode": 137
  }
}
```

**Required fields:** `serviceId`, `applicationId`, `tenancyId`, `incidentType`
**Optional fields:** `detectedAt`, `diagnostics`

### Incident types

| `incidentType` | Meaning |
|----------------|---------|
| `SERVICE_DOWN` | Service unresponsive |
| `DEGRADED` | Partial failure, reduced capacity |
| `CRASH_LOOP` | Pods restarting repeatedly |
| `RESOURCE_PRESSURE` | CPU/memory limits causing degradation |

## Assessment Logic

The assess worker validates input and maps incident type to remediation action:

| Incident Type | Remediation Action | Rationale |
|---|---|---|
| `SERVICE_DOWN` | `restart` | Service unresponsive — restart pods to recover |
| `DEGRADED` | `restart` | Partial failure — restart to clear bad state |
| `CRASH_LOOP` | `rollback` | Pods keep crashing — likely bad deployment, revert |
| `RESOURCE_PRESSURE` | `scale` | Capacity-related — add replicas |
| Unknown / null | `escalate` | Can't auto-remediate safely |

**Validation failures** (missing serviceId, applicationId, tenancyId) →
`WorkerResult.failed(...)`.

**Assessment output** (written to `.incidentAssessment`):

```json
{
  "action":        "restart",
  "severity":      "critical",
  "serviceId":     "order-api",
  "applicationId": "...",
  "tenancyId":     "...",
  "incidentType":  "SERVICE_DOWN",
  "reason":        "Service down — attempting restart"
}
```

If `action == "escalate"`, writes to `.escalationRequired` instead — the
escalation binding fires, skipping remediation entirely.

## Remediation Logic

The remediate worker reads `.incidentAssessment` and dispatches to
`ApplicationLifecycleService`:

| Action | Method | Effect |
|--------|--------|--------|
| `restart` | `restartService(appId, serviceId, tenancyId)` | Increments generation counter on service nodes, forcing re-provision |
| `rollback` | `rollbackService(appId, serviceId, tenancyId)` | Reverts service image to previous deployment record |
| `scale` | `updateServiceReplicas(appId, serviceId, currentReplicas + 1, tenancyId)` | Looks up current replica count from the Application entity, adds one replica |

All three methods return `Set<String>` of affected node IDs.

**Output** (written to `.remediationExecuted`):

```json
{
  "action":          "restart",
  "serviceId":       "order-api",
  "affectedNodeIds": ["node-1", "node-2"],
  "previousState":   {}
}
```

If the lifecycle service throws (application/service not found), the worker
writes `.escalationRequired` with the error detail — routing to the escalation
path instead of returning `WorkerResult.failed()`, which would leave the case
without a completion path.

## Verification Logic

The verify worker registers with `NodeConvergenceTracker`, exactly as
`ScalingEventCaseDescriptor.verifyConvergence()` does:

1. Read `affectedNodeIds` from `.remediationExecuted`
2. Call `convergenceTracker.register(caseId, affectedNodeIds, "incidentStatus", "resolved")`
3. Return empty `WorkerResult`

When all affected nodes emit `NODE_RECOVERED` events, the tracker signals
`.incidentStatus = "resolved"` on the case, satisfying the completion expression.

## Escalation Logic

The escalate worker fires when:
- Assessment determines auto-remediation is unsafe (unknown incident type)
- Assessment routes directly to escalation

Writes `.incidentStatus = "escalated"` to satisfy the completion expression.

**Output** (written to `.escalation`):

```json
{
  "summary":    "Unresolvable incident on order-api — unknown incident type",
  "detail":     "...",
  "serviceId":  "order-api",
  "risk":       "HIGH"
}
```

No `WorkItem` creation in this implementation — human task integration is a
separate concern.

## New ApplicationLifecycleService Methods

### `restartService(UUID applicationId, String serviceId, String tenancyId)`

1. Load `ApplicationEntity` by ID and tenancyId
2. Find the service in the application's service definitions
3. Compile the desired-state graph for each affected cluster
4. Identify the node IDs corresponding to the service's Deployment spec
5. Mark those nodes for re-provision (the reconciliation loop's
   `TransitionPlanner` treats re-provisioned nodes as needing update)
6. Call `ReconciliationLoop.updateDesired()` with the recompiled graph
7. Return the set of affected node IDs

The "generation bump" approach: `ServiceDefinition` gains a `restartGeneration`
field (int, default 0). `restartService()` increments it on the entity and
persists. `ApplicationGoalCompiler` propagates this counter into the
`InfraDesiredNodeSpec` metadata. When the counter changes, the reconciliation
loop detects the node as drifted (desired ≠ actual metadata) and re-provisions.
This is the standard K8s pattern
(`spec.template.metadata.annotations["kubectl.kubernetes.io/restartedAt"]`).

### `rollbackService(UUID applicationId, String serviceId, String tenancyId)`

1. Load `ApplicationEntity` by ID and tenancyId
2. Query `DeploymentRecordEntity` records for this application, ordered by
   timestamp descending, and find the most recent SUCCESSFUL deployment that
   used a different image for the target service (skipping the current deployment
   and any failed deployments)
3. Extract the previous image reference for the target service from that record
4. Update the service definition's image in the Application entity
5. Persist the updated Application
6. Recompile desired-state graph per cluster
7. Call `ReconciliationLoop.updateDesired()` per cluster
8. Return affected node IDs

## File Changes

| File | Change |
|------|--------|
| `app/.../case_/IncidentResponseCaseDescriptor.java` | **New** — full descriptor |
| `app/.../service/ApplicationLifecycleService.java` | **Modified** — add `restartService()`, `rollbackService()` |
| `app/.../case_/CaseDefinitionRegistrar.java` | **Modified** — replace stub with real descriptor |
| `app/.../case_/IncidentResponseCaseDescriptorTest.java` | **New** — worker logic tests |
| `app/.../service/ApplicationLifecycleServiceTest.java` | **Modified** — tests for new methods |

## Test Plan

### IncidentResponseCaseDescriptorTest

- **Case definition identity**: correct namespace, name, version
- **Assess worker — all incident types**: SERVICE_DOWN → restart, DEGRADED → restart, CRASH_LOOP → rollback, RESOURCE_PRESSURE → scale
- **Assess worker — unknown type**: produces escalation, not remediation
- **Assess worker — missing required fields**: returns `WorkerResult.failed()`
- **Remediate worker — restart**: calls `restartService()`, writes correct output
- **Remediate worker — rollback**: calls `rollbackService()`, writes correct output
- **Remediate worker — scale**: calls `updateServiceReplicas()`, writes correct output
- **Verify worker**: registers with convergence tracker with correct case ID and node IDs
- **Escalate worker**: writes `.incidentStatus = "escalated"` and summary

### ApplicationLifecycleService tests

- **restartService — happy path**: recompiles graph, updates reconciliation loop, returns node IDs
- **restartService — unknown service**: throws
- **rollbackService — happy path**: reverts image from previous deployment record, returns node IDs
- **rollbackService — no previous deployment**: throws
- **rollbackService — unknown service**: throws

### CaseDefinitionRegistrarTest

- Verify `incident-response` has real capabilities (not `incident-response-stub`)

## Known Limitations

These are cross-cutting concerns that affect all convergence-tracked cases (drift,
scaling, incident), not just incident-response. They should be addressed at the
platform level, not per case type.

**No convergence timeout.** `NodeConvergenceTracker` has no timeout mechanism. If
nodes never emit `NODE_RECOVERED`, the case hangs. This equally affects
`DriftRemediationCaseDescriptor` and `ScalingEventCaseDescriptor`. A tracker-level
timeout that signals escalation would fix all three.

**No concurrent incident dedup.** Both entry points (application-level and
service-level) can fire for the same service, spawning duplicate incident-response
cases. This is an engine binding dedup concern (GE-20260608-1a56c3), not solvable
per case type. The engine needs binding-level guards to suppress duplicate child
cases for the same service.

**Scale action does not enforce ScalingPolicy bounds.** The incident-response
remediate worker calls `updateServiceReplicas(current + 1)` without checking
min/max replica bounds. `ScalingPolicy` is currently internal to
`ScalingEventCaseDescriptor`. Enforcement should move to
`ApplicationLifecycleService.updateServiceReplicas()` itself — the service boundary,
not every caller.
