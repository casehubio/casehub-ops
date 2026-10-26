package io.casehub.ops.topology.env;

import io.casehub.ops.testing.env.TargetEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PodmanEnvironmentExtension.class)
class PodmanEnvironmentExtensionTest {

    @Test
    void extensionDoesNotActivateForLocal() {
        System.clearProperty("casehub.topology.target-environment");
        assertThat(TargetEnvironment.current()).isEqualTo(TargetEnvironment.LOCAL);
    }
}
