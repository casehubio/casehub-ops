package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.DimensionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServiceCaseDescriptorTest {

    @Test
    void buildReturnsCaseDefinition() {
        var def = ServiceCaseDescriptor.build();
        assertNotNull(def);
        assertEquals("ops", def.getNamespace());
        assertEquals("service-lifecycle", def.getName());
    }

    @Test
    void allNineDimensionsHaveBindings() {
        Map<DimensionType, List<String>> bindings = ServiceCaseDescriptor.dimensionBindings();
        assertEquals(9, bindings.size());
        for (DimensionType type : DimensionType.values()) {
            assertTrue(bindings.containsKey(type), "Missing bindings for " + type);
            assertFalse(bindings.get(type).isEmpty(), "Empty bindings for " + type);
        }
    }

    @Test
    void bindingNamesUseDimensionPrefix() {
        Map<DimensionType, List<String>> bindings = ServiceCaseDescriptor.dimensionBindings();

        for (String name : bindings.get(DimensionType.HEALTH_MONITORING)) {
            assertTrue(name.startsWith("health:"), "Expected health: prefix, got: " + name);
        }
        for (String name : bindings.get(DimensionType.SECURITY)) {
            assertTrue(name.startsWith("security:"), "Expected security: prefix, got: " + name);
        }
        for (String name : bindings.get(DimensionType.DECOMMISSION)) {
            assertTrue(name.startsWith("decommission:"), "Expected decommission: prefix, got: " + name);
        }
    }

    @Test
    void caseDefinitionHasBindingsForAllDimensions() {
        var def = ServiceCaseDescriptor.build();
        var bindingNames = def.getBindings().stream().map(b -> b.getName()).toList();

        assertTrue(bindingNames.stream().anyMatch(n -> n.startsWith("health:")));
        assertTrue(bindingNames.stream().anyMatch(n -> n.startsWith("drift:")));
        assertTrue(bindingNames.stream().anyMatch(n -> n.startsWith("compliance:")));
        assertTrue(bindingNames.stream().anyMatch(n -> n.startsWith("scaling:")));
        assertTrue(bindingNames.stream().anyMatch(n -> n.startsWith("change:")));
        assertTrue(bindingNames.stream().anyMatch(n -> n.startsWith("security:")));
        assertTrue(bindingNames.stream().anyMatch(n -> n.startsWith("maintenance:")));
        assertTrue(bindingNames.stream().anyMatch(n -> n.startsWith("problems:")));
        assertTrue(bindingNames.stream().anyMatch(n -> n.startsWith("decommission:")));
    }

    @Test
    void dimensionBindingsMapIsImmutable() {
        Map<DimensionType, List<String>> bindings = ServiceCaseDescriptor.dimensionBindings();
        assertThrows(UnsupportedOperationException.class,
                () -> bindings.put(DimensionType.HEALTH_MONITORING, List.of()));
    }

    @Test
    void caseDefinitionHasExpectedBindingCount() {
        var def = ServiceCaseDescriptor.build();
        assertEquals(14, def.getBindings().size());
    }
}
