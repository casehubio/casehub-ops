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
}
