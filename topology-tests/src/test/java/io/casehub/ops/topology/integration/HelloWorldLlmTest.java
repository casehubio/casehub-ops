package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the LLM provider configured in the manifest is reachable
 * and returns a coherent response. Gated on manifest provider availability —
 * skipped when no provider is configured (CI without LLM access).
 */
@Tag("integration-live")
@LlmAvailable
class HelloWorldLlmTest {

    @Test
    void llmRespondsToHelloPrompt() throws Exception {
        String response = ManifestLlmClient.helloWorld();
        assertThat(response.toLowerCase()).contains("hello");
    }
}
