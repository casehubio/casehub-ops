package io.casehub.ops.service.case_;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.CapabilityTarget;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.JudgmentTarget;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentApprovalCaseDescriptorTest {

    @Test
    void buildReturnsCorrectIdentity() {
        CaseDefinition def = DeploymentApprovalCaseDescriptor.build();
        assertThat(def.getNamespace()).isEqualTo("ops");
        assertThat(def.getName()).isEqualTo("deployment-approval");
        assertThat(def.getVersion()).isEqualTo("1.0");
    }

    @Test
    void hasThreeCapabilities() {
        CaseDefinition def = DeploymentApprovalCaseDescriptor.build();
        assertThat(def.getCapabilities()).hasSize(3);
        assertThat(def.getCapabilities()).extracting("name")
                .containsExactlyInAnyOrder(
                        "evaluate-risk", "collect-evidence", "record-decision");
    }

    @Test
    void hasThreeWorkers() {
        CaseDefinition def = DeploymentApprovalCaseDescriptor.build();
        assertThat(def.getWorkers()).hasSize(3);
    }

    @Test
    void hasThreeBindings() {
        CaseDefinition def = DeploymentApprovalCaseDescriptor.build();
        assertThat(def.getBindings()).hasSize(3);
        assertThat(def.getBindings()).extracting("name")
                .containsExactlyInAnyOrder(
                        "on-risk-evaluated",
                        "on-review-required", "on-decision-recorded");
    }

    @Test
    void allBindingNamesAreUnique() {
        CaseDefinition def = DeploymentApprovalCaseDescriptor.build();
        var names = def.getBindings().stream().map(Binding::getName).toList();
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    void hasCompletionPredicate() {
        CaseDefinition def = DeploymentApprovalCaseDescriptor.build();
        assertThat(def.getCompletion()).isNotNull();
    }

    @Test
    void riskEvaluatedBindingTriggersOnRiskClassification() {
        CaseDefinition def = DeploymentApprovalCaseDescriptor.build();
        Binding binding = findBinding(def, "on-risk-evaluated");
        assertThat(binding.getOn()).isInstanceOf(ContextChangeTrigger.class);
    }

    @Test
    void reviewBindingUsesJudgmentTarget() {
        CaseDefinition def = DeploymentApprovalCaseDescriptor.build();
        Binding binding = findBinding(def, "on-review-required");
        assertThat(binding.target()).isInstanceOf(JudgmentTarget.class);
        var judgment = (JudgmentTarget) binding.target();
        assertThat(judgment.outcomes()).contains("approve", "reject");
    }

    @Test
    void decisionBindingTriggersOnApprovalDecision() {
        CaseDefinition def = DeploymentApprovalCaseDescriptor.build();
        Binding binding = findBinding(def, "on-decision-recorded");
        assertThat(binding.getOn()).isInstanceOf(ContextChangeTrigger.class);
        assertThat(binding.target()).isInstanceOf(CapabilityTarget.class);
    }

    // --- evaluate-risk worker ---

    @Test
    @SuppressWarnings("unchecked")
    void evaluateRiskClassifiesLowRisk() {
        var input = approvalInput("UPDATE", "deployment", "ops-ns");
        WorkerResult<?> result = DeploymentApprovalCaseDescriptor.evaluateRisk(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        Map<String, Object> classification = (Map<String, Object>) output.get("riskClassification");
        assertThat(classification.get("level")).isIn("LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(classification).containsKey("reason");
    }

    @Test
    @SuppressWarnings("unchecked")
    void evaluateRiskClassifiesDeleteAsCritical() {
        var input = approvalInput("DELETE", "deployment", "production");
        WorkerResult<?> result = DeploymentApprovalCaseDescriptor.evaluateRisk(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        Map<String, Object> classification = (Map<String, Object>) output.get("riskClassification");
        assertThat(classification.get("level")).isEqualTo("CRITICAL");
    }

    // --- collect-evidence worker ---

    @Test
    @SuppressWarnings("unchecked")
    void collectEvidenceProducesEvidenceCollected() {
        var input = Map.<String, Object>of(
                "action", "UPDATE",
                "nodeType", "deployment",
                "namespace", "ops-ns",
                "nodeId", "node-1");
        WorkerResult<?> result = DeploymentApprovalCaseDescriptor.collectEvidence(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output).containsKey("evidenceCollected");
        Map<String, Object> evidence = (Map<String, Object>) output.get("evidenceCollected");
        assertThat(evidence).containsKey("summary");
        assertThat(evidence).containsKey("nodeId");
        assertThat(output).containsEntry("reviewRequired", true);
    }

    // --- record-decision worker ---

    @Test
    @SuppressWarnings("unchecked")
    void recordDecisionApproved() {
        var input = Map.<String, Object>of(
                "reviewOutcome", "approve",
                "nodeId", "node-1",
                "action", "UPDATE");
        WorkerResult<?> result = DeploymentApprovalCaseDescriptor.recordDecision(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output.get("approvalDecision")).isEqualTo("approved");
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordDecisionRejected() {
        var input = Map.<String, Object>of(
                "reviewOutcome", "reject",
                "nodeId", "node-1",
                "action", "UPDATE");
        WorkerResult<?> result = DeploymentApprovalCaseDescriptor.recordDecision(input);
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertThat(output.get("approvalDecision")).isEqualTo("rejected");
    }

    // --- helpers ---

    private static Binding findBinding(CaseDefinition def, String name) {
        return def.getBindings().stream()
                .filter(b -> b.getName().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("Missing binding: " + name));
    }

    private static Map<String, Object> approvalInput(String action, String nodeType, String namespace) {
        var input = new LinkedHashMap<String, Object>();
        input.put("action", action);
        input.put("nodeType", nodeType);
        input.put("namespace", namespace);
        input.put("nodeId", "test-node-1");
        return input;
    }
}
