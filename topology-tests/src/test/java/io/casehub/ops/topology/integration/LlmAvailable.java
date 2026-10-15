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
            try {
                String response = ManifestLlmClient.helloWorld();
                if (!ManifestLlmClient.isSimulated(response)) {
                    return ConditionEvaluationResult.enabled("Real LLM provider available");
                }
            } catch (Exception ignored) {}
            return ConditionEvaluationResult.disabled(
                    "No real LLM provider available — only simulation. "
                    + "Configure a provider in ~/.casehub/agent-config.yaml");
        }
    }
}
