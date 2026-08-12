# Compliance Remediation Child Case — Design Spec

Issue: #37
Epic: #29 (Service lifecycle management)
Branch: `issue-29-service-lifecycle`

## Summary

Replace the `StubChildCaseDescriptor` for `ops:compliance-remediation` with a full
`ComplianceRemediationCaseDescriptor` implementing a four-phase worker chain: assess
violation → remediate (config update) → verify convergence → escalate, with limited
auto-fix for infrastructure-configurable controls and escalation for everything else.

Follows the same structure as `IncidentResponseCaseDescriptor` (#34). No dependency
on the compliance module — the case works with violation data as received and uses
`ApplicationLifecycleService` for infrastructure mutations.

## Case Definition

**Case identity:** `ops:compliance-remediation` v1.0

**Entry points (unchanged — bindings already exist):**

| Source | Binding | Trigger | Input mapping |
|--------|---------|---------|---------------|
| `ApplicationCaseDescriptor` | `on-compliance-violation` | `.complianceViolation` | `.violationData` |
| `ServiceCaseDescriptor` | `compliance:remediation` | `.compliance.controlViolation` | `.compliance.violationData` |

### Capabilities

| Capability | Purpose |
|------------|---------|
| `assess-compliance` | Classify violation, determine remediation action |
| `remediate-compliance` | Apply config fix via ApplicationLifecycleService |
| `verify-compliance` | Track node convergence to confirm fix |
| `escalate-compliance` | Produce summary for human review |

### Workers

| Worker | Capability | Input type | Output |
|--------|-----------|------------|--------|
| `compliance-assess-worker` | `assess-compliance` | `Map` (raw violation data) | `.complianceAssessment` or `.complianceEscalationRequired` |
| `compliance-remediate-worker` | `remediate-compliance` | `Map` (assessment) | `.complianceRemediationExecuted` |
| `compliance-verify-worker` | `verify-compliance` | `Map` (execution result) | (registers with `NodeConvergenceTracker`) |
| `compliance-escalate-worker` | `escalate-compliance` | `Map` (assessment + diagnostics) | `.complianceStatus = "escalated"` |

### Bindings (context change chaining)

| Binding name | Trigger | Target capability |
|-------------|---------|-------------------|
| `on-compliance-assessment` | `.complianceAssessment` | `remediate-compliance` |
| `on-compliance-remediation-executed` | `.complianceRemediationExecuted` | `verify-compliance` |
| `on-compliance-escalation-required` | `.complianceEscalationRequired` | `escalate-compliance` |

### Completion

```
.complianceStatus == "resolved" || .complianceStatus == "escalated"
```

`NodeConvergenceTracker` signals `.complianceStatus = "resolved"` when all affected
nodes converge. The escalate worker writes `.complianceStatus = "escalated"`.

**Worker failure handling:** The remediate worker catches lifecycle service
exceptions and writes `.complianceEscalationRequired` (with the error as detail)
rather than returning `WorkerResult.failed()`. This ensures the case always reaches
a terminal state via the escalation path. Only input validation failures (null/missing
fields in the assess worker) use `WorkerResult.failed()`.

### Dependencies

`ComplianceRemediationCaseDescriptor.build(ApplicationLifecycleService, NodeConvergenceTracker)`
— same injection pattern as `IncidentResponseCaseDescriptor` and `ScalingEventCaseDescriptor`.

## Input Data Shape

Both entry points provide the same contract:

```json
{
  "controlId":      "encryption-at-rest",
  "controlType":    "ENCRYPTION_AT_REST",
  "outcome":        "FAIL",
  "detail":         "AES-256 encryption not verified on datastore-primary",
  "frameworks":     ["SOC2:CC6.1", "GDPR:Art.32"],
  "serviceId":      "order-api",
  "applicationId":  "uuid-string",
  "tenancyId":      "tenant-1"
}
```

**Required fields:** `controlId`, `controlType`, `outcome`, `tenancyId`
**Optional fields:** `detail`, `frameworks`, `serviceId`, `applicationId`

`serviceId` and `applicationId` are optional because not every compliance violation
is scoped to a specific service — application-level controls (e.g., log retention
across all services) may not have a service ID.

## Assessment Logic

The assess worker validates input and maps outcome × controlType × serviceId
presence to a remediation action:

| Outcome | Control Type | serviceId present? | Action |
|---|---|---|---|
| `FAIL` | `LOG_RETENTION` | Yes | `update-config` |
| `FAIL` | `ENCRYPTION_AT_REST` | Yes | `update-config` |
| `FAIL` | Auto-fixable type | No | `escalate` (can't target a service) |
| `FAIL` | All other types | — | `escalate` |
| `UNAVAILABLE` | Any | — | `escalate` |
| `STALE` | Any | — | `escalate` |
| Unknown / null | — | — | `WorkerResult.failed()` |

**Validation failures** (missing controlId, controlType, outcome, tenancyId) →
`WorkerResult.failed(...)`.

**Assessment output** (written to `.complianceAssessment`):

```json
{
  "action":        "update-config",
  "controlId":     "encryption-at-rest",
  "controlType":   "ENCRYPTION_AT_REST",
  "configUpdates": {"ENCRYPTION_ENABLED": "true", "ENCRYPTION_CIPHER": "AES-256"},
  "serviceId":     "order-api",
  "applicationId": "...",
  "tenancyId":     "...",
  "reason":        "ENCRYPTION_AT_REST violation — applying config fix"
}
```

If `action == "escalate"`, writes to `.complianceEscalationRequired` instead — the
escalation binding fires, skipping remediation entirely.

### Control-type-to-config mapping

| Control Type | Config Updates |
|---|---|
| `LOG_RETENTION` | `{LOG_RETENTION_DAYS: "365", LOG_RETENTION_ENABLED: "true"}` |
| `ENCRYPTION_AT_REST` | `{ENCRYPTION_ENABLED: "true", ENCRYPTION_CIPHER: "AES-256"}` |

These are demonstrative defaults for the reference architecture. Real deployments
would need cloud-provider-specific remediation modules.

## Remediation Logic

The remediate worker reads `.complianceAssessment` and dispatches to
`ApplicationLifecycleService`:

| Action | Method | Effect |
|--------|--------|--------|
| `update-config` | `updateServiceConfig(appId, serviceId, configUpdates, tenancyId)` | Merges config into service env, recompiles desired state |

Returns `Set<String>` of affected node IDs.

**Output** (written to `.complianceRemediationExecuted`):

```json
{
  "action":          "update-config",
  "controlId":       "encryption-at-rest",
  "serviceId":       "order-api",
  "affectedNodeIds": ["node-1", "node-2"],
  "configUpdates":   {"ENCRYPTION_ENABLED": "true", "ENCRYPTION_CIPHER": "AES-256"}
}
```

If the lifecycle service throws (application/service not found), the worker
writes `.complianceEscalationRequired` with the error detail — routing to the
escalation path instead of returning `WorkerResult.failed()`.

## Verification Logic

The verify worker registers with `NodeConvergenceTracker`, exactly as
`IncidentResponseCaseDescriptor.verifyRemediation()` does:

1. Read `affectedNodeIds` from `.complianceRemediationExecuted`
2. Call `convergenceTracker.register(caseId, affectedNodeIds, "complianceStatus", "resolved")`
3. Return empty `WorkerResult`

When all affected nodes emit `NODE_RECOVERED` events, the tracker signals
`.complianceStatus = "resolved"` on the case, satisfying the completion expression.

## Escalation Logic

The escalate worker fires when:
- Assessment determines auto-remediation is not possible (non-auto-fixable control
  type, missing serviceId, UNAVAILABLE/STALE outcome)
- Remediation throws an exception

Writes `.complianceStatus = "escalated"` to satisfy the completion expression.

**Output** (written to `.complianceEscalation`):

```json
{
  "summary":     "Compliance violation on encryption-at-rest requires human review",
  "controlId":   "encryption-at-rest",
  "controlType": "ENCRYPTION_AT_REST",
  "outcome":     "FAIL",
  "frameworks":  ["SOC2:CC6.1", "GDPR:Art.32"],
  "serviceId":   "order-api",
  "risk":        "HIGH",
  "detail":      "AES-256 encryption not verified on datastore-primary"
}
```

No `WorkItem` creation in this implementation — human task integration is a
separate concern.

## New ApplicationLifecycleService Method

### `updateServiceConfig(UUID applicationId, String serviceId, Map<String,String> configUpdates, String tenancyId)`

1. Load `ApplicationEntity` by ID and tenancyId
2. Find the service in the application's service definitions
3. Merge `configUpdates` into the service's `env` map (additive — existing keys
   not in `configUpdates` are preserved)
4. Persist the updated Application
5. Recompile desired-state graph per cluster via `compileForCluster()`
6. Call `ReconciliationLoop.updateDesired()` per cluster
7. Return the set of affected node IDs

Same persistence and recompilation pattern as `restartService()` and
`rollbackService()`.

## File Changes

| File | Change |
|------|--------|
| `app/.../case_/ComplianceRemediationCaseDescriptor.java` | **New** — full descriptor |
| `app/.../service/ApplicationLifecycleService.java` | **Modified** — add `updateServiceConfig()` |
| `app/.../case_/CaseDefinitionRegistrar.java` | **Modified** — replace stub with real descriptor |
| `app/.../case_/ComplianceRemediationCaseDescriptorTest.java` | **New** — worker logic tests |
| `app/.../service/ApplicationLifecycleServiceTest.java` | **Modified** — tests for `updateServiceConfig()` |

## Test Plan

### ComplianceRemediationCaseDescriptorTest

- **Case definition identity**: correct namespace, name, version
- **Assess worker — FAIL + LOG_RETENTION + serviceId**: produces `update-config` with correct config keys
- **Assess worker — FAIL + ENCRYPTION_AT_REST + serviceId**: produces `update-config` with correct config keys
- **Assess worker — FAIL + auto-fixable + no serviceId**: escalates
- **Assess worker — FAIL + non-auto-fixable type**: ACCESS_REVIEW → escalate
- **Assess worker — UNAVAILABLE outcome**: escalates regardless of control type
- **Assess worker — STALE outcome**: escalates regardless of control type
- **Assess worker — missing required fields**: returns `WorkerResult.failed()`
- **Remediate worker — update-config**: calls `updateServiceConfig()`, writes correct output
- **Remediate worker — lifecycle service throws**: writes `.complianceEscalationRequired`
- **Verify worker**: registers with convergence tracker with correct case ID and node IDs
- **Escalate worker**: writes `.complianceStatus = "escalated"` and summary

### ApplicationLifecycleService tests

- **updateServiceConfig — happy path**: merges env, recompiles graph, updates reconciliation loop, returns node IDs
- **updateServiceConfig — unknown service**: throws
- **updateServiceConfig — unknown application**: throws
- **updateServiceConfig — empty configUpdates**: no-op, returns empty set

### CaseDefinitionRegistrarTest

- Verify `compliance-remediation` has real capabilities (not `compliance-remediation-stub`)

## Known Limitations

1. **Auto-fix is demonstrative, not production-grade.** Updating ConfigMap env vars
   (`ENCRYPTION_ENABLED=true`) does not enable encryption at the storage layer. Real
   remediation requires cloud-provider-specific modules (AWS KMS API, GCP CMEK, etc.).
   The auto-fix demonstrates the remediation pattern for the reference architecture.

2. **No convergence timeout.** Same cross-cutting limitation as incident-response and
   scaling cases (#34 known limitations). If nodes never converge, the case hangs.
   Tracker-level timeout is a platform concern.

3. **No concurrent violation dedup.** Both entry points (application-level and
   service-level) can fire for the same control, spawning duplicate remediation cases.
   Same dedup concern as incident-response — engine binding dedup concern
   (GE-20260608-1a56c3), not solvable per case type.

4. **Config mapping is hardcoded.** The control-type-to-config-key mapping lives in
   the assess worker. Externalizing to YAML configuration is a follow-up if more
   control types become auto-fixable.
