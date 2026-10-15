package io.casehub.ops.topology.integration;

/**
 * Lightweight LLM client for hello-world verification. Tries Ollama first
 * (fast, local, no auth), then falls back to the {@code claude} CLI which
 * handles Vertex/API-key auth via the same env vars as the dev environment.
 */
public final class ManifestLlmClient {

    private ManifestLlmClient() {}

    public static String helloWorld() throws java.io.IOException, InterruptedException {
        String ollamaResult = tryOllama();
        if (ollamaResult != null) {return ollamaResult;}
        return callClaudeCli();
    }

    private static String tryOllama() {
        try {
            var body = "{\"model\":\"gemma3:4b\",\"messages\":[{\"role\":\"user\",\"content\":\"Say hello and nothing else.\"}],\"stream\":false}";
            var request = java.net.http.HttpRequest.newBuilder()
                                                   .uri(java.net.URI.create("http://localhost:11434/api/chat"))
                                                   .header("content-type", "application/json")
                                                   .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                                                   .timeout(java.time.Duration.ofSeconds(60))
                                                   .build();
            var response = java.net.http.HttpClient.newHttpClient()
                                                   .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {return null;}
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root   = mapper.readTree(response.body());
            return root.path("message").path("content").asText();
        } catch (Exception e) {
            return null;
        }
    }

    private static String callClaudeCli() throws java.io.IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "claude", "-p", "Say hello and nothing else.", "--output-format", "text");
        pb.redirectErrorStream(true);
        Process process  = pb.start();
        String  output   = new String(process.getInputStream().readAllBytes()).trim();
        int     exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new java.io.IOException("claude CLI exited with code " + exitCode + ": " + output);
        }
        return output;
    }
}
