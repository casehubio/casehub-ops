package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OllamaAvailable.OllamaCondition.class)
public @interface OllamaAvailable {

    class OllamaCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            try {
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:11434/api/tags"))
                        .timeout(java.time.Duration.ofSeconds(2))
                        .GET().build();
                var response = java.net.http.HttpClient.newHttpClient()
                        .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return ConditionEvaluationResult.enabled("Ollama is running on localhost:11434");
                }
            } catch (Exception ignored) {}
            return ConditionEvaluationResult.disabled("Ollama not running on localhost:11434");
        }
    }
}
