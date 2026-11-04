package io.casehub.ops.topology.env;

import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.testing.dimension.ContainerSpec;
import io.casehub.ops.testing.dimension.DeployDimension;
import io.casehub.ops.testing.dimension.DimensionTest;
import io.casehub.ops.testing.dimension.LlmDimension;
import io.casehub.ops.testing.dimension.PlatformDimension;
import io.casehub.ops.testing.env.TargetEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;

import java.util.Map;

@DimensionTest(
        containers = @ContainerSpec(
                name = "e2e-hello-llm",
                image = "localhost/casehubio/casehub-runtime:latest",
                env = {"llm.capability=fast"}),
        dimensions = {DeployDimension.class, PlatformDimension.class, LlmDimension.class}
)
@Tag("integration-live")
class Level01DimensionTest {

    static ApplicationDeploymentTarget createTarget() {
        return TargetEnvironmentFactory.createTarget(
                TargetEnvironment.current(), Map.of());
    }

    @TestTemplate
    void dimension() {}
}
