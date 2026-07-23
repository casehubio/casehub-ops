package io.casehub.ops.app.case_;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.ops.app.service.NodeConvergenceTracker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DriftRemediationCaseDescriptorTest {

    @Test
    void buildReturnsCorrectIdentity() {
        CaseDefinition def = DriftRemediationCaseDescriptor.build(noopTracker());
        assertThat(def.getNamespace()).isEqualTo("ops");
        assertThat(def.getName()).isEqualTo("drift-remediation");
        assertThat(def.getVersion()).isEqualTo("1.0");
    }

    @Test
    void hasThreeCapabilities() {
        CaseDefinition def = DriftRemediationCaseDescriptor.build(noopTracker());
        assertThat(def.getCapabilities()).hasSize(3);
        assertThat(def.getCapabilities()).extracting("name")
                                         .containsExactlyInAnyOrder("classify-drift", "remediate-drift", "escalate-drift");
    }

    @Test
    void hasThreeWorkers() {
        CaseDefinition def = DriftRemediationCaseDescriptor.build(noopTracker());
        assertThat(def.getWorkers()).hasSize(3);
    }

    @Test
    void hasTwoInternalBindings() {
        CaseDefinition def = DriftRemediationCaseDescriptor.build(noopTracker());
        assertThat(def.getBindings()).hasSize(2);
    }

    @Test
    void classificationBindingTriggersOnDriftClassification() {
        CaseDefinition def = DriftRemediationCaseDescriptor.build(noopTracker());
        Binding binding = def.getBindings().stream()
                             .filter(b -> b.getName().equals("on-classification-complete"))
                             .findFirst().orElseThrow();
        assertThat(binding.getOn()).isInstanceOf(ContextChangeTrigger.class);
    }

    @Test
    void escalationBindingTriggersOnEscalationRequired() {
        CaseDefinition def = DriftRemediationCaseDescriptor.build(noopTracker());
        Binding binding = def.getBindings().stream()
                             .filter(b -> b.getName().equals("on-escalation-required"))
                             .findFirst().orElseThrow();
        assertThat(binding.getOn()).isInstanceOf(ContextChangeTrigger.class);
    }

    @Test
    void hasCompletionPredicate() {
        CaseDefinition def = DriftRemediationCaseDescriptor.build(noopTracker());
        assertThat(def.getCompletion()).isNotNull();
    }

    @Test
    void remediateBenignRegistersConvergence() {
        NodeConvergenceTracker tracker = new NodeConvergenceTracker(
                (NodeConvergenceTracker.ConvergenceSignaler) (caseId, path, value) -> {},
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));

        java.util.UUID                    caseId = java.util.UUID.randomUUID();
        io.casehub.worker.api.WorkerScope scope  = new TestWorkerScope(caseId);
        var input = java.util.Map.<String, Object>of(
                "driftClassification", java.util.Map.of(
                        "severity", "benign",
                        "nodeIds", java.util.List.of("node-1", "node-2")));
        io.casehub.worker.api.WorkerResult result =
                DriftRemediationCaseDescriptor.remediateDrift(input, scope, tracker);

        assertThat(((java.util.Map<?, ?>) result.output()).get("remediationStatus")).isEqualTo("auto-remediating");
        assertThat(tracker.isTracking(caseId)).isTrue();
    }

    @Test
    void remediateCriticalDoesNotRegisterConvergence() {
        NodeConvergenceTracker tracker = new NodeConvergenceTracker(
                (NodeConvergenceTracker.ConvergenceSignaler) (caseId, path, value) -> {},
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));

        var input = java.util.Map.<String, Object>of(
                "driftClassification", java.util.Map.of(
                        "severity", "critical",
                        "nodeIds", java.util.List.of("node-1")));
        io.casehub.worker.api.WorkerResult result =
                DriftRemediationCaseDescriptor.remediateDrift(input, new TestWorkerScope(java.util.UUID.randomUUID()), tracker);

        assertThat(((java.util.Map<?, ?>) result.output()).get("escalationRequired")).isEqualTo(true);
    }

    private static NodeConvergenceTracker noopTracker() {
        return new NodeConvergenceTracker(
                (NodeConvergenceTracker.ConvergenceSignaler) (caseId, path, value) -> {},
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
    }

    record TestWorkerScope(java.util.UUID caseId) implements io.casehub.worker.api.WorkerScope {
        @Override
        public String taskId()                                                                                                    {return "test";}

        @Override
        public <T, R> io.casehub.worker.api.WorkerResult<R> execute(io.casehub.worker.api.WorkerFunction<T, R> function, T input) {throw new UnsupportedOperationException();}

        @Override
        public io.casehub.worker.api.WorkerResult<?> execute(String workerName, java.util.Map<String, Object> input)              {throw new UnsupportedOperationException();}
    }
}
