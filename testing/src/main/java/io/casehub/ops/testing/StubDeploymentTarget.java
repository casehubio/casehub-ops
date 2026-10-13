package io.casehub.ops.testing;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StubDeploymentTarget implements ApplicationDeploymentTarget {

    private final Map<String, String> deployed = new ConcurrentHashMap<>();

    @Override
    public DeployResult deploy(String imageRef, DeployConfig config) {
        String instanceId = config.applicationId() + "-instance";
        deployed.put(instanceId, imageRef);
        return new DeployResult.Success(instanceId);
    }

    @Override
    public DeployResult undeploy(String instanceId) {
        deployed.remove(instanceId);
        return new DeployResult.Success(instanceId);
    }

    @Override
    public NodeStatus status(String instanceId) {
        return deployed.containsKey(instanceId) ? NodeStatus.PRESENT : NodeStatus.ABSENT;
    }

    @Override
    public HealthCheckResult healthCheck(String instanceId) {
        if (deployed.containsKey(instanceId)) {
            return new HealthCheckResult.Healthy(instanceId);
        }
        return new HealthCheckResult.Unknown(instanceId);
    }

    public Map<String, String> deployedInstances() {
        return Map.copyOf(deployed);
    }
}
