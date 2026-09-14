package io.casehub.ops.example.monitoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentMonitoringExampleTest {

    @Test
    void scenarioProducesAnomalies() throws IOException {
        var result = DeploymentMonitoringExample.run();
        assertThat(result.anomalies()).isNotEmpty();
    }

    @Test
    void scenarioProducesPhaseTransitions() throws IOException {
        var result = DeploymentMonitoringExample.run();
        assertThat(result.phases()).isNotEmpty();
        assertThat(result.phases()).anyMatch(p -> "DEGRADED".equals(p.get("to")));
    }

    @Test
    void anomaliesIncludeProvisionFailures() throws IOException {
        var result = DeploymentMonitoringExample.run();
        var provisionFailures = result.anomalies().stream()
                .filter(a -> "PROVISION_FAILURE".equals(a.get("category")))
                .toList();
        assertThat(provisionFailures).isNotEmpty();
    }

    @Test
    void anomaliesIncludeStabilisingEvents() throws IOException {
        var result = DeploymentMonitoringExample.run();
        var stabilising = result.anomalies().stream()
                .filter(a -> "STABILISING".equals(a.get("category")))
                .toList();
        assertThat(stabilising).isNotEmpty();
    }
}
