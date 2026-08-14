package io.casehub.ops.app.case_;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.ops.app.service.ApplicationLifecycleService;
import io.casehub.ops.app.service.NodeConvergenceTracker;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerFunction;
import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.WorkerScope;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServiceUpgradeCaseDescriptor {

    private ServiceUpgradeCaseDescriptor() {}

    public static CaseDefinition build(ApplicationLifecycleService lifecycleService,
                                        NodeConvergenceTracker convergenceTracker) {
        return CaseDefinition.builder()
                .namespace("ops")
                .name("service-upgrade")
                .version("1.0")
                .title("Service Upgrade")
                .summary("Validates, executes, and verifies service image upgrades")
                .capabilities(capabilities())
                .workers(workers(lifecycleService, convergenceTracker))
                .bindings(bindings())
                .completion(".upgradeStatus == \"completed\" || .upgradeStatus == \"escalated\"")
                .build();
    }

    private static List<Capability> capabilities() {
        return List.of(
                Capability.of("assess-upgrade", "any", "any"),
                Capability.of("execute-upgrade", "any", "any"),
                Capability.of("verify-upgrade", "any", "any"),
                Capability.of("escalate-upgrade", "any", "any"));
    }

    @SuppressWarnings("unchecked")
    private static List<Worker> workers(ApplicationLifecycleService lifecycleService,
                                         NodeConvergenceTracker convergenceTracker) {
        return List.of(
                Worker.builder()
                        .name("upgrade-assess-worker")
                        .capabilityName("assess-upgrade")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> assessUpgrade(input)))
                        .build(),
                Worker.builder()
                        .name("upgrade-execute-worker")
                        .capabilityName("execute-upgrade")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> executeUpgrade(input, lifecycleService)))
                        .build(),
                Worker.builder()
                        .name("upgrade-verify-worker")
                        .capabilityName("verify-upgrade")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> verifyUpgrade(input, scope, convergenceTracker)))
                        .build(),
                Worker.builder()
                        .name("upgrade-escalate-worker")
                        .capabilityName("escalate-upgrade")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> escalateUpgrade(input)))
                        .build());
    }

    private static List<Binding> bindings() {
        return List.of(
                Binding.builder()
                        .name("on-upgrade-assessment")
                        .on(new ContextChangeTrigger(".upgradeAssessment"))
                        .capability(Capability.of("execute-upgrade", "any", "any"))
                        .build(),
                Binding.builder()
                        .name("on-upgrade-executed")
                        .on(new ContextChangeTrigger(".upgradeExecuted"))
                        .capability(Capability.of("verify-upgrade", "any", "any"))
                        .build(),
                Binding.builder()
                        .name("on-upgrade-escalation-required")
                        .on(new ContextChangeTrigger(".upgradeEscalationRequired"))
                        .capability(Capability.of("escalate-upgrade", "any", "any"))
                        .build());
    }

    static WorkerResult assessUpgrade(Map<String, Object> input) {
        if (input == null) return WorkerResult.failed("Upgrade data is null");

        String serviceId = (String) input.get("serviceId");
        String newImage = (String) input.get("newImage");

        if (serviceId == null || serviceId.isBlank()) return WorkerResult.failed("serviceId is required");
        if (newImage == null || newImage.isBlank()) return WorkerResult.failed("newImage is required");

        String applicationId = (String) input.get("applicationId");
        String tenancyId = (String) input.get("tenancyId");

        var assessment = new LinkedHashMap<String, Object>();
        assessment.put("serviceId", serviceId);
        assessment.put("newImage", newImage);
        if (applicationId != null) assessment.put("applicationId", applicationId);
        if (tenancyId != null) assessment.put("tenancyId", tenancyId);
        assessment.put("reason", "Upgrading service " + serviceId + " to " + newImage);

        return WorkerResult.of(Map.of("upgradeAssessment", assessment));
    }

    @SuppressWarnings("unchecked")
    static WorkerResult executeUpgrade(Map<String, Object> input,
                                        ApplicationLifecycleService lifecycleService) {
        Map<String, Object> assessment = (Map<String, Object>) input.get("upgradeAssessment");
        String applicationId = (String) assessment.get("applicationId");
        String serviceId = (String) assessment.get("serviceId");
        String newImage = (String) assessment.get("newImage");
        String tenancyId = (String) assessment.get("tenancyId");

        try {
            Set<String> nodeIds = lifecycleService.updateServiceImage(
                    UUID.fromString(applicationId), serviceId, newImage, tenancyId);

            var executed = new LinkedHashMap<String, Object>();
            executed.put("serviceId", serviceId);
            executed.put("newImage", newImage);
            executed.put("affectedNodeIds", List.copyOf(nodeIds));

            return WorkerResult.of(Map.of("upgradeExecuted", executed));
        } catch (Exception e) {
            var error = new LinkedHashMap<String, Object>();
            error.put("reason", "Upgrade failed: " + e.getMessage());
            error.put("serviceId", serviceId);
            return WorkerResult.of(Map.of("upgradeEscalationRequired", true,
                                           "upgradeError", error));
        }
    }

    @SuppressWarnings("unchecked")
    static WorkerResult verifyUpgrade(Map<String, Object> input,
                                       WorkerScope scope,
                                       NodeConvergenceTracker convergenceTracker) {
        Map<String, Object> executed = (Map<String, Object>) input.get("upgradeExecuted");
        List<String> affectedNodeIdsList = (List<String>) executed.get("affectedNodeIds");
        Set<String> affectedNodeIds = new HashSet<>(affectedNodeIdsList);

        UUID caseId = scope.caseId();
        convergenceTracker.register(caseId, affectedNodeIds, "upgradeStatus", "completed");

        return WorkerResult.of(Map.of());
    }

    @SuppressWarnings("unchecked")
    static WorkerResult escalateUpgrade(Map<String, Object> input) {
        Map<String, Object> assessment = (Map<String, Object>) input.getOrDefault(
                "upgradeAssessment", Map.of());
        String serviceId = (String) assessment.getOrDefault("serviceId", "unknown");
        String newImage = (String) assessment.getOrDefault("newImage", "unknown");

        Map<String, Object> upgradeError = (Map<String, Object>) input.get("upgradeError");
        String escalationDetail = upgradeError != null
                ? (String) upgradeError.get("reason")
                : "Service upgrade for " + serviceId + " requires human review";

        var escalation = new LinkedHashMap<String, Object>();
        escalation.put("summary", "Service " + serviceId + " upgrade to " + newImage + " requires human review");
        escalation.put("serviceId", serviceId);
        escalation.put("newImage", newImage);
        escalation.put("detail", escalationDetail);

        return WorkerResult.of(Map.of(
                "upgradeEscalation", escalation,
                "upgradeStatus", "escalated"));
    }
}
