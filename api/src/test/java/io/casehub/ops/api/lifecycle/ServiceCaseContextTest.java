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

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServiceCaseContextTest {

    @Test
    void createInitializesAllNineDimensions() {
        var store = new HashMap<String, Object>();
        var ctx = ServiceCaseContext.create("order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        assertEquals(9, ctx.dimensions().size());
        for (DimensionType type : DimensionType.values()) {
            assertNotNull(ctx.dimensions().get(type));
        }
    }

    @Test
    void defaultStatusesAreHealthy() {
        var store = new HashMap<String, Object>();
        var ctx = ServiceCaseContext.create("order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        assertEquals(HealthStatus.HEALTHY, ctx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
        assertEquals(ConfigurationDriftStatus.IN_SYNC, ctx.dimensions().get(DimensionType.CONFIGURATION_DRIFT).status());
        assertEquals(ComplianceStatus.COMPLIANT, ctx.dimensions().get(DimensionType.COMPLIANCE).status());
        assertEquals(ScalingStatus.OPTIMAL, ctx.dimensions().get(DimensionType.SCALING).status());
        assertEquals(ChangeManagementStatus.NO_ACTIVITY, ctx.dimensions().get(DimensionType.CHANGE_MANAGEMENT).status());
        assertEquals(SecurityStatus.CLEAR, ctx.dimensions().get(DimensionType.SECURITY).status());
        assertEquals(MaintenanceStatus.NO_ACTIVITY, ctx.dimensions().get(DimensionType.MAINTENANCE).status());
        assertEquals(ProblemManagementStatus.NO_KNOWN_PROBLEMS, ctx.dimensions().get(DimensionType.PROBLEM_MANAGEMENT).status());
        assertEquals(DecommissionStatus.NOT_PLANNED, ctx.dimensions().get(DimensionType.DECOMMISSION).status());
    }

    @Test
    void serviceHealthAggregatesWorstSeverity() {
        var store = new HashMap<String, Object>();
        var ctx = ServiceCaseContext.create("order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        var health = ctx.toServiceHealth();
        assertEquals(Severity.OK, health.overallSeverity());

        ctx.dimensions().get(DimensionType.HEALTH_MONITORING).updateStatus(HealthStatus.DOWN);
        health = ctx.toServiceHealth();
        assertEquals(Severity.CRITICAL, health.overallSeverity());
    }

    @Test
    void serviceHealthContainsAllDimensionStatuses() {
        var store = new HashMap<String, Object>();
        var ctx = ServiceCaseContext.create("order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        var health = ctx.toServiceHealth();
        assertEquals(9, health.dimensions().size());
        assertEquals("order-api", health.serviceId());
        assertEquals("Order API", health.serviceName());
    }

    @Test
    void metadataIsPreserved() {
        var store = new HashMap<String, Object>();
        var ctx = ServiceCaseContext.create("order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of("cluster", "prod", "namespace", "default"),
                store::put, key -> store.get(key));

        assertEquals("prod", ctx.metadata().get("cluster"));
        assertEquals("default", ctx.metadata().get("namespace"));
    }

    @Test
    void metadataIsImmutable() {
        var store = new HashMap<String, Object>();
        var ctx = ServiceCaseContext.create("order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of("cluster", "prod"),
                store::put, key -> store.get(key));

        assertThrows(UnsupportedOperationException.class, () -> ctx.metadata().put("new", "value"));
    }

    @Test
    void createForReconstructionUsesPersistedStatuses() {
        var store    = new HashMap<String, Object>();
        var statuses = new EnumMap<DimensionType, DimensionStatus>(DimensionType.class);
        statuses.put(DimensionType.HEALTH_MONITORING, HealthStatus.DOWN);
        statuses.put(DimensionType.SECURITY, SecurityStatus.VULNERABILITY_DETECTED);

        var ctx = ServiceCaseContext.createForReconstruction(
                "order-api", "Order API", ManagedServiceCategory.APPLICATION,
                Instant.parse("2026-08-01T10:00:00Z"), Map.of(),
                statuses, store::put, store::get);

        assertEquals(HealthStatus.DOWN, ctx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
        assertEquals(SecurityStatus.VULNERABILITY_DETECTED, ctx.dimensions().get(DimensionType.SECURITY).status());
        assertEquals(ComplianceStatus.COMPLIANT, ctx.dimensions().get(DimensionType.COMPLIANCE).status());
    }

    @Test
    void createForReconstructionDimensionsAreNotLoaded() {
        var store = new HashMap<String, Object>();
        var ctx = ServiceCaseContext.createForReconstruction(
                "order-api", "Order API", ManagedServiceCategory.APPLICATION,
                Instant.parse("2026-08-01T10:00:00Z"), Map.of(),
                Map.of(), store::put, store::get);

        for (var dim : ctx.dimensions().values()) {
            assertFalse(dim.isLoaded());
        }
    }

    @Test
    void createForReconstructionPreservesMetadata() {
        var store = new HashMap<String, Object>();
        var ctx = ServiceCaseContext.createForReconstruction(
                "order-api", "Order API", ManagedServiceCategory.APPLICATION,
                Instant.parse("2026-08-01T10:00:00Z"), Map.of("cluster", "prod"),
                Map.of(), store::put, store::get);

        assertEquals("order-api", ctx.serviceId());
        assertEquals("Order API", ctx.serviceName());
        assertEquals(ManagedServiceCategory.APPLICATION, ctx.category());
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), ctx.deployedAt());
        assertEquals("prod", ctx.metadata().get("cluster"));
    }

    @Test
    void createForReconstructionToServiceHealthUsesPersistedStatuses() {
        var store    = new HashMap<String, Object>();
        var statuses = new EnumMap<DimensionType, DimensionStatus>(DimensionType.class);
        statuses.put(DimensionType.HEALTH_MONITORING, HealthStatus.DOWN);

        var ctx = ServiceCaseContext.createForReconstruction(
                "order-api", "Order API", ManagedServiceCategory.APPLICATION,
                Instant.parse("2026-08-01T10:00:00Z"), Map.of(),
                statuses, store::put, store::get);

        var health = ctx.toServiceHealth();
        assertEquals(Severity.CRITICAL, health.overallSeverity());
        assertEquals(HealthStatus.DOWN, health.dimensions().get(DimensionType.HEALTH_MONITORING));
    }


}
