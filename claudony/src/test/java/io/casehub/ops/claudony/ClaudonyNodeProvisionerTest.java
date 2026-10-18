package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.ops.api.SpecHashStore;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.api.ResumeContext;
import io.casehub.desiredstate.api.ResumeResult;
import io.casehub.desiredstate.api.SuspendContext;
import io.casehub.desiredstate.api.SuspendResult;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeTypes;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import io.casehub.platform.api.registry.HealthStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyNodeProvisionerTest {

    @Test
    void handledTypes_returns_app_and_pool() {
        var provisioner = new ClaudonyNodeProvisioner(new RecordingFleetClient(), new SpecHashStore());
        assertThat(provisioner.handledTypes()).containsExactlyInAnyOrder(
            ClaudonyNodeTypes.APP, ClaudonyNodeTypes.POOL);
    }

    @Test
    void supportsStatefulLifecycle() {
        var provisioner = new ClaudonyNodeProvisioner(new RecordingFleetClient(), new SpecHashStore());
        assertThat(provisioner.supportsStatefulLifecycle()).isTrue();
    }

    @Test
    void provision_app_calls_registerApp() {
        var client = new RecordingFleetClient();
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyAppNodeSpec("my-app", "My App", null, "img:1", Map.of("k", "v"));
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.provision(node, new ProvisionContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(client.registered).hasSize(1);
        assertThat(client.registered.getFirst().name()).isEqualTo("my-app");
        assertThat(client.registered.getFirst().namespace()).isEqualTo("ns1");
        assertThat(client.registered.getFirst().image()).isEqualTo("img:1");
    }

    @Test
    void provision_app_409_falls_back_to_update() {
        var client = new RecordingFleetClient();
        client.registerThrows409 = true;
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyAppNodeSpec("my-app", "My App", null, "img:1", Map.of());
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.provision(node, new ProvisionContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(client.updated).hasSize(1);
    }

    @Test
    void provision_pool_read_modify_write() {
        var client = new RecordingFleetClient();
        client.apps.put("my-app", appInfo("my-app", List.of()));
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyPoolNodeSpec("my-app", "agent-1", 1, 3);
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.provision(node, new ProvisionContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(client.updated).hasSize(1);
        assertThat(client.updated.getFirst().pools()).hasSize(1);
        assertThat(client.updated.getFirst().pools().getFirst().agentId()).isEqualTo("agent-1");
    }

    @Test
    void provision_pool_fails_when_parent_app_missing() {
        var client = new RecordingFleetClient();
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyPoolNodeSpec("missing-app", "agent-1", 1, 3);
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.provision(node, new ProvisionContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(ProvisionResult.Failed.class);
    }

    @Test
    void deprovision_app_calls_deregisterApp() {
        var client = new RecordingFleetClient();
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyAppNodeSpec("my-app", "My App", null, "img", Map.of());
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.deprovision(node, new DeprovisionContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
        assertThat(client.deregistered).containsExactly("my-app");
    }

    @Test
    void deprovision_pool_removes_from_app() {
        var client = new RecordingFleetClient();
        client.apps.put("my-app", appInfo("my-app",
            List.of(new ClaudonyAppInfo.AppPoolInfo("agent-1", 1, 3),
                     new ClaudonyAppInfo.AppPoolInfo("agent-2", 2, 4))));
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyPoolNodeSpec("my-app", "agent-1", 1, 3);
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.deprovision(node, new DeprovisionContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
        assertThat(client.updated).hasSize(1);
        assertThat(client.updated.getFirst().pools()).hasSize(1);
        assertThat(client.updated.getFirst().pools().getFirst().agentId()).isEqualTo("agent-2");
    }

    @Test
    void deprovision_pool_succeeds_when_parent_app_gone() {
        var client = new RecordingFleetClient();
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyPoolNodeSpec("gone-app", "agent-1", 1, 3);
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.deprovision(node, new DeprovisionContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
    }

    @Test
    void suspend_app_calls_suspendApp() {
        var client = new RecordingFleetClient();
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyAppNodeSpec("my-app", "App", null, "img", Map.of());
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.suspend(node, new SuspendContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(SuspendResult.Success.class);
        assertThat(client.suspended).containsExactly("my-app");
    }

    @Test
    void resume_app_calls_resumeApp() {
        var client = new RecordingFleetClient();
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyAppNodeSpec("my-app", "App", null, "img", Map.of());
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.resume(node, new ResumeContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(ResumeResult.Success.class);
        assertThat(client.resumed).containsExactly("my-app");
    }

    @Test
    void suspend_pool_returns_failed() {
        var client = new RecordingFleetClient();
        var provisioner = new ClaudonyNodeProvisioner(client, new SpecHashStore());
        var spec = new ClaudonyPoolNodeSpec("my-app", "agent-1", 1, 3);
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);

        var result = provisioner.suspend(node, new SuspendContext("ns1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(SuspendResult.Failed.class);
    }

    // --- helpers ---

    private static final DesiredStateGraph EMPTY_GRAPH = new DefaultDesiredStateGraphFactory()
        .of(List.of(), List.of());

    private static ClaudonyAppInfo appInfo(String name, List<ClaudonyAppInfo.AppPoolInfo> pools) {
        return new ClaudonyAppInfo(name, "ns1", name, null, "img", Map.of(),
            HealthStatus.HEALTHY, Instant.now(), pools);
    }

    static class RecordingFleetClient implements ClaudonyFleetClient {
        final Map<String, ClaudonyAppInfo> apps = new HashMap<>();
        final List<ClaudonyAppSpec> registered = new ArrayList<>();
        final List<ClaudonyAppSpec> updated = new ArrayList<>();
        final List<String> deregistered = new ArrayList<>();
        final List<String> suspended = new ArrayList<>();
        final List<String> resumed = new ArrayList<>();
        boolean registerThrows409 = false;

        @Override
        public ClaudonyAppInfo registerApp(ClaudonyAppSpec spec) {
            if (registerThrows409) throw new ClaudonyApiException(409, "conflict");
            registered.add(spec);
            return toInfo(spec);
        }

        @Override
        public ClaudonyAppInfo updateApp(String name, ClaudonyAppSpec spec) {
            updated.add(spec);
            return toInfo(spec);
        }

        @Override public void deregisterApp(String name) { deregistered.add(name); }
        @Override public Optional<ClaudonyAppInfo> getApp(String name) { return Optional.ofNullable(apps.get(name)); }
        @Override public List<ClaudonyAppInfo> listApps(String ns) { return List.copyOf(apps.values()); }
        @Override public void suspendApp(String name) { suspended.add(name); }
        @Override public void resumeApp(String name) { resumed.add(name); }
        @Override public void heartbeat(String name) {}

        private ClaudonyAppInfo toInfo(ClaudonyAppSpec spec) {
            return new ClaudonyAppInfo(spec.name(), spec.namespace(), spec.displayName(),
                spec.description(), spec.image(), spec.config(),
                HealthStatus.HEALTHY, Instant.now(),
                spec.pools() != null ? spec.pools().stream()
                    .map(p -> new ClaudonyAppInfo.AppPoolInfo(p.agentId(), p.minActive(), p.maxActive()))
                    .toList() : List.of());
        }
    }
}
