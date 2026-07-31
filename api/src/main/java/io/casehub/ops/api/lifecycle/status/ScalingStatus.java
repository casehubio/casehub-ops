package io.casehub.ops.api.lifecycle.status;

import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.Severity;

public enum ScalingStatus implements DimensionStatus {
    OPTIMAL(Severity.OK, "Optimal"),
    UNDER_PROVISIONED(Severity.WARNING, "Under-Provisioned"),
    OVER_PROVISIONED(Severity.INFO, "Over-Provisioned"),
    SCALING(Severity.INFO, "Scaling"),
    SCALE_FAILED(Severity.CRITICAL, "Scale Failed");

    private final Severity severity;
    private final String label;

    ScalingStatus(Severity severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    @Override public Severity severity() { return severity; }
    @Override public String label() { return label; }
    @Override public boolean isTerminal() { return false; }
}
