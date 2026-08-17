package io.casehub.ops.api.lifecycle;

public final class OpsCloudEventTypes {

    // Health Monitoring
    public static final String HEALTH_PROBE      = "io.casehub.ops.health.probe";
    public static final String HEALTH_METRIC     = "io.casehub.ops.health.metric";
    public static final String HEALTH_LOG        = "io.casehub.ops.health.log";
    public static final String HEALTH_DEPENDENCY = "io.casehub.ops.health.dependency";

    // Configuration Drift
    public static final String DRIFT_SPEC  = "io.casehub.ops.drift.spec";
    public static final String DRIFT_AUDIT = "io.casehub.ops.drift.audit";

    // Compliance
    public static final String COMPLIANCE_EVIDENCE  = "io.casehub.ops.compliance.evidence";
    public static final String COMPLIANCE_CONTROL   = "io.casehub.ops.compliance.control";
    public static final String COMPLIANCE_FRAMEWORK = "io.casehub.ops.compliance.framework";

    // Scaling
    public static final String SCALING_CPU     = "io.casehub.ops.scaling.cpu";
    public static final String SCALING_MEMORY  = "io.casehub.ops.scaling.memory";
    public static final String SCALING_QUEUE   = "io.casehub.ops.scaling.queue";
    public static final String SCALING_LATENCY = "io.casehub.ops.scaling.latency";
    public static final String SCALING_COST    = "io.casehub.ops.scaling.cost";

    // Change Management
    public static final String CHANGE_VERSION = "io.casehub.ops.change.version";
    public static final String CHANGE_CANARY  = "io.casehub.ops.change.canary";
    public static final String CHANGE_ROLLOUT = "io.casehub.ops.change.rollout";

    // Security
    public static final String SECURITY_CVE      = "io.casehub.ops.security.cve";
    public static final String SECURITY_ANOMALY  = "io.casehub.ops.security.anomaly";
    public static final String SECURITY_ROTATION = "io.casehub.ops.security.rotation";
    public static final String SECURITY_PENTEST  = "io.casehub.ops.security.pentest";

    // Maintenance
    public static final String MAINTENANCE_WINDOW = "io.casehub.ops.maintenance.window";
    public static final String MAINTENANCE_BACKUP = "io.casehub.ops.maintenance.backup";
    public static final String MAINTENANCE_DR     = "io.casehub.ops.maintenance.dr";
    public static final String MAINTENANCE_CERT   = "io.casehub.ops.maintenance.cert";

    // Problem Management
    public static final String PROBLEMS_INCIDENT = "io.casehub.ops.problems.incident";
    public static final String PROBLEMS_SCALING  = "io.casehub.ops.problems.scaling";
    public static final String PROBLEMS_DRIFT    = "io.casehub.ops.problems.drift";

    // Decommission
    public static final String DECOMMISSION_SCHEDULE   = "io.casehub.ops.decommission.schedule";
    public static final String DECOMMISSION_DEPENDENCY = "io.casehub.ops.decommission.dependency";
    public static final String DECOMMISSION_MIGRATION  = "io.casehub.ops.decommission.migration";
    public static final String DECOMMISSION_TRAFFIC    = "io.casehub.ops.decommission.traffic";

    private OpsCloudEventTypes() {}
}
