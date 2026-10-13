package io.casehub.ops.api.deployment;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RuntimeConfigTest {

    @Test
    void validConfig() {
        var config = new RuntimeConfig("0.2", List.of("a2a", "react"));
        assertThat(config.engine()).isEqualTo("0.2");
        assertThat(config.modules()).containsExactly("a2a", "react");
    }

    @Test
    void nullEngineThrows() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RuntimeConfig(null, List.of()))
            .withMessageContaining("engine");
    }

    @Test
    void blankEngineThrows() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RuntimeConfig("  ", List.of()))
            .withMessageContaining("engine");
    }

    @Test
    void nullModulesDefaultsToEmpty() {
        var config = new RuntimeConfig("0.2", null);
        assertThat(config.modules()).isEmpty();
    }

    @Test
    void modulesAreDefensivelyCopied() {
        var mutable = new java.util.ArrayList<>(List.of("a2a"));
        var config = new RuntimeConfig("0.2", mutable);
        assertThatThrownBy(() -> config.modules().add("react"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
