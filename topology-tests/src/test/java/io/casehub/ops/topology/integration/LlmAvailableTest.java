package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAvailableTest {

    @Test
    void fromResponse_realLlm_returnsFull() {
        assertThat(LlmMode.fromResponse("Hello!")).isEqualTo(LlmMode.FULL);
    }

    @Test
    void fromResponse_simulated_returnsSimulated() {
        assertThat(LlmMode.fromResponse("Simulated response from fast"))
                .isEqualTo(LlmMode.SIMULATED);
    }

    @Test
    void helloWorld_alwaysReturnsResponse() throws Exception {
        String response = ManifestLlmClient.helloWorld();
        assertThat(response).isNotEmpty();
        LlmMode mode = LlmMode.fromResponse(response);
        assertThat(mode).isIn(LlmMode.FULL, LlmMode.SIMULATED);
    }
}
