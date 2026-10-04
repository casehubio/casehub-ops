package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoolNodeSpecTest {

    @Test
    void nodeIdAppendsDashPool() {
        var spec = testPool("code-reviewer");
        assertThat(spec.nodeId()).isEqualTo("code-reviewer-pool");
    }

    @Test
    void nodeTypeIsPool() {
        var spec = testPool("code-reviewer");
        assertThat(spec.nodeType()).isEqualTo(NodeType.of("pool"));
    }

    @Test
    void agentIdRequired() {
        assertThatThrownBy(() -> new PoolNodeSpec(
                null, "claudony", 2, 8, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
    }

    @Test
    void blankAgentIdRejected() {
        assertThatThrownBy(() -> new PoolNodeSpec(
                "  ", "claudony", 2, 8, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
    }

    @Test
    void backendRequired() {
        assertThatThrownBy(() -> new PoolNodeSpec(
                "agent-1", null, 2, 8, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backend");
    }

    @Test
    void maxActiveMustBeAtLeastOne() {
        assertThatThrownBy(() -> new PoolNodeSpec(
                "agent-1", "claudony", 0, 0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxActive must be >= 1");
    }

    @Test
    void maxActiveMustBeGreaterOrEqualMinActive() {
        assertThatThrownBy(() -> new PoolNodeSpec(
                "agent-1", "claudony", 5, 3, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxActive must be >= minActive");
    }

    @Test
    void evictionDefaultsToMemoryWeighted() {
        var spec = new PoolNodeSpec("agent-1", "claudony", 2, 8, null, null, null, null);
        assertThat(spec.eviction()).isEqualTo(EvictionStrategy.MEMORY_WEIGHTED);
    }

    @Test
    void scalingSpecDefaultsTypeToNone() {
        var scaling = new PoolScalingSpec(null, null, null, null);
        assertThat(scaling.type()).isEqualTo("none");
    }

    @Test
    void scalingSpecPreservesExplicitType() {
        var scaling = new PoolScalingSpec("target-tracking", 0.7, "30s", null);
        assertThat(scaling.type()).isEqualTo("target-tracking");
        assertThat(scaling.targetFillRatio()).isEqualTo(0.7);
        assertThat(scaling.cooldown()).isEqualTo("30s");
    }

    @Test
    void fullConstruction() {
        var scaling = new PoolScalingSpec("target-tracking", 0.7, "30s", "60s");
        var spec = new PoolNodeSpec(
                "code-reviewer", "claudony", 2, 8,
                "~/workspace/reviews", WorkingDirPolicy.SHARED_READ,
                scaling, EvictionStrategy.LRU);

        assertThat(spec.agentId()).isEqualTo("code-reviewer");
        assertThat(spec.backend()).isEqualTo("claudony");
        assertThat(spec.minActive()).isEqualTo(2);
        assertThat(spec.maxActive()).isEqualTo(8);
        assertThat(spec.workingDir()).isEqualTo("~/workspace/reviews");
        assertThat(spec.workingDirPolicy()).isEqualTo(WorkingDirPolicy.SHARED_READ);
        assertThat(spec.scaling().type()).isEqualTo("target-tracking");
        assertThat(spec.eviction()).isEqualTo(EvictionStrategy.LRU);
    }

    static PoolNodeSpec testPool(String agentId) {
        return new PoolNodeSpec(agentId, "claudony", 2, 8,
                "~/workspace", WorkingDirPolicy.SHARED_READ,
                new PoolScalingSpec("target-tracking", 0.7, "30s", null),
                EvictionStrategy.MEMORY_WEIGHTED);
    }
}
