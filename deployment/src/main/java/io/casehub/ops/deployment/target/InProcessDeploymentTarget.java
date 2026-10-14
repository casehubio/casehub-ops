package io.casehub.ops.deployment.target;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
public class InProcessDeploymentTarget implements ApplicationDeploymentTarget {

    private final BeanRegistry beanRegistry;

    public InProcessDeploymentTarget(BeanRegistry beanRegistry) {
        this.beanRegistry = beanRegistry;
    }

    @Override
    public String targetId() { return "in-process"; }

    @Override
    public DeployResult deploy(String imageRef, DeployConfig config) {
        if (!beanRegistry.isResolvable(config.applicationId())) {
            return new DeployResult.Failed(
                "In-process deployment failed: no CDI beans found for applicationId '"
                + config.applicationId() + "'. Ensure the service JAR is on the classpath.");
        }
        return new DeployResult.Success(config.applicationId());
    }

    @Override
    public DeployResult undeploy(String instanceId) {
        return new DeployResult.Success(instanceId);
    }

    @Override
    public NodeStatus status(String instanceId) {
        return beanRegistry.isResolvable(instanceId) ? NodeStatus.PRESENT : NodeStatus.ABSENT;
    }

    @Override
    public HealthCheckResult healthCheck(String instanceId) {
        return beanRegistry.isResolvable(instanceId)
            ? new HealthCheckResult.Healthy(instanceId)
            : new HealthCheckResult.Unhealthy(instanceId, "CDI beans not resolvable");
    }

    @FunctionalInterface
    public interface BeanRegistry {
        boolean isResolvable(String applicationId);
    }
}
