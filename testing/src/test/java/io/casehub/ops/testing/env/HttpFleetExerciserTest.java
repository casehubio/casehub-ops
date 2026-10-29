package io.casehub.ops.testing.env;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpFleetExerciserTest {

    @Test
    void buildRequestPayload_containsAgentIdAndPrompt() {
        String payload = HttpFleetExerciser.buildRequestPayload("my-llm",
                HttpFleetExerciser.DEFAULT_USER_PROMPT);
        assertThat(payload)
                .contains("\"agentId\":\"my-llm\"")
                .contains("\"userPrompt\":");
    }

    @Test
    void parseResponse_extractsAllFields() {
        String json = """
                {"text":"I would check the logs.","agentId":"my-llm",\
                "agentMetadata":{"slot":"analyst","domain":"analysis"},\
                "backendKey":"claude","instanceId":"inst-1",\
                "inputTokens":100,"outputTokens":50,"thinkingTokens":0,\
                "cacheReadTokens":0,"cacheWriteTokens":0,\
                "totalCostUsd":0.001,"durationMs":1200}""";
        LlmResponse response = HttpFleetExerciser.parseResponse("my-llm", json);
        assertThat(response.appId()).isEqualTo("my-llm");
        assertThat(response.role()).isEqualTo("analyst");
        assertThat(response.domain()).isEqualTo("analysis");
        assertThat(response.content()).isEqualTo("I would check the logs.");
        assertThat(response.backendKey()).isEqualTo("claude");
        assertThat(response.instanceId()).isEqualTo("inst-1");
    }

    @Test
    void parseResponse_handlesMinimalResponse() {
        String json = """
                {"text":"Short answer.","agentId":"my-llm",\
                "backendKey":"claude","instanceId":"i1",\
                "inputTokens":10,"outputTokens":5,"thinkingTokens":0,\
                "cacheReadTokens":0,"cacheWriteTokens":0,"durationMs":100}""";
        LlmResponse response = HttpFleetExerciser.parseResponse("my-llm", json);
        assertThat(response.appId()).isEqualTo("my-llm");
        assertThat(response.role()).isEqualTo("unknown");
        assertThat(response.domain()).isEqualTo("general");
        assertThat(response.content()).isEqualTo("Short answer.");
    }

    @Test
    void exercise_failsWithBadUrl() {
        assertThatThrownBy(() ->
                HttpFleetExerciser.exercise("http://localhost:1", "my-llm"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("my-llm");
    }
}
