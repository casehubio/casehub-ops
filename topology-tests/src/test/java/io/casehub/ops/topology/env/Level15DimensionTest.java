package io.casehub.ops.topology.env;

import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.testing.dimension.ChannelsDimension;
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
                        name = "coordinator",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"role=coordinator", "llm.capability=deep", "llm.role=coordinator"}),
                @ContainerSpec(
                        name = "reviewer-1",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"role=reviewer", "llm.capability=deep", "llm.role=reviewer"}),
                @ContainerSpec(
                        name = "reviewer-2",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"role=reviewer", "llm.capability=deep", "llm.role=reviewer"})
        },
        dimensions = {DeployDimension.class, FleetDimension.class, ChannelsDimension.class}
)
@Tag("integration-live")
class Level15DimensionTest {

    static ApplicationDeploymentTarget createTarget() {
        return TargetEnvironmentFactory.createTarget(
                TargetEnvironment.current(), Map.of());
    }

    @TestTemplate
    void dimension() {}
}
