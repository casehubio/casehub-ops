# Wire RAS Health Monitoring — Design Spec

**Issue:** casehubio/casehub-ops#47 (parent: #29)
**Date:** 2026-08-17
**Status:** Draft
**Depends on:** casehubio/casehub-ras#6 (CLOSED — TriggerAction, dynamic registration, enriched SituationChangeEvent)
**Decisions:** `decisions.md` in this directory

---

## 1. Problem Statement

The ops console app has a fully functional detection-to-dimension routing layer:
`ServiceDetectionBridge` maps `(situationType, caseId)` → dimension context writes via
`GanglionBinding`, and `ServiceCaseDescriptor` declares engine bindings that spawn child
cases on context changes. But nothing connects RAS to the bridge:

1. No ganglia are declared — RAS has no detection logic for ops concerns
2. No situations are registered when applications deploy — RAS doesn't know about managed services
3. No CDI observer translates `SituationChangeEvent` into bridge calls — detections never reach dimensions

## 2. Solution Summary

Fill three gaps with four new components:

1. **`OpsMonitoringSituationDefinitionProvider`** — declares 32 ganglia as `ExpressionRules` descriptors covering all 9 operational dimensions. Static CDI bean, deployed once with the app.

2. **`ServiceMonitoringRegistrar`** — on deploy, builds and registers ~15 `SituationDefinition` instances (cadence-class grouped per dimension) with `SituationRegistrar`, plus corresponding `GanglionBinding` lists with `ServiceDetectionBridge`. On decommission, deregisters both. Encapsulates the full RAS registration lifecycle.

3. **`RasSituationObserver`** — `@ObservesAsync SituationChangeEvent` CDI observer. Translates RAS concepts (situationId, correlationKey, SituationContext) to ops concepts (ganglionId, caseId UUID, detectionData map) and calls `ServiceDetectionBridge.onDetection()`.

4. **`OpsCloudEventTypes`** — constants class defining the `io.casehub.ops.*` CloudEvent type namespace.

End-to-end flow:

```
CloudEvent (io.casehub.ops.health.probe)
  → RAS engine routes to "ops:health-rt:<appId>" situation
    → heartbeat-check ganglion evaluates → DetectionResult(DETECTED, 0.95)
      → ChainMode.Or evaluates → TRIGGER decision
        → TriggerAction.NotifyOnly → SituationChangeEvent(TRIGGERED, context)
          → RasSituationObserver.onSituation(@ObservesAsync)
            → parses dimension=HEALTH_MONITORING from situationId
            → extracts caseId from correlationKey
            → iterates detections: ganglionId="heartbeat-check"
            → calls ServiceDetectionBridge.onDetection("heartbeat-check", caseId, detectionData)
              → GanglionBinding matches: HEALTH_MONITORING, "serviceDown", HealthStatus.DOWN
                → DimensionSection.put("serviceDown", detectionData)
                  → Engine Binding on ".health.serviceDown" fires
                    → SubCase spawns ops:incident-response
```

## 3. Conventions

### 3.1 situationId Format

```
ops:<dimension-short>[-<cadence-class>]:<applicationId>
```

Examples:
- `ops:health-rt:550e8400-e29b-41d4-a716-446655440000` — health realtime
- `ops:health-pd:550e8400-e29b-41d4-a716-446655440000` — health periodic
- `ops:drift:550e8400-e29b-41d4-a716-446655440000` — drift (unified, no cadence suffix)
- `ops:maint:550e8400-e29b-41d4-a716-446655440000` — maintenance (unified)

Dimension short codes: `health`, `drift`, `compliance`, `scaling`, `change`, `security`, `maint`, `problems`, `decommission`.

Cadence suffixes: `-rt` (realtime), `-pd` (periodic), `-ev` (event-driven). Omitted for unified dimensions.

The observer parses the dimension from segment 2 (before `-rt`/`-pd`/`-ev` if present). The cadence suffix is transparent to bridge routing.

Global uniqueness: the applicationId UUID suffix guarantees uniqueness across apps. `SituationRegistrar.register()` rejects duplicates.

### 3.2 correlationKey Strategy

The `CorrelationKeyExtractor` returns the **engine case ID** (UUID string) for the
managed application. Set as a closure at registration time:

