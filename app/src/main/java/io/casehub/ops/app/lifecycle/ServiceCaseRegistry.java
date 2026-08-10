package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.DimensionSection;
import io.casehub.ops.api.lifecycle.DimensionStatus;
import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.ManagedServiceCategory;
import io.casehub.ops.api.lifecycle.ServiceCaseContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ServiceCaseRegistry {

    private final ConcurrentHashMap<UUID, ServiceCaseContext> contexts     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID>             serviceIndex = new ConcurrentHashMap<>();

    public void register(UUID caseId, String serviceId, String serviceName,
                         ManagedServiceCategory category, Map<String, Object> metadata,
                         DimensionSection.ContextWriter writer, DimensionSection.ContextReader reader) {
        writer.write("service.serviceId", serviceId);
        writer.write("service.serviceName", serviceName);
        writer.write("service.category", category.name());
        writer.write("service.deployedAt", Instant.now().toString());

        var ctx = ServiceCaseContext.create(serviceId, serviceName, category, metadata, writer, reader);
        contexts.put(caseId, ctx);
        serviceIndex.put(serviceId, caseId);
    }

    public ServiceCaseContext get(UUID caseId) {
        return contexts.get(caseId);
    }

    public ServiceCaseContext getByServiceId(String serviceId) {
        UUID caseId = serviceIndex.get(serviceId);
        return caseId != null ? contexts.get(caseId) : null;
    }

    public ServiceCaseContext getOrReconstruct(UUID caseId,
                                               DimensionSection.ContextWriter writer,
                                               DimensionSection.ContextReader reader) {
        ServiceCaseContext existing = contexts.get(caseId);
        if (existing != null) {return existing;}

        String serviceId = (String) reader.read("service.serviceId");
        if (serviceId == null) {return null;}

        String serviceName   = (String) reader.read("service.serviceName");
        String categoryName  = (String) reader.read("service.category");
        String deployedAtStr = (String) reader.read("service.deployedAt");

        ManagedServiceCategory category   = ManagedServiceCategory.valueOf(categoryName);
        Instant                deployedAt = Instant.parse(deployedAtStr);

        var statuses = new EnumMap<DimensionType, DimensionStatus>(DimensionType.class);
        for (DimensionType type : DimensionType.values()) {
            String statusName = (String) reader.read(type.contextPrefix() + "status");
            if (statusName != null) {
                statuses.put(type, type.resolveStatus(statusName));
            }
        }

        var ctx = ServiceCaseContext.createForReconstruction(
                serviceId, serviceName, category, deployedAt, Map.of(),
                statuses, writer, reader);

        ServiceCaseContext winner = contexts.putIfAbsent(caseId, ctx);
        if (winner != null) {return winner;}

        serviceIndex.put(serviceId, caseId);
        return ctx;
    }

    public void deregister(UUID caseId) {
        ServiceCaseContext ctx = contexts.remove(caseId);
        if (ctx != null) {
            serviceIndex.remove(ctx.serviceId());
        }
    }
}
