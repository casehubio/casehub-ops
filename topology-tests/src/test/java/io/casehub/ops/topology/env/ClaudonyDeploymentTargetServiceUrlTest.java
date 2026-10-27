package io.casehub.ops.topology.env;

import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyDeploymentTargetServiceUrlTest {

    @Test
    void serviceUrl_returnsEmptyWithoutRegistration() {
        var target = new ClaudonyDeploymentTarget(null);
        assertThat(target.serviceUrl("my-app")).isEmpty();
    }

    @Test
    void serviceUrl_returnsRegisteredUrl() {
        var target = new ClaudonyDeploymentTarget(null);
        target.registerServiceUrl("my-app", "http://localhost:9090");
        assertThat(target.serviceUrl("my-app"))
            .isPresent()
            .hasValue("http://localhost:9090");
    }

    @Test
    void serviceUrl_tracksMultipleApps() {
        var target = new ClaudonyDeploymentTarget(null);
        target.registerServiceUrl("app-a", "http://localhost:9001");
        target.registerServiceUrl("app-b", "http://localhost:9002");
        assertThat(target.serviceUrl("app-a")).hasValue("http://localhost:9001");
        assertThat(target.serviceUrl("app-b")).hasValue("http://localhost:9002");
    }
}
