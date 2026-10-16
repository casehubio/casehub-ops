package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public final class FleetLlmExerciser {

    private static final String OLLAMA_HOST = "http://localhost:11434";
    private static final String MODEL = "gemma3:4b";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record LlmResponse(String appId, String role, String domain, String content) {}

    public static LlmResponse exercise(String appId, Map<String, ?> config) {
        String role = str(config, "llm.role", "assistant");
        String domain = str(config, "llm.domain", "general");
        String capability = str(config, "llm.capability", "general");
        String archetype = str(config, "llm.archetype", "agent");

        String systemPrompt = "You are a " + role + " AI agent specializing in " + domain
                + ". Your approach is " + capability + " and " + archetype + "."
                + " Always answer from the perspective of your specialty.";

        String userPrompt = "A production system is showing intermittent 500 errors."
                + " What is your specific first action? Answer in 1-2 sentences.";

        String content = callOllama(systemPrompt, userPrompt);
        return new LlmResponse(appId, role, domain, content);
    }

    public static void assertSubstantive(LlmResponse response) {
        assertThat(response.content())
                .as("LLM %s (role=%s, domain=%s) must produce substantive output",
                        response.appId(), response.role(), response.domain())
                .isNotBlank()
                .hasSizeGreaterThan(20);
    }

    public static void assertDomainRelevant(LlmResponse response) {
        assertSubstantive(response);
        String keywords = domainKeywords(response.domain());
        assertThat(response.content().toLowerCase())
                .as("LLM %s response should reflect domain '%s':\n  \"%s\"",
                        response.appId(), response.domain(), response.content())
                .containsPattern(keywords);
    }

    public static void assertDifferentiated(List<LlmResponse> responses) {
        assertThat(responses).hasSizeGreaterThan(1);
        long distinct = responses.stream()
                .map(r -> r.content().trim().toLowerCase())
                .distinct()
                .count();
        assertThat(distinct)
                .as("Fleet members with different roles must produce different responses")
                .isGreaterThan(1);
    }

    public static List<LlmResponse> exerciseFleet(List<String> appIds,
                                                    java.util.function.Function<String, Map<String, ?>> configProvider) {
        List<LlmResponse> responses = new ArrayList<>();
        for (String appId : appIds) {
            responses.add(exercise(appId, configProvider.apply(appId)));
        }
        return responses;
    }

    public static void assertFleetOperational(List<LlmResponse> responses) {
        for (LlmResponse r : responses) {
            assertDomainRelevant(r);
        }
        assertDifferentiated(responses);
    }

    private static String domainKeywords(String domain) {
        return switch (domain) {
            case "analysis", "data-analysis" ->
                    "analy|data|investigat|insight|pattern|examin|root.cause|diagnos|metric|trend|correlat";
            case "software-engineering" ->
                    "code|software|debug|log|stack|implement|fix|program|engineer|develop|deploy|error.handl|trace|exception";
            case "general" ->
                    "assess|monitor|gather|information|coordinat|team|communicat|priorit|triage|respond|address|status|check|review|help|assist";
            case "knowledge-discovery" ->
                    "research|knowledge|discover|learn|study|explor|understand|literature|methodolog|hypothes|investigat";
            default -> ".+";
        };
    }

    private static String callOllama(String system, String user) {
        try {
            String body = MAPPER.writeValueAsString(Map.of(
                    "model", MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)),
                    "stream", false));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_HOST + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(90))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama returned " + response.statusCode() + ": " + response.body());
            }
            return MAPPER.readTree(response.body()).path("message").path("content").asText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted calling Ollama", e);
        }
    }

    private static String str(Map<String, ?> config, String key, String defaultVal) {
        Object val = config.get(key);
        return val != null ? String.valueOf(val) : defaultVal;
    }

    private FleetLlmExerciser() {}
}
