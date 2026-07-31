package io.casehub.ops.api.lifecycle.status;

import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.Severity;

public enum SecurityStatus implements DimensionStatus {
    CLEAR(Severity.OK, "Clear"),
    VULNERABILITY_DETECTED(Severity.WARNING, "Vulnerability Detected"),
    PATCHING(Severity.INFO, "Patching"),
    BREACH_DETECTED(Severity.CRITICAL, "Breach Detected"),
    INVESTIGATING(Severity.WARNING, "Investigating");

    private final Severity severity;
    private final String label;

    SecurityStatus(Severity severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    @Override public Severity severity() { return severity; }
    @Override public String label() { return label; }
    @Override public boolean isTerminal() { return false; }
}
