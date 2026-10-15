package io.casehub.ops.topology.integration;

/**
 * Lightweight LLM client for hello-world verification. Uses the {@code claude}
 * CLI which handles Vertex/API-key auth, model selection, and all provider
 * configuration via the same env vars as the development environment.
 */
public final class ManifestLlmClient {

    private ManifestLlmClient() {}

    public static String helloWorld() throws java.io.IOException, InterruptedException {
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
