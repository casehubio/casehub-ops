package io.casehub.ops.topology.compilation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeType;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class EventDrivenCompilationTest extends TopologyTestBase {

    @Test
    void eventDrivenDev_compilesToFlatGraph() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/event-driven/single-node-dev.yaml");
        assertThat(graph.nodes()).hasSize(4);
        assertThat(graph.nodes()).containsKey(NodeId.of("rabbitmq"));
        assertThat(graph.nodes()).containsKey(NodeId.of("producer"));
        assertThat(graph.nodes()).containsKey(NodeId.of("consumer"));
    }

    @Test
    void eventDrivenDev_producerAndConsumerDependOnBroker() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/event-driven/single-node-dev.yaml");
        assertThat(graph.dependenciesOf(NodeId.of("producer")))
                .contains(NodeId.of("rabbitmq"));
        assertThat(graph.dependenciesOf(NodeId.of("consumer")))
                .contains(NodeId.of("rabbitmq"));
    }

    @Test
    void iotTelemetry_compilesWithLoadBalancer() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/event-driven/lb-cluster-telemetry.yaml");
        assertThat(graph.nodes()).hasSizeGreaterThanOrEqualTo(5);
        boolean hasLb = graph.nodes().values().stream()
                .anyMatch(n -> n.type().equals(NodeType.of("load_balancer")));
        assertThat(hasLb).as("IoT telemetry should include load balancer").isTrue();
    }

    @Test
    void iotTelemetry_processorDependsOnBroker() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/event-driven/lb-cluster-telemetry.yaml");
        assertThat(graph.dependenciesOf(NodeId.of("telemetry-processor")))
                .contains(NodeId.of("kafka-broker"));
    }
}
