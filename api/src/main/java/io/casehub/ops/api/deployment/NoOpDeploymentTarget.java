package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeStatus;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@DefaultBean
public class NoOpDeploymentTarget implements ApplicationDeploymentTarget {

    @Override
    public DeployResult deploy(String imageRef, DeployConfig config) {
        return new DeployResult.Failed("No ApplicationDeploymentTarget configured — add an implementation to the classpath");
    }

    @Override
    public DeployResult undeploy(String instanceId) {
        return new DeployResult.Failed("No ApplicationDeploymentTarget configured");
    }

    @Override
    public NodeStatus status(String instanceId) {
        return NodeStatus.UNKNOWN;
    }

    @Override
    public HealthCheckResult healthCheck(String instanceId) {
        return new HealthCheckResult.Unknown(instanceId);
    }
}
