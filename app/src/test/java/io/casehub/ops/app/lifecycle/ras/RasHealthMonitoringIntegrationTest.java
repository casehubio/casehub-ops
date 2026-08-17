package io.casehub.ops.app.lifecycle.ras;

import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.ManagedServiceCategory;
import io.casehub.ops.api.lifecycle.status.HealthStatus;
import io.casehub.ops.api.lifecycle.status.SecurityStatus;
import io.casehub.ops.app.lifecycle.DimensionStatusService;
import io.casehub.ops.app.lifecycle.ServiceCaseRegistry;
import io.casehub.ops.app.lifecycle.ServiceDetectionBridge;
import io.casehub.ras.api.DetectionResult;
import io.casehub.ras.api.DetectionSignal;
import io.casehub.ras.api.SituationChangeEvent;
import io.casehub.ras.api.SituationContext;
import io.casehub.ras.api.SituationRegistrar;
import io.casehub.ras.api.SituationRegistration;
import io.casehub.ras.api.SituationStore;
import io.casehub.ras.api.TimestampedDetection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RasHealthMonitoringIntegrationTest {

    private ServiceCaseRegistry registry;
    private ServiceDetectionBridge bridge;
    private ServiceMonitoringRegistrar registrar;
    private RasSituationObserver observer;
    private Map<String, Object> store;
    private List<SituationRegistration> registeredSituations;

    @BeforeEach
    void setUp() {
        registry = new ServiceCaseRegistry();
        var statusService = new DimensionStatusService();
        store = new HashMap<>();
        bridge = new ServiceDetectionBridge(registry, statusService);
        registeredSituations = new ArrayList<>();

        SituationRegistrar stubRegistrar = new SituationRegistrar() {
            @Override public void register(SituationRegistration r) { registeredSituations.add(r); }
            @Override public void deregister(String id) {}
            @Override public boolean exists(String id) { return false; }
        };
        SituationStore stubStore = new SituationStore() {
            @Override public Optional<SituationContext> find(String s, String c, String t) { return Optional.empty(); }
            @Override public SituationContext save(SituationContext ctx) { return ctx; }
            @Override public void remove(String s, String c, String t) {}
            @Override public int removeExpired(Instant cutoff) { return 0; }
            @Override public void removeAllForSituation(String situationId) {}
        };
        registrar = new ServiceMonitoringRegistrar(stubRegistrar, bridge, stubStore);
        observer = new RasSituationObserver(bridge,
            (caseId, key, value) -> store.put(key, value),
            (caseId, key) -> store.get(key));
    }

    @Test
    void endToEnd_deploy_detect_route_recover() {
        UUID appId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        registry.register(caseId, appId.toString(), "order-api",
            ManagedServiceCategory.APPLICATION, Map.of(), store::put, store::get);
        registrar.register(appId, caseId);

        assertEquals(15, registeredSituations.size());

        var failResult = new DetectionResult("heartbeat-check", 0.95, DetectionSignal.DETECTED,
            Map.of("reason", "liveness-fail"));
        var failDetection = new TimestampedDetection(failResult, Instant.now());
        var failCtx = new SituationContext("ops:health-rt:" + appId, caseId.toString(), "tenant-a",
            Instant.now(), Instant.now(), List.of(failDetection), OptionalLong.empty(), null, 1);
        var failEvent = new SituationChangeEvent("tenant-a", "ops:health-rt:" + appId,
            caseId.toString(), SituationChangeEvent.ChangeType.TRIGGERED, failCtx);

        observer.onSituation(failEvent);

        var svcCtx = registry.get(caseId);
        assertEquals(HealthStatus.DOWN,
            svcCtx.dimensions().get(DimensionType.HEALTH_MONITORING).status());

        var section = svcCtx.dimensions().get(DimensionType.HEALTH_MONITORING).section();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = section.get("serviceDown", Map.class);
        assertNotNull(data);
        assertEquals("liveness-fail", data.get("reason"));

        var recoveryResult = new DetectionResult("heartbeat-recovery", 0.95, DetectionSignal.DETECTED, Map.of());
        var recoveryDetection = new TimestampedDetection(recoveryResult, Instant.now());
        var recoveryCtx = new SituationContext("ops:health-rt:" + appId, caseId.toString(), "tenant-a",
            Instant.now(), Instant.now(), List.of(recoveryDetection), OptionalLong.empty(), null, 2);
        var recoveryEvent = new SituationChangeEvent("tenant-a", "ops:health-rt:" + appId,
            caseId.toString(), SituationChangeEvent.ChangeType.TRIGGERED, recoveryCtx);

        observer.onSituation(recoveryEvent);

        assertEquals(HealthStatus.HEALTHY,
            svcCtx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void crossDimension_securityDetection() {
        UUID appId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        registry.register(caseId, appId.toString(), "order-api",
            ManagedServiceCategory.APPLICATION, Map.of(), store::put, store::get);
        registrar.register(appId, caseId);

        var cveResult = new DetectionResult("cve-scanner", 0.99, DetectionSignal.DETECTED,
            Map.of("cveId", "CVE-2026-1234"));
        var cveDetection = new TimestampedDetection(cveResult, Instant.now());
        var cveCtx = new SituationContext("ops:security-pd:" + appId, caseId.toString(), "tenant-a",
            Instant.now(), Instant.now(), List.of(cveDetection), OptionalLong.empty(), null, 1);
        var cveEvent = new SituationChangeEvent("tenant-a", "ops:security-pd:" + appId,
            caseId.toString(), SituationChangeEvent.ChangeType.TRIGGERED, cveCtx);

        observer.onSituation(cveEvent);

        var svcCtx = registry.get(caseId);
        assertEquals(SecurityStatus.VULNERABILITY_DETECTED,
            svcCtx.dimensions().get(DimensionType.SECURITY).status());
    }

    @Test
    void deregister_removesBindings() {
        UUID appId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        registry.register(caseId, appId.toString(), "order-api",
            ManagedServiceCategory.APPLICATION, Map.of(), store::put, store::get);
        registrar.register(appId, caseId);

        registrar.deregister(appId, caseId);

        var failResult = new DetectionResult("heartbeat-check", 0.95, DetectionSignal.DETECTED, Map.of());
        var failDetection = new TimestampedDetection(failResult, Instant.now());
        var failCtx = new SituationContext("ops:health-rt:" + appId, caseId.toString(), "tenant-a",
            Instant.now(), Instant.now(), List.of(failDetection), OptionalLong.empty(), null, 1);
        var failEvent = new SituationChangeEvent("tenant-a", "ops:health-rt:" + appId,
            caseId.toString(), SituationChangeEvent.ChangeType.TRIGGERED, failCtx);

        observer.onSituation(failEvent);

        assertEquals(HealthStatus.HEALTHY,
            registry.get(caseId).dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }
}
