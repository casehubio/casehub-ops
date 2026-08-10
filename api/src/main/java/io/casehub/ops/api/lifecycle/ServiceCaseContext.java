package io.casehub.ops.api.lifecycle;

import io.casehub.ops.api.lifecycle.status.ChangeManagementStatus;
import io.casehub.ops.api.lifecycle.status.ComplianceStatus;
import io.casehub.ops.api.lifecycle.status.ConfigurationDriftStatus;
import io.casehub.ops.api.lifecycle.status.DecommissionStatus;
import io.casehub.ops.api.lifecycle.status.HealthStatus;
import io.casehub.ops.api.lifecycle.status.MaintenanceStatus;
import io.casehub.ops.api.lifecycle.status.ProblemManagementStatus;
import io.casehub.ops.api.lifecycle.status.ScalingStatus;
import io.casehub.ops.api.lifecycle.status.SecurityStatus;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ServiceCaseContext {

    private final String serviceId;
    private final String serviceName;
    private final ManagedServiceCategory category;
    private final Map<DimensionType, OperationalDimension> dimensions;
    private final Instant deployedAt;
    private final Map<String, Object> metadata;

    private ServiceCaseContext(String serviceId, String serviceName, ManagedServiceCategory category,
                               Map<DimensionType, OperationalDimension> dimensions,
                               Instant deployedAt, Map<String, Object> metadata) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.category = category;
        this.dimensions = dimensions;
        this.deployedAt = deployedAt;
        this.metadata = Map.copyOf(metadata);
    }

    public static ServiceCaseContext create(String serviceId, String serviceName,
                                            ManagedServiceCategory category, Map<String, Object> metadata,
                                            DimensionSection.ContextWriter writer,
                                            DimensionSection.ContextReader reader) {
        var dims = new EnumMap<DimensionType, OperationalDimension>(DimensionType.class);
        for (DimensionType type : DimensionType.values()) {
            var section = new DimensionSection(type, writer, reader);
            dims.put(type, new OperationalDimension(type, type.defaultStatus(), section, List.of()));
        }
        return new ServiceCaseContext(serviceId, serviceName, category, dims, Instant.now(), metadata);}

    public static ServiceCaseContext createForReconstruction(
            String serviceId, String serviceName,
            ManagedServiceCategory category, Instant deployedAt,
            Map<String, Object> metadata,
            Map<DimensionType, DimensionStatus> persistedStatuses,
            DimensionSection.ContextWriter writer,
            DimensionSection.ContextReader reader) {
        var dims = new EnumMap<DimensionType, OperationalDimension>(DimensionType.class);
        for (DimensionType type : DimensionType.values()) {
            var             section = new DimensionSection(type, writer, reader);
            DimensionStatus status  = persistedStatuses.getOrDefault(type, type.defaultStatus());
            dims.put(type, new OperationalDimension(type, status, section, List.of(), false));
        }
        return new ServiceCaseContext(serviceId, serviceName, category, dims, deployedAt, metadata);
    }




    public String serviceId() { return serviceId; }
    public String serviceName() { return serviceName; }
    public ManagedServiceCategory category() { return category; }
    public Map<DimensionType, OperationalDimension> dimensions() { return dimensions; }
    public Instant deployedAt() { return deployedAt; }
    public Map<String, Object> metadata() { return metadata; }

    public ServiceHealth toServiceHealth() {
        var statuses = new EnumMap<DimensionType, DimensionStatus>(DimensionType.class);
        Severity worst = Severity.OK;
        for (var entry : dimensions.entrySet()) {
            statuses.put(entry.getKey(), entry.getValue().status());
            worst = Severity.worstOf(worst, entry.getValue().severity());
        }
        return new ServiceHealth(serviceId, serviceName, statuses, worst);
    }
}
