package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies LLM routing via the manifest provider chain. Follows the
 * manifest-declared provider order (Ollama -> Vertex -> Simulation),
 * falling through to simulation when no real LLM is available.
 *
 * <p>Always runs — simulation ensures a response even without real LLM access.
 */
@Tag("integration-live")
class HelloWorldLlmTest {

    @Test
    void llmRespondsToHelloPrompt() throws Exception {
        String response = ManifestLlmClient.helloWorld();

        assertThat(response).isNotEmpty();

        if (ManifestLlmClient.isSimulated(response)) {
            assertThat(response).startsWith("Simulated response");
        } else {
            assertThat(response.toLowerCase()).contains("hello");
        }
    }
}
