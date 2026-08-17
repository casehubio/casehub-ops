package io.casehub.ops.app.lifecycle.ras;

import io.casehub.ops.api.lifecycle.OpsCloudEventTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OpsCloudEventTypesTest {

    @Test
    void allConstantsStartWithIoCasehubOps() throws Exception {
        for (Field f : OpsCloudEventTypes.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String value = (String) f.get(null);
                assertTrue(value.startsWith("io.casehub.ops."),
                    f.getName() + " = " + value + " does not start with io.casehub.ops.");
            }
        }
    }

    @Test
    void allConstantsAreUnique() throws Exception {
        Set<String> values = new HashSet<>();
        for (Field f : OpsCloudEventTypes.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String value = (String) f.get(null);
                assertTrue(values.add(value), "Duplicate value: " + value);
            }
        }
    }

    @Test
    void has32Constants() throws Exception {
        long count = 0;
        for (Field f : OpsCloudEventTypes.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) count++;
        }
        assertEquals(32, count);
    }

    @Test
    void spotCheckKnownConstants() {
        assertEquals("io.casehub.ops.health.probe", OpsCloudEventTypes.HEALTH_PROBE);
        assertEquals("io.casehub.ops.security.cve", OpsCloudEventTypes.SECURITY_CVE);
        assertEquals("io.casehub.ops.decommission.traffic", OpsCloudEventTypes.DECOMMISSION_TRAFFIC);
        assertEquals("io.casehub.ops.scaling.cpu", OpsCloudEventTypes.SCALING_CPU);
        assertEquals("io.casehub.ops.compliance.evidence", OpsCloudEventTypes.COMPLIANCE_EVIDENCE);
    }
}
