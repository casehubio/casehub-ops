package io.casehub.ops.service.target;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
public class KubernetesDeploymentTarget implements ApplicationDeploymentTarget {

    @Override
    public String targetId() { return "kubernetes"; }

    @Override
    public DeployResult deploy(String imageRef, DeployConfig config) {
        return new DeployResult.Failed(
            "Kubernetes deployment target not yet implemented — requires fabric8 client wiring (#126)");
    }

    @Override
    public DeployResult undeploy(String instanceId) {
        return new DeployResult.Failed("Kubernetes deployment target not yet implemented");
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
