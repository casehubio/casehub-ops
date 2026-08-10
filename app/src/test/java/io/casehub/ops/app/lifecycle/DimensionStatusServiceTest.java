package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.CaseRef;
import io.casehub.ops.api.lifecycle.DimensionSection;
import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.ManagedServiceCategory;
import io.casehub.ops.api.lifecycle.OperationalDimension;
import io.casehub.ops.api.lifecycle.ServiceCaseContext;
import io.casehub.ops.api.lifecycle.Severity;
import io.casehub.ops.api.lifecycle.status.ComplianceStatus;
import io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus;
import io.casehub.ops.api.lifecycle.status.HealthStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DimensionStatusServiceTest {

    private final DimensionStatusService service = new DimensionStatusService();

    @Test
    void healthyConditionNoResponses_returnsConditionStatus() {
        var dim = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY);
        dim.section().put("condition", HealthStatus.HEALTHY.name());

        service.recompute(dim);
        assertEquals(HealthStatus.HEALTHY, dim.status());
    }

    @Test
    void unhealthyConditionNoResponses_returnsConditionStatus() {
        var dim = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY);
        dim.section().put("condition", HealthStatus.DOWN.name());

        service.recompute(dim);
        assertEquals(HealthStatus.DOWN, dim.status());
    }

    @Test
    void unhealthyConditionWithActiveResponse_returnsResponseStatus() {
        var dim = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY);
        dim.section().put("condition", HealthStatus.DOWN.name());
        dim.addResponse(new CaseRef(UUID.randomUUID(), "health:incident-response", Instant.now()));

        service.recompute(dim);
        assertEquals(HealthStatus.REMEDIATING, dim.status());
    }

    @Test
    void healthyConditionWithActiveResponse_returnsHealthyStatus() {
        var dim = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY);
        dim.section().put("condition", HealthStatus.HEALTHY.name());
        dim.addResponse(new CaseRef(UUID.randomUUID(), "health:incident-response", Instant.now()));

        service.recompute(dim);
        assertEquals(HealthStatus.HEALTHY, dim.status());
    }

    @Test
    void driftConditionWithResponse_returnsReconciling() {
        var dim = createDimension(DimensionType.CONFIGURATION_DRIFT, ConfigurationDriftStatus.IN_SYNC);
        dim.section().put("condition", ConfigurationDriftStatus.DRIFTED.name());
        dim.addResponse(new CaseRef(UUID.randomUUID(), "drift:auto-reconciliation", Instant.now()));

        service.recompute(dim);
        assertEquals(ConfigurationDriftStatus.RECONCILING, dim.status());
    }

    @Test
    void noConditionInSection_defaultsToCurrentStatus() {
        var dim = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY);

        service.recompute(dim);
        assertEquals(HealthStatus.HEALTHY, dim.status());
    }

    @Test
    void recomputeAllUpdatesAllDimensions() {
        var store = new HashMap<String, Object>();
        var ctx = ServiceCaseContext.create("order-api", "Order API",
                ManagedServiceCategory.APPLICATION, Map.of(),
                store::put, key -> store.get(key));

        store.put("health.condition", HealthStatus.DOWN.name());
        store.put("compliance.condition", ComplianceStatus.NON_COMPLIANT.name());

        var health = service.recomputeAll(ctx);
        assertEquals(Severity.CRITICAL, health.overallSeverity());
        assertEquals(HealthStatus.DOWN, health.dimensions().get(DimensionType.HEALTH_MONITORING));
        assertEquals(ComplianceStatus.NON_COMPLIANT, health.dimensions().get(DimensionType.COMPLIANCE));
    }

    @Test
    void recomputePersistsStatusToSection() {
        var store = new HashMap<String, Object>();
        var dim   = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY, store);

        dim.section().put("condition", "DOWN");
        service.recompute(dim);

        assertEquals("DOWN", store.get("health.status"));
    }

    @Test
    void recomputeWithResponsePersistsResponseStatus() {
        var store = new HashMap<String, Object>();
        var dim   = createDimension(DimensionType.HEALTH_MONITORING, HealthStatus.HEALTHY, store);

        dim.section().put("condition", "DOWN");
        dim.addResponse(new CaseRef(UUID.randomUUID(), "health:incident-response", Instant.now()));
        service.recompute(dim);

        assertEquals("REMEDIATING", store.get("health.status"));
    }


    private OperationalDimension createDimension(DimensionType type, DimensionStatus initialStatus) {
        var store = new HashMap<String, Object>();
        var section = new DimensionSection(type, store::put, key -> store.get(key));
        return new OperationalDimension(type, initialStatus, section, List.of());
    }

    private OperationalDimension createDimension(DimensionType type, DimensionStatus initialStatus, Map<String, Object> store) {
        var section = new DimensionSection(type, store::put, store::get);
        return new OperationalDimension(type, initialStatus, section, List.of());
    }

}
