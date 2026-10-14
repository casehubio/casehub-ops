package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeStatus;

public interface ApplicationDeploymentTarget {

    default String targetId() {return "";}

    DeployResult deploy(String imageRef, DeployConfig config);
    DeployResult undeploy(String instanceId);
    NodeStatus status(String instanceId);
    HealthCheckResult healthCheck(String instanceId);
}
