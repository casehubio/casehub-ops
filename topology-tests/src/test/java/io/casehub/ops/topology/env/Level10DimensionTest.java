package io.casehub.ops.topology.env;

import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.testing.dimension.ContainerSpec;
import io.casehub.ops.testing.dimension.DeployDimension;
import io.casehub.ops.testing.dimension.DimensionTest;
import io.casehub.ops.testing.dimension.PoolDimension;
import io.casehub.ops.testing.env.TargetEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;

import java.util.Map;

@DimensionTest(
        containers = {
                @ContainerSpec(
                        name = "support-app",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"role=support", "support.mode=pool-managed", "support.channel=direct"})
        },
        dimensions = {DeployDimension.class, PoolDimension.class}
)
@Tag("integration-live")
class Level10DimensionTest {

    static ApplicationDeploymentTarget createTarget() {
        return TargetEnvironmentFactory.createTarget(
                TargetEnvironment.current(), Map.of());
    }

    @TestTemplate
    void dimension() {}
}
