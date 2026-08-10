package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.CaseRef;
import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.GanglionBinding;
import io.casehub.ops.api.lifecycle.ManagedServiceCategory;
import io.casehub.ops.api.lifecycle.status.ComplianceStatus;
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

class ServiceDetectionBridgeTest {

    private ServiceCaseRegistry registry;
    private DimensionStatusService statusService;
    private ServiceDetectionBridge bridge;
    private Map<String, Object> store;

    @BeforeEach
    void setUp() {
        registry = new ServiceCaseRegistry();
        statusService = new DimensionStatusService();
        store = new HashMap<>();
        bridge = new ServiceDetectionBridge(registry, statusService);
    }

    @Test
    void detectionWritesToDimensionSection() {
        UUID caseId = registerService();
        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("heartbeat-failure", DimensionType.HEALTH_MONITORING,
                        "serviceDown", HealthStatus.DOWN)));

        bridge.onDetection("heartbeat-failure", caseId, Map.of("timestamp", "2026-07-30T12:00:00Z"));

        var ctx = registry.get(caseId);
        var healthSection = ctx.dimensions().get(DimensionType.HEALTH_MONITORING).section();
        assertNotNull(healthSection.get("serviceDown", Map.class));
    }

    @Test
    void detectionUpdatesConditionStatus() {
        UUID caseId = registerService();
        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("heartbeat-failure", DimensionType.HEALTH_MONITORING,
                        "serviceDown", HealthStatus.DOWN)));

        bridge.onDetection("heartbeat-failure", caseId, Map.of());

        var ctx = registry.get(caseId);
        assertEquals(HealthStatus.DOWN, ctx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void oneDetectionFeedsMultipleDimensions() {
        UUID caseId = registerService();
        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("cve-detected", DimensionType.SECURITY,
                        "vulnerabilityFound", SecurityStatus.VULNERABILITY_DETECTED),
                new GanglionBinding("cve-detected", DimensionType.COMPLIANCE,
                        "controlViolation", ComplianceStatus.NON_COMPLIANT)));

        bridge.onDetection("cve-detected", caseId, Map.of("cve", "CVE-2026-1234"));

        var ctx = registry.get(caseId);
        assertEquals(SecurityStatus.VULNERABILITY_DETECTED,
                ctx.dimensions().get(DimensionType.SECURITY).status());
        assertEquals(ComplianceStatus.NON_COMPLIANT,
                ctx.dimensions().get(DimensionType.COMPLIANCE).status());
    }

    @Test
    void unknownSituationTypeIsIgnored() {
        UUID caseId = registerService();
        bridge.registerBindings(caseId, List.of());

        bridge.onDetection("unknown-type", caseId, Map.of());

        var ctx = registry.get(caseId);
        assertEquals(HealthStatus.HEALTHY, ctx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void unknownCaseIdIsIgnored() {
        bridge.onDetection("heartbeat-failure", UUID.randomUUID(), Map.of());
    }

    @Test
    void recoveryDetectionRestoresHealthyStatus() {
        UUID caseId = registerService();
        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("heartbeat-failure", DimensionType.HEALTH_MONITORING,
                        "serviceDown", HealthStatus.DOWN),
                new GanglionBinding("heartbeat-ok", DimensionType.HEALTH_MONITORING,
                        "serviceUp", HealthStatus.HEALTHY)));

        bridge.onDetection("heartbeat-failure", caseId, Map.of());
        assertEquals(HealthStatus.DOWN, registry.get(caseId).dimensions().get(DimensionType.HEALTH_MONITORING).status());

        bridge.onDetection("heartbeat-ok", caseId, Map.of());
        assertEquals(HealthStatus.HEALTHY, registry.get(caseId).dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void deregisterBindingsStopsRouting() {
        UUID caseId = registerService();
        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("heartbeat-failure", DimensionType.HEALTH_MONITORING,
                        "serviceDown", HealthStatus.DOWN)));

        bridge.deregisterBindings(caseId);
        bridge.onDetection("heartbeat-failure", caseId, Map.of());

        assertEquals(HealthStatus.HEALTHY, registry.get(caseId).dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void bindingWithNullConditionStatusWritesDataOnly() {
        UUID caseId = registerService();
        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("metric-update", DimensionType.SCALING,
                        "metricData", null)));

        bridge.onDetection("metric-update", caseId, Map.of("cpu", 85));

        var ctx = registry.get(caseId);
        var scalingSection = ctx.dimensions().get(DimensionType.SCALING).section();
        assertNotNull(scalingSection.get("metricData", Map.class));
    }

    @Test
    void detectionOnNonLoadedDimensionLoadsItFirst() {
        var  localStore = new HashMap<String, Object>();
        UUID caseId     = UUID.randomUUID();

        localStore.put("service.serviceId", "order-api");
        localStore.put("service.serviceName", "Order API");
        localStore.put("service.category", "APPLICATION");
        localStore.put("service.deployedAt", "2026-08-01T10:00:00Z");
        localStore.put("health.activeResponseIds", List.of(
                Map.<String, Object>of("caseId", UUID.randomUUID().toString(),
                                       "bindingName", "health:incident-response",
                                       "createdAt", "2026-08-01T10:00:00Z")));

        bridge.registerBindings(caseId, List.of(
                new GanglionBinding("heartbeat-failure", DimensionType.HEALTH_MONITORING,
                                    "serviceDown", HealthStatus.DOWN)));

        bridge.onDetection("heartbeat-failure", caseId, localStore::put, localStore::get, Map.of());

        var ctx = registry.getOrReconstruct(caseId, localStore::put, localStore::get);
        var dim = ctx.dimensions().get(DimensionType.HEALTH_MONITORING);
        assertTrue(dim.isLoaded());
        assertEquals(1, dim.activeResponses().size());
        assertEquals(HealthStatus.REMEDIATING, dim.status());
    }

    @Test
    void addResponseAndPersistWritesToSection() {
        UUID caseId = registerService();
        var  ref    = new CaseRef(UUID.randomUUID(), "health:incident-response", Instant.now());

        bridge.addResponseAndPersist(caseId, DimensionType.HEALTH_MONITORING, ref);

        var ctx = registry.get(caseId);
        var dim = ctx.dimensions().get(DimensionType.HEALTH_MONITORING);
        assertEquals(1, dim.activeResponses().size());
        assertNotNull(store.get("health.activeResponseIds"));
    }

    @Test
    void removeResponseAndPersistWritesToSection() {
        UUID caseId = registerService();
        var  ref    = new CaseRef(UUID.randomUUID(), "health:incident-response", Instant.now());
        bridge.addResponseAndPersist(caseId, DimensionType.HEALTH_MONITORING, ref);

        bridge.removeResponseAndPersist(caseId, DimensionType.HEALTH_MONITORING, ref.caseId());

        var ctx = registry.get(caseId);
        var dim = ctx.dimensions().get(DimensionType.HEALTH_MONITORING);
        assertTrue(dim.activeResponses().isEmpty());
        var persisted = store.get("health.activeResponseIds");
        assertNotNull(persisted);
        assertTrue(((List<?>) persisted).isEmpty());
    }


    private UUID registerService() {
        UUID caseId = UUID.randomUUID();
        registry.register(caseId, "order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));
        return caseId;
    }
}
