package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeTypes;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import io.casehub.platform.api.registry.HealthStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyActualStateAdapterTest {

    @Test
    void handledTypes_returns_app_and_pool() {
        var adapter = new ClaudonyActualStateAdapter(new StubFleetClient(), new ClaudonySpecHashStore());
        assertThat(adapter.handledTypes()).containsExactlyInAnyOrder(
            ClaudonyNodeTypes.APP, ClaudonyNodeTypes.POOL);
    }

    @Test
    void app_healthy_returns_present() {
        var client = new StubFleetClient();
        client.apps.put("my-app", appInfo("my-app", HealthStatus.HEALTHY));
        var store = new ClaudonySpecHashStore();
        var spec = new ClaudonyAppNodeSpec("my-app", "My App", null, "img", Map.of());
        store.record(NodeId.of(spec.nodeId()), spec);
        var adapter = new ClaudonyActualStateAdapter(client, store);

        var actual = adapter.readActual(graphWith(spec), "ns1");

        assertThat(actual.statuses().get(NodeId.of("claudony_app:my-app")))
            .isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void app_absent_returns_absent() {
        var adapter = new ClaudonyActualStateAdapter(new StubFleetClient(), new ClaudonySpecHashStore());

        var actual = adapter.readActual(
            graphWith(new ClaudonyAppNodeSpec("missing", "M", null, "img", Map.of())), "ns1");

        assertThat(actual.statuses().get(NodeId.of("claudony_app:missing")))
            .isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void app_suspended_returns_suspended() {
        var client = new StubFleetClient();
        client.apps.put("my-app", appInfo("my-app", HealthStatus.SUSPENDED));
        var adapter = new ClaudonyActualStateAdapter(client, new ClaudonySpecHashStore());

        var actual = adapter.readActual(
            graphWith(new ClaudonyAppNodeSpec("my-app", "App", null, "img", Map.of())), "ns1");

        assertThat(actual.statuses().get(NodeId.of("claudony_app:my-app")))
            .isEqualTo(NodeStatus.SUSPENDED);
    }

    @Test
    void app_degraded_returns_drifted() {
        var client = new StubFleetClient();
        client.apps.put("my-app", appInfo("my-app", HealthStatus.DEGRADED));
        var adapter = new ClaudonyActualStateAdapter(client, new ClaudonySpecHashStore());

        var actual = adapter.readActual(
            graphWith(new ClaudonyAppNodeSpec("my-app", "App", null, "img", Map.of())), "ns1");

        assertThat(actual.statuses().get(NodeId.of("claudony_app:my-app")))
            .isEqualTo(NodeStatus.DRIFTED);
    }

    @Test
    void app_specHashDrift_returns_drifted() {
        var client = new StubFleetClient();
        client.apps.put("my-app", appInfo("my-app", HealthStatus.HEALTHY));
        var store = new ClaudonySpecHashStore();
        var oldSpec = new ClaudonyAppNodeSpec("my-app", "Old", null, "img:1", Map.of());
        store.record(NodeId.of(oldSpec.nodeId()), oldSpec);
        var adapter = new ClaudonyActualStateAdapter(client, store);

        var newSpec = new ClaudonyAppNodeSpec("my-app", "New", null, "img:2", Map.of());
        var actual = adapter.readActual(graphWith(newSpec), "ns1");

        assertThat(actual.statuses().get(NodeId.of("claudony_app:my-app")))
            .isEqualTo(NodeStatus.DRIFTED);
    }

    @Test
    void pool_present_when_matching() {
        var client = new StubFleetClient();
        client.apps.put("my-app", appInfoWithPool("my-app", "agent-1", 1, 3));
        var store = new ClaudonySpecHashStore();
        var poolSpec = new ClaudonyPoolNodeSpec("my-app", "agent-1", 1, 3);
        store.record(NodeId.of(poolSpec.nodeId()), poolSpec);
        var adapter = new ClaudonyActualStateAdapter(client, store);

        var actual = adapter.readActual(graphWith(poolSpec), "ns1");

        assertThat(actual.statuses().get(NodeId.of("claudony_pool:my-app:agent-1")))
            .isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void pool_drifted_when_minActive_differs() {
        var client = new StubFleetClient();
        client.apps.put("my-app", appInfoWithPool("my-app", "agent-1", 2, 3));
        var store = new ClaudonySpecHashStore();
        var poolSpec = new ClaudonyPoolNodeSpec("my-app", "agent-1", 1, 3);
        store.record(NodeId.of(poolSpec.nodeId()), poolSpec);
        var adapter = new ClaudonyActualStateAdapter(client, store);

        var actual = adapter.readActual(graphWith(poolSpec), "ns1");

        assertThat(actual.statuses().get(NodeId.of("claudony_pool:my-app:agent-1")))
            .isEqualTo(NodeStatus.DRIFTED);
    }

    @Test
    void pool_absent_when_parent_app_missing() {
        var adapter = new ClaudonyActualStateAdapter(new StubFleetClient(), new ClaudonySpecHashStore());

        var actual = adapter.readActual(
            graphWith(new ClaudonyPoolNodeSpec("missing-app", "agent-1", 1, 3)), "ns1");

        assertThat(actual.statuses().get(NodeId.of("claudony_pool:missing-app:agent-1")))
            .isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void pool_absent_when_agentId_not_found() {
        var client = new StubFleetClient();
        client.apps.put("my-app", appInfoWithPool("my-app", "agent-other", 1, 3));
        var adapter = new ClaudonyActualStateAdapter(client, new ClaudonySpecHashStore());

        var actual = adapter.readActual(
            graphWith(new ClaudonyPoolNodeSpec("my-app", "agent-1", 1, 3)), "ns1");

        assertThat(actual.statuses().get(NodeId.of("claudony_pool:my-app:agent-1")))
            .isEqualTo(NodeStatus.ABSENT);
    }

    // --- helpers ---

    private DesiredStateGraph graphWith(ClaudonyNodeSpec spec) {
        var factory = new DefaultDesiredStateGraphFactory();
        return factory.of(
            List.of(new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE)),
            List.of());
    }

    private ClaudonyAppInfo appInfo(String name, HealthStatus health) {
        return new ClaudonyAppInfo(name, "ns1", name, null, "img", Map.of(),
            health, Instant.now(), List.of());
    }

    private ClaudonyAppInfo appInfoWithPool(String name, String agentId, int min, int max) {
        return new ClaudonyAppInfo(name, "ns1", name, null, "img", Map.of(),
            HealthStatus.HEALTHY, Instant.now(),
            List.of(new ClaudonyAppInfo.AppPoolInfo(agentId, min, max)));
    }

    static class StubFleetClient implements ClaudonyFleetClient {
        final Map<String, ClaudonyAppInfo> apps = new HashMap<>();

        @Override public ClaudonyAppInfo registerApp(ClaudonyAppSpec spec) { return null; }
        @Override public ClaudonyAppInfo updateApp(String name, ClaudonyAppSpec spec) { return null; }
        @Override public void deregisterApp(String name) {}
        @Override public Optional<ClaudonyAppInfo> getApp(String name) { return Optional.ofNullable(apps.get(name)); }
        @Override public List<ClaudonyAppInfo> listApps(String namespace) { return List.copyOf(apps.values()); }
        @Override public void suspendApp(String name) {}
        @Override public void resumeApp(String name) {}
        @Override public void heartbeat(String name) {}
    }
}
