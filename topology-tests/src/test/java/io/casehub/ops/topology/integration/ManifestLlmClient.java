package io.casehub.ops.topology.integration;

import io.casehub.platform.agent.config.Manifest;
import io.casehub.platform.agent.config.ManifestLoader;
import io.casehub.platform.agent.config.ProviderDeclaration;

/**
 * Lightweight LLM client that reads providers from the manifest and
 * follows the declared order: Ollama (local) -> Claude CLI (Vertex) -> Simulation.
 *
 * <p>Mirrors the platform's {@code RoutingAgentProvider} chain resolution but
 * without CDI — suitable for standalone topology tests.
 */
public final class ManifestLlmClient {

    private static final String SIMULATED_PREFIX = "Simulated response";

    private ManifestLlmClient() {}

    public static String helloWorld() throws java.io.IOException, InterruptedException {
        ManifestLoader loader = new ManifestLoader();
        Manifest manifest = loader.load(null, null);

        for (ProviderDeclaration provider : manifest.providers()) {
            String result = tryProvider(provider);
            if (result != null) return result;
        }

        return simulate();
    }

    public static boolean isSimulated(String response) {
        return response != null && response.startsWith(SIMULATED_PREFIX);
    }

    private static String tryProvider(ProviderDeclaration provider) {
        return switch (provider.vendor()) {
            case "ollama" -> tryOllama(provider);
            case "vertex", "google-vertex", "anthropic" -> tryClaudeCli();
            case "simulation" -> simulate();
            default -> null;
        };
    }

    private static String tryOllama(ProviderDeclaration provider) {
        try {
            String host = provider.host() != null ? provider.host() : "localhost:11434";
            var body = "{\"model\":\"gemma3:4b\",\"messages\":[{\"role\":\"user\",\"content\":\"Say hello and nothing else.\"}],\"stream\":false}";
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://" + host + "/api/chat"))
                    .header("content-type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(60))
                    .build();
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(response.body());
            return root.path("message").path("content").asText();
        } catch (Exception e) {
            return null;
        }
    }

    private static String tryClaudeCli() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "claude", "-p", "Say hello and nothing else.", "--output-format", "text");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();
            return exitCode == 0 ? output : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String simulate() {
        return SIMULATED_PREFIX + " from fast";
    }
}