```java
UUID engineCaseId = app.engineCaseId;
CorrelationKeyExtractor extractor = event -> engineCaseId.toString();
```

All CloudEvents matching the situation's event types AND event filter correlate into the
same `SituationContext` keyed by the engine case ID.

The `EventFilter` discriminates by application identity — each CloudEvent carries the
applicationId in its `subject` field (CloudEvents spec). The filter checks
`event.getSubject().equals(applicationId.toString())`.

The observer resolves caseId directly: `UUID.fromString(event.correlationKey())`. No
registry lookup needed.

### 3.3 situationType ↔ ganglionId Naming

`GanglionBinding.situationType()` uses the **same string** as `GanglionDescriptor.ganglionId()`.

When `RasSituationObserver` processes a `SituationChangeEvent`, it iterates
`context.detections()`. Each `TimestampedDetection` wraps a `DetectionResult` which
carries `ganglionId()`. The observer passes this directly as the `situationType`
parameter to `ServiceDetectionBridge.onDetection()`.

The binding then matches: `binding.situationType().equals(situationType)`.

This creates a compile-time-checkable contract: every ganglionId declared in the provider
(§5) must have a matching `GanglionBinding.situationType` registered with the bridge (§7).
The `ServiceMonitoringRegistrar` (§6) enforces this by building both from the same source.

### 3.4 CloudEvent Type Namespace

All event types under `io.casehub.ops.*`. The ops app owns the event contract regardless
of upstream source. External events (K8s watches, CVE feeds, compliance scanners) are
translated to ops-domain CloudEvents by ingestion adapters before reaching RAS.

See §4 for the complete type catalog.

### 3.5 Dual Registration Paths

Two independent paths register situations with `SituationRegistrar` in the same application:

1. **Desiredstate path** — `DetectionNodeSpec` → `DetectionProvisionHandler` → `SituationRegistrar`. Situations are desiredstate graph nodes, managed by the reconciliation loop. Used for deployment-domain detection (node faults, drift).

2. **Service lifecycle path** (this issue) — `ServiceMonitoringRegistrar` → `SituationRegistrar`. Situations are registered directly on deploy, deregistered on decommission. Used for operational monitoring of managed applications.

Invariants:
- **No ID overlap:** Desiredstate situationIds use `desiredstate.*` prefix. Service lifecycle situationIds use `ops:*` prefix. Namespaces are disjoint.
- **Independent lifecycle:** Desiredstate situations are managed by the reconciliation loop (provision/deprovision). Service lifecycle situations are managed by `ApplicationLifecycleService` (deploy/decommission). Neither path affects the other.
- **Shared ganglia:** Both paths can reference the same ganglia (CDI beans). Ganglia are stateless evaluators — concurrent use is safe.

## 4. CloudEvent Type Catalog

| Type | Dimension | Description |
|------|-----------|-------------|
| `io.casehub.ops.health.probe` | HEALTH | Liveness/readiness probe result |
| `io.casehub.ops.health.metric` | HEALTH | Service metric snapshot (latency, error rate, throughput) |
| `io.casehub.ops.health.log` | HEALTH | Log pattern event (anomalous log entries) |
| `io.casehub.ops.health.dependency` | HEALTH | Dependency availability check result |
| `io.casehub.ops.drift.spec` | DRIFT | Spec hash comparison result |
| `io.casehub.ops.drift.audit` | DRIFT | Audit log change event |
| `io.casehub.ops.compliance.evidence` | COMPLIANCE | Evidence collection staleness check |
| `io.casehub.ops.compliance.control` | COMPLIANCE | Control check result |
| `io.casehub.ops.compliance.framework` | COMPLIANCE | Regulatory framework update |
| `io.casehub.ops.scaling.cpu` | SCALING | CPU utilization metric |
| `io.casehub.ops.scaling.memory` | SCALING | Memory utilization metric |
| `io.casehub.ops.scaling.queue` | SCALING | Queue depth metric |
| `io.casehub.ops.scaling.latency` | SCALING | Request latency trend |
| `io.casehub.ops.scaling.cost` | SCALING | Cost anomaly metric |
| `io.casehub.ops.change.version` | CHANGE | Version availability check |
| `io.casehub.ops.change.canary` | CHANGE | Canary deployment health |
| `io.casehub.ops.change.rollout` | CHANGE | Rollout progress event |
| `io.casehub.ops.security.cve` | SECURITY | CVE scan result |
| `io.casehub.ops.security.anomaly` | SECURITY | Security anomaly event |
| `io.casehub.ops.security.rotation` | SECURITY | Secret rotation status |
| `io.casehub.ops.security.pentest` | SECURITY | Penetration test finding |
| `io.casehub.ops.maintenance.window` | MAINTENANCE | Maintenance window status |
| `io.casehub.ops.maintenance.backup` | MAINTENANCE | Backup verification result |
| `io.casehub.ops.maintenance.dr` | MAINTENANCE | DR drill status |
| `io.casehub.ops.maintenance.cert` | MAINTENANCE | Certificate expiry check |
| `io.casehub.ops.problems.incident` | PROBLEMS | Incident pattern analysis result |
| `io.casehub.ops.problems.scaling` | PROBLEMS | Scaling pattern analysis result |
| `io.casehub.ops.problems.drift` | PROBLEMS | Drift pattern analysis result |
| `io.casehub.ops.decommission.schedule` | DECOMMISSION | Decommission schedule status |
| `io.casehub.ops.decommission.dependency` | DECOMMISSION | Dependency check result |
| `io.casehub.ops.decommission.migration` | DECOMMISSION | Data migration progress |
| `io.casehub.ops.decommission.traffic` | DECOMMISSION | Traffic monitoring metric |

