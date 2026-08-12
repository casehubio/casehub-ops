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

public final class ComplianceRemediationCaseDescriptor {

    private static final Map<String, Map<String, String>> AUTO_FIX_CONFIGS = Map.of(
            "LOG_RETENTION", Map.of("LOG_RETENTION_DAYS", "365", "LOG_RETENTION_ENABLED", "true"),
            "ENCRYPTION_AT_REST", Map.of("ENCRYPTION_ENABLED", "true", "ENCRYPTION_CIPHER", "AES-256"));

    private ComplianceRemediationCaseDescriptor() {}

    public static CaseDefinition build(ApplicationLifecycleService lifecycleService,
                                        NodeConvergenceTracker convergenceTracker) {
        return CaseDefinition.builder()
                .namespace("ops")
                .name("compliance-remediation")
                .version("1.0")
                .title("Compliance Remediation")
                .summary("Assesses compliance violations and applies config fixes or escalates")
                .capabilities(capabilities())
                .workers(workers(lifecycleService, convergenceTracker))
                .bindings(bindings())
                .completion(".complianceStatus == \"resolved\" || .complianceStatus == \"escalated\"")
                .build();
    }

    private static List<Capability> capabilities() {
        return List.of(
                Capability.of("assess-compliance", "any", "any"),
                Capability.of("remediate-compliance", "any", "any"),
                Capability.of("verify-compliance", "any", "any"),
                Capability.of("escalate-compliance", "any", "any"));
    }

