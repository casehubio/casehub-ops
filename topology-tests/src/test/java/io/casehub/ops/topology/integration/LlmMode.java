package io.casehub.ops.topology.integration;

import io.casehub.platform.agent.config.Manifest;
import io.casehub.platform.agent.config.ManifestLoader;

public enum LlmMode {
    FULL,
    NO_PROVIDER;

    private static LlmMode cached;

    public static LlmMode detect() {
        if (cached != null) {return cached;}
        ManifestLoader loader   = new ManifestLoader();
        Manifest       manifest = loader.load(null, null);
        if (!manifest.providers().isEmpty() || ollamaAvailable() || claudeCliAvailable()) {
            cached = FULL;
        } else {
            cached = NO_PROVIDER;
        }
        return cached;
    }

    private static boolean ollamaAvailable() {
        try {
            var request = java.net.http.HttpRequest.newBuilder()
                                                   .uri(java.net.URI.create("http://localhost:11434/api/tags"))
                                                   .timeout(java.time.Duration.ofSeconds(2))
                                                   .GET().build();
            var response = java.net.http.HttpClient.newHttpClient()
                                                   .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean claudeCliAvailable() {
        try {
            Process p = new ProcessBuilder("claude", "--version")
                                .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