All CloudEvents carry `subject = applicationId.toString()` for per-app filtering.

## 5. Ganglion Catalog

All ganglia are `GanglionDescriptor.ExpressionRules`. Each evaluates CloudEvent data
against conditions and returns `DetectionSignal.DETECTED` or `DetectionSignal.NOISE`.

Recovery ganglia are paired with detection ganglia for dimensions needing rapid status
recovery (HEALTH, SCALING). Other dimensions rely on child case completion for recovery —
`DimensionStatusService.recompute()` restores healthy status when active responses clear.

### 5.1 HEALTH_MONITORING

| ganglionId | Event Types | Signal | Description |
|-----------|-------------|--------|-------------|
| `heartbeat-check` | health.probe | DETECTED if probe fails | Liveness/readiness failure |
| `heartbeat-recovery` | health.probe | DETECTED if probe succeeds | Health restored |
| `metrics-trend` | health.metric | DETECTED if error rate > threshold OR latency > threshold | Degradation signal |
| `metrics-recovery` | health.metric | DETECTED if metrics return to normal | Degradation cleared |
| `log-anomaly` | health.log | DETECTED if anomalous pattern count > threshold | Log anomaly signal |
| `dependency-health` | health.dependency | DETECTED if dependency unreachable | Upstream dependency failure |

Cadence classes:
- **health-rt**: heartbeat-check, heartbeat-recovery, metrics-trend, metrics-recovery — `correlationWindow=30s`, `TriggerMode.Repeating(1min)`
- **health-pd**: log-anomaly, dependency-health — `correlationWindow=5min`, `TriggerMode.Repeating(5min)`

### 5.2 CONFIGURATION_DRIFT

| ganglionId | Event Types | Signal | Description |
|-----------|-------------|--------|-------------|
| `config-drift` | drift.spec | DETECTED if actual hash ≠ desired hash | Spec drift detected |
| `manual-change-detected` | drift.audit | DETECTED if unauthorized change logged | Manual modification |

Cadence class: **drift** (unified) — `correlationWindow=null` (persistent), `TriggerMode.Repeating(5min)`

### 5.3 COMPLIANCE

| ganglionId | Event Types | Signal | Description |
|-----------|-------------|--------|-------------|
| `evidence-stale` | compliance.evidence | DETECTED if evidence age > threshold | Stale compliance evidence |
| `control-violation` | compliance.control | DETECTED if control check fails | Control violation |
| `framework-change` | compliance.framework | DETECTED if framework version changes | Regulatory update |

Cadence classes:
- **compliance-pd**: evidence-stale — `correlationWindow=1hr`, `TriggerMode.Repeating(1hr)`
- **compliance-ev**: control-violation, framework-change — `correlationWindow=null` (persistent), `TriggerMode.Repeating(10min)`

### 5.4 SCALING

