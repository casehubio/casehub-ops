package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.GanglionBinding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ServiceDetectionBridge {

    private final ServiceCaseRegistry registry;
    private final DimensionStatusService statusService;
    private final ConcurrentHashMap<UUID, List<GanglionBinding>> bindingsMap = new ConcurrentHashMap<>();

    @Inject
    public ServiceDetectionBridge(ServiceCaseRegistry registry, DimensionStatusService statusService) {
        this.registry = registry;
        this.statusService = statusService;
    }

    public void registerBindings(UUID caseId, List<GanglionBinding> bindings) {
        bindingsMap.put(caseId, List.copyOf(bindings));
    }

    public void deregisterBindings(UUID caseId) {
        bindingsMap.remove(caseId);
    }

    public void onDetection(String situationType, UUID caseId, Map<String, Object> detectionData) {
        var ctx = registry.get(caseId);
        if (ctx == null) return;

        var bindings = bindingsMap.getOrDefault(caseId, List.of());
        for (var binding : bindings) {
            if (binding.situationType().equals(situationType)) {
                var dimension = ctx.dimensions().get(binding.dimension());
                dimension.section().put(binding.contextKey(), detectionData);

                if (binding.conditionStatus() != null) {
                    dimension.section().put("condition", ((Enum<?>) binding.conditionStatus()).name());
                }

                statusService.recompute(dimension);
            }
        }
    }
}
