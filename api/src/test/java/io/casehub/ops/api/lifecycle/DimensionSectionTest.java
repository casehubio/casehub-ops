package io.casehub.ops.api.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class DimensionSectionTest {

    @Test
    void putAndGetDelegatesToAccessorWithPrefix() {
        var store = new HashMap<String, Object>();
        var section = new DimensionSection(
                DimensionType.HEALTH_MONITORING,
                store::put,
                key -> store.get(key)
        );

        section.put("consecutiveFailures", 3);
        assertEquals(3, section.get("consecutiveFailures", Integer.class));
        assertTrue(store.containsKey("health.consecutiveFailures"));
    }

    @Test
    void prefixIsolatesDimensions() {
        var store = new HashMap<String, Object>();
        var health = new DimensionSection(
                DimensionType.HEALTH_MONITORING,
                store::put,
                key -> store.get(key)
        );
        var security = new DimensionSection(
                DimensionType.SECURITY,
                store::put,
                key -> store.get(key)
        );

        health.put("status", "DOWN");
        security.put("status", "CLEAR");

        assertEquals("DOWN", health.get("status", String.class));
        assertEquals("CLEAR", security.get("status", String.class));
        assertEquals("DOWN", store.get("health.status"));
        assertEquals("CLEAR", store.get("security.status"));
    }

    @Test
    void lastUpdatedTracksWriteTime() {
        var store = new HashMap<String, Object>();
        var section = new DimensionSection(
                DimensionType.SCALING,
                store::put,
                key -> store.get(key)
        );

        Instant before = Instant.now();
        section.put("currentReplicas", 3);
        Instant after = Instant.now();

        assertNotNull(section.lastUpdated());
        assertFalse(section.lastUpdated().isBefore(before));
        assertFalse(section.lastUpdated().isAfter(after));
    }

    @Test
    void getReturnsNullForMissingKey() {
        var store = new HashMap<String, Object>();
        var section = new DimensionSection(
                DimensionType.COMPLIANCE,
                store::put,
                key -> store.get(key)
        );

        assertNull(section.get("nonExistent", String.class));
    }

    @Test
    void typeReturnsConfiguredDimensionType() {
        var store = new HashMap<String, Object>();
        var section = new DimensionSection(
                DimensionType.DECOMMISSION,
                store::put,
                key -> store.get(key)
        );

        assertEquals(DimensionType.DECOMMISSION, section.type());
    }
}
