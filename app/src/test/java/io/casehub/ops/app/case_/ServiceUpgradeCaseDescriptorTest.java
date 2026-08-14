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
class ServiceUpgradeCaseDescriptorTest {

    @Test
    void caseDefinitionIdentity() {
        CaseDefinition def = ServiceUpgradeCaseDescriptor.build(null, null);
        assertThat(def.getNamespace()).isEqualTo("ops");
        assertThat(def.getName()).isEqualTo("service-upgrade");
        assertThat(def.getVersion()).isEqualTo("1.0");
    }

    @Test
    void hasFourCapabilities() {
        CaseDefinition def = ServiceUpgradeCaseDescriptor.build(null, null);
        assertThat(def.getCapabilities()).hasSize(4);
        assertThat(def.getCapabilities()).extracting("name")
                .containsExactlyInAnyOrder("assess-upgrade", "execute-upgrade",
                        "verify-upgrade", "escalate-upgrade");
    }

    @Test
    void hasThreeBindings() {
        CaseDefinition def = ServiceUpgradeCaseDescriptor.build(null, null);
        assertThat(def.getBindings()).hasSize(3);
        assertThat(def.getBindings()).extracting("name")
                .containsExactlyInAnyOrder("on-upgrade-assessment",
                        "on-upgrade-executed", "on-upgrade-escalation-required");
    }

    @Test
    void assessValidInputProducesAssessment() {
        var input = upgradeInput("gateway", "nginx:2.0", "app-1", "tenant-1");
        WorkerResult<?> result = ServiceUpgradeCaseDescriptor.assessUpgrade(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Success.class);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("upgradeAssessment");
        Map<String, Object> assessment = (Map<String, Object>) output.get("upgradeAssessment");
        assertThat(assessment.get("serviceId")).isEqualTo("gateway");
        assertThat(assessment.get("newImage")).isEqualTo("nginx:2.0");
        assertThat(assessment.get("applicationId")).isEqualTo("app-1");
    }

    @Test
    void assessMissingServiceIdFails() {
        var input = new LinkedHashMap<String, Object>();
        input.put("newImage", "nginx:2.0");
        input.put("applicationId", "app-1");
        input.put("tenancyId", "tenant-1");
        WorkerResult<?> result = ServiceUpgradeCaseDescriptor.assessUpgrade(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void assessMissingNewImageFails() {
        var input = new LinkedHashMap<String, Object>();
        input.put("serviceId", "gateway");
        input.put("applicationId", "app-1");
        input.put("tenancyId", "tenant-1");
        WorkerResult<?> result = ServiceUpgradeCaseDescriptor.assessUpgrade(input);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void assessNullInputFails() {
        WorkerResult<?> result = ServiceUpgradeCaseDescriptor.assessUpgrade(null);
        assertThat(result.outcome()).isInstanceOf(WorkerOutcome.Failed.class);
    }

    @Test
    void executeCallsUpdateServiceImage() {
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
        assessment.put("serviceId", "gateway");
        assessment.put("newImage", "nginx:2.0");
        assessment.put("applicationId", appId.toString());
        assessment.put("tenancyId", "tenant-1");
        var input = Map.<String, Object>of("upgradeAssessment", assessment);

        WorkerResult<?> result = ServiceUpgradeCaseDescriptor.executeUpgrade(input, mockService);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("upgradeExecuted");
        Map<String, Object> executed = (Map<String, Object>) output.get("upgradeExecuted");
        assertThat(executed.get("serviceId")).isEqualTo("gateway");
        assertThat(executed.get("newImage")).isEqualTo("nginx:2.0");
        assertThat((List<String>) executed.get("affectedNodeIds"))
                .containsExactlyInAnyOrderElementsOf(returnedNodeIds);
    }

    @Test
    void executeLifecycleServiceThrowsEscalates() {
        io.casehub.ops.app.service.ApplicationLifecycleService failingService =
                new io.casehub.ops.app.service.ApplicationLifecycleService() {
                    @Override
                    public Set<String> updateServiceImage(UUID appId, String serviceId,
                                                           String newImage, String tenancyId) {
                        throw new IllegalArgumentException("Service not found: bogus");
                    }
                };

        var assessment = new LinkedHashMap<String, Object>();
        assessment.put("serviceId", "bogus");
        assessment.put("newImage", "nginx:2.0");
        assessment.put("applicationId", UUID.randomUUID().toString());
        assessment.put("tenancyId", "tenant-1");
        var input = Map.<String, Object>of("upgradeAssessment", assessment);

        WorkerResult<?> result = ServiceUpgradeCaseDescriptor.executeUpgrade(input, failingService);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("upgradeEscalationRequired");
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
                "upgradeExecuted", Map.of(
                        "affectedNodeIds", List.of("cluster-1:gateway:deployment")));

        WorkerResult<?> result = ServiceUpgradeCaseDescriptor.verifyUpgrade(input, scope, tracker);

        assertThat((Map<?, ?>) result.output()).isEmpty();
        assertThat(tracker.isTracking(caseId)).isTrue();
    }

    @Test
    void escalateWritesEscalatedStatus() {
        var input = Map.<String, Object>of(
                "upgradeAssessment", Map.of(
                        "serviceId", "gateway",
                        "newImage", "nginx:2.0"));

        WorkerResult<?> result = ServiceUpgradeCaseDescriptor.escalateUpgrade(input);

        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output.get("upgradeStatus")).isEqualTo("escalated");
        assertThat(output).containsKey("upgradeEscalation");
    }

    @Test
    void escalateIncludesUpgradeError() {
        var input = Map.<String, Object>of(
                "upgradeAssessment", Map.of(
                        "serviceId", "gateway",
                        "newImage", "nginx:2.0"),
                "upgradeError", Map.of(
                        "reason", "Upgrade failed: Service not found"));

        WorkerResult<?> result = ServiceUpgradeCaseDescriptor.escalateUpgrade(input);

        Map<String, Object> output = (Map<String, Object>) result.output();
        Map<String, Object> escalation = (Map<String, Object>) output.get("upgradeEscalation");
        assertThat((String) escalation.get("detail")).contains("Upgrade failed");
    }

    private Map<String, Object> upgradeInput(String serviceId, String newImage,
                                              String appId, String tenancyId) {
        var map = new LinkedHashMap<String, Object>();
        map.put("serviceId", serviceId);
        map.put("newImage", newImage);
        map.put("applicationId", appId);
        map.put("tenancyId", tenancyId);
        return map;
    }
}
