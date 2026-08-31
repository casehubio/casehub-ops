package io.casehub.ops.topology.compilation;

import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MultiTierCompilationTest extends TopologyTestBase {

    @Test
    void ecommerceDev_compilesToLifecycle() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(
                "topologies/multi-tier/single-node-dev.yaml");
        assertThat(lifecycle.phases()).hasSize(3);
        assertThat(lifecycle.phases().get(0).id()).isEqualTo("data");
        assertThat(lifecycle.phases().get(1).id()).isEqualTo("application");
        assertThat(lifecycle.phases().get(2).id()).isEqualTo("web");
    }

    @Test
    void ecommerceDev_webPhaseHasCarryForward() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(
                "topologies/multi-tier/single-node-dev.yaml");
        var webGraph = lifecycle.phases().get(2).graph();
        assertThat(webGraph.nodes()).containsKey(NodeId.of("storefront"));
        assertThat(webGraph.nodes()).containsKey(NodeId.of("catalog-api"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "topologies/multi-tier/lb-cluster-ecommerce.yaml",
            "topologies/multi-tier/ha-multi-az-healthcare.yaml",
            "topologies/multi-tier/multi-region-banking.yaml"
    })
    void tutorialExemplar_compilesToLifecycle(String path) throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(path);
        assertThat(lifecycle.phases()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void bankingCore_hasMultiRegionModule() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(
                "topologies/multi-tier/multi-region-banking.yaml");
        var lastPhase = lifecycle.phases().getLast();
        boolean hasDnsFailover = lastPhase.graph().nodes().values().stream()
                .anyMatch(n -> n.type().equals(NodeType.of("dns_failover")));
        boolean hasDataReplication = lastPhase.graph().nodes().values().stream()
                .anyMatch(n -> n.type().equals(NodeType.of("data_replication")));
        assertThat(hasDnsFailover || hasDataReplication)
                .as("Banking topology should include multi-region module nodes").isTrue();
    }
}
