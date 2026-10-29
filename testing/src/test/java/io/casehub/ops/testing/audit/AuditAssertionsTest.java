package io.casehub.ops.testing.audit;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionResult;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditAssertionsTest {
    private static final io.casehub.desiredstate.api.NodeSpec STUB_SPEC =
            () -> io.casehub.desiredstate.api.NodeType.of("stub");

    private static io.casehub.desiredstate.api.DesiredStateGraph graphOf(String... nodeIds) {
        var factory = new io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory();
        var nodes   = new java.util.ArrayList<io.casehub.desiredstate.api.DesiredNode>();
        for (String id : nodeIds) {
            nodes.add(new io.casehub.desiredstate.api.DesiredNode(
                    NodeId.of(id), STUB_SPEC, io.casehub.desiredstate.api.HumanGating.NONE));
        }
        return factory.of(nodes, java.util.List.of());
    }


    @Test
    void assertAllSucceeded_passes_whenAllSucceeded() {
        AuditCapture capture = new AuditCapture();
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("a"), new StepOutcome.Succeeded(),
                NodeId.of("b"), new StepOutcome.Succeeded())));
        assertThatCode(() -> AuditAssertions.assertAllSucceeded(capture))
                .doesNotThrowAnyException();
    }

    @Test
    void assertAllSucceeded_fails_whenAnyFailed() {
        AuditCapture capture = new AuditCapture();
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("a"), new StepOutcome.Succeeded(),
                NodeId.of("b"), new StepOutcome.Failed("err"))));
        assertThatThrownBy(() -> AuditAssertions.assertAllSucceeded(capture))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertNodeSucceeded_passes_whenNodeSucceeded() {
        AuditCapture capture = new AuditCapture();
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("my-node"), new StepOutcome.Succeeded())));
        assertThatCode(() -> AuditAssertions.assertNodeSucceeded(capture, "my-node"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNodeSucceeded_fails_whenNodeFailed() {
        AuditCapture capture = new AuditCapture();
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("my-node"), new StepOutcome.Failed("err"))));
        assertThatThrownBy(() -> AuditAssertions.assertNodeSucceeded(capture, "my-node"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertNodeFailed_passes_whenNodeFailed() {
        AuditCapture capture = new AuditCapture();
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("bad"), new StepOutcome.Failed("err"))));
        assertThatCode(() -> AuditAssertions.assertNodeFailed(capture, "bad"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNoFailures_passes_whenNoFailures() {
        AuditCapture capture = new AuditCapture();
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("a"), new StepOutcome.Succeeded())));
        assertThatCode(() -> AuditAssertions.assertNoFailures(capture))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNoFailures_fails_whenFailuresExist() {
        AuditCapture capture = new AuditCapture();
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("a"), new StepOutcome.Failed("err"))));
        assertThatThrownBy(() -> AuditAssertions.assertNoFailures(capture))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertCloudEventEmitted_passes_whenEventPresent() {
        AuditCapture capture = new AuditCapture();
        capture.recordCloudEvent(CloudEventBuilder.v1()
                .withId("1").withSource(URI.create("/test"))
                .withType("io.casehub.desiredstate.reconciliation.completed").build());
        assertThatCode(() -> AuditAssertions.assertCloudEventEmitted(
                capture, "io.casehub.desiredstate.reconciliation.completed"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertCloudEventEmitted_fails_whenEventAbsent() {
        AuditCapture capture = new AuditCapture();
        assertThatThrownBy(() -> AuditAssertions.assertCloudEventEmitted(
                capture, "io.casehub.desiredstate.reconciliation.completed"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertCloudEventEmitted_withMinCount() {
        AuditCapture capture = new AuditCapture();
        capture.recordCloudEvent(CloudEventBuilder.v1()
                .withId("1").withSource(URI.create("/test"))
                .withType("test.type").build());
        capture.recordCloudEvent(CloudEventBuilder.v1()
                .withId("2").withSource(URI.create("/test"))
                .withType("test.type").build());
        assertThatCode(() -> AuditAssertions.assertCloudEventEmitted(capture, "test.type", 2))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> AuditAssertions.assertCloudEventEmitted(capture, "test.type", 3))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertCloudEventForNode_passes_whenEventContainsNodeId() {
        AuditCapture capture = new AuditCapture();
        capture.recordCloudEvent(CloudEventBuilder.v1()
                                                  .withId("1").withSource(URI.create("/test"))
                                                  .withType("io.casehub.desiredstate.node.faulted")
                                                  .withData("application/json", "{\"nodeId\":\"my-llm\",\"tenancyId\":\"t1\"}".getBytes())
                                                  .build());
        assertThatCode(() -> AuditAssertions.assertCloudEventForNode(
                capture, "io.casehub.desiredstate.node.faulted", "my-llm"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertCloudEventForNode_fails_whenEventMissesNodeId() {
        AuditCapture capture = new AuditCapture();
        capture.recordCloudEvent(CloudEventBuilder.v1()
                                                  .withId("1").withSource(URI.create("/test"))
                                                  .withType("io.casehub.desiredstate.node.faulted")
                                                  .withData("application/json", "{\"nodeId\":\"other-node\"}".getBytes())
                                                  .build());
        assertThatThrownBy(() -> AuditAssertions.assertCloudEventForNode(
                capture, "io.casehub.desiredstate.node.faulted", "my-llm"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertCloudEventForNode_fails_whenEventHasNoData() {
        AuditCapture capture = new AuditCapture();
        capture.recordCloudEvent(CloudEventBuilder.v1()
                                                  .withId("1").withSource(URI.create("/test"))
                                                  .withType("io.casehub.desiredstate.node.faulted")
                                                  .build());
        assertThatThrownBy(() -> AuditAssertions.assertCloudEventForNode(
                capture, "io.casehub.desiredstate.node.faulted", "my-llm"))
                .isInstanceOf(AssertionError.class);
    }


    @Test
    void assertNoCloudEventsOfType_passes_whenNonePresent() {
        AuditCapture capture = new AuditCapture();
        assertThatCode(() -> AuditAssertions.assertNoCloudEventsOfType(
                capture, "io.casehub.desiredstate.node.faulted"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNoCloudEventsOfType_fails_whenPresent() {
        AuditCapture capture = new AuditCapture();
        capture.recordCloudEvent(CloudEventBuilder.v1()
                .withId("1").withSource(URI.create("/test"))
                .withType("io.casehub.desiredstate.node.faulted").build());
        assertThatThrownBy(() -> AuditAssertions.assertNoCloudEventsOfType(
                capture, "io.casehub.desiredstate.node.faulted"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertAuditTrailCoversGraph_passes_whenAllNodesCovered() {
        AuditCapture capture = new AuditCapture();
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("a"), new StepOutcome.Succeeded(),
                NodeId.of("b"), new StepOutcome.Succeeded())));
        var graph = graphOf("a", "b");
        assertThatCode(() -> AuditAssertions.assertAuditTrailCoversGraph(capture, graph))
                .doesNotThrowAnyException();
    }

    @Test
    void assertAuditTrailCoversGraph_fails_whenNodeMissing() {
        AuditCapture capture = new AuditCapture();
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("a"), new StepOutcome.Succeeded())));
        var graph = graphOf("a", "b");
        assertThatThrownBy(() -> AuditAssertions.assertAuditTrailCoversGraph(capture, graph))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("audit trail is incomplete");
    }

    @Test
    void assertCloudEventsHaveAttribution_passes_whenAllAttributed() {
        AuditCapture capture = new AuditCapture();
        capture.recordCloudEvent(CloudEventBuilder.v1()
                                                  .withId("1").withSource(URI.create("urn:test"))
                                                  .withType("test.type")
                                                  .withTime(java.time.OffsetDateTime.now())
                                                  .withExtension("tenancyid", "t1")
                                                  .build());
        assertThatCode(() -> AuditAssertions.assertCloudEventsHaveAttribution(capture, "t1"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertCloudEventsHaveAttribution_fails_whenTimeMissing() {
        AuditCapture capture = new AuditCapture();
        capture.recordCloudEvent(CloudEventBuilder.v1()
                                                  .withId("1").withSource(URI.create("urn:test"))
                                                  .withType("test.type")
                                                  .build());
        assertThatThrownBy(() -> AuditAssertions.assertCloudEventsHaveAttribution(capture, "t1"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void assertCloudEventsHaveAttribution_fails_whenTenancyMismatch() {
        AuditCapture capture = new AuditCapture();
        capture.recordCloudEvent(CloudEventBuilder.v1()
                                                  .withId("1").withSource(URI.create("urn:test"))
                                                  .withType("test.type")
                                                  .withTime(java.time.OffsetDateTime.now())
                                                  .withExtension("tenancyid", "wrong-tenant")
                                                  .build());
        assertThatThrownBy(() -> AuditAssertions.assertCloudEventsHaveAttribution(capture, "t1"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tenancyid");
    }

    @Test
    void assertReconciliationSummaryConsistent_passes_whenDataMatches() {
        AuditCapture capture = new AuditCapture();
        String data = "{\"tenancyId\":\"t1\",\"graphVersion\":1,\"nodeCount\":2," +
                      "\"additionsCount\":2,\"removalsCount\":0,\"suspensionsCount\":0," +
                      "\"resumptionsCount\":0,\"faultCount\":0,\"exemptedCount\":0," +
                      "\"nodeOutcomes\":{},\"timestamp\":\"2026-01-01T00:00:00Z\"}";
        capture.recordCloudEvent(CloudEventBuilder.v1()
                                                  .withId("1").withSource(URI.create("urn:test"))
                                                  .withType("io.casehub.desiredstate.reconciliation.completed")
                                                  .withData("application/json", data.getBytes())
                                                  .build());
        var graph = graphOf("a", "b");
        assertThatCode(() -> AuditAssertions.assertReconciliationSummaryConsistent(capture, graph))
                .doesNotThrowAnyException();
    }

    @Test
    void assertReconciliationSummaryConsistent_fails_whenNodeCountWrong() {
        AuditCapture capture = new AuditCapture();
        String data = "{\"tenancyId\":\"t1\",\"graphVersion\":1,\"nodeCount\":5," +
                      "\"additionsCount\":5,\"removalsCount\":0,\"suspensionsCount\":0," +
                      "\"resumptionsCount\":0,\"faultCount\":0,\"exemptedCount\":0," +
                      "\"nodeOutcomes\":{},\"timestamp\":\"2026-01-01T00:00:00Z\"}";
        capture.recordCloudEvent(CloudEventBuilder.v1()
                                                  .withId("1").withSource(URI.create("urn:test"))
                                                  .withType("io.casehub.desiredstate.reconciliation.completed")
                                                  .withData("application/json", data.getBytes())
                                                  .build());
        var graph = graphOf("a", "b");
        assertThatThrownBy(() -> AuditAssertions.assertReconciliationSummaryConsistent(capture, graph))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("nodeCount");
    }

    @Test
    void assertReconciliationSummaryConsistent_fails_whenFaultsPresent() {
        AuditCapture capture = new AuditCapture();
        String data = "{\"tenancyId\":\"t1\",\"graphVersion\":1,\"nodeCount\":2," +
                      "\"additionsCount\":2,\"removalsCount\":0,\"suspensionsCount\":0," +
                      "\"resumptionsCount\":0,\"faultCount\":1,\"exemptedCount\":0," +
                      "\"nodeOutcomes\":{},\"timestamp\":\"2026-01-01T00:00:00Z\"}";
        capture.recordCloudEvent(CloudEventBuilder.v1()
                                                  .withId("1").withSource(URI.create("urn:test"))
                                                  .withType("io.casehub.desiredstate.reconciliation.completed")
                                                  .withData("application/json", data.getBytes())
                                                  .build());
        var graph = graphOf("a", "b");
        assertThatThrownBy(() -> AuditAssertions.assertReconciliationSummaryConsistent(capture, graph))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("zero faults");
    }
}
