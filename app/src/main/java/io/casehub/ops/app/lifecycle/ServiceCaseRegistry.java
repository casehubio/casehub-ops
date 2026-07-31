package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.DimensionSection;
import io.casehub.ops.api.lifecycle.ManagedServiceCategory;
import io.casehub.ops.api.lifecycle.ServiceCaseContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ServiceCaseRegistry {

    private final ConcurrentHashMap<UUID, ServiceCaseContext> contexts = new ConcurrentHashMap<>();

    public void register(UUID caseId, String serviceId, String serviceName,
                         ManagedServiceCategory category, Map<String, Object> metadata,
                         DimensionSection.ContextWriter writer, DimensionSection.ContextReader reader) {
        var ctx = ServiceCaseContext.create(serviceId, serviceName, category, metadata, writer, reader);
        contexts.put(caseId, ctx);
    }

    public ServiceCaseContext get(UUID caseId) {
        return contexts.get(caseId);
    }

    public void deregister(UUID caseId) {
        contexts.remove(caseId);
    }
}
