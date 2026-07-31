package io.casehub.ops.api.lifecycle.status;

import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.Severity;

public enum MaintenanceStatus implements DimensionStatus {
    NO_ACTIVITY(Severity.OK, "No Activity"),
    SCHEDULED(Severity.INFO, "Scheduled"),
    IN_PROGRESS(Severity.INFO, "In Progress"),
    OVERDUE(Severity.WARNING, "Overdue"),
    FAILED(Severity.CRITICAL, "Failed");

    private final Severity severity;
    private final String label;

    MaintenanceStatus(Severity severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    @Override public Severity severity() { return severity; }
    @Override public String label() { return label; }
    @Override public boolean isTerminal() { return false; }
}
