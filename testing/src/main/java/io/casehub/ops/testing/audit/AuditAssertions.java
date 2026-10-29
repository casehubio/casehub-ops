package io.casehub.ops.testing.audit;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.StepOutcome;
import io.cloudevents.CloudEvent;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static void assertAuditTrailCoversGraph(AuditCapture capture, DesiredStateGraph graph) {
        Map<NodeId, StepOutcome> outcomes     = capture.allOutcomes();
        Set<NodeId>              graphNodeIds = graph.nodes().keySet();
        Set<NodeId> missingFromAudit = graphNodeIds.stream()
                                                   .filter(id -> !outcomes.containsKey(id))
                                                   .collect(Collectors.toSet());
        assertThat(missingFromAudit)
                .as("Every node in the desired-state graph must have a corresponding audit outcome — " +
                    "missing nodes indicate the audit trail is incomplete")
                .isEmpty();
    }

    public static void assertCloudEventsHaveAttribution(AuditCapture capture, String expectedTenancyId) {
        assertThat(capture.cloudEvents())
                .as("Audit trail must contain at least one CloudEvent")
                .isNotEmpty();
        for (CloudEvent event : capture.cloudEvents()) {
            assertThat(event.getSource())
                    .as("CloudEvent '%s' must have a source for attribution", event.getType())
                    .isNotNull();
            assertThat(event.getType())
                    .as("CloudEvent must have a type")
                    .isNotBlank();
            assertThat(event.getTime())
                    .as("CloudEvent '%s' must have a timestamp for accountability", event.getType())
                    .isNotNull();
            Object tenancyId = event.getExtension("tenancyid");
            if (tenancyId != null) {
                assertThat(tenancyId.toString())
                        .as("CloudEvent '%s' tenancyid must match expected tenant", event.getType())
                        .isEqualTo(expectedTenancyId);
            }
        }
    }

    public static void assertReconciliationSummaryConsistent(AuditCapture capture, DesiredStateGraph graph) {
        var completedEvents = capture.cloudEventsOfType(
                io.casehub.desiredstate.api.DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        assertThat(completedEvents)
                .as("At least one RECONCILIATION_COMPLETED event expected")
                .isNotEmpty();
        CloudEvent latest = completedEvents.get(completedEvents.size() - 1);
        assertThat(latest.getData())
                .as("RECONCILIATION_COMPLETED event must carry a data payload")
                .isNotNull();
        String json = new String(latest.getData().toBytes());
        assertThat(json).contains("\"nodeCount\"");
        int expectedNodeCount = graph.nodes().size();
        assertThat(json)
                .as("Reconciliation summary nodeCount should reflect graph size (%d)", expectedNodeCount)
                .contains("\"nodeCount\":" + expectedNodeCount);
        assertThat(json)
                .as("Reconciliation summary should report zero faults for a clean deployment")
                .contains("\"faultCount\":0");
    }

}
