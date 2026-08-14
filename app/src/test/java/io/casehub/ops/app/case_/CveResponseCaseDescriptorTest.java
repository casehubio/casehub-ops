package io.casehub.ops.app.case_;

import io.casehub.api.model.CaseDefinition;
import io.casehub.worker.api.WorkerOutcome;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class CveResponseCaseDescriptorTest {

    @Test
    void caseDefinitionIdentity() {
        CaseDefinition def = CveResponseCaseDescriptor.build(null, null);
        assertThat(def.getNamespace()).isEqualTo("ops");
        assertThat(def.getName()).isEqualTo("cve-response");
        assertThat(def.getVersion()).isEqualTo("1.0");
    }

    @Test
    void hasFourCapabilities() {
        CaseDefinition def = CveResponseCaseDescriptor.build(null, null);
        assertThat(def.getCapabilities()).hasSize(4);
        assertThat(def.getCapabilities()).extracting("name")
                .containsExactlyInAnyOrder("assess-cve", "remediate-cve", "verify-cve", "escalate-cve");
    }

    @Test
    void assessWithFixedInTagAndServicesProducesUpdateImage() {
        var input = cveInput("CVE-2026-1234", "HIGH", "nginx:1.24",
                List.of("gateway"), "nginx:1.25", "app-1", "tenant-1");
        WorkerResult<?> result = CveResponseCaseDescriptor.assessCve(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Success.class);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("cveAssessment");
        Map<String, Object> assessment = (Map<String, Object>) output.get("cveAssessment");
        assertThat(assessment.get("action")).isEqualTo("update-image");
        assertThat(assessment.get("cveId")).isEqualTo("CVE-2026-1234");
        assertThat(assessment.get("fixedInTag")).isEqualTo("nginx:1.25");
    }

    @Test
    void assessWithNullFixedInTagEscalates() {
        var input = cveInput("CVE-2026-5678", "CRITICAL", "openssl:3.0",
                List.of("auth"), null, "app-1", "tenant-1");
        WorkerResult<?> result = CveResponseCaseDescriptor.assessCve(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Success.class);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("cveEscalationRequired");
    }

    @Test
    void assessWithNoAffectedServicesEscalates() {
        var input = cveInput("CVE-2026-9999", "MEDIUM", "busybox:1.36",
                List.of(), "busybox:1.37", "app-1", "tenant-1");
        WorkerResult<?> result = CveResponseCaseDescriptor.assessCve(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Success.class);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("cveEscalationRequired");
    }

    @Test
    void assessHighSeveritySetsApprovalFlag() {
        var input = cveInput("CVE-2026-1234", "HIGH", "nginx:1.24",
                List.of("gateway"), "nginx:1.25", "app-1", "tenant-1");
        WorkerResult<?> result = CveResponseCaseDescriptor.assessCve(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsEntry("cveApprovalRequired", true);
    }

    @Test
    void assessCriticalSeveritySetsApprovalFlag() {
        var input = cveInput("CVE-2026-1234", "CRITICAL", "nginx:1.24",
                List.of("gateway"), "nginx:1.25", "app-1", "tenant-1");
        WorkerResult<?> result = CveResponseCaseDescriptor.assessCve(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsEntry("cveApprovalRequired", true);
    }

    @Test
    void assessMediumSeverityNoApprovalFlag() {
        var input = cveInput("CVE-2026-1234", "MEDIUM", "nginx:1.24",
                List.of("gateway"), "nginx:1.25", "app-1", "tenant-1");
        WorkerResult<?> result = CveResponseCaseDescriptor.assessCve(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).doesNotContainKey("cveApprovalRequired");
    }

    @Test
    void assessMissingCveIdFails() {
        var input = new LinkedHashMap<String, Object>();
        input.put("severity", "HIGH");
        input.put("affectedImage", "nginx:1.24");
        WorkerResult<?> result = CveResponseCaseDescriptor.assessCve(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void assessMissingSeverityFails() {
        var input = new LinkedHashMap<String, Object>();
        input.put("cveId", "CVE-2026-1234");
        input.put("affectedImage", "nginx:1.24");
        WorkerResult<?> result = CveResponseCaseDescriptor.assessCve(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void assessNullInputFails() {
        WorkerResult<?> result = CveResponseCaseDescriptor.assessCve(null);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void remediateCallsUpdateServiceImage() {
        Set<String> returnedNodeIds = Set.of("cluster-1:gateway:deployment");
        io.casehub.ops.app.service.ApplicationLifecycleService mockService =
                new io.casehub.ops.app.service.ApplicationLifecycleService() {
                    @Override
                    public Set<String> updateServiceImage(UUID appId, String serviceId,
                                                           String newImage, String tenancyId) {
                        return returnedNodeIds;
                    }
                };

        UUID appId = UUID.randomUUID();
        var assessment = new LinkedHashMap<String, Object>();
        assessment.put("action", "update-image");
        assessment.put("cveId", "CVE-2026-1234");
        assessment.put("fixedInTag", "nginx:1.25");
        assessment.put("affectedServices", List.of("gateway"));
        assessment.put("applicationId", appId.toString());
        assessment.put("tenancyId", "tenant-1");
        var input = Map.<String, Object>of("cveAssessment", assessment);

        WorkerResult<?> result = CveResponseCaseDescriptor.remediateCve(input, mockService);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("cveRemediationExecuted");
        Map<String, Object> executed = (Map<String, Object>) output.get("cveRemediationExecuted");
        assertThat((List<String>) executed.get("affectedNodeIds"))
                .containsExactlyInAnyOrderElementsOf(returnedNodeIds);
    }

    @Test
    void remediateLifecycleServiceThrowsEscalates() {
        io.casehub.ops.app.service.ApplicationLifecycleService failingService =
                new io.casehub.ops.app.service.ApplicationLifecycleService() {
                    @Override
                    public Set<String> updateServiceImage(UUID appId, String serviceId,
                                                           String newImage, String tenancyId) {
                        throw new IllegalArgumentException("Service not found: bogus");
                    }
                };

        var assessment = new LinkedHashMap<String, Object>();
        assessment.put("action", "update-image");
        assessment.put("cveId", "CVE-2026-1234");
        assessment.put("fixedInTag", "nginx:1.25");
        assessment.put("affectedServices", List.of("bogus"));
        assessment.put("applicationId", UUID.randomUUID().toString());
        assessment.put("tenancyId", "tenant-1");
        var input = Map.<String, Object>of("cveAssessment", assessment);

        WorkerResult<?> result = CveResponseCaseDescriptor.remediateCve(input, failingService);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("cveEscalationRequired");
    }

    @Test
    void verifyRegistersWithConvergenceTracker() {
        io.casehub.ops.app.service.NodeConvergenceTracker tracker =
                new io.casehub.ops.app.service.NodeConvergenceTracker(
                        (caseId, path, value) -> {},
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));

        UUID caseId = UUID.randomUUID();
        io.casehub.worker.api.WorkerScope scope = new IncidentResponseCaseDescriptorTest.TestWorkerScope(caseId);
        var input = Map.<String, Object>of(
                "cveRemediationExecuted", Map.of(
                        "affectedNodeIds", List.of("cluster-1:gateway:deployment")));

        WorkerResult<?> result = CveResponseCaseDescriptor.verifyCve(input, scope, tracker);

        assertThat((Map<?, ?>) result.output()).isEmpty();
        assertThat(tracker.isTracking(caseId)).isTrue();
    }

    @Test
    void escalateWritesEscalatedStatus() {
        var input = Map.<String, Object>of(
                "cveAssessment", Map.of(
                        "cveId", "CVE-2026-1234",
                        "severity", "HIGH",
                        "affectedImage", "nginx:1.24"));

        WorkerResult<?> result = CveResponseCaseDescriptor.escalateCve(input);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output.get("cveStatus")).isEqualTo("escalated");
        assertThat(output).containsKey("cveEscalation");
    }

    private Map<String, Object> cveInput(String cveId, String severity, String image,
                                          List<String> services, String fixedInTag,
                                          String appId, String tenancyId) {
        var map = new LinkedHashMap<String, Object>();
        map.put("cveId", cveId);
        map.put("severity", severity);
        map.put("affectedImage", image);
        map.put("affectedServices", services);
        if (fixedInTag != null) map.put("fixedInTag", fixedInTag);
        map.put("applicationId", appId);
        map.put("tenancyId", tenancyId);
        return map;
    }
}
