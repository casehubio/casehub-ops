package io.casehub.ops.deployment.handler;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.*;
import io.casehub.ops.testing.StubDeploymentTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CaseHubAppProvisionHandlerTest {

    private PrebuiltImagePipeline buildPipeline;
    private StubDeploymentTarget deployTarget;
    private CaseHubAppProvisionHandler handler;

    @BeforeEach
    void setUp() {
        buildPipeline = new PrebuiltImagePipeline();
        deployTarget = new StubDeploymentTarget();
        handler = new CaseHubAppProvisionHandler(buildPipeline, deployTarget, new io.casehub.ops.deployment.InstanceIdStore(), null);
    }

    @Test
    void provisionWithPrebuiltImage() {
        var spec = new CaseHubApplicationSpec(
            "incident-app", null, "ghcr.io/acme/incident:1.0",
            new RuntimeConfig("0.2", List.of("a2a")),
            Map.of(), List.of(), 1);
        var graph = new DefaultDesiredStateGraphFactory().empty();
        var result = handler.provision(spec, new ProvisionContext("tenant-1", graph));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(deployTarget.deployedInstances()).containsKey("incident-app-instance");
        assertThat(deployTarget.deployedInstances().get("incident-app-instance"))
            .isEqualTo("ghcr.io/acme/incident:1.0");
    }

    @Test
    void provisionFailsWhenBuildFails() {
        var spec = new CaseHubApplicationSpec(
            "source-app", "./apps/test", null,
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        var graph = new DefaultDesiredStateGraphFactory().empty();
        var result = handler.provision(spec, new ProvisionContext("tenant-1", graph));

        assertThat(result).isInstanceOf(ProvisionResult.Failed.class);
        assertThat(((ProvisionResult.Failed) result).reason()).contains("no image");
    }

    @Test
    void deprovisionRemovesInstance() {
        var spec = new CaseHubApplicationSpec(
            "incident-app", null, "ghcr.io/acme/incident:1.0",
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        var graph = new DefaultDesiredStateGraphFactory().empty();

        handler.provision(spec, new ProvisionContext("tenant-1", graph));
        assertThat(deployTarget.deployedInstances()).isNotEmpty();

        var result = handler.deprovision(spec, new DeprovisionContext("tenant-1", graph));
        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
        assertThat(deployTarget.deployedInstances()).isEmpty();
    }
}
