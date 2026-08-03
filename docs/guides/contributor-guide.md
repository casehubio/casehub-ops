# casehub-ops -- Contributor Guide

> Platform internals, SPIs, and architecture for casehub-ops contributors.

**GitHub:** [casehubio/casehub-ops](https://github.com/casehubio/casehub-ops)

---

## Architecture Overview

casehub-ops is structured as a multi-module Maven project. Each domain module implements the full desiredstate SPI quad independently. The `api` module provides shared types; domain modules depend on `api` but not on each other. The `app` module assembles everything into a Quarkus application.

```
api (shared types, SPIs)
 |
 +-- deployment (agent topology)
 +-- infra (infrastructure provisioning)
 +-- compliance (regulatory posture)
 +-- iot (device management)
 +-- app (K8s ops console, embeds engine + desiredstate runtime)
 +-- testing (shared test fixtures, no sources)
```

### The SPI Quad Pattern

Every domain module implements these five casehub-desiredstate SPIs:

1. **GoalCompiler** -- translates domain-specific goals into a `DesiredStateGraph`
2. **ActualStateAdapter** -- reads the real-world state and returns `NodeStatus`
3. **NodeProvisioner** -- creates, updates, or removes resources to match desired state
4. **FaultPolicy** -- decides what to do when provisioning fails (retry, escalate, ignore)
5. **EventSource** -- feeds external state-change events into the reconciliation loop

Each module also provides an `ApprovalEvaluator` for the approval workflow.

---

## Deployment Module Internals

### DeploymentGoalCompiler

`io.casehub.ops.deployment.DeploymentGoalCompiler` -- `@ApplicationScoped`, implements `GoalCompiler<DeploymentGoals>`.

Compiles five entry lists from `DeploymentGoals` into a flat `DesiredStateGraph`:
- `agents()` -> `AgentNodeSpec` nodes
- `channels()` -> `ChannelNodeSpec` nodes
- `caseTypes()` -> `CaseTypeNodeSpec` nodes (with `DefinitionPayloadLoader` resolution for file-backed definitions)
- `trust()` -> `TrustPolicyNodeSpec` nodes
- `endpoints()` -> `EndpointNodeSpec` nodes

Each `GoalEntry<S>` carries a `dependsOn` list of node IDs for dependency edges.

### DeploymentFaultPolicy

Delegates to `ThresholdFaultPolicy` from casehub-desiredstate:
- **Monitored fault types**: `PROVISION_FAILED`
- **Monitored node types**: `agent`, `channel`, `case_type`, `trust_policy`, `endpoint`
- **Ignored types**: `deployment-review` (prevents cascading escalation)
- **Threshold**: 3 consecutive failures
- **Action**: adds `deployment-review` node with `DeploymentReviewSpec` (human escalation)

### Drift Detection Architecture

Five `NodeDriftChecker` implementations, one per node type. The `DeploymentActualStateAdapter` routes to the correct checker by `nodeType()`.

**AgentDriftChecker** -- delegates to `AgentDescriptorComparator.compare(desired, actual)`. Resolves actual via `AgentRegistry.findById()`. Field-by-field comparison with DEBUG logging of each `drift.field()`, `drift.desiredValue()`, `drift.actualValue()`. Returns `ABSENT`/`DRIFTED`/`PRESENT`.

**ChannelDriftChecker** -- full field comparison including connector binding and tenancy. Enriched in issue #14.

### Provision Handler Architecture

Five handlers routed by `DeploymentNodeProvisioner`:

| Handler | Provisioning target |
|---------|-------------------|
| `AgentProvisionHandler` | eidos (agent registry) |
| `ChannelProvisionHandler` | qhorus (channel management) |
| `CaseTypeProvisionHandler` | engine (case definition) |
| `EndpointProvisionHandler` | endpoint registration |
| `TrustPolicyProvisionHandler` | trust routing |

### Adaptive Topology Manager

`io.casehub.ops.deployment.adaptation.AdaptiveTopologyManager` -- `@ApplicationScoped`.

**Thread model**: `@ObservesAsync SituationChangeEvent` runs on Vert.x worker pool. Per-tenant serialization via `synchronized(state)` on the `TenantAdaptationState` instance.

**Initialization flow**:
1. `initialize(tenancyId, goals)` parses `AdaptationRule`s from `DeploymentGoals.adaptations()`
2. Compiles initial adapted topology (applying any already-active situations)
3. Starts reconciliation via `ReconciliationTarget.start()`
4. Stores `TenantAdaptationState` in `ConcurrentHashMap`

**Event-driven recompilation**:
1. `onSituationChange()` receives `SituationChangeEvent`
2. Locks per-tenant state
3. Recompiles adapted graph: base compile -> apply matching rules -> clear absent situations
4. Pushes to `ReconciliationTarget.updateDesired()` + `requestReconciliation()`

**Periodic poll** (safety net): every 5 minutes, recompiles all tenants and compares graph content equality. Fires only on actual change.

**Three adaptation actions** (all in `io.casehub.ops.deployment.adaptation`):

| Action | What it does |
|--------|-------------|
| `ScaleAction` | Modifies node attributes (e.g., replica count) |
| `AddAction` | Adds new nodes to the graph |
| `UpdateAction` | Replaces node specs in the graph |

**Hysteresis**: `TenantAdaptationState.shouldActivate()` prevents rapid oscillation. `clearAbsentSituations()` removes stale hysteresis state.

**ReconciliationTarget interface**: abstracts `start(tenancyId, graph)`, `updateDesired(tenancyId, graph)`, `requestReconciliation(tenancyId)`. In production, a CDI bean adapts the runtime `ReconciliationLoop`. In tests, a spy implementation.

### Supporting Classes

- `DeploymentGoalLoader` -- YAML loading for `DeploymentGoals`
- `DeploymentProviderConfigStore` -- per-provider config with reverse index. `declaredAgentIds()` returns all agent IDs across all providers (issue #12, optimized with reverse index in #27)
- `DeploymentProvisionerConfigRegistry` -- CDI-backed `ProvisionerConfigRegistry` (issue #24)
- `SpecHashStore` -- SHA-based change detection for spec updates

---

## Infrastructure Module Internals

### InfraBackend SPI

`io.casehub.ops.api.infra.spi.InfraBackend` -- blocking interface (migrated from reactive `Uni` in ADR-0005 virtual thread migration):

```java
String backendId();
BackendProvisionResult provision(InfraNodeSpec spec, InfraProvisionContext context);
BackendDeprovisionResult deprovision(InfraNodeSpec spec, InfraProvisionContext context);
ResourceState readState(NodeId nodeId, InfraNodeSpec spec);
DriftReport detectDrift(NodeId nodeId, InfraNodeSpec spec);
Optional<ProvisionPlan> plan(InfraNodeSpec spec, InfraProvisionContext context);
```

Both `readState` and `detectDrift` accept `InfraNodeSpec` (added in #41) for backends that need spec context to query external state.

**Implementation**: `StandaloneBackend` (in-memory, `ConcurrentHashMap`-based). Production `TerraformBackend` and `AnsibleBackend` are planned but not yet implemented.

### InfraNodeSpec Sealed Hierarchy

Ten permits on `InfraNodeSpec`:

- **K8s resources** (5): `K8sNamespaceSpec`, `K8sDeploymentSpec`, `K8sServiceSpec`, `K8sIngressSpec`, `K8sConfigMapSpec` (#39)
- **Cloud resources** (2): `ComputeInstanceSpec`, `DatabaseClusterSpec`
- **IaC tools** (2): `TerraformWorkspaceSpec`, `AnsiblePlaybookSpec`
- **Catch-all**: `GenericResourceSpec`

### InfraFaultPolicy

Delegates to `ThresholdFaultPolicy`:
- **Monitored**: 8 node types (`k8s_namespace`, `k8s_deployment`, `k8s_service`, `k8s_ingress`, `compute_instance`, `database_cluster`, `terraform_workspace`, `ansible_playbook`)
- **Threshold**: 3 consecutive `PROVISION_FAILED`
- **Action**: adds `infra-review` node with `InfraReviewSpec`

### Provisioning Context

`InfraProvisionContext` record: carries context through the provision lifecycle.
`ProvisionAction` enum, `ProvisionPhase` enum -- lifecycle tracking.

### Plan/Apply Model

`ProvisionPlan` -- captures planned changes before execution. Contains `PlannedChange` entries with `ChangeAction`, `FieldDiff`, and `ToolPlanDetail`. Used by the approval workflow to show what will change.

### InMemoryResourceProvisioner

`io.casehub.ops.infra.standalone.InMemoryResourceProvisioner` -- test-friendly `ResourceProvisioner` with no external dependencies.

---

## Compliance Module Internals

### ComplianceGoalCompiler

`io.casehub.ops.compliance.ComplianceGoalCompiler` -- compiles `ComplianceGoals` (list of `ComplianceGoalEntry<ComplianceControlSpec>`) into a graph. Controls with `requiresHumanReview=true` get `HumanGating.ALL`.

### ComplianceControlSpec Fields

Record with validated fields:
- `controlId` (required, non-blank)
- `controlType` (required, non-blank)
- `strategy` (required, non-blank) -- routes to `EvidenceCollector`
- `title`, `description`
- `frameworks` -- `List<FrameworkMapping>` (framework ID + section reference)
- `evidenceMaxAgeDays` (must be positive)
- `requiresHumanReview` (boolean)
- `properties` (Map for strategy-specific config)

### Evidence Collection Pipeline

`ComplianceEvidenceService` routes to strategy-keyed `EvidenceCollector` implementations:

1. **FileExistenceEvidenceCollector** (`file-existence`) -- reads `path` and `shouldExist` from `properties`. Verifies file presence/absence
2. **CertificateExpiryEvidenceCollector** (`certificate-expiry`) -- reads `certPath` and `warningDays`. Checks X.509 certificate expiry dates
3. **ConfigHashEvidenceCollector** (`config-hash`) -- reads `configPath` and `expectedHash`. Computes SHA-256 and compares against baseline
4. **LogDirectoryEvidenceCollector** (`log-directory`) -- reads `logPath`, `requiredPermissions`, `retentionDays`. Validates directory structure

Each returns `EvidenceResult` containing `EvidenceOutcome` (PASS, FAIL, ERROR, STALE).

### ComplianceFrameworkRegistry

`ConcurrentHashMap`-based registry with bidirectional lookup:
- `controls` map: `controlId` -> `ComplianceControlSpec`
- `frameworkToControls` map: `frameworkId` -> `Set<controlId>`
- Thread-safe register/deregister with cross-index maintenance

### ComplianceFaultPolicy

No-op implementation -- compliance faults are logged but not retried. Returns empty mutation list.

### Ledger Integration

`ComplianceLedgerEntry` extends `JpaLedgerEntry` -- compliance evidence records are stored in the tamper-evident ledger. `ComplianceSpecHashStore` tracks spec hashes.

---

## IoT Module Internals

### IoTGoalCompiler

`io.casehub.ops.iot.IoTGoalCompiler` -- compiles `IoTGoals` (list of `IoTDeviceGoal`).

**Two compilation paths**:
1. **Physical devices** (`goal.physical() == true`): generates both a `physical-device` node (human-gated, `HumanGating.ALL`) and a `device-config` node with a dependency edge from config to physical
2. **Virtual/configured devices**: generates only a `device-config` node

Rejects duplicate `deviceId` values with `IllegalArgumentException`.

### CapabilityNormalizer

`io.casehub.ops.iot.CapabilityNormalizer` -- normalizes capability values for accurate comparison between desired and actual state:
- `BigDecimal`: strips trailing zeros for numeric comparison
- Enums: uppercase normalization
- Temperature: unit normalization
- Maps: recursive normalization of nested structures

### CapabilityCommandMapper

Static mapping from capability key + desired value to `DeviceCommand`:
- `CommandContext` record: `dispatchedBy` (always `"casehub-ops-iot-provisioner"`), `correlationId` (UUID)
- Produces `DeviceCommand` for `DeviceProvider.dispatch()`

### IoTNodeProvisioner

`io.casehub.ops.iot.IoTNodeProvisioner` -- `@ApplicationScoped`, implements `NodeProvisioner`.

**Handled types**: `physical-device`, `device-config`, `iot-review`
**Resync interval**: 30 seconds

**Provision flow for DeviceConfigSpec**:
1. Check for existing approval -> handle re-entry if present
2. Evaluate via `ApprovalEvaluator` -> return `PendingApproval` if required
3. Look up device via `DeviceRegistry.findById()`
4. Route to `DeviceProvider` by `device.providerId()`
5. Normalize actual vs desired capabilities
6. Dispatch `DeviceCommand` for each differing capability
7. Return `Success` or `Failed`

**Approval re-entry pattern** (shared across provision and deprovision):
- Plan missing -> re-evaluate without approval
- Spec changed since approval -> remove stale plan, re-evaluate
- Plan valid -> execute and clean up

**Deprovision**: `Success` for both `PhysicalDeviceSpec` and `DeviceConfigSpec` (no-op deprovisioning).

### IoTFaultPolicy

Delegates to `ThresholdFaultPolicy`:
- **Monitored**: `device-config` node type
- **Threshold**: 3 consecutive `PROVISION_FAILED`
- **Action**: adds `iot-review` node with `IoTReviewSpec`

### IoTActualStateAdapter

Reads actual device state via `DeviceRegistry`. Uses `CapabilityNormalizer` for accurate comparison between desired and actual capability values.

### IoTEventSource

Bridges two CDI event types from casehub-iot:
- `StateChangeEvent` -- device state changes
- `ProviderStatusEvent` -- provider connectivity changes

### IoTGoalLoader

YAML loading with:
- Classpath-first strategy (embedded defaults)
- Directory merge (external overrides)
- Duplicate device rejection

---

## App Module Internals

### Ops Console Architecture

The app module is a standalone Quarkus application, not a domain module. It embeds:
- `casehub-engine` -- case management runtime
- `casehub-desiredstate` -- reconciliation loop runtime
- `casehub-work` -- work item runtime
- `casehub-platform` -- platform services

It implements the desiredstate SPI quad directly for K8s resource management (not through domain modules).

### K8sClientRegistry

`io.casehub.ops.app.k8s.K8sClientRegistry` -- `@ApplicationScoped`.

**Data structure**: `ConcurrentHashMap<String, ClientEntry>` where `ClientEntry` is a record: `(KubernetesClient, apiUrl, credentialRef, trustCerts, expiresAt)`.

**Credential resolution**: `CredentialResolver` SPI resolves credential references to credential maps. Three credential types: bearer token, user/password, API key.

**Proactive refresh**: `@Scheduled(every="60s")` `checkExpiring()` refreshes clients with < 5 minutes to credential expiry.

**Reactive 401 refresh**: `withRetryOn401(clusterId, operation)` catches `KubernetesClientException` code 401, refreshes, retries once.

**Concurrent refresh deduplication**: `CompletableFuture` coalescing via `refreshesInFlight` `ConcurrentHashMap`. `putIfAbsent` ensures only one refresh per cluster runs concurrently.

**CDI integration**: fires `CredentialRefreshedEvent` after refresh. `K8sWatchManager` observes this to reconnect watches.

### K8sWatchManager

`io.casehub.ops.app.k8s.K8sWatchManager` -- `@ApplicationScoped`.

**Watch setup**: `startWatching(clusterId, namespace)` creates 4 fabric8 `Watch` instances:
1. Deployments
2. Services
3. ConfigMaps
4. Ingresses

All filtered by `managed-by=casehub-ops` label.

**Drift emission**: `driftWatcher()` creates a `Watcher<T>` that emits `StateEvent` on MODIFIED (-> `DRIFTED`) and DELETED (-> `ABSENT`) actions. NodeId format: `clusterId:name:resourceSlug`.

**Watch reconnection**: `onCredentialRefreshed(@Observes CredentialRefreshedEvent)` stops and restarts watches for the affected cluster.

**State tracking**: `ConcurrentHashMap<String, List<Watch>>` keyed by `clusterId:namespace`.

### ApplicationGoalCompiler

`io.casehub.ops.app.goal.ApplicationGoalCompiler` -- `@ApplicationScoped`. Not a `GoalCompiler<T>` impl; uses direct `compileForCluster()` method.

**Compilation**: per cluster, generates:
1. One `K8sNamespaceSpec` node
2. Per service: one `K8sDeploymentSpec` node (depends on namespace), optionally one `K8sServiceSpec` node (depends on deployment, created when ports are defined)
3. Inter-service dependencies from `ServiceDefinition.dependsOn()`

**Node ID format**: `clusterId:serviceId:deployment`, `clusterId:serviceId:service`, `clusterId:namespace`.

**Labels**: `managed-by=casehub-ops` on all resources, `app=<serviceId>` on service-specific resources.

### ApplicationLifecycleService

`io.casehub.ops.app.service.ApplicationLifecycleService` -- `@ApplicationScoped`.

**Active loop tracking**: `ConcurrentHashMap<String, Set<String>>` maps `clusterId` to composite keys (format: `tenancyId:appId:clusterId`). Used by `ClusterService.delete()` to reject deletion when active loops reference the cluster (409 response).

**Deploy flow**:
1. Cancel any pending decommission
2. Parse `ServiceDefinition` list from JSON
3. For each cluster: register K8s client, compile graph, start/update reconciliation loop, start watches
4. Update status to DEPLOYING
5. Record deployment with `DeploymentOutcome.PENDING`
6. Register deployment tracker, scaling evaluator, drift signal bridge

**Decommission flow**:
1. For each cluster: push empty graph via `reconciliationLoop.updateDesired()`
2. Register decommission handler with composite keys
3. Update status to DECOMMISSIONING
4. Deregister scaling evaluator and drift signal bridges

### Case Model

`io.casehub.ops.app.case_` package (trailing underscore avoids Java keyword conflict).

**CaseDefinitionRegistrar** -- `@ApplicationScoped`. Registers `ApplicationCaseDescriptor` (parent), `DriftRemediationCaseDescriptor`, `ScalingEventCaseDescriptor`, and `IncidentResponseCaseDescriptor` as child cases. `StubChildCaseDescriptor` used for unimplemented cases (cve-response, service-upgrade, compliance-remediation).

**DriftRemediationCaseDescriptor** -- static `build(NodeConvergenceTracker)` factory:
- **classify-drift worker**: evaluates `consecutiveDriftCount`, `driftDetails` (node count, security-sensitive fields: image, serviceAccount, rbac, secrets). Outputs severity: `benign` or `critical`
- **remediate-drift worker**: benign -> auto-remediate (register with `NodeConvergenceTracker`); critical -> sets `escalationRequired=true`
- **escalate-drift worker**: produces escalation summary with risk=HIGH
- **Completion**: `.remediationStatus == "converged"`
- **Bindings**: `ContextChangeTrigger` on `.driftClassification` -> remediate, `.escalationRequired` -> escalate

**ScalingEventCaseDescriptor** -- static `build(ApplicationLifecycleService, NodeConvergenceTracker)` factory:
- **evaluate-scaling worker**: validates inputs, applies `ScalingPolicy.clamp()`, determines scale-up/scale-down
- **execute-scaling worker**: calls `lifecycleService.updateServiceReplicas()`, returns affected node IDs
- **verify-convergence worker**: registers affected nodes with `NodeConvergenceTracker`
- **Completion**: `.scalingStatus == "converged" || .scalingStatus == "no-change-needed"`

**ScalingPolicy** record: `clamp(targetReplicas)` applies `Math.max(min, Math.min(max, target))`. `isCoolingDown(lastScalingEvent, now)` enforces cooldown. Constructor validates `minReplicas >= 0`, `maxReplicas >= minReplicas`, non-negative cooldown. `UNBOUNDED` constant for unconstrained scaling.

### Scaling Trigger Mechanism

Two trigger paths:

1. **REST**: `ScalingResource` POST `/api/applications/{appId}/services/{serviceId}/scale` -- accepts `ScaleServiceRequest(targetReplicas, reason)`. Validates application status (RUNNING or DEGRADED). Enforces cooldown. Fires `ScalingRequestedEvent` via CDI async.

2. **RAS situation-driven**: `SituationScalingEvaluator` -- observes `SituationChangeEvent`, evaluates `ScalingRule`s per service, fires `ScalingRequestedEvent`. `ScalingSignalBridge` bridges events to the case model.

### Approval Workflow

Three-layer approval architecture:

1. **Risk classification**: `K8sApprovalEvaluator` -- namespace-aware thresholds. Production namespace operations classified higher risk than dev/staging.

2. **Authorization**: `ConfigDrivenApprovalAuthorizer` implements `ApprovalAuthorizer` -- role-based gates per risk level. Returns `Authorized` or `Denied(reason)`.

3. **Persistence**: `JpaPlanStore` implements `PlanStore` -- JPA-backed approval plan storage via `ApprovalPlanEntity`.

4. **REST API**: `ApprovalResource` at `/api/approvals` -- list pending approvals, approve/reject with authorization check.

### Stubbed SPI Beans

`@DefaultBean` stubs in `io.casehub.ops.app.spi` for SPI interfaces not yet wired to real implementations:
- `StubActualStateAdapter`
- `StubNodeProvisioner`
- `StubFaultPolicy`
- `StubEventSource`
- `StubSituationRecompiler`
- `StubSituationSource`

These are replaced by `KubernetesActualStateAdapter`, `KubernetesNodeProvisioner`, etc. when K8s integration is active.

### CDI Producers

- `FaultPolicyListProducer` -- produces `List<FaultPolicy>` for the reconciliation runtime
- `SituationRecompilerListProducer` -- produces `List<SituationRecompiler>` for RAS integration

### JPA Entities

| Entity | Table | Purpose |
|--------|-------|---------|
| `ApplicationEntity` | applications | Application metadata, status, services JSON |
| `ClusterReferenceEntity` | cluster_references | K8s cluster connection details |
| `DeploymentRecordEntity` | deployment_records | Deployment history with topology JSON, trigger, outcome |
| `ApprovalPlanEntity` | approval_plans | JPA-backed plan store for approval workflow |

Migrations via Flyway.

### Test Profile

The `skip-quarkus-tests` profile (active by default unless `-DrunQuarkusTests` is set) excludes `@QuarkusTest` tests in `entity/`, `rest/`, `ApplicationLifecycleService*`, and `ClusterServiceTest` due to H2/JpaLedgerEntry JOINED inheritance incompatibility (issue #49).

---

## Cross-Module Patterns

### Approval Workflow

All four domain modules implement the same approval lifecycle:
1. Provisioner calls `ApprovalEvaluator.evaluate(node, action, tenancyId)`
2. If `RequiresApproval`: store plan via `PlanStore.store()`, return `PendingApproval`
3. On re-entry with approval context: retrieve plan, validate spec unchanged, execute, clean up
4. Authorization via `ApprovalAuthorizer` (role-based, risk-classified)

Domain evaluators: `DeploymentApprovalEvaluator`, `InfraApprovalEvaluator`, `IoTApprovalEvaluator`, `ComplianceApprovalEvaluator`, `K8sApprovalEvaluator`.

### ThresholdFaultPolicy Adoption

Three of four domain modules use `ThresholdFaultPolicy` (deployment, infra, IoT). Compliance uses a no-op policy. All threshold policies:
- Monitor `PROVISION_FAILED` faults
- Threshold: 3 consecutive failures
- Escalate to a domain-specific review node type (human gate)
- Ignore the review node type to prevent cascading escalation

### Human Gating

- IoT physical devices: `HumanGating.ALL` (always requires human installation)
- Compliance controls with `requiresHumanReview=true`: `HumanGating.ALL`
- Deployment nodes: `HumanGating.NONE` (automated provisioning)
- Infra nodes: configurable via approval workflow

### Node ID Conventions

| Domain | Format |
|--------|--------|
| Deployment | `agentId`, `channelId`, etc. (from spec) |
| Infrastructure | `clusterId:resourceName:resourceType` |
| Compliance | `controlId` |
| IoT | `deviceId` (physical), `deviceId-config` (config) |
| App (K8s) | `clusterId:serviceId:deployment`, `clusterId:serviceId:service` |

---

## Dependencies Map

### Compile Dependencies (api module)

| From | To |
|------|-----|
| `casehub-ops-api` | `casehub-desiredstate-api`, `casehub-eidos-api`, `casehub-qhorus-api`, `casehub-platform-api`, `casehub-iot-api` |

### Compile Dependencies (domain modules)

| From | To |
|------|-----|
| `casehub-ops-deployment` | `casehub-ops-api`, `casehub-desiredstate-api`, `casehub-ras-api`, `casehub-eidos-api`, `casehub-ledger-api`, `casehub-qhorus-api`, `casehub-work-api`, `casehub-engine-api`, `casehub-platform-api` |
| `casehub-ops-infra` | `casehub-ops-api`, `casehub-desiredstate-api`, `casehub-platform-api`, `casehub-work-api` |
| `casehub-ops-compliance` | `casehub-ops-api`, `casehub-desiredstate-api`, `casehub-platform-api`, `casehub-work-api`, `casehub-ledger-api`, `casehub-ledger` |
| `casehub-ops-iot` | `casehub-ops-api`, `casehub-desiredstate-api`, `casehub-platform-api`, `casehub-work-api`, `casehub-iot-api` |
| `casehub-ops-app` | `casehub-ops-api`, `casehub-engine`, `casehub-desiredstate`, `casehub-desiredstate-work`, `casehub-ras-api`, `casehub-work`, `casehub-platform-expression`, `io.fabric8:kubernetes-client` |

### Test-Only Dependencies

All domain modules use: `casehub-desiredstate` (runtime, for `DefaultDesiredStateGraphFactory`), `casehub-desiredstate-testing`. The IoT module additionally uses `casehub-iot-testing`.

---

## Current State

Active development. Key completed milestones:
- Infra module PoC (issue #1)
- Deployment module with 5 node types (issues #2, #6, #7)
- Compliance module with 4 evidence collectors (issues #3, #18)
- IoT module with full reconciliation cycle (issue #4)
- Ops console Phase 1 (foundation) and Phase 2 (K8s integration) (issue #29)
- Adaptive topology with RAS integration (issue #25)
- Approval workflow with persistent storage and role-based authorization (issue #43)
- ThresholdFaultPolicy adoption across infra (#64), deployment (#65), IoT (#10)
- Credential rotation and scaling events (#51, #53, #54, #55)
- Scaling trigger mechanism -- REST + RAS situation-driven (#56)
- K8s-aware FaultPolicy with threshold escalation (#45)

Open design issues: cross-domain dependency graphs (#23), deployment drift remediation strategies (#22), operational efficiency (#31), service-as-case modelling (#30).

---

## Design Documents

Design specs and implementation plans live in the `docs/` directory:

| Path | Topic |
|------|-------|
| `docs/specs/2026-07-18-approval-workflow-design.md` | Approval workflow design |
| `docs/specs/2026-07-23-k8s-faultpolicy-escalation-design.md` | K8s fault policy escalation |
| `docs/specs/2026-07-17-scaling-trigger-mechanism-design.md` | Scaling trigger mechanism |
| `docs/specs/2026-07-13-credential-rotation-scaling-event-design.md` | Credential rotation + scaling |
| `docs/superpowers/specs/2026-06-12-infra-terraform-ansible-adapter-design.md` | Infra Terraform/Ansible adapter |
| `docs/superpowers/specs/2026-06-24-iot-desired-state-design.md` | IoT desired state domain |
| `docs/superpowers/specs/2026-06-29-adaptive-ops-design.md` | Adaptive topology |
| `docs/superpowers/specs/2026-07-05-ops-console-app-design.md` | Ops console design |
| `docs/superpowers/specs/2026-07-06-ops-console-phase2-kubernetes-design.md` | K8s integration design |
