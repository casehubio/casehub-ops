package io.casehub.ops.claudony;

import io.casehub.ops.api.deployment.PoolCreateRequest;
import io.casehub.ops.api.deployment.PoolInfo;
import io.casehub.ops.api.deployment.PoolUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudonyPoolOperationsTest {

    private MapFleetClient fleetClient;
    private ClaudonyPoolOperations ops;

    @BeforeEach
    void setUp() {
        fleetClient = new MapFleetClient();
        ops = new ClaudonyPoolOperations(fleetClient);
    }

    @Test
    void backendIdIsClaudony() {
        assertThat(ops.backendId()).isEqualTo("claudony");
    }

    @Test
    void createPoolAddsPoolToParentApp() {
        fleetClient.apps.put("platform", new ClaudonyAppInfo(
                "platform", "default", "Platform", "", "image:latest",
                Map.of(), null, null, List.of()));

        var result = ops.createPool(new PoolCreateRequest(
                "platform", "analyst", "claudony", 2, 5,
                null, null, null, null, null, null, null));

        assertThat(result.applicationId()).isEqualTo("platform");
        assertThat(result.name()).isEqualTo("analyst");
        var app = fleetClient.apps.get("platform");
        assertThat(app.pools()).hasSize(1);
        assertThat(app.pools().getFirst().agentId()).isEqualTo("analyst");
        assertThat(app.pools().getFirst().minActive()).isEqualTo(2);
        assertThat(app.pools().getFirst().maxActive()).isEqualTo(5);
    }

    @Test
    void getPoolReturnsInfoWhenExists() {
        fleetClient.apps.put("platform", new ClaudonyAppInfo(
                "platform", "default", "Platform", "", "image:latest",
                Map.of(), null, null,
                List.of(new ClaudonyAppInfo.AppPoolInfo("analyst", 2, 5))));

        var result = ops.getPool("platform", "analyst");

        assertThat(result).isPresent();
        assertThat(result.get().applicationId()).isEqualTo("platform");
        assertThat(result.get().name()).isEqualTo("analyst");
        assertThat(result.get().minActive()).isEqualTo(2);
        assertThat(result.get().maxActive()).isEqualTo(5);
    }

    @Test
    void getPoolReturnsEmptyWhenAppMissing() {
        assertThat(ops.getPool("missing", "analyst")).isEmpty();
    }

    @Test
    void getPoolReturnsEmptyWhenPoolMissing() {
        fleetClient.apps.put("platform", new ClaudonyAppInfo(
                "platform", "default", "Platform", "", "image:latest",
                Map.of(), null, null, List.of()));

        assertThat(ops.getPool("platform", "nonexistent")).isEmpty();
    }

    @Test
    void updatePoolModifiesExistingPool() {
        fleetClient.apps.put("platform", new ClaudonyAppInfo(
                "platform", "default", "Platform", "", "image:latest",
                Map.of(), null, null,
                List.of(new ClaudonyAppInfo.AppPoolInfo("analyst", 2, 5))));

        ops.updatePool("platform", "analyst", new PoolUpdateRequest(
                3, 8, null, null, null, null, null));

        var app = fleetClient.apps.get("platform");
        assertThat(app.pools()).hasSize(1);
        assertThat(app.pools().getFirst().minActive()).isEqualTo(3);
        assertThat(app.pools().getFirst().maxActive()).isEqualTo(8);
    }

    @Test
    void destroyPoolRemovesPoolFromParentApp() {
        fleetClient.apps.put("platform", new ClaudonyAppInfo(
                "platform", "default", "Platform", "", "image:latest",
                Map.of(), null, null,
                List.of(new ClaudonyAppInfo.AppPoolInfo("analyst", 2, 5),
                        new ClaudonyAppInfo.AppPoolInfo("coder", 1, 3))));

        ops.destroyPool("platform", "analyst");

        var app = fleetClient.apps.get("platform");
        assertThat(app.pools()).hasSize(1);
        assertThat(app.pools().getFirst().agentId()).isEqualTo("coder");
    }

    static class MapFleetClient implements ClaudonyFleetClient {
        final ConcurrentHashMap<String, ClaudonyAppInfo> apps = new ConcurrentHashMap<>();

        @Override
        public ClaudonyAppInfo registerApp(ClaudonyAppSpec spec) {
            var info = new ClaudonyAppInfo(spec.name(), spec.namespace(), spec.displayName(),
                    spec.description(), spec.image(), spec.config(), null, null,
                    spec.pools() != null
                            ? spec.pools().stream()
                            .map(p -> new ClaudonyAppInfo.AppPoolInfo(p.agentId(), p.minActive(), p.maxActive()))
                            .toList()
                            : List.of());
            apps.put(spec.name(), info);
            return info;
        }

        @Override
        public ClaudonyAppInfo updateApp(String name, ClaudonyAppSpec spec) {
            var info = new ClaudonyAppInfo(spec.name(), spec.namespace(), spec.displayName(),
                    spec.description(), spec.image(), spec.config(), null, null,
                    spec.pools() != null
                            ? spec.pools().stream()
                            .map(p -> new ClaudonyAppInfo.AppPoolInfo(p.agentId(), p.minActive(), p.maxActive()))
                            .toList()
                            : List.of());
            apps.put(name, info);
            return info;
        }

        @Override public void deregisterApp(String name) { apps.remove(name); }
        @Override public Optional<ClaudonyAppInfo> getApp(String name) { return Optional.ofNullable(apps.get(name)); }
        @Override public List<ClaudonyAppInfo> listApps(String namespace) { return List.copyOf(apps.values()); }
        @Override public void suspendApp(String name) {}
        @Override public void resumeApp(String name) {}
        @Override public void heartbeat(String name) {}
    }
}
