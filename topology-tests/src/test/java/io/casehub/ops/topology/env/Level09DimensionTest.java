package io.casehub.ops.topology.env;

import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.testing.dimension.ChannelsDimension;
import io.casehub.ops.testing.dimension.ContainerSpec;
import io.casehub.ops.testing.dimension.DeployDimension;
import io.casehub.ops.testing.dimension.DimensionTest;
import io.casehub.ops.testing.dimension.PlatformDimension;
import io.casehub.ops.testing.env.TargetEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;

import java.util.Map;

@DimensionTest(
        containers = {
                @ContainerSpec(
                        name = "e2e-qhorus",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"role=qhorus"}),
                @ContainerSpec(
                        name = "e2e-chat-viewer",
                        image = "localhost/casehubio/casehub-runtime:latest",
                        env = {"role=chat-viewer", "chat.mode=conversation-viewer"})
        },
        dimensions = {DeployDimension.class, PlatformDimension.class, ChannelsDimension.class}
)
@Tag("integration-live")
class Level09DimensionTest {

    static ApplicationDeploymentTarget createTarget() {
        return TargetEnvironmentFactory.createTarget(
                TargetEnvironment.current(), Map.of());
    }

    @TestTemplate
    void dimension() {}
}
