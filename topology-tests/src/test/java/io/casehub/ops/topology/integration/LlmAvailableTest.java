package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAvailableTest {

    @Test
    void detect_returnsValidMode() {
        LlmMode mode = LlmMode.detect();
        assertThat(mode).isNotNull();
        assertThat(mode).isIn(LlmMode.FULL, LlmMode.NO_PROVIDER);
    }
}
