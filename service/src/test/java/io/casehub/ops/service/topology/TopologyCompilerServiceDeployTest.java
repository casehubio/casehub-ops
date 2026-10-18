package io.casehub.ops.service.topology;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.ActualStateAdapterRouter;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.TransitionResult;
import io.casehub.desiredstate.runtime.FaultPolicyEngine;
import io.casehub.desiredstate.runtime.ReconciliationLoop;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopologyCompilerServiceDeployTest {

    private ReconciliationLoop loop;
    private TopologyCompilerService service;
    private Path topologiesDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        topologiesDir = tempDir.resolve("topologies");

        ActualStateAdapterRouter stubRouter = new ActualStateAdapterRouter() {
            @Override public ActualState readActual(DesiredStateGraph desired, String tenancyId) {
                return new ActualState(Map.of());
            }
            @Override public Set<NodeType> allHandledTypes() { return Set.of(); }
        };

        loop = ReconciliationLoop.builder(
                new TransitionPlanner(),
                (plan, tenancyId) -> new TransitionResult(Map.of()),
                stubRouter,
                new FaultPolicyEngine(List.of()),
                () -> Multi.createFrom().empty()
        ).build();

        service = new TopologyCompilerService();
        service.reconciliationLoop = loop;
        service.registryProducer = new NodeTypeRegistryProducer();
    }

    @Test
    void deploy_emptyTopology_startsLoop() throws IOException {
        Path topoDir = topologiesDir.resolve("empty-topo");
        Files.createDirectories(topoDir);
        Files.writeString(topoDir.resolve("topology.yaml"), """
                desiredState:
                  namespace: test
                  name: empty-topo
                nodes: {}
                """);

        service.deploy("default", topologiesDir.toString(), "empty-topo");

        assertThat(loop.tenantIds()).contains("topology:default:empty-topo");
    }

    @Test
    void isRunning_afterDeploy_returnsTrue() throws IOException {
        Path topoDir = topologiesDir.resolve("running-topo");
        Files.createDirectories(topoDir);
        Files.writeString(topoDir.resolve("topology.yaml"), """
                desiredState:
                  namespace: test
                  name: running-topo
                nodes: {}
                """);

        service.deploy("default", topologiesDir.toString(), "running-topo");

        assertThat(service.isRunning("default", "running-topo")).isTrue();
    }

    @Test
    void stop_removesLoop() throws IOException {
        Path topoDir = topologiesDir.resolve("stop-topo");
        Files.createDirectories(topoDir);
        Files.writeString(topoDir.resolve("topology.yaml"), """
                desiredState:
                  namespace: test
                  name: stop-topo
                nodes: {}
                """);

        service.deploy("default", topologiesDir.toString(), "stop-topo");
        assertThat(service.isRunning("default", "stop-topo")).isTrue();

        service.stop("default", "stop-topo");
        assertThat(service.isRunning("default", "stop-topo")).isFalse();
    }

    @Test
    void isRunning_beforeDeploy_returnsFalse() {
        assertThat(service.isRunning("default", "no-such-topo")).isFalse();
    }

    @Test
    void deploy_nonExistentTopology_throws() {
        assertThatThrownBy(() ->
                service.deploy("default", topologiesDir.toString(), "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
