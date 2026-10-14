package io.casehub.ops.service.target;

import io.casehub.ops.api.deployment.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class KubernetesDeploymentTargetTest {

    @Test
    void targetIdIsKubernetes() {
        var target = new KubernetesDeploymentTarget();
        assertThat(target.targetId()).isEqualTo("kubernetes");
    }

    @Test
    void deployReturnsNotImplemented() {
        var target = new KubernetesDeploymentTarget();
        var config = new DeployConfig("qhorus", 1, Map.of(), Map.of());
        var result = target.deploy("casehubio/qhorus:0.5", config);
        assertThat(result).isInstanceOf(DeployResult.Failed.class);
        assertThat(((DeployResult.Failed) result).reason()).contains("not yet implemented");
    }

    @Test
    void undeployReturnsNotImplemented() {
        var target = new KubernetesDeploymentTarget();
        var result = target.undeploy("qhorus-instance");
        assertThat(result).isInstanceOf(DeployResult.Failed.class);
    }
}
