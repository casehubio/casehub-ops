package io.casehub.ops.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.ops.app.model.CveStatus;
import io.casehub.ops.app.persistence.CveStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CveStatusObserverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void completedCveResponseUpdatesStatusToResolved() {
        var recorder = new RecordingCveStore();
        var observer = new CveStatusObserver(recorder);

        UUID appId = UUID.randomUUID();
        var context = contextWith(appId.toString(), "CVE-2026-1234");
        var event = lifecycleEvent("ops", "cve-response", "COMPLETED", context);

        observer.onCaseLifecycleEvent(event);

        assertThat(recorder.lastAppId).isEqualTo(appId);
        assertThat(recorder.lastCveId).isEqualTo("CVE-2026-1234");
        assertThat(recorder.lastStatus).isEqualTo(CveStatus.RESOLVED);
    }

    @Test
    void faultedCveResponseUpdatesStatusToEscalated() {
        var recorder = new RecordingCveStore();
        var observer = new CveStatusObserver(recorder);

        UUID appId = UUID.randomUUID();
        var context = contextWith(appId.toString(), "CVE-2026-5678");
        var event = lifecycleEvent("ops", "cve-response", "FAULTED", context);

        observer.onCaseLifecycleEvent(event);

        assertThat(recorder.lastAppId).isEqualTo(appId);
        assertThat(recorder.lastCveId).isEqualTo("CVE-2026-5678");
        assertThat(recorder.lastStatus).isEqualTo(CveStatus.ESCALATED);
    }

    @Test
    void ignoresNonTerminalStatus() {
        var recorder = new RecordingCveStore();
        var observer = new CveStatusObserver(recorder);

        var context = contextWith(UUID.randomUUID().toString(), "CVE-2026-1234");
        var event = lifecycleEvent("ops", "cve-response", "RUNNING", context);

        observer.onCaseLifecycleEvent(event);

        assertThat(recorder.lastCveId).isNull();
    }

    @Test
    void ignoresOtherCaseTypes() {
        var recorder = new RecordingCveStore();
        var observer = new CveStatusObserver(recorder);

        var context = contextWith(UUID.randomUUID().toString(), "CVE-2026-1234");
        var event = lifecycleEvent("ops", "incident-response", "COMPLETED", context);

        observer.onCaseLifecycleEvent(event);

        assertThat(recorder.lastCveId).isNull();
    }

    @Test
    void ignoresNullNamespace() {
        var recorder = new RecordingCveStore();
        var observer = new CveStatusObserver(recorder);

        var context = contextWith(UUID.randomUUID().toString(), "CVE-2026-1234");
        var event = lifecycleEvent(null, "cve-response", "COMPLETED", context);

        observer.onCaseLifecycleEvent(event);

        assertThat(recorder.lastCveId).isNull();
    }

    @Test
    void ignoresMissingContextFields() {
        var recorder = new RecordingCveStore();
        var observer = new CveStatusObserver(recorder);

        ObjectNode emptyContext = MAPPER.createObjectNode();
        var event = lifecycleEvent("ops", "cve-response", "COMPLETED", emptyContext);

        observer.onCaseLifecycleEvent(event);

        assertThat(recorder.lastCveId).isNull();
    }

    private ObjectNode contextWith(String applicationId, String cveId) {
        ObjectNode node = MAPPER.createObjectNode();
        ObjectNode assessment = MAPPER.createObjectNode();
        assessment.put("applicationId", applicationId);
        assessment.put("cveId", cveId);
        node.set("cveAssessment", assessment);
        return node;
    }

    private CaseLifecycleEvent lifecycleEvent(String namespace, String caseName,
                                               String status, ObjectNode context) {
        return new CaseLifecycleEvent(
                UUID.randomUUID(), "tenant-1",
                "CompleteCase", "CaseCompleted",
                status, null, null, null,
                caseName, namespace, context,
                null, null);
    }

    private static class RecordingCveStore implements CveStore {
        UUID lastAppId;
        String lastCveId;
        CveStatus lastStatus;

        @Override
        public void store(io.casehub.ops.app.model.CveRecord record) {}

        @Override
        public java.util.List<io.casehub.ops.app.model.CveRecord> findByApplicationId(UUID applicationId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<io.casehub.ops.app.model.CveRecord> findByServiceId(UUID applicationId, String serviceId) {
            return java.util.List.of();
        }

        @Override
        public java.util.Optional<io.casehub.ops.app.model.CveRecord> findByCveId(UUID applicationId, String cveId) {
            return java.util.Optional.empty();
        }

        @Override
        public void updateStatus(UUID applicationId, String cveId, CveStatus newStatus) {
            this.lastAppId = applicationId;
            this.lastCveId = cveId;
            this.lastStatus = newStatus;
        }
    }
}
