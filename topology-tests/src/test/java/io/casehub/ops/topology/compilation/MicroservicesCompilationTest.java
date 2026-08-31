package io.casehub.ops.topology.compilation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MicroservicesCompilationTest extends TopologyTestBase {

    @Test
    void microservicesDev_compilesToFlatGraph() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/microservices/single-node-dev.yaml");
        assertThat(graph.nodes()).hasSize(4);
        assertThat(graph.nodes()).containsKey(NodeId.of("user-svc"));
        assertThat(graph.nodes()).containsKey(NodeId.of("order-svc"));
        assertThat(graph.nodes()).containsKey(NodeId.of("payment-svc"));
    }

    @Test
    void tradingPlatform_forEachExpandsAcrossAZs() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/microservices/ha-multi-az-trading.yaml");
        assertThat(graph.nodes()).containsKey(NodeId.of("market-data.us-east-1a"));
        assertThat(graph.nodes()).containsKey(NodeId.of("market-data.us-east-1b"));
        assertThat(graph.nodes()).containsKey(NodeId.of("market-data.us-east-1c"));
        assertThat(graph.nodes()).containsKey(NodeId.of("order-matching.us-east-1a"));
        assertThat(graph.nodes()).containsKey(NodeId.of("order-matching.us-east-1b"));
        assertThat(graph.nodes()).containsKey(NodeId.of("order-matching.us-east-1c"));
    }

    @Test
    void tradingPlatform_azStampsDependOnNamespace() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/microservices/ha-multi-az-trading.yaml");
        assertThat(graph.dependenciesOf(NodeId.of("market-data.us-east-1a")))
                .contains(NodeId.of("trading-ns"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "topologies/microservices/lb-cluster-delivery.yaml",
            "topologies/microservices/multi-region-payments.yaml"
    })
    void tutorialExemplar_compilesSuccessfully(String path) throws IOException {
        DesiredStateGraph graph = compileSingleGraph(path);
        assertThat(graph.nodes()).hasSizeGreaterThanOrEqualTo(3);
    }
}