| ganglionId | Event Types | Signal | Description |
|-----------|-------------|--------|-------------|
| `cpu-threshold` | scaling.cpu | DETECTED if cpu > upper OR cpu < lower | CPU out of band |
| `cpu-recovery` | scaling.cpu | DETECTED if cpu returns to normal | CPU normalized |
| `memory-threshold` | scaling.memory | DETECTED if memory > upper OR memory < lower | Memory out of band |
| `memory-recovery` | scaling.memory | DETECTED if memory returns to normal | Memory normalized |
| `queue-depth` | scaling.queue | DETECTED if queue depth > threshold | Queue backing up |
| `queue-recovery` | scaling.queue | DETECTED if queue drains to normal | Queue cleared |
| `request-latency-trend` | scaling.latency | DETECTED if latency trending up over window | Latency degradation |
| `cost-anomaly` | scaling.cost | DETECTED if cost deviates > threshold from baseline | Cost spike |

Cadence classes:
- **scaling-rt**: cpu-threshold, cpu-recovery, memory-threshold, memory-recovery, queue-depth, queue-recovery — `correlationWindow=1min`, `TriggerMode.Repeating(2min)`
- **scaling-pd**: request-latency-trend, cost-anomaly — `correlationWindow=10min`, `TriggerMode.Repeating(15min)`

### 5.5 CHANGE_MANAGEMENT

| ganglionId | Event Types | Signal | Description |
|-----------|-------------|--------|-------------|
| `version-check` | change.version | DETECTED if newer version available | Upgrade available |
| `canary-health` | change.canary | DETECTED if canary error rate > threshold | Canary failing |
| `rollout-progress` | change.rollout | DETECTED if rollout stalled > threshold | Rollout stalled |

Cadence classes:
- **change-rt**: canary-health, rollout-progress — `correlationWindow=1min`, `TriggerMode.Repeating(2min)`
- **change-pd**: version-check — `correlationWindow=6hr`, `TriggerMode.Repeating(6hr)`

### 5.6 SECURITY

| ganglionId | Event Types | Signal | Description |
|-----------|-------------|--------|-------------|
| `cve-scanner` | security.cve | DETECTED if new CVEs found | Vulnerability detected |
| `anomaly-detector` | security.anomaly | DETECTED if anomalous behavior pattern | Security anomaly |
| `secret-rotation-due` | security.rotation | DETECTED if rotation overdue | Secret rotation needed |
| `penetration-test-finding` | security.pentest | DETECTED if findings reported | Pen test issue |

Cadence classes:
- **security-rt**: anomaly-detector — `correlationWindow=1min`, `TriggerMode.Repeating(5min)`
- **security-pd**: cve-scanner, secret-rotation-due, penetration-test-finding — `correlationWindow=1hr`, `TriggerMode.Repeating(1hr)`

### 5.7 MAINTENANCE

| ganglionId | Event Types | Signal | Description |
|-----------|-------------|--------|-------------|
| `maintenance-due` | maintenance.window | DETECTED if window approaching | Maintenance window approaching |
| `backup-verification` | maintenance.backup | DETECTED if backup stale or failed | Backup needs attention |
| `dr-drill-due` | maintenance.dr | DETECTED if DR drill overdue | DR drill overdue |
| `certificate-expiry` | maintenance.cert | DETECTED if cert expires within threshold | Certificate expiring |

Cadence class: **maint** (unified) — `correlationWindow=1hr`, `TriggerMode.Repeating(1hr)`

### 5.8 PROBLEM_MANAGEMENT

| ganglionId | Event Types | Signal | Description |
|-----------|-------------|--------|-------------|
| `incident-pattern` | problems.incident | DETECTED if recurring incident pattern found | Incident pattern |
| `scaling-pattern` | problems.scaling | DETECTED if recurring scaling pattern found | Scaling pattern |
| `drift-pattern` | problems.drift | DETECTED if recurring drift pattern found | Drift pattern |

Cadence class: **problems** (unified) — `correlationWindow=30min`, `TriggerMode.Repeating(30min)`

### 5.9 DECOMMISSION

