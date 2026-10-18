package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonySpecHashStoreTest {

    private final ClaudonySpecHashStore store = new ClaudonySpecHashStore();

    @Test
    void unknownNode_hasDrifted_returnsTrue() {
        var id = NodeId.of("claudony_app:x");
        var spec = new ClaudonyAppNodeSpec("x", "X", null, "img", Map.of());
        assertThat(store.hasDrifted(id, spec)).isTrue();
    }

    @Test
    void recordedNode_sameSpec_notDrifted() {
        var id = NodeId.of("claudony_app:x");
        var spec = new ClaudonyAppNodeSpec("x", "X", null, "img", Map.of());
        store.record(id, spec);
        assertThat(store.hasDrifted(id, spec)).isFalse();
    }

    @Test
    void recordedNode_differentSpec_hasDrifted() {
        var id = NodeId.of("claudony_app:x");
        var spec1 = new ClaudonyAppNodeSpec("x", "X", null, "img:1", Map.of());
        var spec2 = new ClaudonyAppNodeSpec("x", "X", null, "img:2", Map.of());
        store.record(id, spec1);
        assertThat(store.hasDrifted(id, spec2)).isTrue();
    }

    @Test
    void removedNode_hasDrifted_returnsTrue() {
        var id = NodeId.of("claudony_app:x");
        var spec = new ClaudonyAppNodeSpec("x", "X", null, "img", Map.of());
        store.record(id, spec);
        store.remove(id);
        assertThat(store.hasDrifted(id, spec)).isTrue();
    }
}
