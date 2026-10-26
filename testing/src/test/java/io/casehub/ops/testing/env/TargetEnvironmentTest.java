package io.casehub.ops.testing.env;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetEnvironmentTest {

    @Test
    void current_defaultsToLocal() {
        System.clearProperty("casehub.topology.target-environment");
        assertThat(TargetEnvironment.current()).isEqualTo(TargetEnvironment.LOCAL);
    }

    @Test
    void current_readsK8s() {
        System.setProperty("casehub.topology.target-environment", "k8s");
        try {
            assertThat(TargetEnvironment.current()).isEqualTo(TargetEnvironment.K8S);
        } finally {
            System.clearProperty("casehub.topology.target-environment");
        }
    }

    @Test
    void current_readsPodman() {
        System.setProperty("casehub.topology.target-environment", "podman");
        try {
            assertThat(TargetEnvironment.current()).isEqualTo(TargetEnvironment.PODMAN);
        } finally {
            System.clearProperty("casehub.topology.target-environment");
        }
    }
}
