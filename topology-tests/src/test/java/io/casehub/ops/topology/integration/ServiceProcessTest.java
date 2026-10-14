package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceProcessTest {

    @Test
    void resolveJar_missingJar_throwsWithActionableMessage() {
        assertThatThrownBy(() -> ServiceProcess.resolveJar("nonexistent/app"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quarkus-run.jar")
                .hasMessageContaining("mvn");
    }

    @Test
    void findFreePort_returnsPortInValidRange() {
        int port = ServiceProcess.findFreePort();
        assertThat(port).isBetween(1024, 65535);
    }

    @Test
    void findFreePort_returnsDifferentPortsOnSubsequentCalls() {
        int port1 = ServiceProcess.findFreePort();
        int port2 = ServiceProcess.findFreePort();
        assertThat(port1).isNotEqualTo(port2);
    }
}
