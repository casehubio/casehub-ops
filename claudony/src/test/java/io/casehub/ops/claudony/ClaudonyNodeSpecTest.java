package io.casehub.ops.claudony;

import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyGoalEntry;
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeTypes;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyReviewSpec;
import io.casehub.desiredstate.api.NodeId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaudonyNodeSpecTest {

    @Test
    void appNodeSpec_nodeType_and_nodeId() {
        var spec = new ClaudonyAppNodeSpec("my-app", "My App", "desc", "img:1.0", Map.of("k", "v"));
        assertThat(spec.nodeType()).isEqualTo(ClaudonyNodeTypes.APP);
        assertThat(spec.nodeId()).isEqualTo("claudony_app:my-app");
    }

    @Test
    void poolNodeSpec_nodeType_and_nodeId() {
        var spec = new ClaudonyPoolNodeSpec("my-app", "agent-1", 1, 3);
        assertThat(spec.nodeType()).isEqualTo(ClaudonyNodeTypes.POOL);
        assertThat(spec.nodeId()).isEqualTo("claudony_pool:my-app:agent-1");
    }

    @Test
    void sealedInterface_permits_only_app_and_pool() {
        var permitted = ClaudonyNodeSpec.class.getPermittedSubclasses();
        assertThat(permitted).containsExactlyInAnyOrder(
            ClaudonyAppNodeSpec.class, ClaudonyPoolNodeSpec.class);
    }

    @Test
    void goalEntry_defensiveCopy_dependsOn() {
        var entry = new ClaudonyGoalEntry<>(
            new ClaudonyAppNodeSpec("a", "A", null, "img", Map.of()), null);
        assertThat(entry.dependsOn()).isEmpty();
    }

    @Test
    void reviewSpec_rejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> new ClaudonyReviewSpec(null, "reason"));
    }

    @Test
    void appNodeSpec_defensiveCopy_config() {
        var mutable = new HashMap<String, String>();
        mutable.put("k", "v");
        var spec = new ClaudonyAppNodeSpec("a", "A", null, "img", mutable);
        mutable.put("k2", "v2");
        assertThat(spec.config()).doesNotContainKey("k2");
    }

    @Test
    void appNodeSpec_rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
            () -> new ClaudonyAppNodeSpec("", "A", null, "img", Map.of()));
    }

    @Test
    void poolNodeSpec_rejectsBlankAppName() {
        assertThrows(IllegalArgumentException.class,
            () -> new ClaudonyPoolNodeSpec("", "agent-1", 1, 3));
    }

    @Test
    void reviewSpec_nodeType() {
        var spec = new ClaudonyReviewSpec(NodeId.of("test"), "reason");
        assertThat(spec.nodeType().value()).isEqualTo("claudony_review");
    }
}
