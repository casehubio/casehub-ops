package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.platform.api.registry.HealthStatus;

public final class ClaudonyHealthMapper {
    private ClaudonyHealthMapper() {}

    public static NodeStatus toNodeStatus(HealthStatus health) {
        return switch (health) {
            case HEALTHY -> NodeStatus.PRESENT;
            case DEGRADED -> NodeStatus.DRIFTED;
            case DOWN -> NodeStatus.ABSENT;
            case SUSPENDED -> NodeStatus.SUSPENDED;
        };
    }
}
