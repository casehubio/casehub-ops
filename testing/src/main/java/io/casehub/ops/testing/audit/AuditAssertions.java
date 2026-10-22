package io.casehub.ops.testing.audit;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.StepOutcome;

import static org.assertj.core.api.Assertions.assertThat;

public final class AuditAssertions {

    private AuditAssertions() {}

    public static void assertAllSucceeded(AuditCapture capture) {
        assertThat(capture.allOutcomes().values())
                .as("All transition outcomes should be Succeeded")
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }

    public static void assertNodeSucceeded(AuditCapture capture, String nodeId) {
        StepOutcome outcome = capture.allOutcomes().get(NodeId.of(nodeId));
        assertThat(outcome)
                .as("Outcome for node '%s'", nodeId)
                .isNotNull()
                .isInstanceOf(StepOutcome.Succeeded.class);
    }

    public static void assertNodeFailed(AuditCapture capture, String nodeId) {
        StepOutcome outcome = capture.allOutcomes().get(NodeId.of(nodeId));
        assertThat(outcome)
                .as("Outcome for node '%s'", nodeId)
                .isNotNull()
                .isInstanceOf(StepOutcome.Failed.class);
    }

    public static void assertNoFailures(AuditCapture capture) {
        assertThat(capture.failedNodes())
                .as("No nodes should have failed")
                .isEmpty();
    }

    public static void assertCloudEventEmitted(AuditCapture capture, String eventType) {
        assertThat(capture.cloudEventsOfType(eventType))
                .as("Expected at least one CloudEvent of type '%s'", eventType)
                .isNotEmpty();
    }

    public static void assertCloudEventEmitted(AuditCapture capture, String eventType, int minCount) {
        assertThat(capture.cloudEventCount(eventType))
                .as("Expected at least %d CloudEvent(s) of type '%s'", minCount, eventType)
                .isGreaterThanOrEqualTo(minCount);
    }

    public static void assertCloudEventForNode(AuditCapture capture, String eventType, String nodeId) {
        boolean found = capture.cloudEventsOfType(eventType).stream()
                .anyMatch(e -> {
                    if (e.getData() == null) return false;
                    String json = new String(e.getData().toBytes());
                    return json.contains("\"" + nodeId + "\"");
                });
        assertThat(found)
                .as("Expected CloudEvent of type '%s' for node '%s'", eventType, nodeId)
                .isTrue();
    }

    public static void assertNoCloudEventsOfType(AuditCapture capture, String eventType) {
        assertThat(capture.cloudEventsOfType(eventType))
                .as("Expected no CloudEvents of type '%s'", eventType)
                .isEmpty();
    }
}
