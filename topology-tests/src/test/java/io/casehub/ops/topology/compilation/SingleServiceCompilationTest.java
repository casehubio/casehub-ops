package io.casehub.ops.topology.compilation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.ops.api.infra.InfraDesiredNodeSpec;
import io.casehub.ops.api.infra.K8sDeploymentSpec;
import io.casehub.ops.api.infra.K8sNamespaceSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SingleServiceCompilationTest extends TopologyTestBase {

    @Test
    void ghostBlog_compilesCorrectGraph() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        assertThat(graph.nodes()).hasSize(3);
    }

    @Test
    void ghostBlog_hasNamespaceNode() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        var ns = graph.nodes().get(NodeId.of("blog-namespace"));
        assertThat(ns).isNotNull();
        assertThat(ns.type()).isEqualTo(NodeType.of("k8s_namespace"));
        assertThat(ns.spec()).isInstanceOf(InfraDesiredNodeSpec.class);
        var infraSpec = ((InfraDesiredNodeSpec) ns.spec()).resourceSpec();
        assertThat(infraSpec).isInstanceOf(K8sNamespaceSpec.class);
        assertThat(((K8sNamespaceSpec) infraSpec).name()).isEqualTo("ghost-blog");
    }

    @Test
    void ghostBlog_deploymentDependsOnNamespace() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        assertThat(graph.dependenciesOf(NodeId.of("ghost")))
                .contains(NodeId.of("blog-namespace"));
    }

    @Test
    void ghostBlog_serviceDependsOnDeployment() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        assertThat(graph.dependenciesOf(NodeId.of("ghost-service")))
                .contains(NodeId.of("ghost"));
    }

    @Test
    void ghostBlog_variableSubstitution() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/single-node-blog.yaml");
        var deploy = (InfraDesiredNodeSpec) graph.nodes().get(NodeId.of("ghost")).spec();
        var k8sSpec = (K8sDeploymentSpec) deploy.resourceSpec();
        assertThat(k8sSpec.image()).isEqualTo("ghost:5-alpine");
        assertThat(k8sSpec.replicas()).isEqualTo(1);
    }

    @Test
    void companyWebsite_compilesWithLoadBalancer() throws IOException {
        DesiredStateGraph graph = compileSingleGraph("topologies/single-service/lb-cluster-website.yaml");
        assertThat(graph.nodes()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(graph.nodes().values().stream()
                .anyMatch(n -> n.type().equals(NodeType.of("load_balancer")))).isTrue();
    }
}