| ganglionId | Event Types | Signal | Description |
|-----------|-------------|--------|-------------|
| `decommission-schedule` | decommission.schedule | DETECTED if decommission date approaching | Decommission approaching |
| `dependency-check` | decommission.dependency | DETECTED if blocking dependencies remain | Decommission blocked |
| `data-migration-progress` | decommission.migration | DETECTED if migration stalled | Migration stalled |
| `traffic-monitor` | decommission.traffic | DETECTED if traffic not draining | Traffic not drained |

Cadence classes:
- **decommission-rt**: traffic-monitor — `correlationWindow=1min`, `TriggerMode.Repeating(5min)`
- **decommission-pd**: decommission-schedule, dependency-check, data-migration-progress — `correlationWindow=1hr`, `TriggerMode.Repeating(1hr)`

### 5.10 Totals

- 32 detection ganglia + 5 recovery ganglia = **37 ganglia**
- **15 cadence-class situations** per managed application
- 5 dimensions split (HEALTH, SCALING, CHANGE, SECURITY, DECOMMISSION)
- 4 dimensions unified (DRIFT, MAINTENANCE, PROBLEMS) + COMPLIANCE split into pd/ev

## 6. GanglionBinding Catalog

Registered with `ServiceDetectionBridge` per managed application. The `ServiceMonitoringRegistrar`
builds these from the ganglion catalog — one binding per ganglionId.

### Detection bindings

| ganglionId | DimensionType | contextKey | conditionStatus |
|-----------|--------------|-----------|----------------|
| `heartbeat-check` | HEALTH_MONITORING | `serviceDown` | `HealthStatus.DOWN` |
| `metrics-trend` | HEALTH_MONITORING | `degraded` | `HealthStatus.DEGRADED` |
| `log-anomaly` | HEALTH_MONITORING | `logAnomaly` | `HealthStatus.DEGRADED` |
| `dependency-health` | HEALTH_MONITORING | `dependencyDown` | `HealthStatus.DEGRADED` |
| `config-drift` | CONFIGURATION_DRIFT | `detected` | `ConfigurationDriftStatus.DRIFTED` |
| `manual-change-detected` | CONFIGURATION_DRIFT | `manualChange` | `ConfigurationDriftStatus.DRIFTED` |
| `evidence-stale` | COMPLIANCE | `evidenceStale` | `ComplianceStatus.STALE_EVIDENCE` |
| `control-violation` | COMPLIANCE | `controlViolation` | `ComplianceStatus.NON_COMPLIANT` |
| `framework-change` | COMPLIANCE | `frameworkChange` | `ComplianceStatus.STALE_EVIDENCE` |
| `cpu-threshold` | SCALING | `cpuBreached` | `ScalingStatus.UNDER_PROVISIONED` |
| `memory-threshold` | SCALING | `memoryBreached` | `ScalingStatus.UNDER_PROVISIONED` |
| `queue-depth` | SCALING | `queueBreached` | `ScalingStatus.UNDER_PROVISIONED` |
| `request-latency-trend` | SCALING | `latencyTrend` | `ScalingStatus.UNDER_PROVISIONED` |
| `cost-anomaly` | SCALING | `costAnomaly` | `ScalingStatus.OVER_PROVISIONED` |
| `version-check` | CHANGE_MANAGEMENT | `upgradeAvailable` | `ChangeManagementStatus.UPGRADE_AVAILABLE` |
| `canary-health` | CHANGE_MANAGEMENT | `canaryUnhealthy` | `ChangeManagementStatus.ROLLBACK` |
| `rollout-progress` | CHANGE_MANAGEMENT | `rolloutStalled` | `ChangeManagementStatus.CHANGE_FAILED` |
| `cve-scanner` | SECURITY | `vulnerabilityFound` | `SecurityStatus.VULNERABILITY_DETECTED` |
| `anomaly-detector` | SECURITY | `anomalyDetected` | `SecurityStatus.INVESTIGATING` |
| `secret-rotation-due` | SECURITY | `rotationDue` | `SecurityStatus.VULNERABILITY_DETECTED` |
| `penetration-test-finding` | SECURITY | `pentestFinding` | `SecurityStatus.VULNERABILITY_DETECTED` |
| `maintenance-due` | MAINTENANCE | `windowDue` | `MaintenanceStatus.SCHEDULED` |
| `backup-verification` | MAINTENANCE | `backupStale` | `MaintenanceStatus.OVERDUE` |
| `dr-drill-due` | MAINTENANCE | `drOverdue` | `MaintenanceStatus.OVERDUE` |
| `certificate-expiry` | MAINTENANCE | `certExpiring` | `MaintenanceStatus.OVERDUE` |
| `incident-pattern` | PROBLEM_MANAGEMENT | `patternDetected` | `ProblemManagementStatus.PATTERN_DETECTED` |
| `scaling-pattern` | PROBLEM_MANAGEMENT | `scalingPatternDetected` | `ProblemManagementStatus.PATTERN_DETECTED` |
| `drift-pattern` | PROBLEM_MANAGEMENT | `driftPatternDetected` | `ProblemManagementStatus.PATTERN_DETECTED` |
| `decommission-schedule` | DECOMMISSION | `scheduledDate` | `DecommissionStatus.SCHEDULED` |
| `dependency-check` | DECOMMISSION | `blocked` | `DecommissionStatus.BLOCKED` |
| `data-migration-progress` | DECOMMISSION | `migrationStalled` | `DecommissionStatus.IN_PROGRESS` |
| `traffic-monitor` | DECOMMISSION | `trafficNotDrained` | `DecommissionStatus.IN_PROGRESS` |