    @SuppressWarnings("unchecked")
    private static List<Worker> workers(ApplicationLifecycleService lifecycleService,
                                         NodeConvergenceTracker convergenceTracker) {
        return List.of(
                Worker.builder()
                        .name("compliance-assess-worker")
                        .capabilityName("assess-compliance")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> assessCompliance(input)))
                        .build(),
                Worker.builder()
                        .name("compliance-remediate-worker")
                        .capabilityName("remediate-compliance")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> remediateCompliance(input, lifecycleService)))
                        .build(),
                Worker.builder()
                        .name("compliance-verify-worker")
                        .capabilityName("verify-compliance")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> verifyCompliance(input, scope, convergenceTracker)))
                        .build(),
                Worker.builder()
                        .name("compliance-escalate-worker")
                        .capabilityName("escalate-compliance")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> escalateCompliance(input)))
                        .build());
    }

    private static List<Binding> bindings() {
        return List.of(
                Binding.builder()
                        .name("on-compliance-assessment")
                        .on(new ContextChangeTrigger(".complianceAssessment"))
                        .capability(Capability.of("remediate-compliance", "any", "any"))
                        .build(),
                Binding.builder()
                        .name("on-compliance-remediation-executed")
                        .on(new ContextChangeTrigger(".complianceRemediationExecuted"))
                        .capability(Capability.of("verify-compliance", "any", "any"))
                        .build(),
                Binding.builder()
                        .name("on-compliance-escalation-required")
                        .on(new ContextChangeTrigger(".complianceEscalationRequired"))
                        .capability(Capability.of("escalate-compliance", "any", "any"))
                        .build());
    }

    static WorkerResult assessCompliance(Map<String, Object> input) {
        if (input == null) return WorkerResult.failed("Violation data is null");

        String controlId = (String) input.get("controlId");
        String controlType = (String) input.get("controlType");
        String outcome = (String) input.get("outcome");
        String tenancyId = (String) input.get("tenancyId");

        if (controlId == null || controlId.isBlank())
            return WorkerResult.failed("controlId is required");
        if (controlType == null || controlType.isBlank())
            return WorkerResult.failed("controlType is required");
        if (outcome == null || outcome.isBlank())
            return WorkerResult.failed("outcome is required");
        if (tenancyId == null || tenancyId.isBlank())
            return WorkerResult.failed("tenancyId is required");

        String serviceId = (String) input.get("serviceId");
        String applicationId = (String) input.get("applicationId");
        String detail = (String) input.get("detail");
        @SuppressWarnings("unchecked")
        List<String> frameworks = (List<String>) input.get("frameworks");

        boolean isAutoFixable = AUTO_FIX_CONFIGS.containsKey(controlType);
        boolean hasServiceId = serviceId != null && !serviceId.isBlank();
        boolean isFail = "FAIL".equals(outcome);

        String action = (isFail && isAutoFixable && hasServiceId) ? "update-config" : "escalate";

        var assessment = new LinkedHashMap<String, Object>();
        assessment.put("action", action);
        assessment.put("controlId", controlId);
        assessment.put("controlType", controlType);
        assessment.put("outcome", outcome);
        assessment.put("tenancyId", tenancyId);
        if (serviceId != null) assessment.put("serviceId", serviceId);
        if (applicationId != null) assessment.put("applicationId", applicationId);
        if (detail != null) assessment.put("detail", detail);
        if (frameworks != null) assessment.put("frameworks", frameworks);

        if ("update-config".equals(action)) {
            assessment.put("configUpdates", AUTO_FIX_CONFIGS.get(controlType));
            assessment.put("reason", controlType + " violation — applying config fix");
            return WorkerResult.of(Map.of("complianceAssessment", assessment));
        }

        assessment.put("reason", describeEscalation(controlType, outcome, hasServiceId));
        return WorkerResult.of(Map.of(
                "complianceEscalationRequired", true,
                "complianceAssessment", assessment));
    }

    @SuppressWarnings("unchecked")
    static WorkerResult remediateCompliance(Map<String, Object> input,
                                             ApplicationLifecycleService lifecycleService) {
        Map<String, Object> assessment = (Map<String, Object>) input.get("complianceAssessment");
        String applicationId = (String) assessment.get("applicationId");
        String serviceId = (String) assessment.get("serviceId");
        String tenancyId = (String) assessment.get("tenancyId");
        String controlId = (String) assessment.get("controlId");
        Map<String, String> configUpdates = (Map<String, String>) assessment.get("configUpdates");

        Set<String> affectedNodeIds;
        try {
            affectedNodeIds = lifecycleService.updateServiceConfig(
                    UUID.fromString(applicationId), serviceId, configUpdates, tenancyId);
        } catch (Exception e) {
            var escalation = new LinkedHashMap<String, Object>();
            escalation.put("reason", "Remediation failed: " + e.getMessage());
            escalation.put("controlId", controlId);
            escalation.put("serviceId", serviceId);
            return WorkerResult.of(Map.of("complianceEscalationRequired", true,
                                           "complianceRemediationError", escalation));
        }

        var executed = new LinkedHashMap<String, Object>();
        executed.put("action", "update-config");
        executed.put("controlId", controlId);
        executed.put("serviceId", serviceId);
        executed.put("affectedNodeIds", List.copyOf(affectedNodeIds));
        executed.put("configUpdates", configUpdates);

        return WorkerResult.of(Map.of("complianceRemediationExecuted", executed));
    }

    @SuppressWarnings("unchecked")
    static WorkerResult verifyCompliance(Map<String, Object> input,
                                          WorkerScope scope,
                                          NodeConvergenceTracker convergenceTracker) {
        Map<String, Object> executed = (Map<String, Object>) input.get("complianceRemediationExecuted");
        List<String> affectedNodeIdsList = (List<String>) executed.get("affectedNodeIds");
        Set<String> affectedNodeIds = new HashSet<>(affectedNodeIdsList);

        UUID caseId = scope.caseId();
        convergenceTracker.register(caseId, affectedNodeIds, "complianceStatus", "resolved");

        return WorkerResult.of(Map.of());
    }

    @SuppressWarnings("unchecked")
    static WorkerResult escalateCompliance(Map<String, Object> input) {
        Map<String, Object> assessment = (Map<String, Object>) input.getOrDefault(
                "complianceAssessment", Map.of());
        String controlId = (String) assessment.getOrDefault("controlId", "unknown");
        String controlType = (String) assessment.getOrDefault("controlType", "unknown");
        String outcome = (String) assessment.getOrDefault("outcome", "unknown");
        String serviceId = (String) assessment.getOrDefault("serviceId", "unknown");
        String detail = (String) assessment.getOrDefault("detail", "");
        List<String> frameworks = (List<String>) assessment.getOrDefault("frameworks", List.of());

        Map<String, Object> remediationError = (Map<String, Object>) input.get("complianceRemediationError");
        String escalationDetail = remediationError != null
                ? (String) remediationError.get("reason")
                : "Compliance violation requires human review: " + controlType + " " + outcome;

        var escalation = new LinkedHashMap<String, Object>();
        escalation.put("summary", "Compliance violation on " + controlId + " requires human review");
        escalation.put("controlId", controlId);
        escalation.put("controlType", controlType);
        escalation.put("outcome", outcome);
        escalation.put("frameworks", frameworks);
        escalation.put("serviceId", serviceId);
        escalation.put("risk", "HIGH");
        escalation.put("detail", escalationDetail);

        return WorkerResult.of(Map.of(
                "complianceEscalation", escalation,
                "complianceStatus", "escalated"));
    }

    private static String describeEscalation(String controlType, String outcome, boolean hasServiceId) {
        if (!"FAIL".equals(outcome)) return outcome + " — escalating (outcome not actionable)";
        if (!hasServiceId) return controlType + " — escalating (no service target)";
        return controlType + " — escalating (no auto-fix available)";
    }
}
