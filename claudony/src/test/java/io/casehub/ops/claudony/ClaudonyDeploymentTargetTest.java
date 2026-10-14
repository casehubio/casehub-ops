package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
import io.casehub.platform.api.registry.HealthStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyDeploymentTargetTest {

    private StubFleetClient client;
    private ClaudonyDeploymentTarget target;

    @BeforeEach
    void setUp() {
        client = new StubFleetClient();
        target = new ClaudonyDeploymentTarget(client);
    }

    @Test
    void targetId() {
        assertThat(target.targetId()).isEqualTo("claudony");
    }

    @Test
    void supportsStatefulLifecycle() {
        assertThat(target.supportsStatefulLifecycle()).isTrue();
    }

    @Test
    void deploy_registers_app_and_returns_name_as_instanceId() {
        client.nextRegisterResult = appInfo("my-app", HealthStatus.HEALTHY);

        DeployConfig config = new DeployConfig("my-app", 2, Map.of("key", "val"), Map.of());
        DeployResult result = target.deploy("my-image:latest", config);

        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(((DeployResult.Success) result).instanceId()).isEqualTo("my-app");

        ClaudonyAppSpec registered = client.lastRegisteredSpec;
        assertThat(registered.name()).isEqualTo("my-app");
        assertThat(registered.image()).isEqualTo("my-image:latest");
    }

    @Test
    void deploy_updates_existing_app() {
        client.registerThrows = new ClaudonyApiException(409, "already exists");
        client.nextUpdateResult = appInfo("my-app", HealthStatus.HEALTHY);

        DeployConfig config = new DeployConfig("my-app", 1, Map.of(), Map.of());
        DeployResult result = target.deploy("img:v2", config);

        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(client.lastUpdatedName).isEqualTo("my-app");
    }

    @Test
    void deploy_propagates_non_conflict_errors() {
        client.registerThrows = new ClaudonyApiException(500, "internal error");

        DeployConfig config = new DeployConfig("my-app", 1, Map.of(), Map.of());
        DeployResult result = target.deploy("img:v1", config);

        assertThat(result).isInstanceOf(DeployResult.Failed.class);
        assertThat(((DeployResult.Failed) result).reason()).contains("500");
    }

    @Test
    void undeploy_deregisters_app() {
        DeployResult result = target.undeploy("my-app");

        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(client.lastDeregisteredName).isEqualTo("my-app");
    }

    @Test
    void undeploy_returns_success_when_not_found() {
        client.deregisterThrows = new ClaudonyApiException(404, "not found");

        DeployResult result = target.undeploy("gone-app");

        assertThat(result).isInstanceOf(DeployResult.Success.class);
    }

    @Test
    void status_maps_healthy_to_present() {
        client.nextGetResult = Optional.of(appInfo("app", HealthStatus.HEALTHY));
        assertThat(target.status("app")).isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void status_maps_degraded_to_degraded() {
        client.nextGetResult = Optional.of(appInfo("app", HealthStatus.DEGRADED));
        assertThat(target.status("app")).isEqualTo(NodeStatus.DRIFTED);
    }

    @Test
    void status_maps_down_to_absent() {
        client.nextGetResult = Optional.of(appInfo("app", HealthStatus.DOWN));
        assertThat(target.status("app")).isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void status_maps_suspended_to_suspended() {
        client.nextGetResult = Optional.of(appInfo("app", HealthStatus.SUSPENDED));
        assertThat(target.status("app")).isEqualTo(NodeStatus.SUSPENDED);
    }

    @Test
    void status_returns_absent_when_app_not_found() {
        client.nextGetResult = Optional.empty();
        assertThat(target.status("missing")).isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void healthCheck_healthy() {
        client.nextGetResult = Optional.of(appInfo("app", HealthStatus.HEALTHY));

        HealthCheckResult result = target.healthCheck("app");

        assertThat(result).isInstanceOf(HealthCheckResult.Healthy.class);
        assertThat(((HealthCheckResult.Healthy) result).instanceId()).isEqualTo("app");
    }

    @Test
    void healthCheck_unhealthy() {
        client.nextGetResult = Optional.of(appInfo("app", HealthStatus.DOWN));

        HealthCheckResult result = target.healthCheck("app");

        assertThat(result).isInstanceOf(HealthCheckResult.Unhealthy.class);
    }

    @Test
    void healthCheck_not_found() {
        client.nextGetResult = Optional.empty();

        HealthCheckResult result = target.healthCheck("missing");

        assertThat(result).isInstanceOf(HealthCheckResult.Unhealthy.class);
    }

    @Test
    void suspend_delegates_to_client() {
        DeployResult result = target.suspend("app");

        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(client.lastSuspendedName).isEqualTo("app");
    }

    @Test
    void resume_delegates_to_client() {
        DeployResult result = target.resume("app");

        assertThat(result).isInstanceOf(DeployResult.Success.class);
        assertThat(client.lastResumedName).isEqualTo("app");
    }

    private static ClaudonyAppInfo appInfo(final String name, final HealthStatus health) {
        return new ClaudonyAppInfo(name, "default", name, "", "", Map.of(), health, Instant.now(), List.of());
    }

    static class StubFleetClient implements ClaudonyFleetClient {
        ClaudonyAppSpec lastRegisteredSpec;
        ClaudonyAppInfo nextRegisterResult;
        ClaudonyApiException registerThrows;
        String lastUpdatedName;
        ClaudonyAppInfo nextUpdateResult;
        String lastDeregisteredName;
        ClaudonyApiException deregisterThrows;
        Optional<ClaudonyAppInfo> nextGetResult = Optional.empty();
        String lastSuspendedName;
        String lastResumedName;

        @Override
        public ClaudonyAppInfo registerApp(final ClaudonyAppSpec spec) {
            if (registerThrows != null) throw registerThrows;
            lastRegisteredSpec = spec;
            return nextRegisterResult;
        }

        @Override
        public ClaudonyAppInfo updateApp(final String name, final ClaudonyAppSpec spec) {
            lastUpdatedName = name;
            lastRegisteredSpec = spec;
            return nextUpdateResult;
        }

        @Override
        public void deregisterApp(final String name) {
            if (deregisterThrows != null) throw deregisterThrows;
            lastDeregisteredName = name;
        }

        @Override
        public Optional<ClaudonyAppInfo> getApp(final String name) {
            return nextGetResult;
        }

        @Override
        public List<ClaudonyAppInfo> listApps(final String namespace) {
            return List.of();
        }

        @Override
        public void suspendApp(final String name) {
            lastSuspendedName = name;
        }

        @Override
        public void resumeApp(final String name) {
            lastResumedName = name;
        }

        @Override
        public void heartbeat(final String name) {}
    }
}
