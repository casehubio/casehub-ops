package io.casehub.ops.app.case_;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.worker.api.WorkerOutcome;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceRemediationCaseDescriptorTest {

    // --- Case definition structure ---

    @Test
    void buildReturnsCorrectIdentity() {
        CaseDefinition def = ComplianceRemediationCaseDescriptor.build(null, null);
        assertThat(def.getNamespace()).isEqualTo("ops");
        assertThat(def.getName()).isEqualTo("compliance-remediation");
        assertThat(def.getVersion()).isEqualTo("1.0");
    }

    @Test
    void hasFourCapabilities() {
        CaseDefinition def = ComplianceRemediationCaseDescriptor.build(null, null);
        assertThat(def.getCapabilities()).hasSize(4);
        assertThat(def.getCapabilities()).extracting("name")
                .containsExactlyInAnyOrder("assess-compliance", "remediate-compliance",
                        "verify-compliance", "escalate-compliance");
    }

    @Test
    void hasFourWorkers() {
        CaseDefinition def = ComplianceRemediationCaseDescriptor.build(null, null);
        assertThat(def.getWorkers()).hasSize(4);
    }

    @Test
    void hasThreeInternalBindings() {
        CaseDefinition def = ComplianceRemediationCaseDescriptor.build(null, null);
        assertThat(def.getBindings()).hasSize(3);
        assertThat(def.getBindings()).extracting("name")
                .containsExactlyInAnyOrder("on-compliance-assessment",
                        "on-compliance-remediation-executed", "on-compliance-escalation-required");
    }

    @Test
    void hasCompletionPredicate() {
        CaseDefinition def = ComplianceRemediationCaseDescriptor.build(null, null);
        assertThat(def.getCompletion()).isNotNull();
    }

    @Test
    void assessmentBindingTriggersOnComplianceAssessment() {
        CaseDefinition def = ComplianceRemediationCaseDescriptor.build(null, null);
        var binding = def.getBindings().stream()
                .filter(b -> b.getName().equals("on-compliance-assessment"))
                .findFirst().orElseThrow();
        assertThat(binding.getOn()).isInstanceOf(ContextChangeTrigger.class);
    }

    // --- Assess worker: auto-fixable controls ---

    @Test
    void assessFailLogRetentionWithServiceIdReturnsUpdateConfig() {
        var input = violationInput("log-retention-policy", "LOG_RETENTION", "FAIL", "order-api");
        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.assessCompliance(input);
        var assessment = extractAssessment(result);
        assertThat(assessment.get("action")).isEqualTo("update-config");
        assertThat(assessment.get("controlType")).isEqualTo("LOG_RETENTION");
        @SuppressWarnings("unchecked")
        Map<String, String> configUpdates = (Map<String, String>) assessment.get("configUpdates");
        assertThat(configUpdates).containsEntry("LOG_RETENTION_DAYS", "365");
        assertThat(configUpdates).containsEntry("LOG_RETENTION_ENABLED", "true");
    }

    @Test
    void assessFailEncryptionWithServiceIdReturnsUpdateConfig() {
        var input = violationInput("encryption-at-rest", "ENCRYPTION_AT_REST", "FAIL", "order-api");
        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.assessCompliance(input);
        var assessment = extractAssessment(result);
        assertThat(assessment.get("action")).isEqualTo("update-config");
        @SuppressWarnings("unchecked")
        Map<String, String> configUpdates = (Map<String, String>) assessment.get("configUpdates");
        assertThat(configUpdates).containsEntry("ENCRYPTION_ENABLED", "true");
        assertThat(configUpdates).containsEntry("ENCRYPTION_CIPHER", "AES-256");
    }

    // --- Assess worker: escalation paths ---

    @Test
    @SuppressWarnings("unchecked")
    void assessFailAutoFixableNoServiceIdEscalates() {
        var input = violationInput("log-retention-policy", "LOG_RETENTION", "FAIL", null);
        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.assessCompliance(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("complianceEscalationRequired");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assessFailNonAutoFixableEscalates() {
        var input = violationInput("access-review-quarterly", "ACCESS_REVIEW", "FAIL", "order-api");
        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.assessCompliance(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("complianceEscalationRequired");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assessUnavailableOutcomeEscalates() {
        var input = violationInput("encryption-at-rest", "ENCRYPTION_AT_REST", "UNAVAILABLE", "order-api");
        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.assessCompliance(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("complianceEscalationRequired");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assessStaleOutcomeEscalates() {
        var input = violationInput("encryption-at-rest", "ENCRYPTION_AT_REST", "STALE", "order-api");
        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.assessCompliance(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("complianceEscalationRequired");
    }

    // --- Assess worker: validation failures ---

    @Test
    void assessNullInputReturnsFailed() {
        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.assessCompliance(null);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void assessMissingControlIdReturnsFailed() {
        var input = new LinkedHashMap<String, Object>();
        input.put("controlType", "LOG_RETENTION");
        input.put("outcome", "FAIL");
        input.put("tenancyId", "tenant-1");
        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.assessCompliance(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void assessMissingOutcomeReturnsFailed() {
        var input = new LinkedHashMap<String, Object>();
        input.put("controlId", "encryption-at-rest");
        input.put("controlType", "ENCRYPTION_AT_REST");
        input.put("tenancyId", "tenant-1");
        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.assessCompliance(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }
// --- Remediate worker ---

    @Test
    @SuppressWarnings("unchecked")
    void remediateUpdateConfigCallsUpdateServiceConfig() {
        java.util.Set<String> returnedNodeIds = java.util.Set.of("cluster-1:order-api:deployment");
        io.casehub.ops.app.service.ApplicationLifecycleService mockService =
                new io.casehub.ops.app.service.ApplicationLifecycleService() {
                    @Override
                    public java.util.Set<String> updateServiceConfig(java.util.UUID appId, String serviceId,
                                                                     java.util.Map<String, String> configUpdates, String tenancyId) {
                        return returnedNodeIds;
                    }
                };

        java.util.UUID appId      = java.util.UUID.randomUUID();
        var            assessment = new LinkedHashMap<String, Object>();
        assessment.put("action", "update-config");
        assessment.put("controlId", "encryption-at-rest");
        assessment.put("applicationId", appId.toString());
        assessment.put("serviceId", "order-api");
        assessment.put("tenancyId", "tenant-1");
        assessment.put("configUpdates", Map.of("ENCRYPTION_ENABLED", "true"));
        var input = Map.<String, Object>of("complianceAssessment", assessment);

        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.remediateCompliance(
                input, mockService);

        Map<String, Object> executed = (Map<String, Object>)
                                               ((Map<String, Object>) result.output()).get("complianceRemediationExecuted");
        assertThat(executed.get("action")).isEqualTo("update-config");
        assertThat(executed.get("controlId")).isEqualTo("encryption-at-rest");
        assertThat(executed.get("serviceId")).isEqualTo("order-api");
        assertThat((List<String>) executed.get("affectedNodeIds"))
                .containsExactlyInAnyOrderElementsOf(returnedNodeIds);
    }

    @Test
    @SuppressWarnings("unchecked")
    void remediateServiceFailureRoutesToEscalation() {
        io.casehub.ops.app.service.ApplicationLifecycleService failingService =
                new io.casehub.ops.app.service.ApplicationLifecycleService() {
                    @Override
                    public java.util.Set<String> updateServiceConfig(java.util.UUID appId, String serviceId,
                                                                     java.util.Map<String, String> configUpdates, String tenancyId) {
                        throw new IllegalArgumentException("Application not found");
                    }
                };

        java.util.UUID appId      = java.util.UUID.randomUUID();
        var            assessment = new LinkedHashMap<String, Object>();
        assessment.put("action", "update-config");
        assessment.put("controlId", "encryption-at-rest");
        assessment.put("applicationId", appId.toString());
        assessment.put("serviceId", "order-api");
        assessment.put("tenancyId", "tenant-1");
        assessment.put("configUpdates", Map.of("ENCRYPTION_ENABLED", "true"));
        var input = Map.<String, Object>of("complianceAssessment", assessment);

        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.remediateCompliance(
                input, failingService);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("complianceEscalationRequired");
        assertThat(output).containsKey("complianceRemediationError");
    }
// --- Verify worker ---

    @Test
    void verifyRegistersWithConvergenceTracker() {
        io.casehub.ops.app.service.NodeConvergenceTracker tracker =
                new io.casehub.ops.app.service.NodeConvergenceTracker(
                        (caseId, path, value) -> {},
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));

        java.util.UUID                    caseId = java.util.UUID.randomUUID();
        io.casehub.worker.api.WorkerScope scope  = new IncidentResponseCaseDescriptorTest.TestWorkerScope(caseId);
        var input = Map.<String, Object>of(
                "complianceRemediationExecuted", Map.of(
                        "action", "update-config",
                        "controlId", "encryption-at-rest",
                        "serviceId", "order-api",
                        "affectedNodeIds", List.of("cluster-1:order-api:deployment")));

        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.verifyCompliance(
                input, scope, tracker);

        assertThat((Map<?, ?>) result.output()).isEmpty();
        assertThat(tracker.isTracking(caseId)).isTrue();
    }
// --- Escalate worker ---

    @Test
    @SuppressWarnings("unchecked")
    void escalateWritesStatusAndSummary() {
        var input = Map.<String, Object>of(
                "complianceAssessment", Map.of(
                        "controlId", "access-review-quarterly",
                        "controlType", "ACCESS_REVIEW",
                        "outcome", "FAIL",
                        "serviceId", "order-api",
                        "frameworks", List.of("SOC2:CC6.2"),
                        "detail", "Access review overdue"));

        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.escalateCompliance(input);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output.get("complianceStatus")).isEqualTo("escalated");
        assertThat(output).containsKey("complianceEscalation");
        Map<String, Object> escalation = (Map<String, Object>) output.get("complianceEscalation");
        assertThat(escalation.get("controlId")).isEqualTo("access-review-quarterly");
        assertThat(escalation.get("risk")).isEqualTo("HIGH");
    }

    @Test
    @SuppressWarnings("unchecked")
    void escalateIncludesRemediationError() {
        var input = Map.<String, Object>of(
                "complianceAssessment", Map.of(
                        "controlId", "encryption-at-rest",
                        "controlType", "ENCRYPTION_AT_REST",
                        "outcome", "FAIL",
                        "serviceId", "order-api"),
                "complianceRemediationError", Map.of(
                        "reason", "Remediation failed: Application not found",
                        "controlId", "encryption-at-rest",
                        "serviceId", "order-api"));

        WorkerResult<?> result = ComplianceRemediationCaseDescriptor.escalateCompliance(input);

        Map<String, Object> output     = (Map<String, Object>) result.output();
        Map<String, Object> escalation = (Map<String, Object>) output.get("complianceEscalation");
        assertThat((String) escalation.get("detail")).contains("Remediation failed");
    }


    // --- Test helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAssessment(WorkerResult<?> result) {
        return (Map<String, Object>) ((Map<String, Object>) result.output()).get("complianceAssessment");
    }

    private Map<String, Object> violationInput(String controlId, String controlType,
                                                String outcome, String serviceId) {
        var input = new LinkedHashMap<String, Object>();
        input.put("controlId", controlId);
        input.put("controlType", controlType);
        input.put("outcome", outcome);
        input.put("tenancyId", "tenant-1");
        input.put("applicationId", UUID.randomUUID().toString());
        input.put("frameworks", List.of("SOC2:CC6.1", "GDPR:Art.32"));
        input.put("detail", "Test violation detail");
        if (serviceId != null) input.put("serviceId", serviceId);
        return input;
    }
}
