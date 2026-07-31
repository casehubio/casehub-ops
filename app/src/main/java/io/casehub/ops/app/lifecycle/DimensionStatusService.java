package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.OperationalDimension;
import io.casehub.ops.api.lifecycle.ServiceCaseContext;
import io.casehub.ops.api.lifecycle.ServiceHealth;
import io.casehub.ops.api.lifecycle.Severity;
import io.casehub.ops.api.lifecycle.status.ChangeManagementStatus;
import io.casehub.ops.api.lifecycle.status.ComplianceStatus;
import io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus;
import io.casehub.ops.api.lifecycle.status.DecommissionStatus;
import io.casehub.ops.api.lifecycle.status.HealthStatus;
import io.casehub.ops.api.lifecycle.status.MaintenanceStatus;
import io.casehub.ops.api.lifecycle.status.ProblemManagementStatus;
import io.casehub.ops.api.lifecycle.status.ScalingStatus;
import io.casehub.ops.api.lifecycle.status.SecurityStatus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DimensionStatusService {

    public void recompute(OperationalDimension dimension) {
        DimensionStatus condition = readCondition(dimension);
        boolean hasActiveResponses = !dimension.activeResponses().isEmpty();
        boolean conditionHealthy = condition.severity() == Severity.OK;

        if (conditionHealthy || !hasActiveResponses) {
            dimension.updateStatus(condition);
        } else {
            dimension.updateStatus(responseStatus(dimension.type()));
        }
    }

    public ServiceHealth recomputeAll(ServiceCaseContext context) {
        for (var dim : context.dimensions().values()) {
            recompute(dim);
        }
        return context.toServiceHealth();
    }

    private DimensionStatus readCondition(OperationalDimension dimension) {
        String conditionName = dimension.section().get("condition", String.class);
        if (conditionName == null) {
            return dimension.status();
        }
        return resolveStatus(dimension.type(), conditionName);
    }

    private DimensionStatus responseStatus(DimensionType type) {
        return switch (type) {
            case HEALTH_MONITORING -> HealthStatus.REMEDIATING;
            case CONFIGURATION_DRIFT -> ConfigurationDriftStatus.RECONCILING;
            case COMPLIANCE -> ComplianceStatus.REMEDIATING;
            case SCALING -> ScalingStatus.SCALING;
            case CHANGE_MANAGEMENT -> ChangeManagementStatus.ROLLING_OUT;
            case SECURITY -> SecurityStatus.PATCHING;
            case MAINTENANCE -> MaintenanceStatus.IN_PROGRESS;
            case PROBLEM_MANAGEMENT -> ProblemManagementStatus.INVESTIGATING;
            case DECOMMISSION -> DecommissionStatus.IN_PROGRESS;
        };
    }

    private DimensionStatus resolveStatus(DimensionType type, String name) {
        return switch (type) {
            case HEALTH_MONITORING -> HealthStatus.valueOf(name);
            case CONFIGURATION_DRIFT -> ConfigurationDriftStatus.valueOf(name);
            case COMPLIANCE -> ComplianceStatus.valueOf(name);
            case SCALING -> ScalingStatus.valueOf(name);
            case CHANGE_MANAGEMENT -> ChangeManagementStatus.valueOf(name);
            case SECURITY -> SecurityStatus.valueOf(name);
            case MAINTENANCE -> MaintenanceStatus.valueOf(name);
            case PROBLEM_MANAGEMENT -> ProblemManagementStatus.valueOf(name);
            case DECOMMISSION -> DecommissionStatus.valueOf(name);
        };
    }
}
