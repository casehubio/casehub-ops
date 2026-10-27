package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
import io.casehub.platform.api.registry.HealthStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@Named("claudony")
public class ClaudonyDeploymentTarget implements ApplicationDeploymentTarget {

    private final ClaudonyFleetClient fleetClient;
    private final Map<String, String> serviceUrls = new java.util.concurrent.ConcurrentHashMap<>();


    @Inject
    public ClaudonyDeploymentTarget(final ClaudonyFleetClient fleetClient) {
        this.fleetClient = fleetClient;
    }

    @Override
    public String targetId() { return "claudony"; }

    @Override
    public boolean supportsStatefulLifecycle() { return true; }

    @Override
    public DeployResult deploy(final String imageRef, final DeployConfig config) {
        ClaudonyAppSpec spec = toAppSpec(imageRef, config);
        try {
            fleetClient.registerApp(spec);
            return new DeployResult.Success(config.applicationId());
        } catch (ClaudonyApiException e) {
            if (e.statusCode() == 409) {
                return deployAsUpdate(config.applicationId(), spec);
            }
            return new DeployResult.Failed("Claudony register failed (" + e.statusCode() + "): " + e.responseBody());
        }
    }

    @Override
    public DeployResult undeploy(final String instanceId) {
        try {
            fleetClient.deregisterApp(instanceId);
            return new DeployResult.Success(instanceId);
        } catch (ClaudonyApiException e) {
            if (e.statusCode() == 404) {
                return new DeployResult.Success(instanceId);
            }
            return new DeployResult.Failed("Claudony deregister failed: " + e.getMessage());
        }
    }

    @Override
    public NodeStatus status(final String instanceId) {
        return fleetClient.getApp(instanceId)
                .map(info -> mapHealth(info.health()))
                .orElse(NodeStatus.ABSENT);
    }

    @Override
    public HealthCheckResult healthCheck(final String instanceId) {
        return fleetClient.getApp(instanceId)
                .map(info -> toHealthCheckResult(instanceId, info.health()))
                .orElse(new HealthCheckResult.Unhealthy(instanceId, "App not found in claudony fleet"));
    }

    @Override
    public DeployResult suspend(final String instanceId) {
        try {
            fleetClient.suspendApp(instanceId);
            return new DeployResult.Success(instanceId);
        } catch (ClaudonyApiException e) {
            return new DeployResult.Failed("Claudony suspend failed: " + e.getMessage());
        }
    }

    @Override
    public DeployResult resume(final String instanceId) {
        try {
            fleetClient.resumeApp(instanceId);
            return new DeployResult.Success(instanceId);
        } catch (ClaudonyApiException e) {
            return new DeployResult.Failed("Claudony resume failed: " + e.getMessage());
        }
    }

    public void registerServiceUrl(String instanceId, String url) {
        serviceUrls.put(instanceId, url);
    }

    @Override
    public java.util.Optional<String> serviceUrl(String instanceId) {
        return java.util.Optional.ofNullable(serviceUrls.get(instanceId));
    }


    private DeployResult deployAsUpdate(final String name, final ClaudonyAppSpec spec) {
        try {
            fleetClient.updateApp(name, spec);
            return new DeployResult.Success(name);
        } catch (ClaudonyApiException e) {
            return new DeployResult.Failed("Claudony update failed: " + e.getMessage());
        }
    }

    private static ClaudonyAppSpec toAppSpec(final String imageRef, final DeployConfig config) {
        Map<String, String> stringConfig = new HashMap<>();
        for (var entry : config.config().entrySet()) {
            stringConfig.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new ClaudonyAppSpec(
                config.applicationId(),
                null,
                config.applicationId(),
                null,
                imageRef,
                Map.copyOf(stringConfig),
                null);
    }

    static NodeStatus mapHealth(final HealthStatus health) {return ClaudonyHealthMapper.toNodeStatus(health);}

    private static HealthCheckResult toHealthCheckResult(final String instanceId, final HealthStatus health) {
        return switch (health) {
            case HEALTHY -> new HealthCheckResult.Healthy(instanceId);
            case DEGRADED -> new HealthCheckResult.Unhealthy(instanceId, "Degraded");
            case DOWN -> new HealthCheckResult.Unhealthy(instanceId, "Down");
            case SUSPENDED -> new HealthCheckResult.Unhealthy(instanceId, "Suspended");
        };
    }
}
