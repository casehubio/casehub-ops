package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises each LLM provider individually and the manifest-driven fallback
 * chain. On a dev machine all three providers are tested. In CI, Ollama
 * (if installed) and simulation run; Vertex tests are skipped.
 */
@Tag("integration-live")
class HelloWorldLlmTest {

    // ── Per-provider tests ───────────────────────────────────────────────

    @Test
    @OllamaAvailable
    void ollama_respondsToHello() {
        String response = ManifestLlmClient.tryOllama();
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).contains("hello");
    }

    @Test
    @LlmAvailable
    void vertex_respondsToHello() {
        String response = ManifestLlmClient.tryClaudeCli();
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).contains("hello");
    }

    @Test
    void simulation_returnsResponse() {
        String response = ManifestLlmClient.simulate();
        assertThat(response).startsWith("Simulated response");
        assertThat(ManifestLlmClient.isSimulated(response)).isTrue();
    }

    // ── Chain fallback test ──────────────────────────────────────────────

    @Test
    void chain_fallsThrough() throws Exception {
        String response = ManifestLlmClient.helloWorld();

        assertThat(response).isNotEmpty();
        LlmMode mode = LlmMode.fromResponse(response);
        assertThat(mode).isIn(LlmMode.FULL, LlmMode.SIMULATED);

        if (mode == LlmMode.FULL) {
            assertThat(response.toLowerCase()).contains("hello");
        }
    }
}
