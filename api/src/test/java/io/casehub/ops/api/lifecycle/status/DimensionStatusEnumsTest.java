package io.casehub.ops.api.lifecycle.status;

import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.Severity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DimensionStatusEnumsTest {

    @Test
    void healthStatusSeverityMapping() {
        assertEquals(Severity.OK, HealthStatus.HEALTHY.severity());
        assertEquals(Severity.WARNING, HealthStatus.DEGRADED.severity());
        assertEquals(Severity.CRITICAL, HealthStatus.DOWN.severity());
        assertEquals(Severity.INFO, HealthStatus.INVESTIGATING.severity());
        assertEquals(Severity.WARNING, HealthStatus.REMEDIATING.severity());
    }

    @Test
    void configDriftStatusSeverityMapping() {
        assertEquals(Severity.OK, ConfigurationDriftStatus.IN_SYNC.severity());
        assertEquals(Severity.WARNING, ConfigurationDriftStatus.DRIFTED.severity());
        assertEquals(Severity.INFO, ConfigurationDriftStatus.RECONCILING.severity());
        assertEquals(Severity.CRITICAL, ConfigurationDriftStatus.RECONCILIATION_FAILED.severity());
    }

    @Test
    void complianceStatusSeverityMapping() {
        assertEquals(Severity.OK, ComplianceStatus.COMPLIANT.severity());
        assertEquals(Severity.WARNING, ComplianceStatus.STALE_EVIDENCE.severity());
        assertEquals(Severity.CRITICAL, ComplianceStatus.NON_COMPLIANT.severity());
        assertEquals(Severity.WARNING, ComplianceStatus.REMEDIATING.severity());
        assertEquals(Severity.WARNING, ComplianceStatus.UNAVAILABLE.severity());
    }

    @Test
    void scalingStatusSeverityMapping() {
        assertEquals(Severity.OK, ScalingStatus.OPTIMAL.severity());
        assertEquals(Severity.WARNING, ScalingStatus.UNDER_PROVISIONED.severity());
        assertEquals(Severity.INFO, ScalingStatus.OVER_PROVISIONED.severity());
        assertEquals(Severity.INFO, ScalingStatus.SCALING.severity());
        assertEquals(Severity.CRITICAL, ScalingStatus.SCALE_FAILED.severity());
    }

    @Test
    void changeManagementStatusSeverityMapping() {
        assertEquals(Severity.OK, ChangeManagementStatus.NO_ACTIVITY.severity());
        assertEquals(Severity.INFO, ChangeManagementStatus.UPGRADE_AVAILABLE.severity());
        assertEquals(Severity.INFO, ChangeManagementStatus.CANARY.severity());
        assertEquals(Severity.INFO, ChangeManagementStatus.ROLLING_OUT.severity());
        assertEquals(Severity.WARNING, ChangeManagementStatus.ROLLBACK.severity());
        assertEquals(Severity.CRITICAL, ChangeManagementStatus.CHANGE_FAILED.severity());
    }

    @Test
    void securityStatusSeverityMapping() {
        assertEquals(Severity.OK, SecurityStatus.CLEAR.severity());
        assertEquals(Severity.WARNING, SecurityStatus.VULNERABILITY_DETECTED.severity());
        assertEquals(Severity.INFO, SecurityStatus.PATCHING.severity());
        assertEquals(Severity.CRITICAL, SecurityStatus.BREACH_DETECTED.severity());
        assertEquals(Severity.WARNING, SecurityStatus.INVESTIGATING.severity());
    }

    @Test
    void maintenanceStatusSeverityMapping() {
        assertEquals(Severity.OK, MaintenanceStatus.NO_ACTIVITY.severity());
        assertEquals(Severity.INFO, MaintenanceStatus.SCHEDULED.severity());
        assertEquals(Severity.INFO, MaintenanceStatus.IN_PROGRESS.severity());
        assertEquals(Severity.WARNING, MaintenanceStatus.OVERDUE.severity());
        assertEquals(Severity.CRITICAL, MaintenanceStatus.FAILED.severity());
    }

    @Test
    void problemManagementStatusSeverityMapping() {
        assertEquals(Severity.OK, ProblemManagementStatus.NO_KNOWN_PROBLEMS.severity());
        assertEquals(Severity.WARNING, ProblemManagementStatus.PATTERN_DETECTED.severity());
        assertEquals(Severity.INFO, ProblemManagementStatus.INVESTIGATING.severity());
        assertEquals(Severity.WARNING, ProblemManagementStatus.WORKAROUND_APPLIED.severity());
        assertEquals(Severity.OK, ProblemManagementStatus.ROOT_CAUSE_FIXED.severity());
    }

    @Test
    void decommissionStatusSeverityMapping() {
        assertEquals(Severity.OK, DecommissionStatus.NOT_PLANNED.severity());
        assertEquals(Severity.INFO, DecommissionStatus.SCHEDULED.severity());
        assertEquals(Severity.WARNING, DecommissionStatus.IN_PROGRESS.severity());
        assertEquals(Severity.CRITICAL, DecommissionStatus.BLOCKED.severity());
        assertEquals(Severity.OK, DecommissionStatus.COMPLETED.severity());
    }

    @Test
    void onlyDecommissionCompletedIsTerminal() {
        for (HealthStatus s : HealthStatus.values()) assertFalse(s.isTerminal());
        for (ConfigurationDriftStatus s : ConfigurationDriftStatus.values()) assertFalse(s.isTerminal());
        for (ComplianceStatus s : ComplianceStatus.values()) assertFalse(s.isTerminal());
        for (ScalingStatus s : ScalingStatus.values()) assertFalse(s.isTerminal());
        for (ChangeManagementStatus s : ChangeManagementStatus.values()) assertFalse(s.isTerminal());
        for (SecurityStatus s : SecurityStatus.values()) assertFalse(s.isTerminal());
        for (MaintenanceStatus s : MaintenanceStatus.values()) assertFalse(s.isTerminal());
        for (ProblemManagementStatus s : ProblemManagementStatus.values()) assertFalse(s.isTerminal());

        assertFalse(DecommissionStatus.NOT_PLANNED.isTerminal());
        assertFalse(DecommissionStatus.SCHEDULED.isTerminal());
        assertFalse(DecommissionStatus.IN_PROGRESS.isTerminal());
        assertFalse(DecommissionStatus.BLOCKED.isTerminal());
        assertTrue(DecommissionStatus.COMPLETED.isTerminal());
    }

    @Test
    void allStatusEnumsImplementDimensionStatus() {
        assertInstanceOf(DimensionStatus.class, HealthStatus.HEALTHY);
        assertInstanceOf(DimensionStatus.class, ConfigurationDriftStatus.IN_SYNC);
        assertInstanceOf(DimensionStatus.class, ComplianceStatus.COMPLIANT);
        assertInstanceOf(DimensionStatus.class, ScalingStatus.OPTIMAL);
        assertInstanceOf(DimensionStatus.class, ChangeManagementStatus.NO_ACTIVITY);
        assertInstanceOf(DimensionStatus.class, SecurityStatus.CLEAR);
        assertInstanceOf(DimensionStatus.class, MaintenanceStatus.NO_ACTIVITY);
        assertInstanceOf(DimensionStatus.class, ProblemManagementStatus.NO_KNOWN_PROBLEMS);
        assertInstanceOf(DimensionStatus.class, DecommissionStatus.NOT_PLANNED);
    }

    @Test
    void labelReturnsHumanReadableString() {
        assertEquals("Healthy", HealthStatus.HEALTHY.label());
        assertEquals("Down", HealthStatus.DOWN.label());
        assertEquals("Remediating", HealthStatus.REMEDIATING.label());
        assertEquals("In Sync", ConfigurationDriftStatus.IN_SYNC.label());
        assertEquals("Non-Compliant", ComplianceStatus.NON_COMPLIANT.label());
    }
}
