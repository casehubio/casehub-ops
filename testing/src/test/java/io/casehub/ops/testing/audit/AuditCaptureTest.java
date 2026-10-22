package io.casehub.ops.testing.audit;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionResult;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditCaptureTest {

    private AuditCapture capture;

    @BeforeEach
    void setUp() {
        capture = new AuditCapture();
    }

    @Test
    void recordsTransitionResult() {
        TransitionResult result = new TransitionResult(Map.of(
                NodeId.of("node-a"), new StepOutcome.Succeeded(),
                NodeId.of("node-b"), new StepOutcome.Succeeded()));
        capture.recordTransition(result);
        assertThat(capture.transitionResults()).hasSize(1);
        assertThat(capture.allOutcomes()).hasSize(2);
    }

    @Test
    void succeededNodesFiltersCorrectly() {
        TransitionResult result = new TransitionResult(Map.of(
                NodeId.of("ok"), new StepOutcome.Succeeded(),
                NodeId.of("fail"), new StepOutcome.Failed("boom")));
        capture.recordTransition(result);
        assertThat(capture.succeededNodes()).containsExactly(NodeId.of("ok"));
        assertThat(capture.failedNodes()).containsExactly(NodeId.of("fail"));
    }

    @Test
    void recordsCloudEvents() {
        CloudEvent event = CloudEventBuilder.v1()
                .withId("1")
                .withSource(URI.create("/test"))
                .withType("io.casehub.desiredstate.reconciliation.completed")
                .build();
        capture.recordCloudEvent(event);
        assertThat(capture.cloudEvents()).hasSize(1);
        assertThat(capture.cloudEventsOfType("io.casehub.desiredstate.reconciliation.completed")).hasSize(1);
        assertThat(capture.cloudEventsOfType("io.casehub.desiredstate.node.faulted")).isEmpty();
    }

    @Test
    void cloudEventCountReturnsCorrectCount() {
        for (int i = 0; i < 3; i++) {
            capture.recordCloudEvent(CloudEventBuilder.v1()
                    .withId(String.valueOf(i))
                    .withSource(URI.create("/test"))
                    .withType("io.casehub.desiredstate.node.faulted")
                    .build());
        }
        assertThat(capture.cloudEventCount("io.casehub.desiredstate.node.faulted")).isEqualTo(3);
    }

    @Test
    void clearResetsAllState() {
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("n"), new StepOutcome.Succeeded())));
        capture.recordCloudEvent(CloudEventBuilder.v1()
                .withId("1").withSource(URI.create("/test"))
                .withType("test").build());
        capture.clear();
        assertThat(capture.transitionResults()).isEmpty();
        assertThat(capture.cloudEvents()).isEmpty();
    }

    @Test
    void accumulatesMultipleTransitions() {
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("a"), new StepOutcome.Succeeded())));
        capture.recordTransition(new TransitionResult(Map.of(
                NodeId.of("b"), new StepOutcome.Failed("err"))));
        assertThat(capture.transitionResults()).hasSize(2);
        assertThat(capture.allOutcomes()).hasSize(2);
        assertThat(capture.succeededNodes()).containsExactly(NodeId.of("a"));
        assertThat(capture.failedNodes()).containsExactly(NodeId.of("b"));
    }
}
