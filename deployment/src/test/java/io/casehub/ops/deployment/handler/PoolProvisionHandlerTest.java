package io.casehub.ops.deployment.handler;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.EvictionStrategy;
import io.casehub.ops.api.deployment.PoolCreateRequest;
import io.casehub.ops.api.deployment.PoolInfo;
import io.casehub.ops.api.deployment.PoolNodeSpec;
import io.casehub.ops.api.deployment.PoolOperations;
import io.casehub.ops.api.deployment.PoolScalingSpec;
import io.casehub.ops.api.deployment.PoolUpdateRequest;
import io.casehub.ops.api.deployment.WorkingDirPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class PoolProvisionHandlerTest {

    private StubPoolOperations ops;
    private PoolProvisionHandler handler;

    @BeforeEach
    void setUp() {
        ops = new StubPoolOperations();
        handler = new PoolProvisionHandler(List.of(ops));
    }

    @Test
    void provisionCreatesPoolWhenAbsent() {
        var spec = testPool("code-reviewer");
        var context = new ProvisionContext("tenant-1",
                new DefaultDesiredStateGraphFactory().empty());

        var result = handler.provision(spec, context);

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(ops.pools).containsKey("code-reviewer");
        var created = ops.pools.get("code-reviewer");
        assertThat(created.minActive()).isEqualTo(2);
        assertThat(created.maxActive()).isEqualTo(8);
        assertThat(created.scalingType()).isEqualTo("target-tracking");
        assertThat(created.eviction()).isEqualTo("MEMORY_WEIGHTED");
    }

    @Test
    void provisionUpdatesPoolWhenPresent() {
        ops.pools.put("code-reviewer", new PoolInfo(
                "platform", "code-reviewer", "ACTIVE", 1, 4,
                "none", null, null, null, "LRU"));

        var spec = testPool("code-reviewer");
        var context = new ProvisionContext("tenant-1",
                new DefaultDesiredStateGraphFactory().empty());

        var result = handler.provision(spec, context);

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        var updated = ops.pools.get("code-reviewer");
        assertThat(updated.minActive()).isEqualTo(2);
        assertThat(updated.maxActive()).isEqualTo(8);
    }

    @Test
    void provisionSkipsUpdateWhenConfigMatches() {
        ops.pools.put("code-reviewer", new PoolInfo(
                "platform", "code-reviewer", "ACTIVE", 2, 8,
                "target-tracking", 0.7, "30s", null, "MEMORY_WEIGHTED"));

        var spec = testPool("code-reviewer");
        var context = new ProvisionContext("tenant-1",
                new DefaultDesiredStateGraphFactory().empty());

        var result = handler.provision(spec, context);

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(ops.updateCount).isZero();
    }

    @Test
    void deprovisionDestroysPoolWhenPresent() {
        ops.pools.put("code-reviewer", new PoolInfo(
                "platform", "code-reviewer", "ACTIVE", 2, 8,
                "target-tracking", 0.7, "30s", null, "MEMORY_WEIGHTED"));

        var spec = testPool("code-reviewer");
        var context = new DeprovisionContext("tenant-1",
                new DefaultDesiredStateGraphFactory().empty());

        var result = handler.deprovision(spec, context);

        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
        assertThat(ops.pools).doesNotContainKey("code-reviewer");
    }

    @Test
    void deprovisionSucceedsWhenPoolAlreadyAbsent() {
        var spec = testPool("code-reviewer");
        var context = new DeprovisionContext("tenant-1",
                new DefaultDesiredStateGraphFactory().empty());

        var result = handler.deprovision(spec, context);

        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
    }

    static PoolNodeSpec testPool(String agentId) {
        return new PoolNodeSpec("platform", agentId, "claudony", 2, 8,
                "~/workspace/reviews", WorkingDirPolicy.SHARED_READ,
                new PoolScalingSpec("target-tracking", 0.7, "30s", null),
                EvictionStrategy.MEMORY_WEIGHTED);
    }

    static class StubPoolOperations implements PoolOperations {
        final ConcurrentHashMap<String, PoolInfo> pools = new ConcurrentHashMap<>();
        int updateCount = 0;

        @Override
        public String backendId() {return "claudony";}

        @Override
        public Optional<PoolInfo> getPool(String applicationId, String poolId) {
            return Optional.ofNullable(pools.get(poolId));
        }

        @Override
        public PoolInfo createPool(PoolCreateRequest request) {
            var info = new PoolInfo(
                    request.applicationId(), request.agentId(), "ACTIVE",
                    request.minActive(), request.maxActive(),
                    request.scalingType(), request.targetFillRatio(),
                    request.cooldown(), request.scaleInCooldown(),
                    request.eviction());
            pools.put(request.agentId(), info);
            return info;
        }

        @Override
        public PoolInfo updatePool(String applicationId, String poolId, PoolUpdateRequest request) {
            updateCount++;
            var existing = pools.get(poolId);
            var updated = new PoolInfo(
                    applicationId, poolId, existing.status(),
                    request.minActive() != null ? request.minActive() : existing.minActive(),
                    request.maxActive() != null ? request.maxActive() : existing.maxActive(),
                    request.scalingType() != null ? request.scalingType() : existing.scalingType(),
                    request.targetFillRatio() != null ? request.targetFillRatio() : existing.targetFillRatio(),
                    request.cooldown() != null ? request.cooldown() : existing.cooldown(),
                    request.scaleInCooldown() != null ? request.scaleInCooldown() : existing.scaleInCooldown(),
                    request.eviction() != null ? request.eviction() : existing.eviction());
            pools.put(poolId, updated);
            return updated;
        }

        @Override
        public void destroyPool(String applicationId, String poolId) {
            pools.remove(poolId);
        }
    }
}
