package io.casehub.ops.api.deployment;

public sealed interface DeployResult {
    record Success(String instanceId) implements DeployResult {}
    record Failed(String reason) implements DeployResult {}
}
