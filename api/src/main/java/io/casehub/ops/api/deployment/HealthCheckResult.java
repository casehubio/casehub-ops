package io.casehub.ops.api.deployment;

public sealed interface HealthCheckResult {
    record Healthy(String instanceId) implements HealthCheckResult {}
    record Unhealthy(String instanceId, String reason) implements HealthCheckResult {}
    record Unknown(String instanceId) implements HealthCheckResult {}
}
