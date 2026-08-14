package io.casehub.ops.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.ops.app.model.CveStatus;
import io.casehub.ops.app.persistence.CveStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class CveStatusObserver {

    private static final Logger LOG = Logger.getLogger(CveStatusObserver.class.getName());

    private final CveStore cveStore;

    @Inject
    public CveStatusObserver(CveStore cveStore) {
        this.cveStore = cveStore;
    }

    void onCaseLifecycleEvent(@ObservesAsync CaseLifecycleEvent event) {
        if (!"ops".equals(event.namespace()) || !"cve-response".equals(event.caseDefinitionName())) {
            return;
        }

        String status = event.caseStatus();
        if (!"COMPLETED".equals(status) && !"FAULTED".equals(status)) {
            return;
        }

        JsonNode context = event.contextSnapshot();
        if (context == null) return;

        JsonNode assessment = context.get("cveAssessment");
        if (assessment == null) return;

        JsonNode appIdNode = assessment.get("applicationId");
        JsonNode cveIdNode = assessment.get("cveId");
        if (appIdNode == null || cveIdNode == null) return;

        try {
            UUID applicationId = UUID.fromString(appIdNode.asText());
            String cveId = cveIdNode.asText();
            CveStatus newStatus = "COMPLETED".equals(status) ? CveStatus.RESOLVED : CveStatus.ESCALATED;

            cveStore.updateStatus(applicationId, cveId, newStatus);
            LOG.fine(() -> "Updated CVE " + cveId + " status to " + newStatus + " for case " + event.caseId());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to update CVE status for case " + event.caseId(), e);
        }
    }
}
