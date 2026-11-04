package io.casehub.ops.testing.dimension;

import io.casehub.ops.testing.StubDeploymentTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceUrlsTest {

    @Test
    void resolvesServiceByName() {
        var stub = new StubDeploymentTarget();
        stub.setServiceUrl("platform", "http://localhost:9090");
        var urls = new ServiceUrls(stub);
        assertThat(urls.serviceUrl("platform")).isEqualTo("http://localhost:9090");
    }

    @Test
    void resolvesMultipleServices() {
        var stub = new StubDeploymentTarget();
        stub.setServiceUrl("platform", "http://localhost:9090");
        stub.setServiceUrl("qhorus", "http://localhost:9091");
        var urls = new ServiceUrls(stub);
        assertThat(urls.serviceUrl("platform")).isEqualTo("http://localhost:9090");
        assertThat(urls.serviceUrl("qhorus")).isEqualTo("http://localhost:9091");
    }

    @Test
    void throwsWhenNoServiceUrl() {
        var stub = new StubDeploymentTarget();
        var urls = new ServiceUrls(stub);
        assertThatThrownBy(() -> urls.serviceUrl("missing"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing");
    }
}
