package io.casehub.ops.service.case_;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.JudgmentTarget;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerFunction;
import io.casehub.worker.api.WorkerResult;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DeploymentApprovalCaseDescriptor {

    private static final Set<String> CRITICAL_ACTIONS = Set.of("DELETE");
    private static final Set<String> HIGH_RISK_NAMESPACES = Set.of("production", "prod", "kube-system");

    private DeploymentApprovalCaseDescriptor() {}

    public static CaseDefinition build() {
        return CaseDefinition.builder()
                .namespace("ops")
                .name("deployment-approval")
                .version("1.0")
                .title("Deployment Approval")
                .summary("Risk-based approval workflow for deployment changes")
                .capabilities(capabilities())
                .workers(workers())
                .bindings(bindings())
                .completion("(.approvalDecision == \"approved\") or (.approvalDecision == \"rejected\")")
                .build();
    }

    private static List<Capability> capabilities() {
        return List.of(
                Capability.of("evaluate-risk", "any", "any"),
                Capability.of("collect-evidence", "any", "any"),
                Capability.of("record-decision", "any", "any"));
    }

    @SuppressWarnings("unchecked")
    private static List<Worker> workers() {
        return List.of(
                Worker.builder()
                        .name("risk-evaluate-worker")
                        .capabilityName("evaluate-risk")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> evaluateRisk(input)))
                        .build(),
                Worker.builder()
                        .name("evidence-collect-worker")
                        .capabilityName("collect-evidence")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> collectEvidence(input)))
                        .build(),
                Worker.builder()
                        .name("decision-record-worker")
                        .capabilityName("record-decision")
                        .function(new WorkerFunction.Sync<>(Map.class, Map.class,
                                (input, scope) -> recordDecision(input)))
                        .build());
    }

    private static List<Binding> bindings() {
        return List.of(
                Binding.builder()
                        .name("on-risk-evaluated")
                        .on(new ContextChangeTrigger(".riskClassification"))
                        .capability(Capability.of("collect-evidence", "any", "any"))
                        .build(),
                Binding.builder()
                        .name("on-review-required")
                        .on(new ContextChangeTrigger(".reviewRequired"))
                        .judgment(JudgmentTarget.builder()
                                .prompt("Review deployment change — assess risk classification and collected evidence, then approve or reject")
                                .title("Deployment Approval Review")
                                .outcomes(Set.of("approve", "reject"))
                                .expiresIn(Duration.ofHours(8))
                                .scope("ops/deployment-approval")
                                .priority("HIGH")
                                .build())
                        .build(),
                Binding.builder()
                        .name("on-decision-recorded")
                        .on(new ContextChangeTrigger(".reviewOutcome"))
                        .capability(Capability.of("record-decision", "any", "any"))
                        .build());
    }

    static WorkerResult evaluateRisk(Map<String, Object> input) {
        String action = (String) input.getOrDefault("action", "UPDATE");
        String nodeType = (String) input.getOrDefault("nodeType", "unknown");
        String namespace = (String) input.getOrDefault("namespace", "default");

        String level;
        String reason;

        if (CRITICAL_ACTIONS.contains(action)) {
            level = "CRITICAL";
            reason = action + " operation on " + nodeType + " in " + namespace;
        } else if (HIGH_RISK_NAMESPACES.contains(namespace)) {
            level = "HIGH";
            reason = action + " in high-risk namespace " + namespace;
        } else if ("CREATE".equals(action)) {
            level = "MEDIUM";
            reason = "New " + nodeType + " in " + namespace;
        } else {
            level = "LOW";
            reason = action + " on " + nodeType + " in " + namespace;
        }

        var classification = new LinkedHashMap<String, Object>();
        classification.put("level", level);
        classification.put("reason", reason);
        classification.put("action", action);
        classification.put("nodeType", nodeType);
        classification.put("namespace", namespace);

        return WorkerResult.of(Map.of("riskClassification", classification));
    }

    static WorkerResult collectEvidence(Map<String, Object> input) {
        String action = (String) input.getOrDefault("action", "UPDATE");
        String nodeType = (String) input.getOrDefault("nodeType", "unknown");
        String namespace = (String) input.getOrDefault("namespace", "default");
        String nodeId = (String) input.getOrDefault("nodeId", "unknown");

        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("nodeId", nodeId);
        evidence.put("action", action);
        evidence.put("nodeType", nodeType);
        evidence.put("namespace", namespace);
        evidence.put("summary", action + " " + nodeType + " " + nodeId + " in " + namespace);

        return WorkerResult.of(Map.of(
                "evidenceCollected", evidence,
                "reviewRequired", true));
    }

    static WorkerResult recordDecision(Map<String, Object> input) {
        String reviewOutcome = (String) input.getOrDefault("reviewOutcome", "reject");
        String nodeId = (String) input.getOrDefault("nodeId", "unknown");
        String action = (String) input.getOrDefault("action", "unknown");

        String decision = "approve".equals(reviewOutcome) ? "approved" : "rejected";

        var record = new LinkedHashMap<String, Object>();
        record.put("decision", decision);
        record.put("nodeId", nodeId);
        record.put("action", action);
        record.put("reviewOutcome", reviewOutcome);

        return WorkerResult.of(Map.of(
                "approvalDecision", decision,
                "decisionRecord", record));
    }
}
