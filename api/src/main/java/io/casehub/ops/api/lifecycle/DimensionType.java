package io.casehub.ops.api.lifecycle;

public enum DimensionType {
    HEALTH_MONITORING("health."),
    CONFIGURATION_DRIFT("drift."),
    COMPLIANCE("compliance."),
    SCALING("scaling."),
    CHANGE_MANAGEMENT("change."),
    SECURITY("security."),
    MAINTENANCE("maintenance."),
    PROBLEM_MANAGEMENT("problems."),
    DECOMMISSION("decommission.");

    private final String contextPrefix;

    DimensionType(String contextPrefix) {
        this.contextPrefix = contextPrefix;
    }

    public String contextPrefix() {
        return contextPrefix;
    }
}
