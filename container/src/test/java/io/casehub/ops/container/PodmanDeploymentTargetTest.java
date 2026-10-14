package io.casehub.ops.container;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
import io.casehub.ops.container.podman.PodmanClient;
import io.casehub.ops.container.podman.PodmanContainer;
import io.casehub.ops.container.podman.PodmanNetwork;
import io.casehub.ops.container.podman.PodmanVolume;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PodmanDeploymentTargetTest {

    @Test
    void targetIdIsPodman() {
        var target = new PodmanDeploymentTarget(new RecordingClient());
        assertThat(target.targetId()).isEqualTo("podman");
    }

    @Test
    void deployPullsCreatesAndStarts() {
        var client = new RecordingClient();
        var target = new PodmanDeploymentTarget(client);
        var config = new DeployConfig("qhorus", 1, Map.of("quarkus.http.port", "8081"), Map.of());
        var result = target.deploy("casehubio/qhorus:0.5", config);

        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(((DeployResult.Success) result).instanceId()).isEqualTo("new-id");
        assertThat(client.pulledImages).contains("casehubio/qhorus:0.5");
        assertThat(client.createdContainers).hasSize(1);
        assertThat(client.startedContainers).contains("new-id");
    }

    @Test
    void statusReturnsPresentWhenContainerRunning() {
        var client = new RecordingClient();
        client.existingContainers.add(new PodmanContainer("c1", List.of("qhorus"), "running", "casehubio/qhorus:0.5"));
        var target = new PodmanDeploymentTarget(client);
        assertThat(target.status("qhorus")).isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void statusReturnsAbsentWhenNoContainer() {
        var target = new PodmanDeploymentTarget(new RecordingClient());
        assertThat(target.status("qhorus")).isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void statusReturnsDriftedWhenContainerNotRunning() {
        var client = new RecordingClient();
        client.existingContainers.add(new PodmanContainer("c1", List.of("qhorus"), "exited", "casehubio/qhorus:0.5"));
        var target = new PodmanDeploymentTarget(client);
        assertThat(target.status("qhorus")).isEqualTo(NodeStatus.DRIFTED);
    }

    @Test
    void undeployStopsAndRemoves() {
        var client = new RecordingClient();
        var target = new PodmanDeploymentTarget(client);
        var result = target.undeploy("container-123");
        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(client.stoppedContainers).contains("container-123");
        assertThat(client.removedContainers).contains("container-123");
    }

    @Test
    void healthCheckHealthyWhenRunning() {
        var client = new RecordingClient();
        client.existingContainers.add(new PodmanContainer("c1", List.of("qhorus"), "running", "casehubio/qhorus:0.5"));
        var target = new PodmanDeploymentTarget(client);
        assertThat(target.healthCheck("qhorus")).isInstanceOf(HealthCheckResult.Healthy.class);
    }

    static class RecordingClient extends PodmanClient {
        final List<String> pulledImages = new ArrayList<>();
        final List<PodmanClient.CreateContainerRequest> createdContainers = new ArrayList<>();
        final List<String> startedContainers = new ArrayList<>();
        final List<String> stoppedContainers = new ArrayList<>();
        final List<String> removedContainers = new ArrayList<>();
        final List<PodmanContainer> existingContainers = new ArrayList<>();

        RecordingClient() { super(null, null); }

        @Override public Uni<Void> pullImage(String image) {
            pulledImages.add(image); return Uni.createFrom().voidItem();
        }
        @Override public Uni<String> createContainer(CreateContainerRequest req) {
            createdContainers.add(req); return Uni.createFrom().item("new-id");
        }
        @Override public Uni<Void> startContainer(String id) {
            startedContainers.add(id); return Uni.createFrom().voidItem();
        }
        @Override public Uni<Void> stopContainer(String id) {
            stoppedContainers.add(id); return Uni.createFrom().voidItem();
        }
        @Override public Uni<Void> removeContainer(String id) {
            removedContainers.add(id); return Uni.createFrom().voidItem();
        }
        @Override public Uni<List<PodmanContainer>> listContainers() {
            return Uni.createFrom().item(List.copyOf(existingContainers));
        }
    }
}
