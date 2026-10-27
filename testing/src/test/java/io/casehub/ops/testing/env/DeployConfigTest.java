package io.casehub.ops.testing.env;

import io.casehub.ops.api.deployment.DeployConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeployConfigTest {

    @Test
    void healthPath_defaultsToQuarkusReady() {
        var config = new DeployConfig("app", 1, Map.of(), Map.of());
        assertThat(config.healthPath()).isEqualTo("/q/health/ready");
    }

    @Test
    void healthPath_preservesExplicitValue() {
        var config = new DeployConfig("app", 1, Map.of(), Map.of(), "/health");
        assertThat(config.healthPath()).isEqualTo("/health");
    }

    @Test
    void healthPath_blankFallsBackToDefault() {
        var config = new DeployConfig("app", 1, Map.of(), Map.of(), "  ");
        assertThat(config.healthPath()).isEqualTo("/q/health/ready");
    }

    @Test
    void fourArgConstructor_remainsBackwardsCompatible() {
        var config = new DeployConfig("app", 2, Map.of("k", "v"), Map.of("E", "V"));
        assertThat(config.applicationId()).isEqualTo("app");
        assertThat(config.replicas()).isEqualTo(2);
        assertThat(config.config()).containsEntry("k", "v");
        assertThat(config.env()).containsEntry("E", "V");
        assertThat(config.healthPath()).isEqualTo("/q/health/ready");
    }
}
