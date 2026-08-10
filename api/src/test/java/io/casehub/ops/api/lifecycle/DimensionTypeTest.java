package io.casehub.ops.api.lifecycle;

import io.casehub.ops.api.lifecycle.status.ChangeManagementStatus;
import io.casehub.ops.api.lifecycle.status.ComplianceStatus;
import io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus;
import io.casehub.ops.api.lifecycle.status.DecommissionStatus;
import io.casehub.ops.api.lifecycle.status.HealthStatus;
import io.casehub.ops.api.lifecycle.status.MaintenanceStatus;
import io.casehub.ops.api.lifecycle.status.ProblemManagementStatus;
import io.casehub.ops.api.lifecycle.status.ScalingStatus;
import io.casehub.ops.api.lifecycle.status.SecurityStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DimensionTypeTest {

    @Test
    void hasNineDimensions() {
        assertEquals(9, DimensionType.values().length);
    }

    @Test
    void dimensionNames() {
        assertNotNull(DimensionType.valueOf("HEALTH_MONITORING"));
        assertNotNull(DimensionType.valueOf("CONFIGURATION_DRIFT"));
        assertNotNull(DimensionType.valueOf("COMPLIANCE"));
        assertNotNull(DimensionType.valueOf("SCALING"));
        assertNotNull(DimensionType.valueOf("CHANGE_MANAGEMENT"));
        assertNotNull(DimensionType.valueOf("SECURITY"));
        assertNotNull(DimensionType.valueOf("MAINTENANCE"));
        assertNotNull(DimensionType.valueOf("PROBLEM_MANAGEMENT"));
        assertNotNull(DimensionType.valueOf("DECOMMISSION"));
    }

    @Test
    void contextPrefixDerivedFromName() {
        assertEquals("health.", DimensionType.HEALTH_MONITORING.contextPrefix());
        assertEquals("drift.", DimensionType.CONFIGURATION_DRIFT.contextPrefix());
        assertEquals("compliance.", DimensionType.COMPLIANCE.contextPrefix());
        assertEquals("scaling.", DimensionType.SCALING.contextPrefix());
        assertEquals("change.", DimensionType.CHANGE_MANAGEMENT.contextPrefix());
        assertEquals("security.", DimensionType.SECURITY.contextPrefix());
        assertEquals("maintenance.", DimensionType.MAINTENANCE.contextPrefix());
        assertEquals("problems.", DimensionType.PROBLEM_MANAGEMENT.contextPrefix());
        assertEquals("decommission.", DimensionType.DECOMMISSION.contextPrefix());
    }

    @Test
    void resolveStatusForEachDimension() {
        assertEquals(HealthStatus.DOWN, DimensionType.HEALTH_MONITORING.resolveStatus("DOWN"));
        assertEquals(ConfigurationDriftStatus.DRIFTED, DimensionType.CONFIGURATION_DRIFT.resolveStatus("DRIFTED"));
        assertEquals(ComplianceStatus.NON_COMPLIANT, DimensionType.COMPLIANCE.resolveStatus("NON_COMPLIANT"));
        assertEquals(ScalingStatus.SCALING, DimensionType.SCALING.resolveStatus("SCALING"));
        assertEquals(ChangeManagementStatus.ROLLBACK, DimensionType.CHANGE_MANAGEMENT.resolveStatus("ROLLBACK"));
        assertEquals(SecurityStatus.BREACH_DETECTED, DimensionType.SECURITY.resolveStatus("BREACH_DETECTED"));
        assertEquals(MaintenanceStatus.OVERDUE, DimensionType.MAINTENANCE.resolveStatus("OVERDUE"));
        assertEquals(ProblemManagementStatus.PATTERN_DETECTED, DimensionType.PROBLEM_MANAGEMENT.resolveStatus("PATTERN_DETECTED"));
        assertEquals(DecommissionStatus.COMPLETED, DimensionType.DECOMMISSION.resolveStatus("COMPLETED"));
    }

    @Test
    void resolveStatusThrowsForInvalidName() {
        assertThrows(IllegalArgumentException.class,
                     () -> DimensionType.HEALTH_MONITORING.resolveStatus("NONEXISTENT"));
    }

    @Test
    void defaultStatusForEachDimension() {
        assertEquals(HealthStatus.HEALTHY, DimensionType.HEALTH_MONITORING.defaultStatus());
        assertEquals(ConfigurationDriftStatus.IN_SYNC, DimensionType.CONFIGURATION_DRIFT.defaultStatus());
        assertEquals(ComplianceStatus.COMPLIANT, DimensionType.COMPLIANCE.defaultStatus());
        assertEquals(ScalingStatus.OPTIMAL, DimensionType.SCALING.defaultStatus());
        assertEquals(ChangeManagementStatus.NO_ACTIVITY, DimensionType.CHANGE_MANAGEMENT.defaultStatus());
        assertEquals(SecurityStatus.CLEAR, DimensionType.SECURITY.defaultStatus());
        assertEquals(MaintenanceStatus.NO_ACTIVITY, DimensionType.MAINTENANCE.defaultStatus());
        assertEquals(ProblemManagementStatus.NO_KNOWN_PROBLEMS, DimensionType.PROBLEM_MANAGEMENT.defaultStatus());
        assertEquals(DecommissionStatus.NOT_PLANNED, DimensionType.DECOMMISSION.defaultStatus());
    }


}
