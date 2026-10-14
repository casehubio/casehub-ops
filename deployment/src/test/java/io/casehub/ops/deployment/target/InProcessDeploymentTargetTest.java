package io.casehub.ops.deployment.target;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class InProcessDeploymentTargetTest {

    @Test
    void targetIdIsInProcess() {
        var target = new InProcessDeploymentTarget(id -> true);
        assertThat(target.targetId()).isEqualTo("in-process");
    }

    @Test
    void deploySucceedsWhenBeanResolvable() {
        var target = new InProcessDeploymentTarget(id -> true);
        var config = new DeployConfig("qhorus", 1, Map.of(), Map.of());
        var result = target.deploy("casehubio/qhorus:0.5", config);
        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(((DeployResult.Success) result).instanceId()).isEqualTo("qhorus");
    }

    @Test
    void deployFailsWhenBeanNotResolvable() {
        var target = new InProcessDeploymentTarget(id -> false);
        var config = new DeployConfig("qhorus", 1, Map.of(), Map.of());
        var result = target.deploy("casehubio/qhorus:0.5", config);
        assertThat(result).isInstanceOf(DeployResult.Failed.class);
    }

    @Test
    void statusReturnsPresentWhenBeanResolvable() {
        var target = new InProcessDeploymentTarget(id -> true);
        assertThat(target.status("qhorus")).isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void statusReturnsAbsentWhenBeanNotResolvable() {
        var target = new InProcessDeploymentTarget(id -> false);
        assertThat(target.status("unknown-app")).isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void undeployIsNoOp() {
        var target = new InProcessDeploymentTarget(id -> true);
        var result = target.undeploy("qhorus");
        assertThat(result).isInstanceOf(DeployResult.Success.class);
    }

    @Test
    void healthCheckHealthyWhenResolvable() {
        var target = new InProcessDeploymentTarget(id -> true);
        assertThat(target.healthCheck("qhorus")).isInstanceOf(HealthCheckResult.Healthy.class);
    }

    @Test
    void healthCheckUnhealthyWhenNotResolvable() {
        var target = new InProcessDeploymentTarget(id -> false);
        assertThat(target.healthCheck("qhorus")).isInstanceOf(HealthCheckResult.Unhealthy.class);
    }
}
