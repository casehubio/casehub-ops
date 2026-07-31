package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.CaseRef;
import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.GanglionBinding;
import io.casehub.ops.api.lifecycle.ManagedServiceCategory;
import io.casehub.ops.api.lifecycle.Severity;
import io.casehub.ops.api.lifecycle.status.ComplianceStatus;
import io.casehub.ops.api.lifecycle.status.DecommissionStatus;
import io.casehub.ops.api.lifecycle.status.HealthStatus;
import io.casehub.ops.api.lifecycle.status.SecurityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceLifecycleIntegrationTest {

    private ServiceCaseRegistry registry;
    private DimensionStatusService statusService;
    private ServiceDetectionBridge bridge;
    private Map<String, Object> store;

    @BeforeEach
    void setUp() {
        registry = new ServiceCaseRegistry();
        statusService = new DimensionStatusService();
        bridge = new ServiceDetectionBridge(registry, statusService);
        store = new HashMap<>();
    }

    @Test
    void fullLifecycle_deploy_detect_remediate_decommission() {
        UUID caseId = UUID.randomUUID();
        registry.register(caseId, "order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of("cluster", "prod"),
                store::put, key -> store.get(key));

        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("heartbeat-failure", DimensionType.HEALTH_MONITORING,
                        "serviceDown", HealthStatus.DOWN),
                new GanglionBinding("heartbeat-ok", DimensionType.HEALTH_MONITORING,
                        "serviceUp", HealthStatus.HEALTHY),
                new GanglionBinding("decommission-complete", DimensionType.DECOMMISSION,
                        "completed", DecommissionStatus.COMPLETED)));

        var ctx = registry.get(caseId);
        var health = statusService.recomputeAll(ctx);
        assertEquals(Severity.OK, health.overallSeverity());

        bridge.onDetection("heartbeat-failure", caseId, Map.of("time", "now"));
        health = ctx.toServiceHealth();
        assertEquals(Severity.CRITICAL, health.overallSeverity());
        assertEquals(HealthStatus.DOWN, health.dimensions().get(DimensionType.HEALTH_MONITORING));

        bridge.onDetection("heartbeat-ok", caseId, Map.of("time", "later"));
        health = ctx.toServiceHealth();
        assertEquals(Severity.OK, health.overallSeverity());
        assertEquals(HealthStatus.HEALTHY, health.dimensions().get(DimensionType.HEALTH_MONITORING));

        bridge.onDetection("decommission-complete", caseId, Map.of());
        var decommissionStatus = ctx.dimensions().get(DimensionType.DECOMMISSION).status();
        assertInstanceOf(DecommissionStatus.class, decommissionStatus);
        assertTrue(decommissionStatus.isTerminal());
    }

    @Test
    void crossDimensionDetection_cveAffectsSecurityAndCompliance() {
        UUID caseId = UUID.randomUUID();
        registry.register(caseId, "payment-svc", "Payment Service",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("cve-detected", DimensionType.SECURITY,
                        "vulnerabilityFound", SecurityStatus.VULNERABILITY_DETECTED),
                new GanglionBinding("cve-detected", DimensionType.COMPLIANCE,
                        "controlViolation", ComplianceStatus.NON_COMPLIANT)));

        bridge.onDetection("cve-detected", caseId, Map.of("cve", "CVE-2026-9999"));

        var ctx = registry.get(caseId);
        assertEquals(SecurityStatus.VULNERABILITY_DETECTED,
                ctx.dimensions().get(DimensionType.SECURITY).status());
        assertEquals(ComplianceStatus.NON_COMPLIANT,
                ctx.dimensions().get(DimensionType.COMPLIANCE).status());

        var health = ctx.toServiceHealth();
        assertEquals(Severity.CRITICAL, health.overallSeverity());
    }

    @Test
    void activeResponseChangesStatusToRemediating() {
        UUID caseId = UUID.randomUUID();
        registry.register(caseId, "order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("heartbeat-failure", DimensionType.HEALTH_MONITORING,
                        "serviceDown", HealthStatus.DOWN)));

        bridge.onDetection("heartbeat-failure", caseId, Map.of());
        var ctx = registry.get(caseId);
        assertEquals(HealthStatus.DOWN, ctx.dimensions().get(DimensionType.HEALTH_MONITORING).status());

        ctx.dimensions().get(DimensionType.HEALTH_MONITORING)
                .addResponse(new CaseRef(UUID.randomUUID(), "health:incident-response", Instant.now()));
        statusService.recompute(ctx.dimensions().get(DimensionType.HEALTH_MONITORING));

        assertEquals(HealthStatus.REMEDIATING, ctx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void dimensionBindingsMap_coversAllNineDimensions() {
        var bindings = ServiceCaseDescriptor.dimensionBindings();
        assertEquals(9, bindings.size());
        for (DimensionType type : DimensionType.values()) {
            assertTrue(bindings.containsKey(type));
        }
    }

    @Test
    void multipleServicesIsolated() {
        UUID caseId1 = UUID.randomUUID();
        UUID caseId2 = UUID.randomUUID();
        registry.register(caseId1, "order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));
        registry.register(caseId2, "payment-svc", "Payment Service",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        bridge.registerBindings(caseId1, List.of(
                new GanglionBinding("heartbeat-failure", DimensionType.HEALTH_MONITORING,
                        "serviceDown", HealthStatus.DOWN)));
        bridge.registerBindings(caseId2, List.of());

        bridge.onDetection("heartbeat-failure", caseId1, Map.of());

        assertEquals(HealthStatus.DOWN, registry.get(caseId1).dimensions().get(DimensionType.HEALTH_MONITORING).status());
        assertEquals(HealthStatus.HEALTHY, registry.get(caseId2).dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }
}
