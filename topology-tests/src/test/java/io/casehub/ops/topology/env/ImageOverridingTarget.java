package io.casehub.ops.topology.env;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;

public class ImageOverridingTarget implements ApplicationDeploymentTarget {

    private final ApplicationDeploymentTarget delegate;
    private final String imageOverride;

    public ImageOverridingTarget(ApplicationDeploymentTarget delegate, String imageOverride) {
        this.delegate = delegate;
        this.imageOverride = imageOverride;
    }

    @Override
    public String targetId() { return delegate.targetId(); }

    @Override
    public DeployResult deploy(String imageRef, DeployConfig config) {
        return delegate.deploy(imageOverride, config);
    }

    @Override
    public DeployResult undeploy(String instanceId) {
        return delegate.undeploy(instanceId);
    }

    @Override
    public NodeStatus status(String instanceId) {
        return delegate.status(instanceId);
    }

    @Override
    public HealthCheckResult healthCheck(String instanceId) {
        return delegate.healthCheck(instanceId);
    }

    @Override
    public boolean supportsStatefulLifecycle() {
        return delegate.supportsStatefulLifecycle();
    }
}
