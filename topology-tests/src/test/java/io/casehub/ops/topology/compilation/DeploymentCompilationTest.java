package io.casehub.ops.topology.compilation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.ops.api.deployment.DeploymentNodeSpec;
import io.casehub.ops.api.infra.InfraNodeSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentCompilationTest extends TopologyTestBase {

    private static final Map<String, String> DEPLOYMENT_REGISTRY =
            buildTypeRegistryFrom(InfraNodeSpec.class, DeploymentNodeSpec.class);

    @Test
    void helloLlm_hasThreeNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(3);
    }

    @Test
    void helloLlm_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void helloLlm_engineDependsOnEidos() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("engine")))
                .contains(NodeId.of("eidos"));
    }

    @Test
    void helloLlm_llmDependsOnEngine() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("my-llm")))
                .contains(NodeId.of("engine"));
    }

    @Test
    void helloLlm_specContainsApplicationId() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        var eidosNode = graph.nodes().get(NodeId.of("eidos"));
        assertThat(eidosNode).isNotNull();
        assertThat(eidosNode.spec()).isNotNull();
    }
}
