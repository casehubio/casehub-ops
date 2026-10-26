package io.casehub.ops.topology.env;

import io.casehub.desiredstate.api.NodeType;
import io.casehub.ops.testing.env.TargetEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TargetEnvironmentFactoryTest {

    @Test
    void createTarget_local_returnsClaudonyTarget() {
        var target = TargetEnvironmentFactory.createTarget(
            TargetEnvironment.LOCAL, Map.of("claudony", "http://localhost:8080"));
        assertThat(target).isNotNull();
    }

    @Test
    void createTarget_podman_returnsPodmanTarget() {
        var target = TargetEnvironmentFactory.createTarget(
            TargetEnvironment.PODMAN, Map.of());
        assertThat(target.targetId()).isEqualTo("podman");
    }

    @Test
    void createProvisioner_handlesApplicationNodes() {
        var provisioner = TargetEnvironmentFactory.createProvisioner(
            TargetEnvironment.LOCAL, Map.of("claudony", "http://localhost:8080"));
        assertThat(provisioner.handledTypes())
            .containsExactly(NodeType.of("casehub_application"));
    }
}
