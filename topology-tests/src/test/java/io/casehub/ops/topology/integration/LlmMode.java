package io.casehub.ops.topology.integration;

/**
 * Indicates whether a response came from a real provider or simulation —
 * useful for test reporting, not gating.
 */
public enum LlmMode {
    FULL,
    SIMULATED;

    public static LlmMode fromBackendKey(String backendKey) {
        return "simulation".equals(backendKey) ? SIMULATED : FULL;
    }
}
