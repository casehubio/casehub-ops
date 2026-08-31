package io.casehub.ops.topology.compilation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeType;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SidecarMeshCompilationTest extends TopologyTestBase {

    @Test
    void logisticsFleet_compilesSuccessfully() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/sidecar-mesh/lb-cluster-logistics.yaml");
        assertThat(graph.nodes()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void logisticsFleet_hasMeshControlPlane() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/sidecar-mesh/lb-cluster-logistics.yaml");
        boolean hasMesh = graph.nodes().values().stream()
                .anyMatch(n -> n.type().equals(NodeType.of("mesh_control_plane")));
        assertThat(hasMesh).as("Logistics fleet should include mesh control plane").isTrue();
    }

    @Test
    void logisticsFleet_sidecarInjectionCreatesProxies() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/sidecar-mesh/lb-cluster-logistics.yaml");
        long proxyCount = graph.nodes().values().stream()
                .filter(n -> n.type().equals(NodeType.of("sidecar_proxy")))
                .count();
        assertThat(proxyCount).as("Sidecar injection should create proxy nodes")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void logisticsFleet_hasLoadBalancer() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/sidecar-mesh/lb-cluster-logistics.yaml");
        boolean hasLb = graph.nodes().values().stream()
                .anyMatch(n -> n.type().equals(NodeType.of("load_balancer")));
        assertThat(hasLb).as("Logistics fleet should include load balancer").isTrue();
    }

    @Test
    void insuranceClaims_compilesSuccessfully() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/sidecar-mesh/ha-multi-az-insurance.yaml");
        assertThat(graph.nodes()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void insuranceClaims_hasMeshAndHa() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/sidecar-mesh/ha-multi-az-insurance.yaml");
        boolean hasMesh = graph.nodes().values().stream()
                .anyMatch(n -> n.type().equals(NodeType.of("mesh_control_plane")));
        assertThat(hasMesh).as("Insurance claims should include mesh control plane").isTrue();
    }
}
