package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.ManagedServiceCategory;
import io.casehub.ops.api.lifecycle.ServiceCaseContext;
import io.casehub.ops.api.lifecycle.status.HealthStatus;
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

    @Test
    void registerWritesMetadataToEngineContext() {
        var  store  = new HashMap<String, Object>();
        UUID caseId = UUID.randomUUID();

        registry.register(caseId, "order-api", "Order API",
                          ManagedServiceCategory.APPLICATION, Map.of("cluster", "prod"),
                          store::put, store::get);

        assertEquals("order-api", store.get("service.serviceId"));
        assertEquals("Order API", store.get("service.serviceName"));
        assertEquals("APPLICATION", store.get("service.category"));
        assertNotNull(store.get("service.deployedAt"));
    }

    @Test
    void getByServiceIdReturnsContext() {
        var  store  = new HashMap<String, Object>();
        UUID caseId = UUID.randomUUID();

        registry.register(caseId, "order-api", "Order API",
                          ManagedServiceCategory.APPLICATION, Map.of(),
                          store::put, store::get);

        assertNotNull(registry.getByServiceId("order-api"));
        assertEquals("order-api", registry.getByServiceId("order-api").serviceId());
    }

    @Test
    void getByServiceIdReturnsNullForUnknown() {
        assertNull(registry.getByServiceId("unknown"));
    }

    @Test
    void deregisterRemovesFromServiceIndex() {
        var  store  = new HashMap<String, Object>();
        UUID caseId = UUID.randomUUID();

        registry.register(caseId, "order-api", "Order API",
                          ManagedServiceCategory.APPLICATION, Map.of(),
                          store::put, store::get);
        registry.deregister(caseId);

        assertNull(registry.getByServiceId("order-api"));
    }

    @Test
    void getOrReconstructCreatesUnloadedContext() {
        var  store  = new HashMap<String, Object>();
        UUID caseId = UUID.randomUUID();

        store.put("service.serviceId", "order-api");
        store.put("service.serviceName", "Order API");
        store.put("service.category", "APPLICATION");
        store.put("service.deployedAt", "2026-08-01T10:00:00Z");
        store.put("health.status", "DOWN");

        var ctx = registry.getOrReconstruct(caseId, store::put, store::get);

        assertNotNull(ctx);
        assertEquals("order-api", ctx.serviceId());
        assertEquals("Order API", ctx.serviceName());
        assertEquals(ManagedServiceCategory.APPLICATION, ctx.category());
        assertEquals(HealthStatus.DOWN, ctx.dimensions().get(DimensionType.HEALTH_MONITORING).status());
        assertFalse(ctx.dimensions().get(DimensionType.HEALTH_MONITORING).isLoaded());
    }

    @Test
    void getOrReconstructReturnsExistingContext() {
        var  store  = new HashMap<String, Object>();
        UUID caseId = UUID.randomUUID();

        registry.register(caseId, "order-api", "Order API",
                          ManagedServiceCategory.APPLICATION, Map.of(),
                          store::put, store::get);

        var ctx = registry.getOrReconstruct(caseId, store::put, store::get);
        assertTrue(ctx.dimensions().get(DimensionType.HEALTH_MONITORING).isLoaded());
    }

    @Test
    void getOrReconstructReturnsNullForNonServiceCase() {
        var store = new HashMap<String, Object>();
        var ctx   = registry.getOrReconstruct(UUID.randomUUID(), store::put, store::get);
        assertNull(ctx);
    }

    @Test
    void getOrReconstructPopulatesServiceIndex() {
        var  store  = new HashMap<String, Object>();
        UUID caseId = UUID.randomUUID();

        store.put("service.serviceId", "order-api");
        store.put("service.serviceName", "Order API");
        store.put("service.category", "APPLICATION");
        store.put("service.deployedAt", "2026-08-01T10:00:00Z");

        registry.getOrReconstruct(caseId, store::put, store::get);
        assertNotNull(registry.getByServiceId("order-api"));
    }

}
