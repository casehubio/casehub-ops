package io.casehub.ops.service.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.GoalCompiler;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.ops.api.deployment.AdaptationActionSpec;
import io.casehub.ops.api.deployment.AdaptationRuleSpec;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ras.api.ActiveSituation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime wrapper for AdaptationRuleSpec that applies adaptation actions to a graph.
 * <p>
 * Each rule contains one or more actions (scale, add, update) that execute in sequence
 * when the rule is applied. Actions mutate the graph based on the active situation's confidence.
 */
public final class AdaptationRule {

    private final AdaptationRuleSpec spec;
    private final List<Object> actions;
    private final GoalCompiler<DeploymentGoals> compiler;
    private final ObjectMapper mapper;
    private final DesiredStateGraphFactory factory;
    private final Map<String, Class<?>> nodeTypeRegistry;
    private final List<Map<String, String>> catalogEntries;


    private AdaptationRule(
            AdaptationRuleSpec spec,
            List<Object> actions,
            GoalCompiler<DeploymentGoals> compiler,
            ObjectMapper mapper,
            DesiredStateGraphFactory factory,
            Map<String, Class<?>> nodeTypeRegistry,
            List<Map<String, String>> catalogEntries) {
        this.spec             = Objects.requireNonNull(spec, "spec");
        this.actions          = List.copyOf(actions);
        this.compiler         = compiler;
        this.mapper           = mapper;
        this.factory          = factory;
        this.nodeTypeRegistry = nodeTypeRegistry != null ? Map.copyOf(nodeTypeRegistry) : Map.of();
        this.catalogEntries   = catalogEntries != null ? List.copyOf(catalogEntries) : List.of();
    }

    /**
     * Creates AdaptationRule instances from specs.
     */
    public static List<AdaptationRule> fromSpecs(
            List<AdaptationRuleSpec> specs,
            GoalCompiler<DeploymentGoals> compiler,
            ObjectMapper mapper,
            DesiredStateGraphFactory factory) {
        return fromSpecs(specs, compiler, mapper, factory, (Map<String, Class<? extends io.casehub.desiredstate.api.NodeSpec>>) null, null);
    }

    /**
     * Creates AdaptationRule instances from specs, validating update action fields
     * against the target NodeSpec type's Jackson-visible properties.
     */
    public static List<AdaptationRule> fromSpecs(
            List<AdaptationRuleSpec> specs,
            GoalCompiler<DeploymentGoals> compiler,
            ObjectMapper mapper,
            DesiredStateGraphFactory factory,
            Map<String, Class<? extends io.casehub.desiredstate.api.NodeSpec>> nodeTypeRegistry) {
        return fromSpecs(specs, compiler, mapper, factory, nodeTypeRegistry, null);
    }

    /**
     * Creates AdaptationRule instances from specs with full cross-domain node type registry.
     */
    public static List<AdaptationRule> fromSpecs(
            List<AdaptationRuleSpec> specs,
            GoalCompiler<DeploymentGoals> compiler,
            ObjectMapper mapper,
            DesiredStateGraphFactory factory,
            Map<String, Class<? extends io.casehub.desiredstate.api.NodeSpec>> nodeTypeRegistry,
            Map<String, Class<?>> crossDomainNodeTypeRegistry) {
        Objects.requireNonNull(specs, "specs");
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(factory, "factory");

        Map<String, Class<?>> effectiveNodeTypeRegistry = crossDomainNodeTypeRegistry != null
                ? crossDomainNodeTypeRegistry : Map.of();

        List<AdaptationRule> rules = new ArrayList<>();
        for (var spec : specs) {
            List<Object> actions = new ArrayList<>();
            for (var actionSpec : spec.actions()) {
                if (actionSpec instanceof AdaptationActionSpec.UpdateActionSpec u
                    && u.nodeType() != null
                    && nodeTypeRegistry != null
                    && nodeTypeRegistry.containsKey(u.nodeType())) {
                    validateUpdateFields(u, nodeTypeRegistry.get(u.nodeType()), mapper);
                }
                Object action = switch (actionSpec) {
                    case AdaptationActionSpec.ScaleActionSpec s -> new ScaleAction(s);
                    case AdaptationActionSpec.UpdateActionSpec u -> new UpdateAction(u);
                    case AdaptationActionSpec.AddActionSpec a -> {
                        if (compiler == null) throw new IllegalStateException(
                                "GoalCompiler required for AddActionSpec but not provided");
                        yield new AddAction(a, compiler, factory);
                    }
                    case AdaptationActionSpec.AddNodeActionSpec a ->
                        new AddNodeAction(a, effectiveNodeTypeRegistry, mapper);
                };
                actions.add(action);
            }
            rules.add(new AdaptationRule(spec, actions, compiler, mapper, factory, effectiveNodeTypeRegistry, null));
        }
        return rules;
    }


