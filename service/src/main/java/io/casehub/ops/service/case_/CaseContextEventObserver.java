package io.casehub.ops.service.case_;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.ops.api.lifecycle.OpsCloudEventTypes;
import io.casehub.ops.service.entity.ApplicationEntity;
import io.casehub.ops.service.service.ApplicationEventBroadcaster;
import io.casehub.ops.service.service.ApplicationEventBroadcaster.BroadcastEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class CaseContextEventObserver {

    private static final Logger LOG = Logger.getLogger(CaseContextEventObserver.class.getName());

    private static final Map<String, String> EVENT_TO_CONTEXT_KEY = Map.of(
            OpsCloudEventTypes.PROBLEMS_DRIFT, ".driftDetected",
            OpsCloudEventTypes.PROBLEMS_INCIDENT, ".incidentDetected",
            OpsCloudEventTypes.PROBLEMS_SCALING, ".scalingRequired",
            OpsCloudEventTypes.SECURITY_CVE, ".cveDetected",
            OpsCloudEventTypes.COMPLIANCE_CONTROL, ".complianceViolation",
            OpsCloudEventTypes.CHANGE_VERSION, ".upgradeRequested"
    );

    private static final Pattern APP_ID_PATTERN =
            Pattern.compile("\"applicationId\"\\s*:\\s*\"([0-9a-f\\-]{36})\"");

    @Inject CaseHubRuntime caseHubRuntime;
    @Inject ApplicationEventBroadcaster broadcaster;

    void onStartup(@Observes @Priority(20) StartupEvent event) {
        for (ApplicationEntity app : ApplicationEntity.<ApplicationEntity>listAll()) {
            if (app.engineCaseId != null) {
                subscribeForApplication(app.id, app.engineCaseId);
            }
        }
    }

    public void subscribeForApplication(UUID applicationId, UUID engineCaseId) {
        broadcaster.subscribe(applicationId, ApplicationEventBroadcaster.EventFilter.ALL,
                broadcastEvent -> onEvent(engineCaseId, broadcastEvent));
    }

    private void onEvent(UUID engineCaseId, BroadcastEvent event) {
        String contextKey = contextKeyFor(event.eventType());
        if (contextKey == null) {
            return;
        }
        try {
            caseHubRuntime.signal(engineCaseId, contextKey, Map.of(
                    "eventType", event.eventType(),
                    "eventId", event.eventId(),
                    "timestamp", java.time.Instant.now().toString(),
                    "data", event.data() != null ? event.data() : ""));
        } catch (Exception e) {
            LOG.warning("Failed to signal case " + engineCaseId + " for event " + event.eventType()
                    + ": " + e.getMessage());
        }
    }

    static String contextKeyFor(String eventType) {
        return EVENT_TO_CONTEXT_KEY.get(eventType);
    }

    static UUID parseApplicationId(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            Matcher m = APP_ID_PATTERN.matcher(data);
            if (m.find()) {
                return UUID.fromString(m.group(1));
            }
        } catch (Exception e) {
            // invalid UUID or parse failure
        }
        return null;
    }
}
