package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(LlmAvailable.LlmCondition.class)
public @interface LlmAvailable {

    class LlmCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (LlmMode.detect() == LlmMode.FULL) {
                return ConditionEvaluationResult.enabled("ANTHROPIC_API_KEY is set");
            }
            return ConditionEvaluationResult.disabled(
                    "ANTHROPIC_API_KEY not set — skipping LLM-dependent test");
        }
    }
}
