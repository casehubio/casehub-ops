package io.casehub.ops.app.lifecycle.ras;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.ops.app.lifecycle.ServiceDetectionBridge;
import io.casehub.ras.api.DetectionResult;
import io.casehub.ras.api.DetectionSignal;
import io.casehub.ras.api.SituationChangeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class RasSituationObserver {

    private static final Logger LOG = Logger.getLogger(RasSituationObserver.class.getName());

    private final ServiceDetectionBridge bridge;
    private final ContextSignaler signaler;
    private final ContextQuerier querier;

    @FunctionalInterface
    interface ContextSignaler {
        void signal(UUID caseId, String key, Object value);
    }

    @FunctionalInterface
    interface ContextQuerier {
        Object query(UUID caseId, String key);
    }

    @Inject
    public RasSituationObserver(ServiceDetectionBridge bridge, CaseHubRuntime runtime) {
        this.bridge = bridge;
        this.signaler = runtime::signal;
        this.querier = runtime::query;
    }

    RasSituationObserver(ServiceDetectionBridge bridge, ContextSignaler signaler, ContextQuerier querier) {
        this.bridge = bridge;
        this.signaler = signaler;
        this.querier = querier;
    }

    void onSituation(@ObservesAsync SituationChangeEvent event) {
        if (event.changeType() != SituationChangeEvent.ChangeType.TRIGGERED) return;
        if (!event.situationId().startsWith("ops:")) return;

        UUID caseId;
        try {
            caseId = UUID.fromString(event.correlationKey());
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Invalid correlationKey (not UUID): " + event.correlationKey(), e);
            return;
        }

        for (var detection : event.context().detections()) {
            DetectionResult result = detection.result();
            if (result.signal() != DetectionSignal.DETECTED) continue;

            Map<String, Object> detectionData = new HashMap<>(result.evidence());
            detectionData.put("confidence", result.confidence());
            detectionData.put("detectedAt", detection.eventTime().toString());
            detectionData.put("situationId", event.situationId());

            try {
                bridge.onDetection(
                    result.ganglionId(),
                    caseId,
                    (key, value) -> signaler.signal(caseId, key, value),
                    key -> querier.query(caseId, key),
                    detectionData
                );
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to route detection " + result.ganglionId()
                    + " for case " + caseId, e);
            }
        }
    }
}
