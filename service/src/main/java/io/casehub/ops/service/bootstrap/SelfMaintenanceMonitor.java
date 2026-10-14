package io.casehub.ops.service.bootstrap;

import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
import org.jboss.logging.Logger;

import java.util.Map;

public class SelfMaintenanceMonitor {

    private static final Logger LOG = Logger.getLogger(SelfMaintenanceMonitor.class);

    private final BootstrapPhaseCoordinator coordinator;
    private final ApplicationDeploymentTarget deployTarget;
    private final boolean enabled;

    public SelfMaintenanceMonitor(
            BootstrapPhaseCoordinator coordinator,
            ApplicationDeploymentTarget deployTarget,
            boolean enabled) {
        this.coordinator = coordinator;
        this.deployTarget = deployTarget;
        this.enabled = enabled;
    }

    public boolean isActive() {
        return enabled && coordinator.currentPhase() == BootstrapPhase.SELF_AWARE;
    }

    public void check(String applicationId, String newImageRef) {
        if (!isActive()) return;

        LOG.infof("Self-maintenance: upgrading %s to %s", applicationId, newImageRef);
        var config = new DeployConfig(applicationId + "-next", 1, Map.of(), Map.of());
        var result = deployTarget.deploy(newImageRef, config);

        if (result instanceof DeployResult.Failed f) {
            LOG.errorf("Self-maintenance: deploy failed for %s — %s", applicationId, f.reason());
            return;
        }

        var instanceId = ((DeployResult.Success) result).instanceId();
        var health = deployTarget.healthCheck(instanceId);

        if (health instanceof HealthCheckResult.Unhealthy u) {
            LOG.errorf("Self-maintenance: health check failed for %s — %s. Rolling back.", applicationId, u.reason());
            deployTarget.undeploy(instanceId);
            return;
        }

        LOG.infof("Self-maintenance: replacement %s healthy. Old instance should deactivate gracefully.", instanceId);
    }
}
