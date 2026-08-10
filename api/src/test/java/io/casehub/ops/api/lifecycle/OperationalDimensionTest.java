package io.casehub.ops.api.lifecycle;

import io.casehub.ops.api.lifecycle.status.HealthStatus;
import io.casehub.ops.api.lifecycle.status.SecurityStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperationalDimensionTest {

    @Test
    void severityDelegatesToStatus() {
        var dim = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.DOWN);
        assertEquals(Severity.CRITICAL, dim.severity());
    }

    @Test
    void activeResponsesStartEmpty() {
        var dim = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY);
        assertTrue(dim.activeResponses().isEmpty());
    }

    @Test
    void addAndRemoveActiveResponse() {
        var dim = createDimension(DimensionType.SECURITY, SecurityStatus.CLEAR);
        var ref = new CaseRef(UUID.randomUUID(), "security:patch", Instant.now());

        dim.addResponse(ref);
        assertEquals(1, dim.activeResponses().size());
        assertEquals(ref, dim.activeResponses().get(0));

        dim.removeResponse(ref.caseId());
        assertTrue(dim.activeResponses().isEmpty());
    }

    @Test
    void statusCanBeUpdated() {
        var dim = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY);
        dim.updateStatus(HealthStatus.DOWN);
        assertEquals(HealthStatus.DOWN, dim.status());
        assertEquals(Severity.CRITICAL, dim.severity());
    }

    @Test
    void subscriptionsAreImmutable() {
        var binding = new GanglionBinding("heartbeat", DimensionType.HEALTH_MONITORING, "serviceDown", HealthStatus.DOWN);
        var dim     = createDimensionWithBindings(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY, List.of(binding));
        assertEquals(1, dim.subscriptions().size());
        assertThrows(UnsupportedOperationException.class, () -> dim.subscriptions().add(binding));
    }

    @Test
    void freshDimensionIsLoaded() {
        var dim = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY);
        assertTrue(dim.isLoaded());
    }

    @Test
    void loadPopulatesActiveResponsesFromReader() {
        var store   = new HashMap<String, Object>();
        var section = new DimensionSection(DimensionType.HEALTH_MONITORING, store::put, store::get);
        var dim = new OperationalDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY,
                                           section, List.of(), false);

        var refId = UUID.randomUUID();
        store.put("health.activeResponseIds", List.of(
                Map.<String, Object>of("caseId", refId.toString(),
                                       "bindingName", "health:incident-response",
                                       "createdAt", "2026-08-01T10:00:00Z")));

        assertFalse(dim.isLoaded());
        dim.load(store::get);
        assertTrue(dim.isLoaded());
        assertEquals(1, dim.activeResponses().size());
        assertEquals(refId, dim.activeResponses().get(0).caseId());
        assertEquals("health:incident-response", dim.activeResponses().get(0).bindingName());
    }

    @Test
    void loadWithNoPersistedDataSetsLoadedWithEmptyResponses() {
        var store   = new HashMap<String, Object>();
        var section = new DimensionSection(DimensionType.SECURITY, store::put, store::get);
        var dim = new OperationalDimension(DimensionType.SECURITY, SecurityStatus.CLEAR,
                                           section, List.of(), false);

        dim.load(store::get);
        assertTrue(dim.isLoaded());
        assertTrue(dim.activeResponses().isEmpty());
    }

    @Test
    void loadIsIdempotent() {
        var store   = new HashMap<String, Object>();
        var section = new DimensionSection(DimensionType.HEALTH_MONITORING, store::put, store::get);
        var dim = new OperationalDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY,
                                           section, List.of(), false);

        store.put("health.activeResponseIds", List.of(
                Map.<String, Object>of("caseId", UUID.randomUUID().toString(),
                                       "bindingName", "health:incident-response",
                                       "createdAt", "2026-08-01T10:00:00Z")));

        dim.load(store::get);
        int countAfterFirstLoad = dim.activeResponses().size();
        dim.load(store::get);
        assertEquals(countAfterFirstLoad, dim.activeResponses().size());
    }


    private OperationalDimension createDimension(DimensionType type, DimensionStatus status) {
        var store   = new HashMap<String, Object>();
        var section = new DimensionSection(type, store::put, key -> store.get(key));
        return new OperationalDimension(type, status, section, List.of());
    }

    private OperationalDimension createDimensionWithBindings(DimensionType type, DimensionStatus status, List<GanglionBinding> bindings) {
        var store   = new HashMap<String, Object>();
        var section = new DimensionSection(type, store::put, key -> store.get(key));
        return new OperationalDimension(type, status, section, bindings);
    }
}
