package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAvailableTest {

    @Test
    void detect_returnsValidMode() {
        LlmMode mode = LlmMode.detect();
        assertThat(mode).isNotNull();
        assertThat(mode).isIn(LlmMode.FULL, LlmMode.SIMULATION);
    }

    @Test
    void detect_withoutApiKey_returnsSimulation() {
        // In CI/test environments, ANTHROPIC_API_KEY is typically not set
        // This test verifies the enum and detection logic work regardless
        if (System.getenv("ANTHROPIC_API_KEY") == null) {
            assertThat(LlmMode.detect()).isEqualTo(LlmMode.SIMULATION);
        }
    }
}
