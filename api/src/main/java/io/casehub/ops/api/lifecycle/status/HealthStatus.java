package io.casehub.ops.api.lifecycle.status;

import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.Severity;

public enum HealthStatus implements DimensionStatus {
    HEALTHY(Severity.OK, "Healthy"),
    DEGRADED(Severity.WARNING, "Degraded"),
    DOWN(Severity.CRITICAL, "Down"),
    INVESTIGATING(Severity.INFO, "Investigating"),
    REMEDIATING(Severity.WARNING, "Remediating");

    private final Severity severity;
    private final String label;

    HealthStatus(Severity severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    @Override public Severity severity() { return severity; }
    @Override public String label() { return label; }
    @Override public boolean isTerminal() { return false; }
}
