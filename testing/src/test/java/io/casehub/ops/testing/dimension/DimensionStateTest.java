package io.casehub.ops.testing.dimension;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DimensionStateTest {

    @Test
    void putAndGetString() {
        var state = new DimensionState();
        state.put("appId", "incident-mgmt");
        assertThat(state.getString("appId")).isEqualTo("incident-mgmt");
    }

    @Test
    void getStringThrowsOnMissingKey() {
        var state = new DimensionState();
        assertThatThrownBy(() -> state.getString("missing"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void getTyped() {
        var state = new DimensionState();
        state.put("port", 8080);
        assertThat(state.get("port", Integer.class)).isEqualTo(8080);
    }

    @Test
    void has() {
        var state = new DimensionState();
        assertThat(state.has("key")).isFalse();
        state.put("key", "value");
        assertThat(state.has("key")).isTrue();
    }

    @Test
    void putRejectsNull() {
        var state = new DimensionState();
        assertThatThrownBy(() -> state.put("key", null))
            .isInstanceOf(NullPointerException.class);
    }
}
