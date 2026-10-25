package io.casehub.ops.service.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.deployment.AdaptationActionSpec.AddNodeActionSpec;
import io.casehub.ops.api.deployment.NodeTemplate;
import io.casehub.ras.api.ActiveSituation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AddNodeActionTest {

    private final DefaultDesiredStateGraphFactory factory = new DefaultDesiredStateGraphFactory();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void applyAddsNodeWithResolvedCatalogPlaceholders() {
        var template = new NodeTemplate(
                "specialist-${catalog.name}",
                "claudony_app",
                Map.of("name", (Object) "specialist-${catalog.name}",
                       "displayName", "${catalog.name}",
                       "description", "Specialist for ${catalog.domain}",
                       "image", "casehubio/my-llm-app:latest"));
        var spec = new AddNodeActionSpec(template, List.of("engine"));

        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "claudony_app", ClaudonyAppNodeSpec.class);

        var action = new AddNodeAction(spec, nodeTypeRegistry, mapper);

        var graph = factory.of(List.of(), List.of());
        var catalogEntry = Map.of(
                "name", "product-x-specialist",
                "domain", "product-x",
                "role", "specialist");
        var situation = new ActiveSituation(
                "surge-alert", "_singleton", "test", 0.9,
                Map.of(), Instant.now(), Instant.now(), 0);

        var result = action.apply(graph, catalogEntry, situation);

        assertThat(result.nodes()).containsKey(NodeId.of("specialist-product-x-specialist"));
        var node = result.nodes().get(NodeId.of("specialist-product-x-specialist"));
        assertThat(node.spec()).isInstanceOf(ClaudonyAppNodeSpec.class);
        var appSpec = (ClaudonyAppNodeSpec) node.spec();
        assertThat(appSpec.name()).isEqualTo("specialist-product-x-specialist");
        assertThat(appSpec.displayName()).isEqualTo("product-x-specialist");
        assertThat(appSpec.description()).isEqualTo("Specialist for product-x");
        assertThat(appSpec.image()).isEqualTo("casehubio/my-llm-app:latest");
    }

    @Test
    void applyResolvesSituationPlaceholders() {
        var template = new NodeTemplate(
                "specialist-${catalog.name}",
                "claudony_app",
                Map.of("name", (Object) "specialist-${catalog.name}",
                       "displayName", "${catalog.name}",
                       "description", "Deployed for ${situation.id}",
                       "image", "img:latest"));
        var spec = new AddNodeActionSpec(template, List.of());
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "claudony_app", ClaudonyAppNodeSpec.class);
        var action = new AddNodeAction(spec, nodeTypeRegistry, mapper);

        var graph = factory.of(List.of(), List.of());
        var catalogEntry = Map.of("name", "test-spec");
        var situation = new ActiveSituation(
                "surge-42", "_singleton", "test", 0.9,
                Map.of(), Instant.now(), Instant.now(), 0);

        var result = action.apply(graph, catalogEntry, situation);
        var node = result.nodes().get(NodeId.of("specialist-test-spec"));
        var appSpec = (ClaudonyAppNodeSpec) node.spec();
        assertThat(appSpec.description()).isEqualTo("Deployed for surge-42");
    }

    @Test
    void applyAddsDependencyEdges() {
        var template = new NodeTemplate(
                "specialist-${catalog.name}", "claudony_app",
                Map.of("name", (Object) "specialist-${catalog.name}",
                       "displayName", "${catalog.name}",
                       "description", "test", "image", "img:latest"));
        var spec = new AddNodeActionSpec(template, List.of("engine"));
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "claudony_app", ClaudonyAppNodeSpec.class);
        var action = new AddNodeAction(spec, nodeTypeRegistry, mapper);

        var engineSpec = new ClaudonyAppNodeSpec("engine", "Engine", "Engine service", "img:latest", Map.of());
        var engineNode = new io.casehub.desiredstate.api.DesiredNode(
                NodeId.of("engine"), engineSpec, io.casehub.desiredstate.api.HumanGating.NONE);
        var graph = factory.of(List.of(engineNode), List.of());
        var catalogEntry = Map.of("name", "test-spec");
        var situation = new ActiveSituation(
                "surge", "_singleton", "test", 0.9,
                Map.of(), Instant.now(), Instant.now(), 0);

        var result = action.apply(graph, catalogEntry, situation);
        assertThat(result.dependencies()).anyMatch(d ->
                d.from().equals(NodeId.of("specialist-test-spec"))
                && d.to().equals(NodeId.of("engine")));
    }

    @Test
    void applyResolvesNestedConfigMap() {
        var template = new NodeTemplate(
                "specialist-${catalog.name}", "claudony_app",
                Map.of("name", (Object) "specialist-${catalog.name}",
                       "displayName", "${catalog.name}",
                       "description", "test",
                       "image", "img:latest",
                       "config", Map.of(
                               "llm.role", "${catalog.role}",
                               "llm.domain", "${catalog.domain}",
                               "llm.source-situation", "${situation.id}")));
        var spec = new AddNodeActionSpec(template, List.of());
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "claudony_app", ClaudonyAppNodeSpec.class);
        var action = new AddNodeAction(spec, nodeTypeRegistry, mapper);

        var graph = factory.of(List.of(), List.of());
        var catalogEntry = Map.of("name", "test-spec", "role", "specialist", "domain", "product-x");
        var situation = new ActiveSituation(
                "surge-99", "_singleton", "test", 0.9,
                Map.of(), Instant.now(), Instant.now(), 0);

        var result = action.apply(graph, catalogEntry, situation);
        var node = result.nodes().get(NodeId.of("specialist-test-spec"));
        var appSpec = (ClaudonyAppNodeSpec) node.spec();
        assertThat(appSpec.config().get("llm.role")).isEqualTo("specialist");
        assertThat(appSpec.config().get("llm.domain")).isEqualTo("product-x");
        assertThat(appSpec.config().get("llm.source-situation")).isEqualTo("surge-99");
    }

    @Test
    void applyThrowsOnUnknownNodeType() {
        var template = new NodeTemplate("node-1", "unknown_type",
                Map.of("name", (Object) "test"));
        var spec = new AddNodeActionSpec(template, List.of());
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "claudony_app", ClaudonyAppNodeSpec.class);
        var action = new AddNodeAction(spec, nodeTypeRegistry, mapper);

        var graph = factory.of(List.of(), List.of());
        var situation = new ActiveSituation(
                "alert", "_singleton", "test", 0.9,
                Map.of(), Instant.now(), Instant.now(), 0);

        assertThatIllegalStateException()
                .isThrownBy(() -> action.apply(graph, Map.of(), situation))
                .withMessageContaining("unknown_type");
    }

    @Test
    void applyThrowsOnUnresolvedPlaceholder() {
        var template = new NodeTemplate(
                "specialist-${catalog.name}", "claudony_app",
                Map.of("name", (Object) "specialist-${catalog.missing_field}",
                       "displayName", "test",
                       "description", "test", "image", "img:latest"));
        var spec = new AddNodeActionSpec(template, List.of());
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "claudony_app", ClaudonyAppNodeSpec.class);
        var action = new AddNodeAction(spec, nodeTypeRegistry, mapper);

        var graph = factory.of(List.of(), List.of());
        var catalogEntry = Map.of("name", "test-spec");
        var situation = new ActiveSituation(
                "alert", "_singleton", "test", 0.9,
                Map.of(), Instant.now(), Instant.now(), 0);

        assertThatIllegalStateException()
                .isThrownBy(() -> action.apply(graph, catalogEntry, situation))
                .withMessageContaining("Unresolved");
    }

    @Test
    void resolvedNodeIdResolvesFromCatalogEntry() {
        var template = new NodeTemplate(
                "specialist-${catalog.name}", "claudony_app",
                Map.of("name", (Object) "test"));
        var spec = new AddNodeActionSpec(template, List.of());
        var action = new AddNodeAction(spec, Map.of(), mapper);

        var resolved = action.resolvedNodeId(Map.of("name", "product-x"));
        assertThat(resolved).isEqualTo("specialist-product-x");
    }
}
