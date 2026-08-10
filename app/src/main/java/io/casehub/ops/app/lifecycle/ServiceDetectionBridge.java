package io.casehub.ops.app.lifecycle;

import io.casehub.ops.api.lifecycle.CaseRef;
import io.casehub.ops.api.lifecycle.DimensionSection;
import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.GanglionBinding;
import io.casehub.ops.api.lifecycle.OperationalDimension;
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

    public void onDetection(String situationType, UUID caseId,
                            DimensionSection.ContextWriter writer,
                            DimensionSection.ContextReader reader,
                            Map<String, Object> detectionData) {
        var ctx = registry.getOrReconstruct(caseId, writer, reader);
        if (ctx == null) {return;}

        var bindings = bindingsMap.getOrDefault(caseId, List.of());
        for (var binding : bindings) {
            if (binding.situationType().equals(situationType)) {
                var dimension = ctx.dimensions().get(binding.dimension());

                if (!dimension.isLoaded()) {
                    dimension.load(reader);
                }

                dimension.section().put(binding.contextKey(), detectionData);
                if (binding.conditionStatus() != null) {
                    dimension.section().put("condition", ((Enum<?>) binding.conditionStatus()).name());
                }
                statusService.recompute(dimension);
            }
        }
    }


    public void addResponseAndPersist(UUID caseId, DimensionType dimType, CaseRef ref) {
        var ctx = registry.get(caseId);
        if (ctx == null) {return;}
        var dimension = ctx.dimensions().get(dimType);
        dimension.addResponse(ref);
        persistActiveResponseIds(dimension);
        statusService.recompute(dimension);
    }

    public void removeResponseAndPersist(UUID caseId, DimensionType dimType, UUID childCaseId) {
        var ctx = registry.get(caseId);
        if (ctx == null) {return;}
        var dimension = ctx.dimensions().get(dimType);
        dimension.removeResponse(childCaseId);
        persistActiveResponseIds(dimension);
        statusService.recompute(dimension);
    }

    private void persistActiveResponseIds(OperationalDimension dimension) {
        List<Map<String, Object>> serialized = dimension.activeResponses().stream()
                                                        .map(r -> Map.<String, Object>of(
                                                                "caseId", r.caseId().toString(),
                                                                "bindingName", r.bindingName(),
                                                                "createdAt", r.createdAt().toString()))
                                                        .toList();
        dimension.section().put("activeResponseIds", serialized);
    }
}
