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

public final class IncidentResponseCaseDescriptor {

    private IncidentResponseCaseDescriptor() {}

    public static CaseDefinition build(ApplicationLifecycleService lifecycleService,
                                        NodeConvergenceTracker convergenceTracker) {
        return CaseDefinition.builder()
                .namespace("ops")
                .name("incident-response")
                .version("1.0")
                .title("Incident Response")
                .summary("Assesses, remediates, and verifies resolution of service incidents")
                .capabilities(capabilities())
                .workers(workers(lifecycleService, convergenceTracker))
                .bindings(bindings())
                .completion(".incidentStatus == \"resolved\" || .incidentStatus == \"escalated\"")
                .build();
    }

    private static List<Capability> capabilities() {
        return List.of(
                Capability.of("assess-incident", "any", "any"),
                Capability.of("remediate-incident", "any", "any"),
                Capability.of("verify-remediation", "any", "any"),
                Capability.of("escalate-incident", "any", "any"));
    }

    @SuppressWarnings("unchecked")
    private static List<Worker> workers(ApplicationLifecycleService lifecycleService,
                                         NodeConvergenceTracker convergenceTracker) {
        return List.of(
                Worker.builder()
                        .name("incident-assess-worker")
                        .capabilityName("assess-incident")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> assessIncident(input)))
                        .build(),
                Worker.builder()
                        .name("incident-remediate-worker")
                        .capabilityName("remediate-incident")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> remediateIncident(input, lifecycleService)))
                        .build(),
                Worker.builder()
                        .name("incident-verify-worker")
                        .capabilityName("verify-remediation")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> verifyRemediation(input, scope, convergenceTracker)))
                        .build(),
                Worker.builder()
                        .name("incident-escalate-worker")
                        .capabilityName("escalate-incident")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> escalateIncident(input)))
                        .build());
    }

    private static List<Binding> bindings() {
        return List.of(
                Binding.builder()
                        .name("on-assessment-complete")
                        .on(new ContextChangeTrigger(".incidentAssessment"))
                        .capability(Capability.of("remediate-incident", "any", "any"))
                        .build(),
                Binding.builder()
                        .name("on-remediation-executed")
                        .on(new ContextChangeTrigger(".remediationExecuted"))
                        .capability(Capability.of("verify-remediation", "any", "any"))
                        .build(),
                Binding.builder()
                        .name("on-escalation-required")
                        .on(new ContextChangeTrigger(".escalationRequired"))
                        .capability(Capability.of("escalate-incident", "any", "any"))
                        .build());
    }

    static WorkerResult assessIncident(Map<String, Object> input) {
        if (input == null) return WorkerResult.failed("Incident data is null");

        String serviceId = (String) input.get("serviceId");
        String applicationId = (String) input.get("applicationId");
        String tenancyId = (String) input.get("tenancyId");
        String incidentType = (String) input.get("incidentType");

        if (serviceId == null || serviceId.isBlank())
            return WorkerResult.failed("serviceId is required");
        if (applicationId == null || applicationId.isBlank())
            return WorkerResult.failed("applicationId is required");
        if (tenancyId == null || tenancyId.isBlank())
            return WorkerResult.failed("tenancyId is required");

        String action = switch (incidentType) {
            case "SERVICE_DOWN", "DEGRADED" -> "restart";
            case "CRASH_LOOP" -> "rollback";
            case "RESOURCE_PRESSURE" -> "scale";
            case null, default -> "escalate";
        };

        String severity = "SERVICE_DOWN".equals(incidentType) || "CRASH_LOOP".equals(incidentType)
                           ? "critical" : "warning";

        var assessment = new LinkedHashMap<String, Object>();
        assessment.put("action", action);
        assessment.put("severity", severity);
        assessment.put("serviceId", serviceId);
        assessment.put("applicationId", applicationId);
        assessment.put("tenancyId", tenancyId);
        assessment.put("incidentType", incidentType);
        assessment.put("reason", describeAction(action, incidentType));

        if ("escalate".equals(action)) {
            return WorkerResult.of(Map.of(
                    "escalationRequired", true,
                    "incidentAssessment", assessment));
        }

        return WorkerResult.of(Map.of("incidentAssessment", assessment));
    }

    @SuppressWarnings("unchecked")
    static WorkerResult remediateIncident(Map<String, Object> input,
                                           ApplicationLifecycleService lifecycleService) {
        Map<String, Object> assessment = (Map<String, Object>) input.get("incidentAssessment");
        String action = (String) assessment.get("action");
        String applicationId = (String) assessment.get("applicationId");
        String serviceId = (String) assessment.get("serviceId");
        String tenancyId = (String) assessment.get("tenancyId");

        Set<String> affectedNodeIds;
        try {
            affectedNodeIds = switch (action) {
                case "restart" -> lifecycleService.restartService(
                        UUID.fromString(applicationId), serviceId, tenancyId);
                case "rollback" -> lifecycleService.rollbackService(
                        UUID.fromString(applicationId), serviceId, tenancyId);
                case "scale" -> {
                    int newReplicas = lookupCurrentReplicas(applicationId, serviceId) + 1;
                    yield lifecycleService.updateServiceReplicas(
                            UUID.fromString(applicationId), serviceId, newReplicas, tenancyId);
                }
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            };
        } catch (Exception e) {
            var escalation = new LinkedHashMap<String, Object>();
            escalation.put("reason", "Remediation failed: " + e.getMessage());
            escalation.put("action", action);
            escalation.put("serviceId", serviceId);
            return WorkerResult.of(Map.of("escalationRequired", true,
                                           "remediationError", escalation));
        }

        var executed = new LinkedHashMap<String, Object>();
        executed.put("action", action);
        executed.put("serviceId", serviceId);
        executed.put("affectedNodeIds", List.copyOf(affectedNodeIds));

        return WorkerResult.of(Map.of("remediationExecuted", executed));
    }

    private static int lookupCurrentReplicas(String applicationId, String serviceId) {
        var app = io.casehub.ops.app.entity.ApplicationEntity
                .<io.casehub.ops.app.entity.ApplicationEntity>findById(UUID.fromString(applicationId));
        if (app == null) throw new IllegalArgumentException("Application not found: " + applicationId);
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            @SuppressWarnings("unchecked")
            List<io.casehub.ops.app.model.ServiceDefinition> services = mapper.readValue(app.servicesJson,
                    mapper.getTypeFactory().constructCollectionType(List.class,
                            io.casehub.ops.app.model.ServiceDefinition.class));
            for (var sd : services) {
                if (sd.serviceId().equals(serviceId)) return sd.replicas();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read current replicas", e);
        }
        throw new IllegalArgumentException("Service not found: " + serviceId);
    }

    @SuppressWarnings("unchecked")
    static WorkerResult verifyRemediation(Map<String, Object> input,
                                          WorkerScope scope,
                                          NodeConvergenceTracker convergenceTracker) {
        Map<String, Object> executed = (Map<String, Object>) input.get("remediationExecuted");
        List<String> affectedNodeIdsList = (List<String>) executed.get("affectedNodeIds");
        Set<String> affectedNodeIds = new HashSet<>(affectedNodeIdsList);

        UUID caseId = scope.caseId();
        convergenceTracker.register(caseId, affectedNodeIds, "incidentStatus", "resolved");

        return WorkerResult.of(Map.of());
    }

    @SuppressWarnings("unchecked")
    static WorkerResult escalateIncident(Map<String, Object> input) {
        Map<String, Object> assessment = (Map<String, Object>) input.getOrDefault(
                "incidentAssessment", Map.of());
        String serviceId = (String) assessment.getOrDefault("serviceId", "unknown");
        String incidentType = (String) assessment.getOrDefault("incidentType", "unknown");

        Map<String, Object> remediationError = (Map<String, Object>) input.get("remediationError");
        String detail = remediationError != null
                ? (String) remediationError.get("reason")
                : "Auto-remediation not available for incident type: " + incidentType;

        var escalation = new LinkedHashMap<String, Object>();
        escalation.put("summary", "Incident on " + serviceId + " requires human review");
        escalation.put("detail", detail);
        escalation.put("serviceId", serviceId);
        escalation.put("incidentType", incidentType);
        escalation.put("risk", "HIGH");

        return WorkerResult.of(Map.of(
                "escalation", escalation,
                "incidentStatus", "escalated"));
    }

    private static String describeAction(String action, String incidentType) {
        return switch (action) {
            case "restart" -> incidentType + " — attempting pod restart";
            case "rollback" -> incidentType + " — rolling back to previous image";
            case "scale" -> incidentType + " — scaling up replicas";
            case "escalate" -> "Unknown incident type '" + incidentType + "' — escalating to human review";
            default -> action;
        };
    }
}
