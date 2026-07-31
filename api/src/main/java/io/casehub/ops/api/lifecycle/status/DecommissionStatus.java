package io.casehub.ops.api.lifecycle.status;

import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.Severity;

public enum DecommissionStatus implements DimensionStatus {
    NOT_PLANNED(Severity.OK, "Not Planned"),
    SCHEDULED(Severity.INFO, "Scheduled"),
    IN_PROGRESS(Severity.WARNING, "In Progress"),
    BLOCKED(Severity.CRITICAL, "Blocked"),
    COMPLETED(Severity.OK, "Completed");

    private final Severity severity;
    private final String label;

    DecommissionStatus(Severity severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    @Override public Severity severity() { return severity; }
    @Override public String label() { return label; }
    @Override public boolean isTerminal() { return this == COMPLETED; }
}
