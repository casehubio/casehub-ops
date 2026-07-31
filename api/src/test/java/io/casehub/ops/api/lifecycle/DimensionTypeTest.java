package io.casehub.ops.api.lifecycle;

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
}
