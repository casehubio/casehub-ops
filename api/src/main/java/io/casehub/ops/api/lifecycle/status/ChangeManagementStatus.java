package io.casehub.ops.api.lifecycle.status;

import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.Severity;

public enum ChangeManagementStatus implements DimensionStatus {
    NO_ACTIVITY(Severity.OK, "No Activity"),
    UPGRADE_AVAILABLE(Severity.INFO, "Upgrade Available"),
    CANARY(Severity.INFO, "Canary"),
    ROLLING_OUT(Severity.INFO, "Rolling Out"),
    ROLLBACK(Severity.WARNING, "Rollback"),
    CHANGE_FAILED(Severity.CRITICAL, "Change Failed");

    private final Severity severity;
    private final String label;

    ChangeManagementStatus(Severity severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    @Override public Severity severity() { return severity; }
    @Override public String label() { return label; }
    @Override public boolean isTerminal() { return false; }
}
