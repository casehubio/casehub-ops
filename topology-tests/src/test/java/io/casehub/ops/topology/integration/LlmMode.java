package io.casehub.ops.topology.integration;

/**
 * With simulation as a built-in fallback provider, an LLM response is always
 * available. This enum now indicates whether the response came from a real
 * provider or simulation — useful for test reporting, not gating.
 */
public enum LlmMode {
    FULL,
    SIMULATED;

    public static LlmMode fromResponse(String response) {
        return ManifestLlmClient.isSimulated(response) ? SIMULATED : FULL;
    }
}
