package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.ops.api.SpecHashStore;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.api.ResumeContext;
import io.casehub.desiredstate.api.ResumeResult;
import io.casehub.desiredstate.api.SuspendContext;
import io.casehub.desiredstate.api.SuspendResult;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyGoalEntry;
import io.casehub.ops.api.claudony.ClaudonyGoals;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import io.casehub.platform.api.registry.HealthStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyReconciliationIntegrationTest {

    private SimulatingFleetClient client;
    private SpecHashStore specHashStore;
    private ClaudonyGoalCompiler compiler;
    private ClaudonyActualStateAdapter adapter;
    private ClaudonyNodeProvisioner provisioner;
    private DesiredStateGraphFactory graphFactory;

    @BeforeEach
    void setUp() {
        client = new SimulatingFleetClient();
        specHashStore = new SpecHashStore();
        compiler = new ClaudonyGoalCompiler();
        adapter = new ClaudonyActualStateAdapter(client, specHashStore);
        provisioner = new ClaudonyNodeProvisioner(client, specHashStore);
        graphFactory = new DefaultDesiredStateGraphFactory();
    }

    @Test
    void greenField_compileProvisionConverge() {
        var goals = new ClaudonyGoals(
            List.of(new ClaudonyGoalEntry<>(
                new ClaudonyAppNodeSpec("app-a", "App A", null, "img:1", Map.of()), List.of())),
            List.of(new ClaudonyGoalEntry<>(
                new ClaudonyPoolNodeSpec("app-a", "agent-1", 1, 3), List.of())));

        var result = compiler.compile(goals, graphFactory);
        var graph = ((CompilationResult.SingleGraph) result).graph();

        var actual1 = adapter.readActual(graph, "ns1");
        assertThat(actual1.statuses().values()).allMatch(s -> s == NodeStatus.ABSENT);

        var appNode = graph.nodes().get(NodeId.of("claudony_app:app-a"));
        assertThat(provisioner.provision(appNode, new ProvisionContext("ns1", graph)))
            .isInstanceOf(ProvisionResult.Success.class);

        var poolNode = graph.nodes().get(NodeId.of("claudony_pool:app-a:agent-1"));
        assertThat(provisioner.provision(poolNode, new ProvisionContext("ns1", graph)))
            .isInstanceOf(ProvisionResult.Success.class);

        var actual2 = adapter.readActual(graph, "ns1");
        assertThat(actual2.statuses().values()).allMatch(s -> s == NodeStatus.PRESENT);
    }

    @Test
    void driftDetection_poolConfigChanged_reconciles() {
        client.apps.put("app-a", new SimApp("app-a", "ns1", "img:1", HealthStatus.HEALTHY,
            List.of(new ClaudonyAppSpec.PoolSpec("agent-1", 2, 5))));

        var poolSpec = new ClaudonyPoolNodeSpec("app-a", "agent-1", 1, 3);
        specHashStore.record(NodeId.of(poolSpec.nodeId()), poolSpec);

        var graph = graphFactory.of(
            List.of(new DesiredNode(NodeId.of(poolSpec.nodeId()), poolSpec, HumanGating.NONE)),
            List.of());

        var actual = adapter.readActual(graph, "ns1");
        assertThat(actual.statuses().get(NodeId.of("claudony_pool:app-a:agent-1")))
            .isEqualTo(NodeStatus.DRIFTED);

        var poolNode = graph.nodes().get(NodeId.of("claudony_pool:app-a:agent-1"));
        assertThat(provisioner.provision(poolNode, new ProvisionContext("ns1", graph)))
            .isInstanceOf(ProvisionResult.Success.class);

        var updatedApp = client.apps.get("app-a");
        assertThat(updatedApp.pools).hasSize(1);
        assertThat(updatedApp.pools.getFirst().minActive()).isEqualTo(1);
        assertThat(updatedApp.pools.getFirst().maxActive()).isEqualTo(3);
    }

    @Test
    void suspendAndResume_lifecycle() {
        client.apps.put("app-a", new SimApp("app-a", "ns1", "img:1", HealthStatus.HEALTHY, List.of()));
        var spec = new ClaudonyAppNodeSpec("app-a", "App", null, "img:1", Map.of());
        var node = new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);
        var graph = graphFactory.of(List.of(node), List.of());

        assertThat(provisioner.suspend(node, new SuspendContext("ns1", graph)))
            .isInstanceOf(SuspendResult.Success.class);
        assertThat(client.apps.get("app-a").health).isEqualTo(HealthStatus.SUSPENDED);

        var actualSuspended = adapter.readActual(graph, "ns1");
        assertThat(actualSuspended.statuses().get(NodeId.of("claudony_app:app-a")))
            .isEqualTo(NodeStatus.SUSPENDED);

        assertThat(provisioner.resume(node, new ResumeContext("ns1", graph)))
            .isInstanceOf(ResumeResult.Success.class);
        assertThat(client.apps.get("app-a").health).isEqualTo(HealthStatus.HEALTHY);
    }

    @Test
    void deprovision_removesApp() {
        client.apps.put("app-a", new SimApp("app-a", "ns1", "img:1", HealthStatus.HEALTHY,
            List.of(new ClaudonyAppSpec.PoolSpec("agent-1", 1, 3))));

        var appSpec = new ClaudonyAppNodeSpec("app-a", "App", null, "img:1", Map.of());
        var appNode = new DesiredNode(NodeId.of(appSpec.nodeId()), appSpec, HumanGating.NONE);
        var graph = graphFactory.of(List.of(appNode), List.of());

        assertThat(provisioner.deprovision(appNode, new DeprovisionContext("ns1", graph)))
            .isInstanceOf(DeprovisionResult.Success.class);
        assertThat(client.apps).doesNotContainKey("app-a");
    }

    @Test
    void goalLoader_roundTrip() {
        var loader = new ClaudonyGoalLoader();
        var goals = loader.load("claudony-fleet-test.yaml");

        var result = compiler.compile(goals, graphFactory);
        var graph = ((CompilationResult.SingleGraph) result).graph();

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.nodes()).containsKey(NodeId.of("claudony_app:test-app"));
        assertThat(graph.nodes()).containsKey(NodeId.of("claudony_pool:test-app:agent-1"));
        assertThat(graph.dependenciesOf(NodeId.of("claudony_pool:test-app:agent-1")))
            .contains(NodeId.of("claudony_app:test-app"));
    }

    // --- Simulating fleet client ---

    static class SimApp {
        String name, namespace, image;
        HealthStatus health;
        List<ClaudonyAppSpec.PoolSpec> pools;
        SimApp(String name, String ns, String img, HealthStatus h, List<ClaudonyAppSpec.PoolSpec> pools) {
            this.name = name; this.namespace = ns; this.image = img;
            this.health = h; this.pools = new ArrayList<>(pools);
        }
    }

    static class SimulatingFleetClient implements ClaudonyFleetClient {
        final ConcurrentHashMap<String, SimApp> apps = new ConcurrentHashMap<>();

        @Override
        public ClaudonyAppInfo registerApp(ClaudonyAppSpec spec) {
            if (apps.containsKey(spec.name())) throw new ClaudonyApiException(409, "exists");
            var app = new SimApp(spec.name(), spec.namespace(), spec.image(), HealthStatus.HEALTHY,
                spec.pools() != null ? spec.pools() : List.of());
            apps.put(spec.name(), app);
            return toInfo(app);
        }

        @Override
        public ClaudonyAppInfo updateApp(String name, ClaudonyAppSpec spec) {
            var app = apps.get(name);
            if (app == null) throw new ClaudonyApiException(404, "not found");
            app.image = spec.image();
            app.pools = spec.pools() != null ? new ArrayList<>(spec.pools()) : new ArrayList<>();
            return toInfo(app);
        }

        @Override
        public void deregisterApp(String name) {
            if (apps.remove(name) == null) throw new ClaudonyApiException(404, "not found");
        }

        @Override
        public Optional<ClaudonyAppInfo> getApp(String name) {
            var app = apps.get(name);
            return app != null ? Optional.of(toInfo(app)) : Optional.empty();
        }

        @Override
        public List<ClaudonyAppInfo> listApps(String namespace) {
            return apps.values().stream()
                .filter(a -> namespace == null || namespace.equals(a.namespace))
                .map(this::toInfo).toList();
        }

        @Override
        public void suspendApp(String name) {
            var app = apps.get(name);
            if (app == null) throw new ClaudonyApiException(404, "not found");
            app.health = HealthStatus.SUSPENDED;
        }

        @Override
        public void resumeApp(String name) {
            var app = apps.get(name);
            if (app == null) throw new ClaudonyApiException(404, "not found");
            app.health = HealthStatus.HEALTHY;
        }

        @Override public void heartbeat(String name) {}

        private ClaudonyAppInfo toInfo(SimApp app) {
            return new ClaudonyAppInfo(app.name, app.namespace, app.name, null,
                app.image, Map.of(), app.health, Instant.now(),
                app.pools.stream()
                    .map(p -> new ClaudonyAppInfo.AppPoolInfo(p.agentId(), p.minActive(), p.maxActive()))
                    .toList());
        }
    }
}
