package io.casehub.ops.testing.audit;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapturingTransitionExecutorTest {

    @Test
    void delegatesAndCaptures() {
        TransitionResult expected = new TransitionResult(Map.of(
                NodeId.of("n1"), new StepOutcome.Succeeded()));

        AuditCapture capture = new AuditCapture();
        CapturingTransitionExecutor executor = new CapturingTransitionExecutor(
                (plan, tenancyId) -> expected, capture);

        TransitionResult result = executor.execute(null, "test-tenant");

        assertThat(result).isSameAs(expected);
        assertThat(capture.transitionResults()).containsExactly(expected);
    }

    @Test
    void capturesMultipleExecutions() {
        TransitionResult r1 = new TransitionResult(Map.of(
                NodeId.of("a"), new StepOutcome.Succeeded()));
        TransitionResult r2 = new TransitionResult(Map.of(
                NodeId.of("b"), new StepOutcome.Failed("err")));

        AuditCapture capture = new AuditCapture();
        var results = List.of(r1, r2).iterator();
        CapturingTransitionExecutor executor = new CapturingTransitionExecutor(
                (plan, tid) -> results.next(), capture);

        executor.execute(null, "t1");
        executor.execute(null, "t2");

        assertThat(capture.transitionResults()).containsExactly(r1, r2);
        assertThat(capture.succeededNodes()).containsExactly(NodeId.of("a"));
        assertThat(capture.failedNodes()).containsExactly(NodeId.of("b"));
    }
}
