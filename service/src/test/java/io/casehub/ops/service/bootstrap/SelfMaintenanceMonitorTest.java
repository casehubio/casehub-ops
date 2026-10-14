package io.casehub.ops.service.bootstrap;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

class SelfMaintenanceMonitorTest {

    @Test
    void notActiveInSeedPhase() {
        var coordinator = new BootstrapPhaseCoordinator();
        var target = new TestTarget();
        var monitor = new SelfMaintenanceMonitor(coordinator, target, true);
        assertThat(monitor.isActive()).isFalse();
    }

    @Test
    void activeInSelfAwarePhase() {
        var coordinator = transitionedCoordinator();
        var target = new TestTarget();
        var monitor = new SelfMaintenanceMonitor(coordinator, target, true);
        assertThat(monitor.isActive()).isTrue();
    }

    @Test
    void disabledWhenConfigFalse() {
        var coordinator = transitionedCoordinator();
        var target = new TestTarget();
        var monitor = new SelfMaintenanceMonitor(coordinator, target, false);
        assertThat(monitor.isActive()).isFalse();
    }

    @Test
    void checkDoesNothingInSeedPhase() {
        var coordinator = new BootstrapPhaseCoordinator();
        var target = new TestTarget();
        var monitor = new SelfMaintenanceMonitor(coordinator, target, true);
        monitor.check("ops-app", "casehubio/casehub-ops-app:2.0");
        assertThat(target.deployed).isEmpty();
    }

    @Test
    void checkDeploysReplacementWhenActive() {
        var coordinator = transitionedCoordinator();
        var target = new TestTarget();
        var monitor = new SelfMaintenanceMonitor(coordinator, target, true);
        monitor.check("ops-app", "casehubio/casehub-ops-app:2.0");
        assertThat(target.deployed).containsKey("ops-app-next-instance");
    }

    private BootstrapPhaseCoordinator transitionedCoordinator() {
        var coordinator = new BootstrapPhaseCoordinator();
        coordinator.markDependencyHealthy("qhorus");
        coordinator.markDependencyHealthy("eidos");
        coordinator.markDependencyHealthy("engine");
        coordinator.evaluatePhaseTransition(Set.of("qhorus", "eidos", "engine"));
        return coordinator;
    }

    private static class TestTarget implements ApplicationDeploymentTarget {
        final Map<String, String> deployed = new ConcurrentHashMap<>();

        @Override public DeployResult deploy(String imageRef, DeployConfig config) {
            String id = config.applicationId() + "-instance";
            deployed.put(id, imageRef);
            return new DeployResult.Success(id);
        }
        @Override public DeployResult undeploy(String instanceId) {
            deployed.remove(instanceId);
            return new DeployResult.Success(instanceId);
        }
        @Override public NodeStatus status(String instanceId) {
            return deployed.containsKey(instanceId) ? NodeStatus.PRESENT : NodeStatus.ABSENT;
        }
        @Override public HealthCheckResult healthCheck(String instanceId) {
            return deployed.containsKey(instanceId)
                ? new HealthCheckResult.Healthy(instanceId)
                : new HealthCheckResult.Unknown(instanceId);
        }
    }
}
