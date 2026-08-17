package io.casehub.ops.app.lifecycle.ras;

import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.GanglionBinding;
import io.casehub.ops.api.lifecycle.OpsCloudEventTypes;
import io.casehub.ops.api.lifecycle.status.ChangeManagementStatus;
import io.casehub.ops.api.lifecycle.status.ComplianceStatus;
import io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus;
import io.casehub.ops.api.lifecycle.status.DecommissionStatus;
import io.casehub.ops.api.lifecycle.status.HealthStatus;
import io.casehub.ops.api.lifecycle.status.MaintenanceStatus;
import io.casehub.ops.api.lifecycle.status.ProblemManagementStatus;
import io.casehub.ops.api.lifecycle.status.ScalingStatus;
import io.casehub.ops.api.lifecycle.status.SecurityStatus;
import io.casehub.ops.app.lifecycle.ServiceDetectionBridge;
import io.casehub.ras.api.ChainMode;
import io.casehub.ras.api.SituationContext;
import io.casehub.ras.api.SituationDefinition;
import io.casehub.ras.api.SituationRegistrar;
import io.casehub.ras.api.SituationRegistration;
import io.casehub.ras.api.SituationStore;
import io.casehub.ras.api.TriggerAction;
import io.casehub.ras.api.TriggerMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ServiceMonitoringRegistrar {

    private final SituationRegistrar     situationRegistrar;
    private final ServiceDetectionBridge detectionBridge;
    private final SituationStore         situationStore;

    private static final List<String> PERSISTENT_SITUATION_KEYS = List.of("drift", "compliance-ev");

    public ServiceMonitoringRegistrar(SituationRegistrar situationRegistrar,
                                      ServiceDetectionBridge detectionBridge,
                                      SituationStore situationStore) {
        this.situationRegistrar = situationRegistrar;
        this.detectionBridge    = detectionBridge;
        this.situationStore     = situationStore;
    }

    public ServiceMonitoringRegistrar(ServiceDetectionBridge detectionBridge) {
        this(noOpRegistrar(), detectionBridge, noOpStore());
    }

    private static SituationRegistrar noOpRegistrar() {
        return new SituationRegistrar() {
            @Override
            public void register(SituationRegistration registration) {}

            @Override
            public void deregister(String situationId)               {}

            @Override
            public boolean exists(String situationId)                {return false;}
        };
    }

    private static SituationStore noOpStore() {
        return new SituationStore() {
            @Override
            public java.util.Optional<SituationContext> find(String s, String c, String t) {return java.util.Optional.empty();}

            @Override
            public SituationContext save(SituationContext ctx)                             {return ctx;}

            @Override
            public void remove(String s, String c, String t)                               {}

            @Override
            public int removeExpired(java.time.Instant cutoff)                             {return 0;}

            @Override
            public void removeAllForSituation(String situationId)                          {}
        };
    }

    public void register(UUID applicationId, UUID engineCaseId) {
        String appId = applicationId.toString();

        for (var def : buildSituationDefinitions(appId)) {
            situationRegistrar.register(new SituationRegistration(
                    def,
                    event -> engineCaseId.toString(),
                    event -> appId.equals(event.getSubject()),
                    null
            ));
        }
        detectionBridge.registerBindings(engineCaseId, buildBindings());
    }

    public void deregister(UUID applicationId, UUID engineCaseId) {
        String appId = applicationId.toString();
        var    ids   = situationIds(appId);

        for (String id : ids) {
            if (isPersistentSituation(id)) {
                situationStore.removeAllForSituation(id);
            }
        }
        for (String id : ids) {
            situationRegistrar.deregister(id);
        }
        detectionBridge.deregisterBindings(engineCaseId);
    }

    private static boolean isPersistentSituation(String situationId) {
        for (String key : PERSISTENT_SITUATION_KEYS) {
            if (situationId.startsWith("ops:" + key + ":")) {return true;}
        }
        return false;
    }

    private static List<SituationDefinition> buildSituationDefinitions(String appId) {
        var defs = new ArrayList<SituationDefinition>();

        defs.add(situation("ops:health-rt:" + appId,
                           Set.of(OpsCloudEventTypes.HEALTH_PROBE, OpsCloudEventTypes.HEALTH_METRIC),
                           Duration.ofSeconds(30), Duration.ofMinutes(1),
                           Set.of("heartbeat-check", "heartbeat-recovery", "metrics-trend", "metrics-recovery")));
        defs.add(situation("ops:health-pd:" + appId,
                           Set.of(OpsCloudEventTypes.HEALTH_LOG, OpsCloudEventTypes.HEALTH_DEPENDENCY),
                           Duration.ofMinutes(5), Duration.ofMinutes(5),
                           Set.of("log-anomaly", "dependency-health")));

        defs.add(situation("ops:drift:" + appId,
                           Set.of(OpsCloudEventTypes.DRIFT_SPEC, OpsCloudEventTypes.DRIFT_AUDIT),
                           null, Duration.ofMinutes(5),
                           Set.of("config-drift", "manual-change-detected")));

        defs.add(situation("ops:compliance-pd:" + appId,
                           Set.of(OpsCloudEventTypes.COMPLIANCE_EVIDENCE),
                           Duration.ofHours(1), Duration.ofHours(1),
                           Set.of("evidence-stale")));
        defs.add(situation("ops:compliance-ev:" + appId,
                           Set.of(OpsCloudEventTypes.COMPLIANCE_CONTROL, OpsCloudEventTypes.COMPLIANCE_FRAMEWORK),
                           null, Duration.ofMinutes(10),
                           Set.of("control-violation", "framework-change")));

        defs.add(situation("ops:scaling-rt:" + appId,
                           Set.of(OpsCloudEventTypes.SCALING_CPU, OpsCloudEventTypes.SCALING_MEMORY, OpsCloudEventTypes.SCALING_QUEUE),
                           Duration.ofMinutes(1), Duration.ofMinutes(2),
                           Set.of("cpu-threshold", "cpu-recovery", "memory-threshold", "memory-recovery", "queue-depth", "queue-recovery")));
        defs.add(situation("ops:scaling-pd:" + appId,
                           Set.of(OpsCloudEventTypes.SCALING_LATENCY, OpsCloudEventTypes.SCALING_COST),
                           Duration.ofMinutes(10), Duration.ofMinutes(15),
                           Set.of("request-latency-trend", "cost-anomaly")));

        defs.add(situation("ops:change-rt:" + appId,
                           Set.of(OpsCloudEventTypes.CHANGE_CANARY, OpsCloudEventTypes.CHANGE_ROLLOUT),
                           Duration.ofMinutes(1), Duration.ofMinutes(2),
                           Set.of("canary-health", "rollout-progress")));
        defs.add(situation("ops:change-pd:" + appId,
                           Set.of(OpsCloudEventTypes.CHANGE_VERSION),
                           Duration.ofHours(6), Duration.ofHours(6),
                           Set.of("version-check")));

        defs.add(situation("ops:security-rt:" + appId,
                           Set.of(OpsCloudEventTypes.SECURITY_ANOMALY),
                           Duration.ofMinutes(1), Duration.ofMinutes(5),
                           Set.of("anomaly-detector")));
        defs.add(situation("ops:security-pd:" + appId,
                           Set.of(OpsCloudEventTypes.SECURITY_CVE, OpsCloudEventTypes.SECURITY_ROTATION, OpsCloudEventTypes.SECURITY_PENTEST),
                           Duration.ofHours(1), Duration.ofHours(1),
                           Set.of("cve-scanner", "secret-rotation-due", "penetration-test-finding")));

        defs.add(situation("ops:maint:" + appId,
                           Set.of(OpsCloudEventTypes.MAINTENANCE_WINDOW, OpsCloudEventTypes.MAINTENANCE_BACKUP,
                                  OpsCloudEventTypes.MAINTENANCE_DR, OpsCloudEventTypes.MAINTENANCE_CERT),
                           Duration.ofHours(1), Duration.ofHours(1),
                           Set.of("maintenance-due", "backup-verification", "dr-drill-due", "certificate-expiry")));

        defs.add(situation("ops:problems:" + appId,
                           Set.of(OpsCloudEventTypes.PROBLEMS_INCIDENT, OpsCloudEventTypes.PROBLEMS_SCALING, OpsCloudEventTypes.PROBLEMS_DRIFT),
                           Duration.ofMinutes(30), Duration.ofMinutes(30),
                           Set.of("incident-pattern", "scaling-pattern", "drift-pattern")));

        defs.add(situation("ops:decommission-rt:" + appId,
                           Set.of(OpsCloudEventTypes.DECOMMISSION_TRAFFIC),
                           Duration.ofMinutes(1), Duration.ofMinutes(5),
                           Set.of("traffic-monitor")));
        defs.add(situation("ops:decommission-pd:" + appId,
                           Set.of(OpsCloudEventTypes.DECOMMISSION_SCHEDULE, OpsCloudEventTypes.DECOMMISSION_DEPENDENCY,
                                  OpsCloudEventTypes.DECOMMISSION_MIGRATION),
                           Duration.ofHours(1), Duration.ofHours(1),
                           Set.of("decommission-schedule", "dependency-check", "data-migration-progress")));

        return defs;
    }

    private static List<String> situationIds(String appId) {
        return List.of(
                "ops:health-rt:" + appId, "ops:health-pd:" + appId,
                "ops:drift:" + appId,
                "ops:compliance-pd:" + appId, "ops:compliance-ev:" + appId,
                "ops:scaling-rt:" + appId, "ops:scaling-pd:" + appId,
                "ops:change-rt:" + appId, "ops:change-pd:" + appId,
                "ops:security-rt:" + appId, "ops:security-pd:" + appId,
                "ops:maint:" + appId,
                "ops:problems:" + appId,
                "ops:decommission-rt:" + appId, "ops:decommission-pd:" + appId
                      );
    }

    private static SituationDefinition situation(String id, Set<String> eventTypes,
                                                 Duration correlationWindow, Duration cooldown,
                                                 Set<String> ganglionIds) {
        return new SituationDefinition(
                id, eventTypes, correlationWindow, null,
                new ChainMode.Or(ganglionIds),
                new TriggerAction.NotifyOnly(),
                new TriggerMode.Repeating(cooldown)
        );
    }

    private static List<GanglionBinding> buildBindings() {
        var bindings = new ArrayList<GanglionBinding>();
        bindings.addAll(healthBindings());
        bindings.addAll(driftBindings());
        bindings.addAll(complianceBindings());
        bindings.addAll(scalingBindings());
        bindings.addAll(changeBindings());
        bindings.addAll(securityBindings());
        bindings.addAll(maintenanceBindings());
        bindings.addAll(problemBindings());
        bindings.addAll(decommissionBindings());
        return bindings;
    }

    private static List<GanglionBinding> healthBindings() {
        return List.of(
                new GanglionBinding("heartbeat-check", DimensionType.HEALTH_MONITORING, "serviceDown", HealthStatus.DOWN),
                new GanglionBinding("heartbeat-recovery", DimensionType.HEALTH_MONITORING, "serviceUp", HealthStatus.HEALTHY),
                new GanglionBinding("metrics-trend", DimensionType.HEALTH_MONITORING, "degraded", HealthStatus.DEGRADED),
                new GanglionBinding("metrics-recovery", DimensionType.HEALTH_MONITORING, "metricsNormal", HealthStatus.HEALTHY),
                new GanglionBinding("log-anomaly", DimensionType.HEALTH_MONITORING, "logAnomaly", HealthStatus.DEGRADED),
                new GanglionBinding("dependency-health", DimensionType.HEALTH_MONITORING, "dependencyDown", HealthStatus.DEGRADED)
                      );
    }

    private static List<GanglionBinding> driftBindings() {
        return List.of(
                new GanglionBinding("config-drift", DimensionType.CONFIGURATION_DRIFT, "detected", ConfigurationDriftStatus.DRIFTED),
                new GanglionBinding("manual-change-detected", DimensionType.CONFIGURATION_DRIFT, "manualChange", ConfigurationDriftStatus.DRIFTED)
                      );
    }

    private static List<GanglionBinding> complianceBindings() {
        return List.of(
                new GanglionBinding("evidence-stale", DimensionType.COMPLIANCE, "evidenceStale", ComplianceStatus.STALE_EVIDENCE),
                new GanglionBinding("control-violation", DimensionType.COMPLIANCE, "controlViolation", ComplianceStatus.NON_COMPLIANT),
                new GanglionBinding("framework-change", DimensionType.COMPLIANCE, "frameworkChange", ComplianceStatus.STALE_EVIDENCE)
                      );
    }

    private static List<GanglionBinding> scalingBindings() {
        return List.of(
                new GanglionBinding("cpu-threshold", DimensionType.SCALING, "cpuBreached", ScalingStatus.UNDER_PROVISIONED),
                new GanglionBinding("cpu-recovery", DimensionType.SCALING, "cpuNormal", ScalingStatus.OPTIMAL),
                new GanglionBinding("memory-threshold", DimensionType.SCALING, "memoryBreached", ScalingStatus.UNDER_PROVISIONED),
                new GanglionBinding("memory-recovery", DimensionType.SCALING, "memoryNormal", ScalingStatus.OPTIMAL),
                new GanglionBinding("queue-depth", DimensionType.SCALING, "queueBreached", ScalingStatus.UNDER_PROVISIONED),
                new GanglionBinding("queue-recovery", DimensionType.SCALING, "queueNormal", ScalingStatus.OPTIMAL),
                new GanglionBinding("request-latency-trend", DimensionType.SCALING, "latencyTrend", ScalingStatus.UNDER_PROVISIONED),
                new GanglionBinding("cost-anomaly", DimensionType.SCALING, "costAnomaly", ScalingStatus.OVER_PROVISIONED)
                      );
    }

    private static List<GanglionBinding> changeBindings() {
        return List.of(
                new GanglionBinding("version-check", DimensionType.CHANGE_MANAGEMENT, "upgradeAvailable", ChangeManagementStatus.UPGRADE_AVAILABLE),
                new GanglionBinding("canary-health", DimensionType.CHANGE_MANAGEMENT, "canaryUnhealthy", ChangeManagementStatus.ROLLBACK),
                new GanglionBinding("rollout-progress", DimensionType.CHANGE_MANAGEMENT, "rolloutStalled", ChangeManagementStatus.CHANGE_FAILED)
                      );
    }

    private static List<GanglionBinding> securityBindings() {
        return List.of(
                new GanglionBinding("cve-scanner", DimensionType.SECURITY, "vulnerabilityFound", SecurityStatus.VULNERABILITY_DETECTED),
                new GanglionBinding("anomaly-detector", DimensionType.SECURITY, "anomalyDetected", SecurityStatus.INVESTIGATING),
                new GanglionBinding("secret-rotation-due", DimensionType.SECURITY, "rotationDue", SecurityStatus.VULNERABILITY_DETECTED),
                new GanglionBinding("penetration-test-finding", DimensionType.SECURITY, "pentestFinding", SecurityStatus.VULNERABILITY_DETECTED)
                      );
    }

    private static List<GanglionBinding> maintenanceBindings() {
        return List.of(
                new GanglionBinding("maintenance-due", DimensionType.MAINTENANCE, "windowDue", MaintenanceStatus.SCHEDULED),
                new GanglionBinding("backup-verification", DimensionType.MAINTENANCE, "backupStale", MaintenanceStatus.OVERDUE),
                new GanglionBinding("dr-drill-due", DimensionType.MAINTENANCE, "drOverdue", MaintenanceStatus.OVERDUE),
                new GanglionBinding("certificate-expiry", DimensionType.MAINTENANCE, "certExpiring", MaintenanceStatus.OVERDUE)
                      );
    }

    private static List<GanglionBinding> problemBindings() {
        return List.of(
                new GanglionBinding("incident-pattern", DimensionType.PROBLEM_MANAGEMENT, "patternDetected", ProblemManagementStatus.PATTERN_DETECTED),
                new GanglionBinding("scaling-pattern", DimensionType.PROBLEM_MANAGEMENT, "scalingPatternDetected", ProblemManagementStatus.PATTERN_DETECTED),
                new GanglionBinding("drift-pattern", DimensionType.PROBLEM_MANAGEMENT, "driftPatternDetected", ProblemManagementStatus.PATTERN_DETECTED)
                      );
    }

    private static List<GanglionBinding> decommissionBindings() {
        return List.of(
                new GanglionBinding("decommission-schedule", DimensionType.DECOMMISSION, "scheduledDate", DecommissionStatus.SCHEDULED),
                new GanglionBinding("dependency-check", DimensionType.DECOMMISSION, "blocked", DecommissionStatus.BLOCKED),
                new GanglionBinding("data-migration-progress", DimensionType.DECOMMISSION, "migrationStalled", DecommissionStatus.IN_PROGRESS),
                new GanglionBinding("traffic-monitor", DimensionType.DECOMMISSION, "trafficNotDrained", DecommissionStatus.IN_PROGRESS)
                      );
    }
}
