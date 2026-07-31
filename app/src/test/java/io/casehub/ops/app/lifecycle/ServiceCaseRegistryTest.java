package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.ManagedServiceCategory;
import io.casehub.ops.api.lifecycle.ServiceCaseContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceCaseRegistryTest {

    private final ServiceCaseRegistry registry = new ServiceCaseRegistry();

    @Test
    void registerAndRetrieveContext() {
        UUID caseId = UUID.randomUUID();
        var store = new HashMap<String, Object>();

        registry.register(caseId, "order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of("cluster", "prod"),
                store::put, key -> store.get(key));

        ServiceCaseContext ctx = registry.get(caseId);
        assertNotNull(ctx);
        assertEquals("order-api", ctx.serviceId());
        assertEquals("Order API", ctx.serviceName());
        assertEquals(ManagedServiceCategory.APPLICATION, ctx.category());
        assertEquals(9, ctx.dimensions().size());
    }

    @Test
    void deregisterRemovesContext() {
        UUID caseId = UUID.randomUUID();
        var store = new HashMap<String, Object>();

        registry.register(caseId, "order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        registry.deregister(caseId);
        assertNull(registry.get(caseId));
    }

    @Test
    void getReturnsNullForUnknownCaseId() {
        assertNull(registry.get(UUID.randomUUID()));
    }

    @Test
    void multipleServicesIndependent() {
        var store = new HashMap<String, Object>();
        UUID caseId1 = UUID.randomUUID();
        UUID caseId2 = UUID.randomUUID();

        registry.register(caseId1, "order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));
        registry.register(caseId2, "payment-svc", "Payment Service",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        assertEquals("order-api", registry.get(caseId1).serviceId());
        assertEquals("payment-svc", registry.get(caseId2).serviceId());
    }
}
