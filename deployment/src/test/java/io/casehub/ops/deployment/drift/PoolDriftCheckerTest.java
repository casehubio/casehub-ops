package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeStatus;
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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoolDriftCheckerTest {

    private PoolDriftChecker checker;
    private StubPoolOperations ops;
    private static final String TENANCY_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        ops = new StubPoolOperations();
        checker = new PoolDriftChecker(ops);
    }

    @Test
    void nodeType() {
        assertEquals("pool", checker.nodeType());
    }

    @Test
    void poolPresent() {
        ops.pools.put("code-reviewer", new PoolInfo(
                "platform", "code-reviewer", "ACTIVE", 2, 8,
                "target-tracking", 0.7, "30s", null, "MEMORY_WEIGHTED"));

        var spec = testPool("code-reviewer");
        assertEquals(NodeStatus.PRESENT, checker.check(spec, TENANCY_ID));
    }

    @Test
    void poolAbsent() {
        var spec = testPool("code-reviewer");
        assertEquals(NodeStatus.ABSENT, checker.check(spec, TENANCY_ID));
    }

    @Test
    void poolDrifted_capacityMismatch() {
        ops.pools.put("code-reviewer", new PoolInfo(
                "platform", "code-reviewer", "ACTIVE", 1, 4,
                "target-tracking", 0.7, "30s", null, "MEMORY_WEIGHTED"));

        var spec = testPool("code-reviewer");
        assertEquals(NodeStatus.DRIFTED, checker.check(spec, TENANCY_ID));
    }

    @Test
    void poolDrifted_scalingTypeMismatch() {
        ops.pools.put("code-reviewer", new PoolInfo(
                "platform", "code-reviewer", "ACTIVE", 2, 8,
                "none", null, null, null, "MEMORY_WEIGHTED"));

        var spec = testPool("code-reviewer");
        assertEquals(NodeStatus.DRIFTED, checker.check(spec, TENANCY_ID));
    }

    @Test
    void poolDrifted_targetFillRatioMismatch() {
        ops.pools.put("code-reviewer", new PoolInfo(
                "platform", "code-reviewer", "ACTIVE", 2, 8,
                "target-tracking", 0.5, "30s", null, "MEMORY_WEIGHTED"));

        var spec = testPool("code-reviewer");
        assertEquals(NodeStatus.DRIFTED, checker.check(spec, TENANCY_ID));
    }

    @Test
    void poolDrifted_evictionMismatch() {
        ops.pools.put("code-reviewer", new PoolInfo(
                "platform", "code-reviewer", "ACTIVE", 2, 8,
                "target-tracking", 0.7, "30s", null, "LRU"));

        var spec = testPool("code-reviewer");
        assertEquals(NodeStatus.DRIFTED, checker.check(spec, TENANCY_ID));
    }

    @Test
    void unknownSpecType() {
        var spec = new io.casehub.ops.api.deployment.ChannelNodeSpec(
                "ch1", "desc", io.casehub.qhorus.api.channel.ChannelSemantic.APPEND,
                null, null, null, null, null, null, null,
                null, null, null, null,
                null, null, null);

        assertEquals(NodeStatus.UNKNOWN, checker.check(spec, TENANCY_ID));
    }

    static PoolNodeSpec testPool(String agentId) {
        return new PoolNodeSpec("platform", agentId, "claudony", 2, 8,
                                "~/workspace/reviews", WorkingDirPolicy.SHARED_READ,
                                new PoolScalingSpec("target-tracking", 0.7, "30s", null),
                                EvictionStrategy.MEMORY_WEIGHTED);
    }

    static class StubPoolOperations implements PoolOperations {
        final ConcurrentHashMap<String, PoolInfo> pools = new ConcurrentHashMap<>();

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
            return pools.get(poolId);
        }

        @Override
        public void destroyPool(String applicationId, String poolId) {
            pools.remove(poolId);
        }
    }
}
