package io.casehub.ops.api.lifecycle.status;

import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.Severity;

public enum ProblemManagementStatus implements DimensionStatus {
    NO_KNOWN_PROBLEMS(Severity.OK, "No Known Problems"),
    PATTERN_DETECTED(Severity.WARNING, "Pattern Detected"),
    INVESTIGATING(Severity.INFO, "Investigating"),
    WORKAROUND_APPLIED(Severity.WARNING, "Workaround Applied"),
    ROOT_CAUSE_FIXED(Severity.OK, "Root Cause Fixed");

    private final Severity severity;
    private final String label;

    ProblemManagementStatus(Severity severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    @Override public Severity severity() { return severity; }
    @Override public String label() { return label; }
    @Override public boolean isTerminal() { return false; }
}
