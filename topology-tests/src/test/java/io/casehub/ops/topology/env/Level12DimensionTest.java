package io.casehub.ops.topology.env;

import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.testing.dimension.AdaptationDimension;
import io.casehub.ops.testing.dimension.ContainerSpec;
import io.casehub.ops.testing.dimension.DeployDimension;
import io.casehub.ops.testing.dimension.DimensionTest;
import io.casehub.ops.testing.env.TargetEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;

import java.util.Map;

@DimensionTest(
        containers = {
                @ContainerSpec(
                        name = "deployer",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"llm.role=deployer", "llm.capability=deployment-decision"})
        },
        dimensions = {DeployDimension.class, AdaptationDimension.class}
)
@Tag("integration-live")
class Level12DimensionTest {

    static ApplicationDeploymentTarget createTarget() {
        return TargetEnvironmentFactory.createTarget(
                TargetEnvironment.current(), Map.of());
    }

    @TestTemplate
    void dimension() {}
}
