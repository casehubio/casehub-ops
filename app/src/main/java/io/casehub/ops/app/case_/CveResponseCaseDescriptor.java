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

public final class CveResponseCaseDescriptor {

    private CveResponseCaseDescriptor() {}

    public static CaseDefinition build(ApplicationLifecycleService lifecycleService,
                                        NodeConvergenceTracker convergenceTracker) {
        return CaseDefinition.builder()
                .namespace("ops")
                .name("cve-response")
                .version("1.0")
                .title("CVE Response")
                .summary("Assesses CVE impact, updates service images or escalates for human review")
                .capabilities(capabilities())
                .workers(workers(lifecycleService, convergenceTracker))
                .bindings(bindings())
                .completion(".cveStatus == \"resolved\" || .cveStatus == \"escalated\"")
                .build();
    }

    private static List<Capability> capabilities() {
        return List.of(
                Capability.of("assess-cve", "any", "any"),
                Capability.of("remediate-cve", "any", "any"),
                Capability.of("verify-cve", "any", "any"),
                Capability.of("escalate-cve", "any", "any"));
    }

    @SuppressWarnings("unchecked")
    private static List<Worker> workers(ApplicationLifecycleService lifecycleService,
                                         NodeConvergenceTracker convergenceTracker) {
        return List.of(
                Worker.builder()
                        .name("cve-assess-worker")
                        .capabilityName("assess-cve")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> assessCve(input)))
                        .build(),
                Worker.builder()
                        .name("cve-remediate-worker")
                        .capabilityName("remediate-cve")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> remediateCve(input, lifecycleService)))
                        .build(),
                Worker.builder()
                        .name("cve-verify-worker")
                        .capabilityName("verify-cve")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> verifyCve(input, scope, convergenceTracker)))
                        .build(),
                Worker.builder()
                        .name("cve-escalate-worker")
                        .capabilityName("escalate-cve")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> escalateCve(input)))
                        .build());
    }

    private static List<Binding> bindings() {
        return List.of(
                Binding.builder()
                        .name("on-cve-assessment")
                        .on(new ContextChangeTrigger(".cveAssessment"))
                        .capability(Capability.of("remediate-cve", "any", "any"))
                        .build(),
                Binding.builder()
                        .name("on-cve-remediation-executed")
                        .on(new ContextChangeTrigger(".cveRemediationExecuted"))
                        .capability(Capability.of("verify-cve", "any", "any"))
                        .build(),
                Binding.builder()
                        .name("on-cve-escalation-required")
                        .on(new ContextChangeTrigger(".cveEscalationRequired"))
                        .capability(Capability.of("escalate-cve", "any", "any"))
                        .build());
    }

    @SuppressWarnings("unchecked")
    static WorkerResult assessCve(Map<String, Object> input) {
        if (input == null) return WorkerResult.failed("CVE data is null");

        String cveId = (String) input.get("cveId");
        String severity = (String) input.get("severity");
        String affectedImage = (String) input.get("affectedImage");

        if (cveId == null || cveId.isBlank()) return WorkerResult.failed("cveId is required");
        if (severity == null || severity.isBlank()) return WorkerResult.failed("severity is required");
        if (affectedImage == null || affectedImage.isBlank()) return WorkerResult.failed("affectedImage is required");

        String fixedInTag = (String) input.get("fixedInTag");
        List<String> affectedServices = (List<String>) input.getOrDefault("affectedServices", List.of());
        String applicationId = (String) input.get("applicationId");
        String tenancyId = (String) input.get("tenancyId");

        boolean hasFixedTag = fixedInTag != null && !fixedInTag.isBlank();
        boolean hasServices = affectedServices != null && !affectedServices.isEmpty();
        boolean isHighSeverity = "HIGH".equals(severity) || "CRITICAL".equals(severity);

        String action = (hasFixedTag && hasServices) ? "update-image" : "escalate";

        var assessment = new LinkedHashMap<String, Object>();
        assessment.put("action", action);
        assessment.put("cveId", cveId);
        assessment.put("severity", severity);
        assessment.put("affectedImage", affectedImage);
        if (fixedInTag != null) assessment.put("fixedInTag", fixedInTag);
        if (affectedServices != null) assessment.put("affectedServices", affectedServices);
        if (applicationId != null) assessment.put("applicationId", applicationId);
        if (tenancyId != null) assessment.put("tenancyId", tenancyId);

        if ("update-image".equals(action)) {
            assessment.put("reason", "CVE " + cveId + " — updating affected services to " + fixedInTag);
            var result = new LinkedHashMap<String, Object>();
            result.put("cveAssessment", assessment);
            if (isHighSeverity) result.put("cveApprovalRequired", true);
            return WorkerResult.of(result);
        }

        assessment.put("reason", describeEscalation(cveId, hasFixedTag, hasServices));
        var result = new LinkedHashMap<String, Object>();
        result.put("cveEscalationRequired", true);
        result.put("cveAssessment", assessment);
        if (isHighSeverity) result.put("cveApprovalRequired", true);
        return WorkerResult.of(result);
    }

    @SuppressWarnings("unchecked")
    static WorkerResult remediateCve(Map<String, Object> input,
                                      ApplicationLifecycleService lifecycleService) {
        Map<String, Object> assessment = (Map<String, Object>) input.get("cveAssessment");
        String applicationId = (String) assessment.get("applicationId");
        String fixedInTag = (String) assessment.get("fixedInTag");
        String tenancyId = (String) assessment.get("tenancyId");
        String cveId = (String) assessment.get("cveId");
        List<String> affectedServices = (List<String>) assessment.get("affectedServices");

        Set<String> allAffectedNodeIds = new HashSet<>();

        try {
            for (String serviceId : affectedServices) {
                Set<String> nodeIds = lifecycleService.updateServiceImage(
                        UUID.fromString(applicationId), serviceId, fixedInTag, tenancyId);
                allAffectedNodeIds.addAll(nodeIds);
            }
        } catch (Exception e) {
            var escalation = new LinkedHashMap<String, Object>();
            escalation.put("reason", "CVE remediation failed: " + e.getMessage());
            escalation.put("cveId", cveId);
            return WorkerResult.of(Map.of("cveEscalationRequired", true,
                                           "cveRemediationError", escalation));
        }

        var executed = new LinkedHashMap<String, Object>();
        executed.put("action", "update-image");
        executed.put("cveId", cveId);
        executed.put("fixedInTag", fixedInTag);
        executed.put("affectedServices", affectedServices);
        executed.put("affectedNodeIds", List.copyOf(allAffectedNodeIds));

        return WorkerResult.of(Map.of("cveRemediationExecuted", executed));
    }

    @SuppressWarnings("unchecked")
    static WorkerResult verifyCve(Map<String, Object> input,
                                   WorkerScope scope,
                                   NodeConvergenceTracker convergenceTracker) {
        Map<String, Object> executed = (Map<String, Object>) input.get("cveRemediationExecuted");
        List<String> affectedNodeIdsList = (List<String>) executed.get("affectedNodeIds");
        Set<String> affectedNodeIds = new HashSet<>(affectedNodeIdsList);

        UUID caseId = scope.caseId();
        convergenceTracker.register(caseId, affectedNodeIds, "cveStatus", "resolved");

        return WorkerResult.of(Map.of());
    }

    @SuppressWarnings("unchecked")
    static WorkerResult escalateCve(Map<String, Object> input) {
        Map<String, Object> assessment = (Map<String, Object>) input.getOrDefault(
                "cveAssessment", Map.of());
        String cveId = (String) assessment.getOrDefault("cveId", "unknown");
        String severity = (String) assessment.getOrDefault("severity", "unknown");
        String affectedImage = (String) assessment.getOrDefault("affectedImage", "unknown");

        Map<String, Object> remediationError = (Map<String, Object>) input.get("cveRemediationError");
        String escalationDetail = remediationError != null
                ? (String) remediationError.get("reason")
                : "CVE " + cveId + " requires human review";

        var escalation = new LinkedHashMap<String, Object>();
        escalation.put("summary", "CVE " + cveId + " on " + affectedImage + " requires human review");
        escalation.put("cveId", cveId);
        escalation.put("severity", severity);
        escalation.put("affectedImage", affectedImage);
        escalation.put("detail", escalationDetail);

        return WorkerResult.of(Map.of(
                "cveEscalation", escalation,
                "cveStatus", "escalated"));
    }

    private static String describeEscalation(String cveId, boolean hasFixedTag, boolean hasServices) {
        if (!hasFixedTag) return "CVE " + cveId + " — no fixed version available";
        if (!hasServices) return "CVE " + cveId + " — no affected services found";
        return "CVE " + cveId + " — escalating";
    }
}