### Recovery bindings

| ganglionId | DimensionType | contextKey | conditionStatus |
|-----------|--------------|-----------|----------------|
| `heartbeat-recovery` | HEALTH_MONITORING | `serviceUp` | `HealthStatus.HEALTHY` |
| `metrics-recovery` | HEALTH_MONITORING | `metricsNormal` | `HealthStatus.HEALTHY` |
| `cpu-recovery` | SCALING | `cpuNormal` | `ScalingStatus.OPTIMAL` |
| `memory-recovery` | SCALING | `memoryNormal` | `ScalingStatus.OPTIMAL` |
| `queue-recovery` | SCALING | `queueNormal` | `ScalingStatus.OPTIMAL` |

Other dimensions recover via child case completion → `DimensionStatusService.recompute()`.

## 7. Component Design

### 7.1 OpsMonitoringSituationDefinitionProvider

```
io.casehub.ops.app.lifecycle.ras.OpsMonitoringSituationDefinitionProvider
```

`@ApplicationScoped` CDI bean implementing `SituationDefinitionProvider`.

- `registrations()` → empty list. Situations are registered dynamically per-app.
- `ganglionDescriptors()` → all 37 `GanglionDescriptor.ExpressionRules` instances.

Internal structure: private methods per dimension matching `ServiceCaseDescriptor` pattern.

```java
@ApplicationScoped
public class OpsMonitoringSituationDefinitionProvider implements SituationDefinitionProvider {

    @Override
    public List<SituationRegistration> registrations() { return List.of(); }

    @Override
    public List<GanglionDescriptor> ganglionDescriptors() {
        var all = new ArrayList<GanglionDescriptor>();
        all.addAll(healthGanglia());
        all.addAll(driftGanglia());
        all.addAll(complianceGanglia());
        all.addAll(scalingGanglia());
        all.addAll(changeGanglia());
        all.addAll(securityGanglia());
        all.addAll(maintenanceGanglia());
        all.addAll(problemGanglia());
        all.addAll(decommissionGanglia());
        return all;
    }
    // private per-dimension methods...
}
```

### 7.2 ServiceMonitoringRegistrar

```
io.casehub.ops.app.lifecycle.ras.ServiceMonitoringRegistrar
```

`@ApplicationScoped` CDI bean. Encapsulates both RAS situation registration and
`GanglionBinding` registration with `ServiceDetectionBridge`.

```java
@ApplicationScoped
public class ServiceMonitoringRegistrar {

    @Inject SituationRegistrar situationRegistrar;
    @Inject ServiceDetectionBridge detectionBridge;

    public void register(UUID applicationId, UUID engineCaseId) {
        // 1. Build 15 SituationDefinitions (per cadence-class)
        // 2. For each: register with SituationRegistrar
        //    - EventFilter: event.getSubject().equals(applicationId.toString())
        //    - CorrelationKeyExtractor: event -> engineCaseId.toString()
        //    - TriggerAction: NotifyOnly
        //    - ChainMode: Or(ganglionIds in this cadence class)
        // 3. Build GanglionBinding list (all 39 bindings)
        // 4. Register with detectionBridge.registerBindings(engineCaseId, bindings)
    }

    public void deregister(UUID applicationId, UUID engineCaseId) {
        // 1. Deregister all 15 situations from SituationRegistrar
        //    (situationId pattern: ops:<dim>[-<cadence>]:<appId>)
        // 2. Deregister bindings from detectionBridge
        // 3. Clean up persistent situation store entries
    }
}
```

