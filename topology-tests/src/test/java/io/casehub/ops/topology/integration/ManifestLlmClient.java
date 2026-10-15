package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import io.casehub.platform.agent.config.CredentialRef;
import io.casehub.platform.agent.config.Manifest;
import io.casehub.platform.agent.config.ManifestLoader;
import io.casehub.platform.agent.config.ProviderDeclaration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Lightweight LLM client that resolves provider configuration from the
 * manifest search path and makes a direct API call. No CDI required.
 *
 * <p>Supports: anthropic (direct API), vertex (Google ADC), ollama (local).
 */
public final class ManifestLlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ManifestLlmClient() {}

    public static String helloWorld() throws IOException, InterruptedException {
        ManifestLoader loader = new ManifestLoader();
        Manifest manifest = loader.load(null, null);

        if (manifest.providers().isEmpty()) {
            throw new IllegalStateException("No LLM providers configured in manifest");
        }

        for (ProviderDeclaration provider : manifest.providers()) {
            try {
                return switch (provider.vendor()) {
                    case "anthropic" -> callAnthropic(provider);
                    case "vertex" -> callVertex(provider);
                    case "google-vertex" -> callVertex(provider);
                    case "ollama" -> callOllama(provider);
                    default -> throw new UnsupportedOperationException(
                            "Unsupported vendor: " + provider.vendor());
                };
            } catch (UnsupportedOperationException e) {
                continue;
            }
        }

        throw new IllegalStateException("No supported LLM provider found in manifest. "
                + "Supported: anthropic, vertex, google-vertex, ollama");
    }

    private static String callAnthropic(ProviderDeclaration provider)
            throws IOException, InterruptedException {
        String apiKey = resolveScalarCredential(provider);
        String body = messagesBody("claude-sonnet-4-20250514");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        return extractResponseText(HTTP.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private static String callVertex(ProviderDeclaration provider)
            throws IOException, InterruptedException {
        Map<String, String> creds = resolveMapCredential(provider);
        String projectId = creds.get("project-id");
        String region = creds.getOrDefault("region", "us-east5");

        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        credentials.refreshIfExpired();
        String token = credentials.getAccessToken().getTokenValue();

        String model = "claude-sonnet-4-20250514";
        String url = String.format(
                "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s"
                        + "/publishers/anthropic/models/%s:rawPredict",
                region, projectId, region, model);

        String body = vertexMessagesBody();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        return extractResponseText(HTTP.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private static String callOllama(ProviderDeclaration provider)
            throws IOException, InterruptedException {
        String host = provider.host() != null ? provider.host() : "localhost:11434";
        String body = """
                {"model":"llama3.2","messages":[{"role":"user","content":"Say hello and nothing else."}],"stream":false}""";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + "/api/chat"))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = MAPPER.readTree(response.body());
        return root.path("message").path("content").asText();
    }

    private static String messagesBody(String model) {
        return String.format("""
                {"model":"%s","max_tokens":100,"messages":[{"role":"user","content":"Say hello and nothing else."}]}""",
                model);
    }

    private static String vertexMessagesBody() {
        return """
                {"anthropic_version":"vertex-2023-10-16","max_tokens":100,"messages":[{"role":"user","content":"Say hello and nothing else."}]}""";
    }

    private static String extractResponseText(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 400) {
            throw new IOException("LLM API returned HTTP " + response.statusCode()
                    + ": " + response.body());
        }
        JsonNode root = MAPPER.readTree(response.body());
        JsonNode content = root.path("content");
        if (content.isArray() && !content.isEmpty()) {
            return content.get(0).path("text").asText();
        }
        return root.path("text").asText();
    }

    private static String resolveScalarCredential(ProviderDeclaration provider) {
        if (!provider.hasScalarCredential()) {
            throw new IllegalStateException("Expected scalar credential for " + provider.vendor());
        }
        CredentialRef ref = CredentialRef.parse(provider.scalarCredential());
        if (ref instanceof CredentialRef.EnvRef env) {
            String value = System.getenv(env.variableName());
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Env var not set: " + env.variableName());
            }
            return value;
        }
        throw new IllegalStateException("Only env: credential refs supported in tests");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> resolveMapCredential(ProviderDeclaration provider) {
        Object cred = provider.credential();
        if (cred == null) return Map.of();
        if (!(cred instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Expected map credential for " + provider.vendor());
        }
        var resolved = new java.util.LinkedHashMap<String, String>();
        for (var entry : ((Map<String, String>) map).entrySet()) {
            String val = entry.getValue();
            if (val.startsWith("env:")) {
                String envVal = System.getenv(val.substring(4));
                if (envVal != null && !envVal.isBlank()) {
                    resolved.put(entry.getKey(), envVal);
                }
            } else {
                resolved.put(entry.getKey(), val);
            }
        }
        return Map.copyOf(resolved);
    }
}
