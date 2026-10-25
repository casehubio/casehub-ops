package io.casehub.ops.service.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.Dependency;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.ops.api.deployment.AdaptationActionSpec.AddNodeActionSpec;
import io.casehub.ras.api.ActiveSituation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class AddNodeAction {

    private final AddNodeActionSpec spec;
    private final Map<String, Class<?>> nodeTypeRegistry;
    private final ObjectMapper mapper;

    AddNodeAction(AddNodeActionSpec spec,
                  Map<String, Class<?>> nodeTypeRegistry,
                  ObjectMapper mapper) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.nodeTypeRegistry = Objects.requireNonNull(nodeTypeRegistry, "nodeTypeRegistry");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    DesiredStateGraph apply(DesiredStateGraph graph,
                            Map<String, String> catalogEntry,
                            ActiveSituation situation) {
        Map<String, String> variables = buildVariables(catalogEntry, situation);
        String resolvedNodeId = resolvePlaceholders(spec.template().nodeId(), variables);

        Map<String, Object> resolvedSpec = resolveSpecMap(spec.template().spec(), variables);

        Class<?> specClass = nodeTypeRegistry.get(spec.template().nodeType());
        if (specClass == null) {
            throw new IllegalStateException(
                    "Unknown nodeType: " + spec.template().nodeType());
        }

        try {
            NodeSpec nodeSpec = (NodeSpec) mapper.convertValue(resolvedSpec, specClass);
            NodeId nodeId = NodeId.of(resolvedNodeId);
            DesiredNode node = new DesiredNode(nodeId, nodeSpec, HumanGating.NONE);

            DesiredStateGraph result = graph.withNode(node);
            for (String dep : spec.dependsOn()) {
                String resolvedDep = resolvePlaceholders(dep, variables);
                result = result.withDependency(
                        new Dependency(nodeId, NodeId.of(resolvedDep)));
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to create node " + resolvedNodeId + ": " + e.getMessage(), e);
        }
    }

    String resolvedNodeId(Map<String, String> catalogEntry) {
        Map<String, String> variables = new HashMap<>();
        if (catalogEntry != null) {
            for (var e : catalogEntry.entrySet()) {
                variables.put("catalog." + e.getKey(), e.getValue());
            }
        }
        return resolvePlaceholders(spec.template().nodeId(), variables);
    }

    private Map<String, String> buildVariables(
            Map<String, String> catalogEntry, ActiveSituation situation) {
        Map<String, String> vars = new HashMap<>();
        if (catalogEntry != null) {
            for (var e : catalogEntry.entrySet()) {
                vars.put("catalog." + e.getKey(), e.getValue());
            }
        }
        if (situation != null) {
            vars.put("situation.id", situation.situationId());
            vars.put("situation.confidence", String.valueOf(situation.confidence()));
            vars.put("situation.tenancyId", situation.tenancyId());
            vars.put("situation.correlationKey", situation.correlationKey());
        }
        return vars;
    }

    private String resolvePlaceholders(String template, Map<String, String> vars) {
        String result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        if (result.contains("${")) {
            throw new IllegalStateException(
                    "Unresolved placeholders in: " + result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveSpecMap(
            Map<String, Object> specMap, Map<String, String> vars) {
        Map<String, Object> resolved = new HashMap<>();
        for (var entry : specMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                resolved.put(entry.getKey(), resolvePlaceholders(s, vars));
            } else if (value instanceof Map<?, ?> m) {
                resolved.put(entry.getKey(),
                        resolveSpecMap((Map<String, Object>) m, vars));
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }
}
