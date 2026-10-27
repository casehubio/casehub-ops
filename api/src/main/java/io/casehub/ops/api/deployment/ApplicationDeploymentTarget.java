package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeStatus;

public interface ApplicationDeploymentTarget extends AutoCloseable {

    default String targetId() {return "";}

    DeployResult deploy(String imageRef, DeployConfig config);

    DeployResult undeploy(String instanceId);

    NodeStatus status(String instanceId);

    HealthCheckResult healthCheck(String instanceId);

    default DeployResult suspend(String instanceId) {
        return new DeployResult.Failed("suspend not supported by " + targetId());
    }

    default DeployResult resume(String instanceId) {
        return new DeployResult.Failed("resume not supported by " + targetId());
    }

    default boolean supportsStatefulLifecycle() {return false;}

    default java.util.Optional<String> serviceUrl(String instanceId) {
        return java.util.Optional.empty();
    }

    @Override
    default void close() {}
}
