package io.casehub.ops.topology.env;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.container.PodmanDeploymentTarget;
import io.casehub.ops.container.podman.PodmanClient;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
class PodmanDeploymentTargetLiveTest {

    static PodmanDeploymentTarget target;
    static Vertx vertx;

    @BeforeAll
    static void setUp() {
        String socketPath = System.getProperty(
            "casehub.container.podman.socket",
            "/var/folders/8m/0v940n7x1m5gy95zvnl3ms700000gn/T/podman/podman-machine-default-api.sock");
        vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
        WebClient webClient = WebClient.create(vertx);
        PodmanClient podmanClient = new PodmanClient(webClient,
            SocketAddress.domainSocketAddress(socketPath));
        target = new PodmanDeploymentTarget(podmanClient);
    }

    @AfterAll
    static void tearDown() {
        for (String name : List.of("test-a", "test-b", "test-c", "test-d")) {
            target.undeploy(name);
        }
        if (vertx != null) vertx.close();
    }

    @Test
    void deploysFourContainersAndChecksStatus() {
        for (String name : List.of("test-a", "test-b", "test-c", "test-d")) {
            var config = new DeployConfig(name, 1, Map.of(), Map.of());
            DeployResult result = target.deploy("nginx:alpine", config);
            System.out.printf("  deploy(%s) → %s%n", name, result);
            assertThat(result)
                .as("Deploy %s", name)
                .isInstanceOf(DeployResult.Success.class);
        }

        for (String name : List.of("test-a", "test-b", "test-c", "test-d")) {
            NodeStatus status = target.status(name);
            System.out.printf("  status(%s) → %s%n", name, status);
            assertThat(status)
                .as("Status of %s", name)
                .isNotEqualTo(NodeStatus.ABSENT);
        }
    }
}
