package io.casehub.ops.service.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
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
                "casehub_application",
                Map.of("applicationId", (Object) "specialist-${catalog.name}",
                       "image", "casehubio/casehub-runtime:latest"));
        var spec = new AddNodeActionSpec(template, List.of("engine"));

        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "casehub_application", io.casehub.ops.api.deployment.CaseHubApplicationSpec.class);

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
        assertThat(node.spec()).isInstanceOf(io.casehub.ops.api.deployment.CaseHubApplicationSpec.class);
        var appSpec = (io.casehub.ops.api.deployment.CaseHubApplicationSpec) node.spec();
        assertThat(appSpec.applicationId()).isEqualTo("specialist-product-x-specialist");
        assertThat(appSpec.image()).isEqualTo("casehubio/casehub-runtime:latest");
    }

    @Test
    void applyResolvesSituationPlaceholders() {
        var template = new NodeTemplate(
                "specialist-${catalog.name}",
                "casehub_application",
                Map.of("applicationId", (Object) "specialist-${catalog.name}",
                       "image", "casehubio/casehub-runtime:latest"));
        var spec = new AddNodeActionSpec(template, List.of());
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "casehub_application", io.casehub.ops.api.deployment.CaseHubApplicationSpec.class);
        var action = new AddNodeAction(spec, nodeTypeRegistry, mapper);

        var graph = factory.of(List.of(), List.of());
        var catalogEntry = Map.of("name", "test-spec");
        var situation = new ActiveSituation(
                "surge-42", "_singleton", "test", 0.9,
                Map.of(), Instant.now(), Instant.now(), 0);

        var result = action.apply(graph, catalogEntry, situation);
        var node = result.nodes().get(NodeId.of("specialist-test-spec"));
        var appSpec = (io.casehub.ops.api.deployment.CaseHubApplicationSpec) node.spec();
        assertThat(appSpec.applicationId()).isEqualTo("specialist-test-spec");
    }

    @Test
    void applyAddsDependencyEdges() {
        var template = new NodeTemplate(
                "specialist-${catalog.name}", "casehub_application",
                Map.of("applicationId", (Object) "specialist-${catalog.name}",
                       "image", "casehubio/casehub-runtime:latest"));
        var spec = new AddNodeActionSpec(template, List.of("engine"));
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "casehub_application", io.casehub.ops.api.deployment.CaseHubApplicationSpec.class);
        var action = new AddNodeAction(spec, nodeTypeRegistry, mapper);

        var engineSpec = new io.casehub.ops.api.deployment.CaseHubApplicationSpec("engine", null, "casehubio/casehub-runtime:latest", null, null, null, 1);
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
                "specialist-${catalog.name}", "casehub_application",
                Map.of("applicationId", (Object) "specialist-${catalog.name}",
                       "image", "casehubio/casehub-runtime:latest",
                       "config", Map.of(
                               "llm.role", "${catalog.role}",
                               "llm.domain", "${catalog.domain}",
                               "llm.source-situation", "${situation.id}")));
        var spec = new AddNodeActionSpec(template, List.of());
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "casehub_application", io.casehub.ops.api.deployment.CaseHubApplicationSpec.class);
        var action = new AddNodeAction(spec, nodeTypeRegistry, mapper);

        var graph = factory.of(List.of(), List.of());
        var catalogEntry = Map.of("name", "test-spec", "role", "specialist", "domain", "product-x");
        var situation = new ActiveSituation(
                "surge-99", "_singleton", "test", 0.9,
                Map.of(), Instant.now(), Instant.now(), 0);

        var result = action.apply(graph, catalogEntry, situation);
        var node = result.nodes().get(NodeId.of("specialist-test-spec"));
        var appSpec = (io.casehub.ops.api.deployment.CaseHubApplicationSpec) node.spec();
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
                "casehub_application", io.casehub.ops.api.deployment.CaseHubApplicationSpec.class);
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
                "specialist-${catalog.name}", "casehub_application",
                Map.of("name", (Object) "specialist-${catalog.missing_field}",
                       "displayName", "test",
                       "description", "test", "image", "img:latest"));
        var spec = new AddNodeActionSpec(template, List.of());
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "casehub_application", io.casehub.ops.api.deployment.CaseHubApplicationSpec.class);
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
                "specialist-${catalog.name}", "casehub_application",
                Map.of("name", (Object) "test"));
        var spec = new AddNodeActionSpec(template, List.of());
        var action = new AddNodeAction(spec, Map.of(), mapper);

        var resolved = action.resolvedNodeId(Map.of("name", "product-x"));
        assertThat(resolved).isEqualTo("specialist-product-x");
    }

    @Test
    void casehubApplicationTemplateRoutedThroughFactory() {
        var template = new NodeTemplate(
                "specialist-test",
                "casehub_application",
                Map.of("applicationId", (Object) "specialist-test",
                       "image", "casehubio/casehub-runtime:latest",
                       "deploymentTarget", "claudony"));
        var spec = new AddNodeActionSpec(template, List.of());

        // Manual factory that mimics DeploymentNodeSpecFactoryProvider logic
        io.casehub.desiredstate.api.NodeSpecFactory appFactory = specMap -> {
            var    fields           = new java.util.LinkedHashMap<>(specMap);
            String deploymentTarget = (String) fields.remove("deploymentTarget");
            String existing         = (String) fields.remove("existing");
            var    appSpec          = mapper.convertValue(fields, io.casehub.ops.api.deployment.CaseHubApplicationSpec.class);
            if (deploymentTarget != null || existing != null) {
                return new io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec(appSpec, deploymentTarget, existing);
            }
            return appSpec;
        };
        var factories = Map.of("casehub_application", appFactory);
        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "casehub_application", io.casehub.ops.api.deployment.CaseHubApplicationSpec.class);

        var action = new AddNodeAction(spec, nodeTypeRegistry, factories, mapper);

        var graph  = factory.of(List.of(), List.of());
        var result = action.apply(graph, Map.of(), null);

        var node = result.nodes().get(NodeId.of("specialist-test"));
        assertThat(node).isNotNull();
        assertThat(node.spec()).isInstanceOf(io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec.class);
        var wrapper = (io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec) node.spec();
        assertThat(wrapper.deploymentTargetId()).isEqualTo("claudony");
        assertThat(wrapper.spec().applicationId()).isEqualTo("specialist-test");
    }
}
