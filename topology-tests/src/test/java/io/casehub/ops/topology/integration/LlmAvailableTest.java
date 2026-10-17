package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAvailableTest {

    @Test
    void fromBackendKey_realProvider_returnsFull() {
        assertThat(LlmMode.fromBackendKey("ollama")).isEqualTo(LlmMode.FULL);
    }

    @Test
    void fromBackendKey_simulated_returnsSimulated() {
        assertThat(LlmMode.fromBackendKey("simulation")).isEqualTo(LlmMode.SIMULATED);
    }

    @Test
    void fromBackendKey_null_returnsFull() {
        assertThat(LlmMode.fromBackendKey(null)).isEqualTo(LlmMode.FULL);
    }
}