    /**
     * Returns the rule name.
     */
    public String name() {
        return spec.name();
    }

    /**
     * Returns the trigger configuration.
     */
    public io.casehub.ops.api.deployment.AdaptationTrigger trigger() {
        return spec.trigger();
    }

    public List<Map<String, String>> catalogEntries() {
        return catalogEntries;
    }

    public AdaptationRule withCatalogEntries(List<Map<String, String>> entries) {
        return new AdaptationRule(spec, actions, compiler, mapper, factory,
                                  nodeTypeRegistry, entries);
    }


    /**
     * Applies all actions in sequence, returning the mutated graph.
     */
    public DesiredStateGraph apply(DesiredStateGraph graph, ActiveSituation situation) {
        return apply(graph, situation, null);
    }

    /**
     * Applies all actions in sequence with catalog entry for template resolution.
     */
    public DesiredStateGraph apply(DesiredStateGraph graph, ActiveSituation situation,
                                   Map<String, String> catalogEntry) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(situation, "situation");

        DesiredStateGraph result = graph;
        double minConfidence = spec.trigger() instanceof io.casehub.ops.api.deployment.PatternTrigger pt ? pt.minConfidence() : 0.0;

        for (var action : actions) {
            result = switch (action) {
                case ScaleAction s -> s.apply(result, situation, minConfidence);
                case UpdateAction u -> u.apply(result, mapper);
                case AddAction a -> a.apply(result);
                case AddNodeAction a -> a.apply(result, catalogEntry, situation);
                default -> throw new IllegalStateException("Unknown action type: " + action.getClass());
            };
        }

        return result;
    }

    /**
     * Returns the set of node IDs that this rule targets.
     */
    public Set<NodeId> targetNodeIds(DesiredStateGraph graph) {
        return targetNodeIds(graph, List.of());
    }

    /**
     * Returns the set of node IDs that this rule targets, resolving AddNodeAction
     * templates against the provided catalog entries.
     */
    public Set<NodeId> targetNodeIds(DesiredStateGraph graph,
                                      List<Map<String, String>> resolvedCatalogEntries) {
        Objects.requireNonNull(graph, "graph");

        Set<NodeId> targets = new HashSet<>();

        for (var actionSpec : spec.actions()) {
            switch (actionSpec) {
                case AdaptationActionSpec.ScaleActionSpec s -> {
                    NodeId baseId = NodeId.of(s.target());
                    if (graph.nodes().containsKey(baseId)) {
                        targets.add(baseId);
                    }
                }
                case AdaptationActionSpec.UpdateActionSpec u -> {
                    NodeId targetId = NodeId.of(u.target());
                    if (graph.nodes().containsKey(targetId)) {
                        targets.add(targetId);
                    }
                }
                case AdaptationActionSpec.AddActionSpec a -> {
                    if (compiler != null) {
                        DesiredStateGraph subGraph = ((CompilationResult.SingleGraph) compiler.compile(a.nodes(), factory)).graph();
                        targets.addAll(subGraph.nodes().keySet());
                    }
                }
                case AdaptationActionSpec.AddNodeActionSpec a -> {
                    for (var action : actions) {
                        if (action instanceof AddNodeAction addNode) {
                            for (Map<String, String> entry : resolvedCatalogEntries) {
                                targets.add(NodeId.of(addNode.resolvedNodeId(entry)));
                            }
                        }
                    }
                }
            }
        }

        return targets;
    }

    private static void validateUpdateFields(
            AdaptationActionSpec.UpdateActionSpec updateSpec,
            Class<? extends io.casehub.desiredstate.api.NodeSpec> specClass,
            ObjectMapper mapper) {
        var         config      = mapper.getSerializationConfig();
        var         javaType    = mapper.constructType(specClass);
        var         description = config.introspect(javaType);
        Set<String> validFields = new HashSet<>();
        for (var prop : description.findProperties()) {
            validFields.add(prop.getName());
        }

        Set<String> unknown = new HashSet<>(updateSpec.fields().keySet());
        unknown.removeAll(validFields);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown fields " + unknown + " in update action for nodeType '"
                    + updateSpec.nodeType() + "'. Valid fields: " + validFields);
        }
    }
}
