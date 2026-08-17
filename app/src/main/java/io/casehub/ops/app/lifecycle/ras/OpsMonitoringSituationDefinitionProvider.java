package io.casehub.ops.app.lifecycle.ras;

import io.casehub.ops.api.lifecycle.OpsCloudEventTypes;
import io.casehub.platform.api.expression.LambdaExpression;
import io.casehub.ras.api.DetectionSignal;
import io.casehub.ras.api.GanglionDescriptor;
import io.casehub.ras.api.SituationDefinitionProvider;
import io.casehub.ras.api.SituationRegistration;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class OpsMonitoringSituationDefinitionProvider implements SituationDefinitionProvider {

    @Override
    public List<SituationRegistration> registrations() {
        return List.of();
    }

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

    // -- HEALTH_MONITORING: 4 detection + 2 recovery --

    private static List<GanglionDescriptor> healthGanglia() {
        return List.of(
            ganglion("heartbeat-check", Set.of(OpsCloudEventTypes.HEALTH_PROBE), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && "FAIL".equals(data.get("status"));
            }, 0.95),
            ganglion("heartbeat-recovery", Set.of(OpsCloudEventTypes.HEALTH_PROBE), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && "OK".equals(data.get("status"));
            }, 0.95),
            ganglion("metrics-trend", Set.of(OpsCloudEventTypes.HEALTH_METRIC), ctx -> {
                Map<String, Object> data = data(ctx);
                if (data == null) return false;
                return toDouble(data.get("errorRate")) > 0.05 || toDouble(data.get("latencyMs")) > 1000;
            }, 0.9),
            ganglion("metrics-recovery", Set.of(OpsCloudEventTypes.HEALTH_METRIC), ctx -> {
                Map<String, Object> data = data(ctx);
                if (data == null) return false;
                return toDouble(data.get("errorRate")) <= 0.01 && toDouble(data.get("latencyMs")) <= 500;
            }, 0.9),
            ganglion("log-anomaly", Set.of(OpsCloudEventTypes.HEALTH_LOG), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("anomalyScore")) > 0.7;
            }, 0.8),
            ganglion("dependency-health", Set.of(OpsCloudEventTypes.HEALTH_DEPENDENCY), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && Boolean.FALSE.equals(data.get("reachable"));
            }, 0.9)
        );
    }

    // -- CONFIGURATION_DRIFT: 2 detection --

    private static List<GanglionDescriptor> driftGanglia() {
        return List.of(
            ganglion("config-drift", Set.of(OpsCloudEventTypes.DRIFT_SPEC), ctx -> {
                Map<String, Object> data = data(ctx);
                if (data == null) return false;
                Object actual = data.get("actualHash");
                Object desired = data.get("desiredHash");
                return actual != null && desired != null && !actual.equals(desired);
            }, 0.95),
            ganglion("manual-change-detected", Set.of(OpsCloudEventTypes.DRIFT_AUDIT), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && Boolean.FALSE.equals(data.get("authorized"));
            }, 0.9)
        );
    }

    // -- COMPLIANCE: 3 detection --

    private static List<GanglionDescriptor> complianceGanglia() {
        return List.of(
            ganglion("evidence-stale", Set.of(OpsCloudEventTypes.COMPLIANCE_EVIDENCE), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("ageHours")) > 72;
            }, 0.9),
            ganglion("control-violation", Set.of(OpsCloudEventTypes.COMPLIANCE_CONTROL), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && Boolean.FALSE.equals(data.get("passed"));
            }, 0.95),
            ganglion("framework-change", Set.of(OpsCloudEventTypes.COMPLIANCE_FRAMEWORK), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && Boolean.TRUE.equals(data.get("changed"));
            }, 0.9)
        );
    }

    // -- SCALING: 6 detection + 3 recovery --

    private static List<GanglionDescriptor> scalingGanglia() {
        return List.of(
            ganglion("cpu-threshold", Set.of(OpsCloudEventTypes.SCALING_CPU), ctx -> {
                Map<String, Object> data = data(ctx);
                if (data == null) return false;
                double pct = toDouble(data.get("utilizationPct"));
                return pct > 85 || pct < 10;
            }, 0.9),
            ganglion("cpu-recovery", Set.of(OpsCloudEventTypes.SCALING_CPU), ctx -> {
                Map<String, Object> data = data(ctx);
                if (data == null) return false;
                double pct = toDouble(data.get("utilizationPct"));
                return pct >= 20 && pct <= 75;
            }, 0.9),
            ganglion("memory-threshold", Set.of(OpsCloudEventTypes.SCALING_MEMORY), ctx -> {
                Map<String, Object> data = data(ctx);
                if (data == null) return false;
                double pct = toDouble(data.get("utilizationPct"));
                return pct > 85 || pct < 10;
            }, 0.9),
            ganglion("memory-recovery", Set.of(OpsCloudEventTypes.SCALING_MEMORY), ctx -> {
                Map<String, Object> data = data(ctx);
                if (data == null) return false;
                double pct = toDouble(data.get("utilizationPct"));
                return pct >= 20 && pct <= 75;
            }, 0.9),
            ganglion("queue-depth", Set.of(OpsCloudEventTypes.SCALING_QUEUE), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("depth")) > 1000;
            }, 0.9),
            ganglion("queue-recovery", Set.of(OpsCloudEventTypes.SCALING_QUEUE), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("depth")) <= 100;
            }, 0.9),
            ganglion("request-latency-trend", Set.of(OpsCloudEventTypes.SCALING_LATENCY), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && "UP".equals(data.get("trendDirection"));
            }, 0.8),
            ganglion("cost-anomaly", Set.of(OpsCloudEventTypes.SCALING_COST), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("deviationPct")) > 50;
            }, 0.8)
        );
    }

    // -- CHANGE_MANAGEMENT: 3 detection --

    private static List<GanglionDescriptor> changeGanglia() {
        return List.of(
            ganglion("version-check", Set.of(OpsCloudEventTypes.CHANGE_VERSION), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && Boolean.TRUE.equals(data.get("newerAvailable"));
            }, 0.9),
            ganglion("canary-health", Set.of(OpsCloudEventTypes.CHANGE_CANARY), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("errorRate")) > 0.1;
            }, 0.9),
            ganglion("rollout-progress", Set.of(OpsCloudEventTypes.CHANGE_ROLLOUT), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("stalledMinutes")) > 10;
            }, 0.85)
        );
    }

    // -- SECURITY: 4 detection --

    private static List<GanglionDescriptor> securityGanglia() {
        return List.of(
            ganglion("cve-scanner", Set.of(OpsCloudEventTypes.SECURITY_CVE), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("newCveCount")) > 0;
            }, 0.95),
            ganglion("anomaly-detector", Set.of(OpsCloudEventTypes.SECURITY_ANOMALY), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("anomalyScore")) > 0.8;
            }, 0.8),
            ganglion("secret-rotation-due", Set.of(OpsCloudEventTypes.SECURITY_ROTATION), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("overdueDays")) > 0;
            }, 0.9),
            ganglion("penetration-test-finding", Set.of(OpsCloudEventTypes.SECURITY_PENTEST), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("findingCount")) > 0;
            }, 0.9)
        );
    }

    // -- MAINTENANCE: 4 detection --

    private static List<GanglionDescriptor> maintenanceGanglia() {
        return List.of(
            ganglion("maintenance-due", Set.of(OpsCloudEventTypes.MAINTENANCE_WINDOW), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("windowInHours")) <= 24;
            }, 0.9),
            ganglion("backup-verification", Set.of(OpsCloudEventTypes.MAINTENANCE_BACKUP), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("lastVerifiedHoursAgo")) > 168;
            }, 0.9),
            ganglion("dr-drill-due", Set.of(OpsCloudEventTypes.MAINTENANCE_DR), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("overdueDays")) > 0;
            }, 0.9),
            ganglion("certificate-expiry", Set.of(OpsCloudEventTypes.MAINTENANCE_CERT), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("expiresInDays")) <= 30;
            }, 0.9)
        );
    }

    // -- PROBLEM_MANAGEMENT: 3 detection --

    private static List<GanglionDescriptor> problemGanglia() {
        return List.of(
            ganglion("incident-pattern", Set.of(OpsCloudEventTypes.PROBLEMS_INCIDENT), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("recurringCount")) >= 3;
            }, 0.85),
            ganglion("scaling-pattern", Set.of(OpsCloudEventTypes.PROBLEMS_SCALING), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("recurringCount")) >= 3;
            }, 0.85),
            ganglion("drift-pattern", Set.of(OpsCloudEventTypes.PROBLEMS_DRIFT), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("recurringCount")) >= 3;
            }, 0.85)
        );
    }

    // -- DECOMMISSION: 4 detection --

    private static List<GanglionDescriptor> decommissionGanglia() {
        return List.of(
            ganglion("decommission-schedule", Set.of(OpsCloudEventTypes.DECOMMISSION_SCHEDULE), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("daysUntil")) <= 14;
            }, 0.9),
            ganglion("dependency-check", Set.of(OpsCloudEventTypes.DECOMMISSION_DEPENDENCY), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("blockingCount")) > 0;
            }, 0.9),
            ganglion("data-migration-progress", Set.of(OpsCloudEventTypes.DECOMMISSION_MIGRATION), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("stalledMinutes")) > 30;
            }, 0.85),
            ganglion("traffic-monitor", Set.of(OpsCloudEventTypes.DECOMMISSION_TRAFFIC), ctx -> {
                Map<String, Object> data = data(ctx);
                return data != null && toDouble(data.get("requestsPerMinute")) > 0;
            }, 0.9)
        );
    }

    // -- helpers --

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map ctx) {
        Object d = ctx.get("data");
        return d instanceof Map ? (Map<String, Object>) d : null;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    @SuppressWarnings("unchecked")
    private static GanglionDescriptor ganglion(String id, Set<String> eventTypes,
                                                java.util.function.Function<Map, Boolean> condition,
                                                double confidence) {
        return new GanglionDescriptor.ExpressionRules(
            id,
            eventTypes,
            List.of(new GanglionDescriptor.ExpressionRules.Rule(
                new LambdaExpression<>(condition),
                DetectionSignal.DETECTED,
                confidence,
                null,
                Map.of()
            )),
            Map.of()
        );
    }
}
