package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.NodeDriftChecker;
import io.casehub.ops.api.deployment.PoolNodeSpec;
import io.casehub.ops.deployment.handler.PoolProvisionHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Objects;

@ApplicationScoped
public class PoolDriftChecker implements NodeDriftChecker {

    private static final Logger LOG = Logger.getLogger(PoolDriftChecker.class);

    private final PoolProvisionHandler.PoolOperations ops;

    @Inject
    public PoolDriftChecker(PoolProvisionHandler.PoolOperations ops) {
        this.ops = ops;
    }

    @Override
    public String nodeType() {
        return "pool";
    }

    @Override
    public NodeStatus check(NodeSpec spec, String tenancyId) {
        if (!(spec instanceof PoolNodeSpec poolSpec)) {
            return NodeStatus.UNKNOWN;
        }

        var actual = ops.getPool(poolSpec.agentId());
        if (actual.isEmpty()) {
            return NodeStatus.ABSENT;
        }

        var pool = actual.get();
        if (capacityDrifted(poolSpec, pool)
                || scalingDrifted(poolSpec, pool)
                || evictionDrifted(poolSpec, pool)) {
            return NodeStatus.DRIFTED;
        }
        return NodeStatus.PRESENT;
    }

    private boolean capacityDrifted(PoolNodeSpec spec, PoolProvisionHandler.PoolInfo actual) {
        if (spec.minActive() != actual.minActive()) {
            LOG.debugf("pool %s: minActive drifted [%d → %d]",
                    spec.agentId(), spec.minActive(), actual.minActive());
            return true;
        }
        if (spec.maxActive() != actual.maxActive()) {
            LOG.debugf("pool %s: maxActive drifted [%d → %d]",
                    spec.agentId(), spec.maxActive(), actual.maxActive());
            return true;
        }
        return false;
    }

    private boolean scalingDrifted(PoolNodeSpec spec, PoolProvisionHandler.PoolInfo actual) {
        var desiredType = spec.scaling() != null ? spec.scaling().type() : "none";
        if (!Objects.equals(desiredType, actual.scalingType())) {
            LOG.debugf("pool %s: scalingType drifted [%s → %s]",
                    spec.agentId(), desiredType, actual.scalingType());
            return true;
        }
        var desiredRatio = spec.scaling() != null ? spec.scaling().targetFillRatio() : null;
        if (!Objects.equals(desiredRatio, actual.targetFillRatio())) {
            LOG.debugf("pool %s: targetFillRatio drifted [%s → %s]",
                    spec.agentId(), desiredRatio, actual.targetFillRatio());
            return true;
        }
        return false;
    }

    private boolean evictionDrifted(PoolNodeSpec spec, PoolProvisionHandler.PoolInfo actual) {
        var desiredEviction = spec.eviction() != null ? spec.eviction().name() : "MEMORY_WEIGHTED";
        if (!Objects.equals(desiredEviction, actual.eviction())) {
            LOG.debugf("pool %s: eviction drifted [%s → %s]",
                    spec.agentId(), desiredEviction, actual.eviction());
            return true;
        }
        return false;
    }
}
