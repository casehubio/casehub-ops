package io.casehub.ops.topology.compilation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.desiredstate.annotations.runtime.DependencyDescriptor;
import io.casehub.desiredstate.annotations.runtime.GraphDescriptor;
import io.casehub.desiredstate.annotations.runtime.NodeDescriptor;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.GoalCompiler;
import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.yaml.YamlGraphRecorder;
import io.casehub.desiredstate.yaml.model.YamlGraph;
import io.casehub.desiredstate.yaml.model.YamlModule;
import io.casehub.desiredstate.yaml.model.YamlModuleFile;
import io.casehub.desiredstate.yaml.model.YamlNode;
import io.casehub.ops.api.infra.InfraNodeSpec;
import io.casehub.ops.infra.InfraNodeSpecFactoryProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class TopologyTestBase {

    private static final ObjectMapper                    YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final DefaultDesiredStateGraphFactory FACTORY     = new DefaultDesiredStateGraphFactory();

    public static Map<String, String> buildTypeRegistry() {
        Map<String, String> registry = new HashMap<>();
        for (Class<?> permit : InfraNodeSpec.class.getPermittedSubclasses()) {
            NodeTypeId ann = permit.getAnnotation(NodeTypeId.class);
            if (ann != null) {
                registry.put(ann.value(), permit.getName());
            }
        }
        return registry;
    }

    @SuppressWarnings("unchecked")
    public static GoalCompiler<Void> compileExemplar(String resourcePath) throws IOException {
        Map<String, String>     typeRegistry = buildTypeRegistry();
        YamlGraph               yamlGraph    = parseYaml(resourcePath);
        Map<String, YamlModule> modules      = discoverModules();

        List<io.casehub.desiredstate.annotations.runtime.ResolvedInvariant> invariants = new ArrayList<>();
        for (var inv : yamlGraph.invariants().entrySet()) {
            invariants.add(io.casehub.desiredstate.yaml.YamlInvariantConverter
                                   .toDeclarativeInvariant(inv.getKey(), inv.getValue()));
        }

        YamlGraphRecorder recorder = new YamlGraphRecorder();

        if (yamlGraph.lifecycle() != null) {
            return recorder.createYamlLifecycleGoalCompiler(
                    yamlGraph, typeRegistry,
                    yamlGraph.variables() != null ? yamlGraph.variables() : Map.of(),
                    invariants, modules,
                    List.of(InfraNodeSpecFactoryProvider.class.getName())).getValue();
        }

        GraphDescriptor descriptor = toGraphDescriptor(yamlGraph, typeRegistry);
        return recorder.createYamlGoalCompiler(
                descriptor, typeRegistry,
                yamlGraph.variables() != null ? yamlGraph.variables() : Map.of(),
                invariants, yamlGraph, modules,
                List.of(), List.of(),
                List.of(InfraNodeSpecFactoryProvider.class.getName())).getValue();
    }

    public static DesiredStateGraph compileSingleGraph(String resourcePath) throws IOException {
        GoalCompiler<Void> compiler = compileExemplar(resourcePath);
        CompilationResult  result   = compiler.compile(null, FACTORY);
        assertThat(result).isInstanceOf(CompilationResult.SingleGraph.class);
        return ((CompilationResult.SingleGraph) result).graph();
    }

    public static CompilationResult.Lifecycle compileLifecycle(String resourcePath) throws IOException {
        GoalCompiler<Void> compiler = compileExemplar(resourcePath);
        CompilationResult  result   = compiler.compile(null, FACTORY);
        assertThat(result).isInstanceOf(CompilationResult.Lifecycle.class);
        return (CompilationResult.Lifecycle) result;
    }

    private static YamlGraph parseYaml(String resourcePath) throws IOException {
        try (InputStream is = TopologyTestBase.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(is).as("YAML file must be on classpath: " + resourcePath).isNotNull();
            return YAML_MAPPER.readValue(is, YamlGraph.class);
        }
    }

    private static Map<String, YamlModule> discoverModules() throws IOException {
        Map<String, YamlModule> modules     = new HashMap<>();
        String[]                moduleNames = {"load-balancer", "ha-multi-az", "service-mesh", "multi-region"};
        for (String name : moduleNames) {
            String path = "META-INF/desiredstate/modules/" + name + ".yaml";
            try (InputStream is = TopologyTestBase.class.getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    YamlModuleFile moduleFile = YAML_MAPPER.readValue(is, YamlModuleFile.class);
                    modules.put(moduleFile.toModule().name(), moduleFile.toModule());
                }
            }
        }
        return modules;
    }

    private static GraphDescriptor toGraphDescriptor(YamlGraph yamlGraph, Map<String, String> typeRegistry) {
        List<NodeDescriptor>       nodes = new ArrayList<>();
        List<DependencyDescriptor> deps  = new ArrayList<>();
        for (Map.Entry<String, YamlNode> entry : yamlGraph.nodes().entrySet()) {
            String   nodeId        = entry.getKey();
            YamlNode yamlNode      = entry.getValue();
            String   specClassName = typeRegistry.get(yamlNode.type());
            nodes.add(new NodeDescriptor.InlineNode(nodeId, specClassName,
                                                    yamlNode.spec() != null ? yamlNode.spec() : Map.of(),
                                                    yamlNode.humanGating()));
            for (String dep : yamlNode.dependencyNodeIds()) {
                deps.add(new DependencyDescriptor(nodeId, dep));
            }
        }
        return new GraphDescriptor(
                yamlGraph.desiredState().namespace(),
                yamlGraph.desiredState().name(),
                null, null, nodes, deps,
                List.of(), null, List.of(), List.of());
    }
}
