package io.casehub.ops.service.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.GoalCompiler;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.ReconciliationLoop;
import io.casehub.desiredstate.yaml.YamlGraphRecorder;
import io.casehub.desiredstate.yaml.model.YamlGraph;
import io.casehub.yaml.core.module.YamlModule;
import io.casehub.yaml.core.module.YamlModuleFile;
import io.casehub.yaml.jackson.YamlMappers;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class TopologyCompilerService {

    private static final ObjectMapper YAML_MAPPER = YamlMappers.createWithCoreModule();
    private static final DefaultDesiredStateGraphFactory GRAPH_FACTORY = new DefaultDesiredStateGraphFactory();

    @Inject
    ReconciliationLoop reconciliationLoop;

    @Inject
    NodeTypeRegistryProducer registryProducer;

    @ConfigProperty(name = "casehub.ops.topology.path")
    Optional<String> topologyPath;

    public TopologyCompilerService() {}

    public List<TopologyDescriptor> listTopologies() {
        List<TopologyDescriptor> result = new ArrayList<>();
        topologyPath.filter(p -> !p.isBlank())
                .ifPresent(p -> result.addAll(listTopologiesFromPath(p)));
        return result;
    }

    public List<TopologyDescriptor> listTopologiesFromPath(String directoryPath) {
        List<TopologyDescriptor> result = new ArrayList<>();
        Path dir = Path.of(directoryPath);
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isDirectory)
                    .sorted()
                    .forEach(subdir -> {
                        String name = subdir.getFileName().toString();
                        result.add(new TopologyDescriptor(name, subdir.toString(), "filesystem"));
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list topologies: " + directoryPath, e);
        }
        return result;
    }

    public DesiredStateGraph compileTopology(String topologyName) {
        String basePath = topologyPath.orElseThrow(() ->
                                                           new IllegalStateException("casehub.ops.topology.path not configured"));
        return compileTopologyFromPath(basePath, topologyName);
    }

    DesiredStateGraph compileTopologyFromPath(String basePath, String topologyName) {
        String topologyDir = basePath + "/" + topologyName;
        Path   dir         = Path.of(topologyDir);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Topology directory not found: " + topologyDir);
        }

        Map<String, String> classRegistry = NodeTypeRegistryProducer.scanSealedInterfaces(
                io.casehub.ops.api.deployment.DeploymentNodeSpec.class,
                io.casehub.ops.api.infra.InfraNodeSpec.class);

        try {
            List<Path> yamlFiles;
            try (Stream<Path> files = Files.list(dir)) {
                yamlFiles = files.filter(p -> {
                                     String name = p.getFileName().toString().toLowerCase();
                                     return name.endsWith(".yaml") || name.endsWith(".yml");
                                 })
                                 .filter(p -> !p.getFileName().toString().startsWith("topology-metadata"))
                                 .filter(p -> !p.getFileName().toString().equals("playbook.yaml"))
                                 .sorted().toList();
            }

            if (yamlFiles.isEmpty()) {
                throw new IllegalArgumentException("No topology YAML files in: " + topologyName);
            }

            List<io.casehub.desiredstate.api.DesiredNode> allNodes = new ArrayList<>();
            List<io.casehub.desiredstate.api.Dependency>  allDeps  = new ArrayList<>();

            for (Path yamlFile : yamlFiles) {
                CompilationResult result = compileDomain(yamlFile.toString(), classRegistry);
                if (result instanceof CompilationResult.SingleGraph sg) {
                    allNodes.addAll(sg.graph().nodes().values().stream().toList());
                    allDeps.addAll(sg.graph().dependencies());
                }
            }

            return GRAPH_FACTORY.of(allNodes, allDeps);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compile topology: " + topologyName, e);
        }
    }


    public String deploy(String tenancyId, String topologiesBasePath, String topologyName) {
        DesiredStateGraph composedGraph = compileTopologyFromPath(topologiesBasePath, topologyName);
        String            loopKey       = "topology:" + tenancyId + ":" + topologyName;

        try {
            reconciliationLoop.start(loopKey, composedGraph);
        } catch (IllegalStateException e) {
            reconciliationLoop.updateDesired(loopKey, composedGraph);
        }

        return loopKey;
    }

    public void stop(String tenancyId, String topologyName) {
        String loopKey = "topology:" + tenancyId + ":" + topologyName;
        reconciliationLoop.stop(loopKey);
    }

    public boolean isRunning(String tenancyId, String topologyName) {
        String loopKey = "topology:" + tenancyId + ":" + topologyName;
        return reconciliationLoop.tenantIds().contains(loopKey);
    }


    public CompilationResult compileDomain(String yamlPath,
                                            Map<String, String> typeRegistry) throws IOException {
        YamlGraph yamlGraph = parseYaml(yamlPath);
        Map<String, YamlModule> modules = discoverModules();
        YamlGraphRecorder recorder = new YamlGraphRecorder();

        List<io.casehub.desiredstate.annotations.runtime.ResolvedInvariant> invariants =
                new ArrayList<>();
        for (var inv : yamlGraph.invariants().entrySet()) {
            invariants.add(io.casehub.desiredstate.yaml.YamlInvariantConverter
                    .toDeclarativeInvariant(inv.getKey(), inv.getValue()));
        }

        Map<String, Object> variables = yamlGraph.variables() != null
                ? yamlGraph.variables() : Map.of();

        if (yamlGraph.lifecycle() != null) {
            return recorder.createYamlLifecycleGoalCompiler(
                    yamlGraph, typeRegistry, variables, invariants)
                    .getValue()
                    .compile(null, GRAPH_FACTORY);
        }

        GoalCompiler<Void> compiler = recorder.createYamlGoalCompiler(
                toGraphDescriptor(yamlGraph, typeRegistry), typeRegistry,
                variables, invariants, yamlGraph, modules).getValue();

        return compiler.compile(null, GRAPH_FACTORY);
    }

    private YamlGraph parseYaml(String path) throws IOException {
        Path filePath = Path.of(path);
        if (Files.exists(filePath)) {
            return YAML_MAPPER.readValue(filePath.toFile(), YamlGraph.class);
        }
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("YAML not found: " + path);
            }
            return YAML_MAPPER.readValue(is, YamlGraph.class);
        }
    }

    Map<String, YamlModule> discoverModules() {
        Map<String, YamlModule> modules = new HashMap<>();
        String[] moduleNames = {"load-balancer", "ha-multi-az", "service-mesh", "multi-region"};
        for (String name : moduleNames) {
            String resourcePath = "META-INF/desiredstate/modules/" + name + ".yaml";
            try (InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resourcePath)) {
                if (is != null) {
                    YamlModuleFile moduleFile = YAML_MAPPER.readValue(is, YamlModuleFile.class);
                    modules.put(moduleFile.toModule().name(), moduleFile.toModule());
                }
            } catch (IOException e) {
                // Module not available — skip
            }
        }
        return modules;
    }

    static io.casehub.desiredstate.annotations.runtime.GraphDescriptor toGraphDescriptor(
            YamlGraph yamlGraph, Map<String, String> typeRegistry) {
        List<io.casehub.desiredstate.annotations.runtime.NodeDescriptor> nodes = new ArrayList<>();
        List<io.casehub.desiredstate.annotations.runtime.DependencyDescriptor> deps = new ArrayList<>();
        for (var entry : yamlGraph.nodes().entrySet()) {
            String nodeId = entry.getKey();
            var yamlNode = entry.getValue();
            String specClassName = typeRegistry.get(yamlNode.type());
            nodes.add(new io.casehub.desiredstate.annotations.runtime.NodeDescriptor.InlineNode(
                    nodeId, specClassName,
                    yamlNode.spec() != null ? yamlNode.spec() : Map.of(),
                    yamlNode.humanGating()));
            for (String dep : yamlNode.dependencyNodeIds()) {
                deps.add(new io.casehub.desiredstate.annotations.runtime.DependencyDescriptor(
                        nodeId, dep));
            }
        }
        return new io.casehub.desiredstate.annotations.runtime.GraphDescriptor(
                yamlGraph.desiredState().namespace(),
                yamlGraph.desiredState().name(),
                null, null, nodes, deps,
                List.of(), null, List.of(), List.of());
    }
}
