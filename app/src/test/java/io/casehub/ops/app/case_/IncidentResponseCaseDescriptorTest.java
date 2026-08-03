package io.casehub.ops.app.case_;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.ops.app.service.ApplicationLifecycleService;
import io.casehub.ops.app.service.NodeConvergenceTracker;
import io.casehub.worker.api.WorkerFunction;
import io.casehub.worker.api.WorkerOutcome;
import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.WorkerScope;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentResponseCaseDescriptorTest {

    // --- Case definition structure ---

    @Test
    void buildReturnsCorrectIdentity() {
        CaseDefinition def = IncidentResponseCaseDescriptor.build(null, null);
        assertThat(def.getNamespace()).isEqualTo("ops");
        assertThat(def.getName()).isEqualTo("incident-response");
        assertThat(def.getVersion()).isEqualTo("1.0");
    }

    @Test
    void hasFourCapabilities() {
        CaseDefinition def = IncidentResponseCaseDescriptor.build(null, null);
        assertThat(def.getCapabilities()).hasSize(4);
        assertThat(def.getCapabilities()).extracting("name")
                .containsExactlyInAnyOrder("assess-incident", "remediate-incident",
                        "verify-remediation", "escalate-incident");
    }

    @Test
    void hasFourWorkers() {
        CaseDefinition def = IncidentResponseCaseDescriptor.build(null, null);
        assertThat(def.getWorkers()).hasSize(4);
    }

    @Test
    void hasThreeInternalBindings() {
        CaseDefinition def = IncidentResponseCaseDescriptor.build(null, null);
        assertThat(def.getBindings()).hasSize(3);
        assertThat(def.getBindings()).extracting("name")
                .containsExactlyInAnyOrder("on-assessment-complete",
                        "on-remediation-executed", "on-escalation-required");
    }

    @Test
    void hasCompletionPredicate() {
        CaseDefinition def = IncidentResponseCaseDescriptor.build(null, null);
        assertThat(def.getCompletion()).isNotNull();
    }

    @Test
    void assessmentBindingTriggersOnIncidentAssessment() {
        CaseDefinition def = IncidentResponseCaseDescriptor.build(null, null);
        var binding = def.getBindings().stream()
                .filter(b -> b.getName().equals("on-assessment-complete"))
                .findFirst().orElseThrow();
        assertThat(binding.getOn()).isInstanceOf(ContextChangeTrigger.class);
    }

    // --- Assess worker ---

    @Test
    void assessServiceDownReturnsRestart() {
        var input = incidentInput("SERVICE_DOWN");
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(input);
        var assessment = extractAssessment(result);
        assertThat(assessment.get("action")).isEqualTo("restart");
        assertThat(assessment.get("severity")).isEqualTo("critical");
    }

    @Test
    void assessDegradedReturnsRestart() {
        var input = incidentInput("DEGRADED");
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(input);
        assertThat(extractAssessment(result).get("action")).isEqualTo("restart");
        assertThat(extractAssessment(result).get("severity")).isEqualTo("warning");
    }

    @Test
    void assessCrashLoopReturnsRollback() {
        var input = incidentInput("CRASH_LOOP");
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(input);
        var assessment = extractAssessment(result);
        assertThat(assessment.get("action")).isEqualTo("rollback");
        assertThat(assessment.get("severity")).isEqualTo("critical");
    }

    @Test
    void assessResourcePressureReturnsScale() {
        var input = incidentInput("RESOURCE_PRESSURE");
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(input);
        assertThat(extractAssessment(result).get("action")).isEqualTo("scale");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assessUnknownTypeReturnsEscalation() {
        var input = incidentInput("SOLAR_FLARE");
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("escalationRequired");
        assertThat(output.get("escalationRequired")).isEqualTo(true);
        assertThat(output).containsKey("incidentAssessment");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assessNullTypeReturnsEscalation() {
        var input = new LinkedHashMap<String, Object>();
        input.put("serviceId", "order-api");
        input.put("applicationId", UUID.randomUUID().toString());
        input.put("tenancyId", "tenant-1");
        input.put("incidentType", null);
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("escalationRequired");
    }

    @Test
    void assessNullInputReturnsFailed() {
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(null);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void assessMissingServiceIdReturnsFailed() {
        var input = Map.<String, Object>of(
                "applicationId", UUID.randomUUID().toString(),
                "tenancyId", "tenant-1",
                "incidentType", "SERVICE_DOWN");
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void assessMissingApplicationIdReturnsFailed() {
        var input = Map.<String, Object>of(
                "serviceId", "order-api",
                "tenancyId", "tenant-1",
                "incidentType", "SERVICE_DOWN");
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void assessMissingTenancyIdReturnsFailed() {
        var input = Map.<String, Object>of(
                "serviceId", "order-api",
                "applicationId", UUID.randomUUID().toString(),
                "incidentType", "SERVICE_DOWN");
        WorkerResult<?> result = IncidentResponseCaseDescriptor.assessIncident(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    // --- Remediate worker ---

    @Test
    @SuppressWarnings("unchecked")
    void remediateRestartCallsRestartService() {
        Set<String> returnedNodeIds = Set.of("cluster-1:order-api:deployment");
        ApplicationLifecycleService mockService = new ApplicationLifecycleService() {
            @Override
            public Set<String> restartService(UUID appId, String serviceId, String tenancyId) {
                return returnedNodeIds;
            }
        };

        UUID appId = UUID.randomUUID();
        var assessment = Map.<String, Object>of(
                "action", "restart",
                "applicationId", appId.toString(),
                "serviceId", "order-api",
                "tenancyId", "tenant-1");
        var input = Map.<String, Object>of("incidentAssessment", assessment);

        WorkerResult<?> result = IncidentResponseCaseDescriptor.remediateIncident(input, mockService);

        Map<String, Object> executed = (Map<String, Object>)
                ((Map<String, Object>) result.output()).get("remediationExecuted");
        assertThat(executed.get("action")).isEqualTo("restart");
        assertThat(executed.get("serviceId")).isEqualTo("order-api");
        assertThat((List<String>) executed.get("affectedNodeIds"))
                .containsExactlyInAnyOrderElementsOf(returnedNodeIds);
    }

    @Test
    @SuppressWarnings("unchecked")
    void remediateRollbackCallsRollbackService() {
        Set<String> returnedNodeIds = Set.of("cluster-1:order-api:deployment");
        ApplicationLifecycleService mockService = new ApplicationLifecycleService() {
            @Override
            public Set<String> rollbackService(UUID appId, String serviceId, String tenancyId) {
                return returnedNodeIds;
            }
        };

        UUID appId = UUID.randomUUID();
        var assessment = Map.<String, Object>of(
                "action", "rollback",
                "applicationId", appId.toString(),
                "serviceId", "order-api",
                "tenancyId", "tenant-1");
        var input = Map.<String, Object>of("incidentAssessment", assessment);

        WorkerResult<?> result = IncidentResponseCaseDescriptor.remediateIncident(input, mockService);

        Map<String, Object> executed = (Map<String, Object>)
                ((Map<String, Object>) result.output()).get("remediationExecuted");
        assertThat(executed.get("action")).isEqualTo("rollback");
    }

    @Test
    @SuppressWarnings("unchecked")
    void remediateServiceFailureRoutesToEscalation() {
        ApplicationLifecycleService failingService = new ApplicationLifecycleService() {
            @Override
            public Set<String> restartService(UUID appId, String serviceId, String tenancyId) {
                throw new IllegalArgumentException("Application not found");
            }
        };

        UUID appId = UUID.randomUUID();
        var assessment = Map.<String, Object>of(
                "action", "restart",
                "applicationId", appId.toString(),
                "serviceId", "order-api",
                "tenancyId", "tenant-1");
        var input = Map.<String, Object>of("incidentAssessment", assessment);

        WorkerResult<?> result = IncidentResponseCaseDescriptor.remediateIncident(input, failingService);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("escalationRequired");
        assertThat(output).containsKey("remediationError");
    }

    // --- Verify worker ---

    @Test
    void verifyRegistersWithConvergenceTracker() {
        NodeConvergenceTracker tracker = new NodeConvergenceTracker(
                (caseId, path, value) -> {},
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));

        UUID caseId = UUID.randomUUID();
        WorkerScope scope = new TestWorkerScope(caseId);
        var input = Map.<String, Object>of(
                "remediationExecuted", Map.of(
                        "action", "restart",
                        "serviceId", "order-api",
                        "affectedNodeIds", List.of("cluster-1:order-api:deployment")));

        WorkerResult<?> result = IncidentResponseCaseDescriptor.verifyRemediation(
                input, scope, tracker);

        assertThat((Map<?, ?>) result.output()).isEmpty();
        assertThat(tracker.isTracking(caseId)).isTrue();
    }

    // --- Escalate worker ---

    @Test
    @SuppressWarnings("unchecked")
    void escalateWritesStatusAndSummary() {
        var input = Map.<String, Object>of(
                "incidentAssessment", Map.of(
                        "serviceId", "order-api",
                        "incidentType", "UNKNOWN",
                        "reason", "Unknown incident"));

        WorkerResult<?> result = IncidentResponseCaseDescriptor.escalateIncident(input);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output.get("incidentStatus")).isEqualTo("escalated");
        assertThat(output).containsKey("escalation");
        Map<String, Object> escalation = (Map<String, Object>) output.get("escalation");
        assertThat(escalation.get("serviceId")).isEqualTo("order-api");
        assertThat(escalation.get("risk")).isEqualTo("HIGH");
    }

    @Test
    @SuppressWarnings("unchecked")
    void escalateIncludesRemediationError() {
        var input = Map.<String, Object>of(
                "incidentAssessment", Map.of(
                        "serviceId", "order-api",
                        "incidentType", "SERVICE_DOWN",
                        "reason", "Service down"),
                "remediationError", Map.of(
                        "reason", "Remediation failed: Application not found",
                        "action", "restart",
                        "serviceId", "order-api"));

        WorkerResult<?> result = IncidentResponseCaseDescriptor.escalateIncident(input);

        Map<String, Object> output = (Map<String, Object>) result.output();
        Map<String, Object> escalation = (Map<String, Object>) output.get("escalation");
        assertThat((String) escalation.get("detail")).contains("Remediation failed");
    }

    // --- Test helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAssessment(WorkerResult<?> result) {
        return (Map<String, Object>) ((Map<String, Object>) result.output()).get("incidentAssessment");
    }

    private Map<String, Object> incidentInput(String type) {
        var input = new LinkedHashMap<String, Object>();
        input.put("serviceId", "order-api");
        input.put("applicationId", UUID.randomUUID().toString());
        input.put("tenancyId", "tenant-1");
        input.put("incidentType", type);
        return input;
    }

    record TestWorkerScope(UUID caseId) implements WorkerScope {
        @Override public String taskId() { return "test"; }
        @Override public <T, R> WorkerResult<R> execute(WorkerFunction<T, R> function, T input) {
            throw new UnsupportedOperationException();
        }
        @Override public WorkerResult<?> execute(String workerName, Map<String, Object> input) {
            throw new UnsupportedOperationException();
        }
    }
}
