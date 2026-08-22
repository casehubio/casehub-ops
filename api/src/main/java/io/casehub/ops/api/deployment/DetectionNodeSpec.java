package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.ras.api.ChainMode;
import io.casehub.ras.api.SituationDefinition;
import io.casehub.ras.api.SituationRegistration;
import io.casehub.ras.api.TriggerAction;
import io.casehub.ras.api.TriggerMode;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DetectionNodeSpec(
        String situationId,
        Set<String> eventTypes,
        Duration correlationWindow,
        Duration eventBufferDelay,
        ChainMode chainMode,
        TriggerAction triggerAction,
        TriggerMode triggerMode
) implements DeploymentNodeSpec {

    public DetectionNodeSpec {
        if (situationId == null || situationId.isBlank()) {
            throw new IllegalArgumentException("situationId is required");
        }
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("eventTypes must not be empty");
        }
        eventTypes = Set.copyOf(eventTypes);
        Objects.requireNonNull(chainMode, "chainMode is required");
        Objects.requireNonNull(triggerAction, "triggerAction is required");
    }

    @Override
    public String nodeId() {
        return situationId;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.of("detection");
    }

    public SituationRegistration toRegistration() {
        return new SituationRegistration(
                new SituationDefinition(situationId, eventTypes,
                        correlationWindow, eventBufferDelay,
                        chainMode, triggerAction, triggerMode),
                null);
    }
}
