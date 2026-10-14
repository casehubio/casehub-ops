package io.casehub.ops.topology.integration;

public enum LlmMode {
    FULL,
    SIMULATION;

    public static LlmMode detect() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        return (apiKey != null && !apiKey.isBlank()) ? FULL : SIMULATION;
    }
}
