package io.casehub.ops.testing.audit;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionResult;
import io.cloudevents.CloudEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class AuditCapture {

    private final List<TransitionResult> transitionResults = new CopyOnWriteArrayList<>();
    private final List<CloudEvent> cloudEvents = new CopyOnWriteArrayList<>();

    public void recordTransition(TransitionResult result) {
        transitionResults.add(result);
    }

    public void recordCloudEvent(CloudEvent event) {
        cloudEvents.add(event);
    }

    public List<TransitionResult> transitionResults() {
        return List.copyOf(transitionResults);
    }

    public Map<NodeId, StepOutcome> allOutcomes() {
        return transitionResults.stream()
                .flatMap(r -> r.outcomes().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
    }

    public List<NodeId> succeededNodes() {
        return allOutcomes().entrySet().stream()
                .filter(e -> e.getValue() instanceof StepOutcome.Succeeded)
                .map(Map.Entry::getKey)
                .toList();
    }

    public List<NodeId> failedNodes() {
        return allOutcomes().entrySet().stream()
                .filter(e -> e.getValue() instanceof StepOutcome.Failed)
                .map(Map.Entry::getKey)
                .toList();
    }

    public List<CloudEvent> cloudEvents() {
        return List.copyOf(cloudEvents);
    }

    public List<CloudEvent> cloudEventsOfType(String eventType) {
        return cloudEvents.stream()
                .filter(e -> e.getType().equals(eventType))
                .toList();
    }

    public int cloudEventCount(String eventType) {
        return (int) cloudEvents.stream()
                .filter(e -> e.getType().equals(eventType))
                .count();
    }

    public void clear() {
        transitionResults.clear();
        cloudEvents.clear();
    }
}
