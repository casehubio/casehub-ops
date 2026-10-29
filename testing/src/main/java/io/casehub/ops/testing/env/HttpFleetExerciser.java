package io.casehub.ops.testing.env;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpFleetExerciser {

    static final String DEFAULT_USER_PROMPT =
            "A production system is showing intermittent 500 errors."
            + " What is your specific first action? Answer in 1-2 sentences.";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private HttpFleetExerciser() {}

    public static LlmResponse exercise(String serviceUrl, String agentId) {
        return exercise(serviceUrl, agentId, DEFAULT_USER_PROMPT);
    }

    public static LlmResponse exercise(String serviceUrl, String agentId, String userPrompt) {
        String endpoint = serviceUrl + "/api/agent-chat/invoke";
        String payload = buildRequestPayload(agentId, userPrompt);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Agent chat failed: HTTP " + response.statusCode()
                        + " from " + endpoint + ": " + response.body());
            }

            LlmResponse result = parseResponse(agentId, response.body());
            System.out.printf("  [http-exerciser] %s → %s/%s (%d chars)%n",
                    agentId, result.backendKey(), result.instanceId(),
                    result.content().length());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during HTTP exercise of " + agentId, e);
        } catch (Exception e) {
            throw new RuntimeException("HTTP exercise failed for " + agentId + " at " + endpoint, e);
        }
    }

    static String buildRequestPayload(String agentId, String userPrompt) {
        try {
            var node = MAPPER.createObjectNode()
                    .put("agentId", agentId)
                    .put("userPrompt", userPrompt);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request payload", e);
        }
    }

    static LlmResponse parseResponse(String agentId, String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode metadata = node.path("agentMetadata");
            return new LlmResponse(
                    agentId,
                    metadata.path("slot").asText("unknown"),
                    metadata.path("domain").asText("general"),
                    node.path("text").asText(""),
                    node.path("backendKey").asText(null),
                    node.path("instanceId").asText(null));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse agent chat response", e);
        }
    }
}