The registrar builds situation definitions and bindings from a shared dimension
configuration — ensuring the ganglionId ↔ situationType contract (§3.3) holds by
construction.

Deregistration order: store cleanup first (while definitions still exist for in-flight
evaluations), then definition removal, then bridge binding removal. Matches the pattern
specified in casehub-ras#6.

### 7.3 RasSituationObserver

```
io.casehub.ops.app.lifecycle.ras.RasSituationObserver
```

`@ApplicationScoped` CDI bean. Observes `SituationChangeEvent` and routes to
`ServiceDetectionBridge`.

```java
@ApplicationScoped
public class RasSituationObserver {

    @Inject ServiceDetectionBridge bridge;
    @Inject io.casehub.api.engine.CaseHubRuntime caseHubRuntime;

    void onSituation(@ObservesAsync SituationChangeEvent event) {
        // 1. Filter: only TRIGGERED events
        if (event.changeType() != SituationChangeEvent.ChangeType.TRIGGERED) return;

        // 2. Filter: only ops situations
        if (!event.situationId().startsWith("ops:")) return;

        // 3. Resolve caseId from correlationKey
        UUID caseId = UUID.fromString(event.correlationKey());

        // 4. For each detection in context:
        for (var detection : event.context().detections()) {
            DetectionResult result = detection.result();
            if (result.signal() != DetectionSignal.DETECTED) continue;

            // 5. Build detectionData from evidence + metadata
            Map<String, Object> detectionData = new HashMap<>(result.evidence());
            detectionData.put("confidence", result.confidence());
            detectionData.put("detectedAt", detection.eventTime().toString());
            detectionData.put("situationId", event.situationId());

            // 6. Route to bridge using ganglionId as situationType
            bridge.onDetection(
                result.ganglionId(),
                caseId,
                (key, value) -> caseHubRuntime.signal(caseId, key, value),
                key -> null,
                detectionData
            );
        }
    }
}
```

Uses the `onDetection` overload that accepts `ContextWriter`/`ContextReader` for
lazy dimension loading after restart (§4.5 of service lifecycle domain model spec).

### 7.4 OpsCloudEventTypes

```
io.casehub.ops.api.lifecycle.OpsCloudEventTypes
```

Constants class in `ops-api` (shared, not app-private). Ingestion adapters in other
modules reference these types when producing ops-domain CloudEvents.

```java
public final class OpsCloudEventTypes {
    public static final String HEALTH_PROBE   = "io.casehub.ops.health.probe";
    public static final String HEALTH_METRIC  = "io.casehub.ops.health.metric";
    public static final String HEALTH_LOG     = "io.casehub.ops.health.log";
    // ... all 32 types from §4
    private OpsCloudEventTypes() {}
}
```

## 8. Lifecycle Integration

### 8.1 ApplicationLifecycleService.deploy() — additions

After existing `serviceCaseRegistry.register(...)` and before `scalingEvaluator.register(...)`:

```java
serviceMonitoringRegistrar.register(applicationId, serviceCaseId);
```

Single line. All complexity lives in `ServiceMonitoringRegistrar`.

### 8.2 ApplicationLifecycleService.decommission() — additions

Before existing `serviceDetectionBridge.deregisterBindings(...)`:

```java
serviceMonitoringRegistrar.deregister(app.id, app.engineCaseId);
```

This replaces the existing `serviceDetectionBridge.deregisterBindings(app.engineCaseId)`
call — `ServiceMonitoringRegistrar.deregister()` handles both RAS situation deregistration
AND bridge binding deregistration. Remove the direct `deregisterBindings` call to avoid
double deregistration.

### 8.3 SituationStore Cleanup

For persistent situations (`correlationWindow=null`, used by DRIFT and COMPLIANCE-ev),
`ServiceMonitoringRegistrar.deregister()` calls
`SituationStore.removeAllForSituation(situationId)` before deregistering the definition.
This prevents orphaned store entries that would never expire.

