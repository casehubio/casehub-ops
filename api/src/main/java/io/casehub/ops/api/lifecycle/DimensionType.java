package io.casehub.ops.api.lifecycle;

public enum DimensionType {
    HEALTH_MONITORING("health.", io.casehub.ops.api.lifecycle.status.HealthStatus.class, io.casehub.ops.api.lifecycle.status.HealthStatus.HEALTHY),
    CONFIGURATION_DRIFT("drift.", io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus.class, io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus.IN_SYNC),
    COMPLIANCE("compliance.", io.casehub.ops.api.lifecycle.status.ComplianceStatus.class, io.casehub.ops.api.lifecycle.status.ComplianceStatus.COMPLIANT),
    SCALING("scaling.", io.casehub.ops.api.lifecycle.status.ScalingStatus.class, io.casehub.ops.api.lifecycle.status.ScalingStatus.OPTIMAL),
    CHANGE_MANAGEMENT("change.", io.casehub.ops.api.lifecycle.status.ChangeManagementStatus.class, io.casehub.ops.api.lifecycle.status.ChangeManagementStatus.NO_ACTIVITY),
    SECURITY("security.", io.casehub.ops.api.lifecycle.status.SecurityStatus.class, io.casehub.ops.api.lifecycle.status.SecurityStatus.CLEAR),
    MAINTENANCE("maintenance.", io.casehub.ops.api.lifecycle.status.MaintenanceStatus.class, io.casehub.ops.api.lifecycle.status.MaintenanceStatus.NO_ACTIVITY),
    PROBLEM_MANAGEMENT("problems.", io.casehub.ops.api.lifecycle.status.ProblemManagementStatus.class, io.casehub.ops.api.lifecycle.status.ProblemManagementStatus.NO_KNOWN_PROBLEMS),
    DECOMMISSION("decommission.", io.casehub.ops.api.lifecycle.status.DecommissionStatus.class, io.casehub.ops.api.lifecycle.status.DecommissionStatus.NOT_PLANNED);

    private final String                   contextPrefix;
    private final Class<? extends Enum<?>> statusClass;
    private final DimensionStatus          defaultStatus;

    DimensionType(String contextPrefix, Class<? extends Enum<?>> statusClass, DimensionStatus defaultStatus) {
        this.contextPrefix = contextPrefix;
        this.statusClass   = statusClass;
        this.defaultStatus = defaultStatus;
    }

    public String contextPrefix()          {return contextPrefix;}

    public DimensionStatus defaultStatus() {return defaultStatus;}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DimensionStatus resolveStatus(String name) {return (DimensionStatus) Enum.valueOf((Class) statusClass, name);}
}
