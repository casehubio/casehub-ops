package io.casehub.ops.testing.audit;

import io.casehub.desiredstate.api.TransitionExecutor;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;

public class CapturingTransitionExecutor implements TransitionExecutor {

    private final TransitionExecutor delegate;
    private final AuditCapture capture;

    public CapturingTransitionExecutor(TransitionExecutor delegate, AuditCapture capture) {
        this.delegate = delegate;
        this.capture = capture;
    }

    @Override
    public TransitionResult execute(TransitionPlan plan, String tenancyId) {
        TransitionResult result = delegate.execute(plan, tenancyId);
        capture.recordTransition(result);
        return result;
    }
}