## 9. Module Placement

| Type | Module | Package |
|------|--------|---------|
| `OpsCloudEventTypes` | ops-api | `io.casehub.ops.api.lifecycle` |
| `OpsMonitoringSituationDefinitionProvider` | app | `io.casehub.ops.app.lifecycle.ras` |
| `ServiceMonitoringRegistrar` | app | `io.casehub.ops.app.lifecycle.ras` |
| `RasSituationObserver` | app | `io.casehub.ops.app.lifecycle.ras` |

New package `ras` under `io.casehub.ops.app.lifecycle` groups all RAS integration code.
Keeps the existing `lifecycle` package (ServiceDetectionBridge, ServiceCaseRegistry,
DimensionStatusService, ServiceCaseDescriptor) focused on ops-domain types.

## 10. Integration Test Strategy

### 10.1 Unit tests (per component)

- `OpsMonitoringSituationDefinitionProviderTest` — verifies all 37 ganglia are declared,
  ganglionIds are unique, event types are valid, `registrations()` is empty
- `ServiceMonitoringRegistrarTest` — verifies register/deregister lifecycle, situation
  count (15 per app), binding count (37 per app), idempotent deregister, store cleanup
  for persistent situations
- `RasSituationObserverTest` — verifies TRIGGERED filtering, ops: prefix filtering,
  correlationKey → UUID resolution, detection iteration, ganglionId passthrough to bridge,
  evidence and metadata extraction

### 10.2 End-to-end integration test

`RasHealthMonitoringIntegrationTest` — verifies the complete flow from CloudEvent to
child case spawn:

1. Deploy an application via `ApplicationLifecycleService.deploy()`
2. Verify 15 situations registered with `SituationRegistrar`
3. Fire a health probe CloudEvent (subject=applicationId, type=health.probe, data=failure)
4. Verify `SituationChangeEvent` fires (CDI async)
5. Verify `ServiceDetectionBridge.onDetection()` called with ganglionId="heartbeat-check"
6. Verify dimension status changed to `HealthStatus.DOWN`
7. Verify engine binding on `.health.serviceDown` evaluates
8. Fire recovery CloudEvent
9. Verify status returns to `HealthStatus.HEALTHY`
10. Decommission application
11. Verify all 15 situations deregistered
12. Verify bindings deregistered

Uses in-memory RAS runtime (`InMemorySituationStore`) and in-memory engine. No Quarkus
test profile needed — plain unit test with CDI-like wiring.

## 11. Scope Boundaries

**In scope:**
- All types in §7 (4 new components)
- All 37 ganglia (§5) as ExpressionRules descriptors
- All 39 GanglionBindings (§6)
- CloudEvent type constants (§7.4)
- `ApplicationLifecycleService` deploy/decommission integration (§8)
- Unit tests and integration test (§10)

**Out of scope:**
- Ingestion adapters that produce `io.casehub.ops.*` CloudEvents from external sources (K8s watches already exist via `K8sWatchManager`; other adapters are future work)
- NaiveBayes ganglia (D2 — ExpressionRules first, NaiveBayes is a per-ganglion upgrade)
- Per-category ganglion filtering (review R1-08 — deferred, universal ganglia for initial wiring)
- `SituationStore` changes — `removeAllForSituation()` was delivered by casehub-ras#6

## 12. Cross-Repo Dependencies

| Dependency | Repo | Status | Impact |
|-----------|------|--------|--------|
| `SituationRegistrar` (register/deregister) | casehub-ras | Exists (ras#6) | No changes needed |
| `SituationChangeEvent` (enriched with context) | casehub-ras | Exists (ras#6) | No changes needed |
| `TriggerAction.NotifyOnly` | casehub-ras | Exists (ras#6) | No changes needed |
| `GanglionDescriptor.ExpressionRules` | casehub-ras | Exists | No changes needed |
| `SituationDefinitionProvider` | casehub-ras | Exists | No changes needed |
| `SituationStore.removeAllForSituation()` | casehub-ras | Exists (ras#6) | No changes needed |
| `casehub-ras-api` Maven dependency | casehub-ops | Exists in app/pom.xml | No changes needed |
