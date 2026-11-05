package io.casehub.ops.topology.env;

import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.testing.dimension.ContainerSpec;
import io.casehub.ops.testing.dimension.DeployDimension;
import io.casehub.ops.testing.dimension.DimensionTest;
import io.casehub.ops.testing.dimension.FleetDimension;
import io.casehub.ops.testing.env.TargetEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;

import java.util.Map;

@DimensionTest(
        containers = {
                @ContainerSpec(
                        name = "fleet-analyst",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"llm.capability=deep", "llm.role=analyst"}),
                @ContainerSpec(
                        name = "fleet-coder",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"llm.capability=fast", "llm.role=developer"}),
                @ContainerSpec(
                        name = "fleet-assistant",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"llm.capability=balanced", "llm.role=assistant"})
        },
        dimensions = {DeployDimension.class, FleetDimension.class}
)
@Tag("integration-live")
class Level04DimensionTest {

    static ApplicationDeploymentTarget createTarget() {
        return TargetEnvironmentFactory.createTarget(
                TargetEnvironment.current(), Map.of());
    }

    @TestTemplate
    void dimension() {}
}
