package io.casehub.ops.app.lifecycle.ras;

import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.GanglionBinding;
import io.casehub.ops.api.lifecycle.ManagedServiceCategory;
import io.casehub.ops.api.lifecycle.status.HealthStatus;
import io.casehub.ops.app.lifecycle.DimensionStatusService;
import io.casehub.ops.app.lifecycle.ServiceCaseRegistry;
import io.casehub.ops.app.lifecycle.ServiceDetectionBridge;
import io.casehub.ras.api.DetectionResult;
import io.casehub.ras.api.DetectionSignal;
import io.casehub.ras.api.SituationChangeEvent;
import io.casehub.ras.api.SituationContext;
import io.casehub.ras.api.TimestampedDetection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RasSituationObserverTest {

    private ServiceCaseRegistry registry;
    private ServiceDetectionBridge bridge;
    private RasSituationObserver observer;
    private Map<String, Object> store;

    @BeforeEach
    void setUp() {
        registry = new ServiceCaseRegistry();
        var statusService = new DimensionStatusService();
        store = new HashMap<>();
        bridge = new ServiceDetectionBridge(registry, statusService);
        observer = new RasSituationObserver(bridge,
            (caseId, key, value) -> store.put(key, value),
            (caseId, key) -> store.get(key));
    }

    @Test
    void triggeredEventRoutesToBridge() {
        UUID caseId = registerServiceWithBindings();
        var event = makeEvent("ops:health-rt:" + UUID.randomUUID(), caseId,
            SituationChangeEvent.ChangeType.TRIGGERED,
            "heartbeat-check", 0.95, DetectionSignal.DETECTED);

        observer.onSituation(event);

        var svcCtx = registry.get(caseId);
        assertEquals(HealthStatus.DOWN,
            svcCtx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void resolvedEventIsIgnored() {
        UUID caseId = registerServiceWithBindings();
        var event = makeEvent("ops:health-rt:" + UUID.randomUUID(), caseId,
            SituationChangeEvent.ChangeType.RESOLVED,
            "heartbeat-check", 0.95, DetectionSignal.DETECTED);

        observer.onSituation(event);

        var svcCtx = registry.get(caseId);
        assertEquals(HealthStatus.HEALTHY,
            svcCtx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void nonOpsSituationIsIgnored() {
        UUID caseId = registerServiceWithBindings();
        var event = makeEvent("desiredstate.repeated-failure", caseId,
            SituationChangeEvent.ChangeType.TRIGGERED,
            "heartbeat-check", 0.95, DetectionSignal.DETECTED);

        observer.onSituation(event);

        var svcCtx = registry.get(caseId);
        assertEquals(HealthStatus.HEALTHY,
            svcCtx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void noiseDetectionsAreSkipped() {
        UUID caseId = registerServiceWithBindings();
        var event = makeEvent("ops:health-rt:" + UUID.randomUUID(), caseId,
            SituationChangeEvent.ChangeType.TRIGGERED,
            "heartbeat-check", 0.1, DetectionSignal.NOISE);

        observer.onSituation(event);

        var svcCtx = registry.get(caseId);
        assertEquals(HealthStatus.HEALTHY,
            svcCtx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
    }

    @Test
    void detectionDataIncludesEvidenceAndMetadata() {
        UUID caseId = registerServiceWithBindings();
        var evidence = Map.<String, Object>of("reason", "liveness-fail", "podName", "app-1");
        var result = new DetectionResult("heartbeat-check", 0.95, DetectionSignal.DETECTED, evidence);
        var detection = new TimestampedDetection(result, Instant.parse("2026-08-17T10:00:00Z"));
        var ctx = new SituationContext("sit-1", caseId.toString(), "tenant-a",
            Instant.now(), Instant.now(), List.of(detection), OptionalLong.empty(), null, 1);
        var event = new SituationChangeEvent("tenant-a", "ops:health-rt:" + UUID.randomUUID(),
            caseId.toString(), SituationChangeEvent.ChangeType.TRIGGERED, ctx);

        observer.onSituation(event);

        var section = registry.get(caseId).dimensions().get(DimensionType.HEALTH_MONITORING).section();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = section.get("serviceDown", Map.class);
        assertNotNull(data);
        assertEquals("liveness-fail", data.get("reason"));
        assertEquals(0.95, data.get("confidence"));
        assertEquals("2026-08-17T10:00:00Z", data.get("detectedAt"));
    }

    @Test
    void multipleDetectionsAllRouted() {
        UUID caseId = registerServiceWithBindings();
        var d1 = new TimestampedDetection(
            new DetectionResult("heartbeat-check", 0.9, DetectionSignal.DETECTED, Map.of()), Instant.now());
        var d2 = new TimestampedDetection(
            new DetectionResult("metrics-trend", 0.8, DetectionSignal.DETECTED, Map.of()), Instant.now());
        var ctx = new SituationContext("sit-1", caseId.toString(), "tenant-a",
            Instant.now(), Instant.now(), List.of(d1, d2), OptionalLong.empty(), null, 1);
        var event = new SituationChangeEvent("tenant-a", "ops:health-rt:" + UUID.randomUUID(),
            caseId.toString(), SituationChangeEvent.ChangeType.TRIGGERED, ctx);

        observer.onSituation(event);

        var section = registry.get(caseId).dimensions().get(DimensionType.HEALTH_MONITORING).section();
        assertNotNull(section.get("serviceDown", Map.class));
        assertNotNull(section.get("degraded", Map.class));
    }

    @Test
    void invalidCorrelationKeyIsHandledGracefully() {
        var result = new DetectionResult("heartbeat-check", 0.95, DetectionSignal.DETECTED, Map.of());
        var detection = new TimestampedDetection(result, Instant.now());
        var ctx = new SituationContext("sit-1", "not-a-uuid", "tenant-a",
            Instant.now(), Instant.now(), List.of(detection), OptionalLong.empty(), null, 1);
        var event = new SituationChangeEvent("tenant-a", "ops:health-rt:" + UUID.randomUUID(),
            "not-a-uuid", SituationChangeEvent.ChangeType.TRIGGERED, ctx);

        assertDoesNotThrow(() -> observer.onSituation(event));
    }

    private UUID registerServiceWithBindings() {
        UUID caseId = UUID.randomUUID();
        registry.register(caseId, "order-api", "Order API",
            ManagedServiceCategory.APPLICATION, Map.of(),
            store::put, store::get);
        bridge.registerBindings(caseId, List.of(
            new GanglionBinding("heartbeat-check", DimensionType.HEALTH_MONITORING, "serviceDown", HealthStatus.DOWN),
            new GanglionBinding("heartbeat-recovery", DimensionType.HEALTH_MONITORING, "serviceUp", HealthStatus.HEALTHY),
            new GanglionBinding("metrics-trend", DimensionType.HEALTH_MONITORING, "degraded", HealthStatus.DEGRADED)));
        return caseId;
    }

    private static SituationChangeEvent makeEvent(String situationId, UUID caseId,
                                                    SituationChangeEvent.ChangeType changeType,
                                                    String ganglionId, double confidence,
                                                    DetectionSignal signal) {
        var result = new DetectionResult(ganglionId, confidence, signal, Map.of());
        var detection = new TimestampedDetection(result, Instant.now());
        var ctx = new SituationContext("sit-1", caseId.toString(), "tenant-a",
            Instant.now(), Instant.now(), List.of(detection), OptionalLong.empty(), null, 1);
        return new SituationChangeEvent("tenant-a", situationId,
            caseId.toString(), changeType, ctx);
    }
}
