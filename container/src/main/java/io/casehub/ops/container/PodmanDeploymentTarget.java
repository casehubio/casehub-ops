package io.casehub.ops.container;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
import io.casehub.ops.container.podman.PodmanClient;
import java.util.HashMap;

public class PodmanDeploymentTarget implements ApplicationDeploymentTarget {

    private final PodmanClient podmanClient;

    public PodmanDeploymentTarget(PodmanClient podmanClient) {
        this.podmanClient = podmanClient;
    }

    @Override
    public String targetId() { return "podman"; }

    @Override
    public DeployResult deploy(String imageRef, DeployConfig config) {
        try {
            podmanClient.pullImage(imageRef).await().indefinitely();
            var env = new HashMap<String, String>();
            config.config().forEach((k, v) -> env.put(k, String.valueOf(v)));
            var request = new PodmanClient.CreateContainerRequest(
                    config.applicationId(), imageRef, java.util.Map.of(), env);
            var containerId = podmanClient.createContainer(request).await().indefinitely();
            podmanClient.startContainer(containerId).await().indefinitely();
            return new DeployResult.Success(containerId);
        } catch (Exception e) {
            return new DeployResult.Failed("Podman deploy failed: " + e.getMessage());
        }
    }

    @Override
    public DeployResult undeploy(String instanceId) {
        try {
            podmanClient.stopContainer(instanceId).await().indefinitely();
            podmanClient.removeContainer(instanceId).await().indefinitely();
            return new DeployResult.Success(instanceId);
        } catch (Exception e) {
            return new DeployResult.Failed("Podman undeploy failed: " + e.getMessage());
        }
    }

    @Override
    public NodeStatus status(String instanceId) {
        try {
            var containers = podmanClient.listContainers().await().indefinitely();
            for (var c : containers) {
                if (c.names().contains(instanceId) || c.id().equals(instanceId)) {
                    return "running".equals(c.state()) ? NodeStatus.PRESENT : NodeStatus.DRIFTED;
                }
            }
            return NodeStatus.ABSENT;
        } catch (Exception e) {
            return NodeStatus.UNKNOWN;
        }
    }

    @Override
    public HealthCheckResult healthCheck(String instanceId) {
        var nodeStatus = status(instanceId);
        return nodeStatus == NodeStatus.PRESENT
            ? new HealthCheckResult.Healthy(instanceId)
            : new HealthCheckResult.Unhealthy(instanceId, "Container not running");
    }
}
