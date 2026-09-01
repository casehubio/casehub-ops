package io.casehub.ops.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.ops.api.deployment.AdaptationRuleSpec;
import io.casehub.ops.api.deployment.AgentNodeSpec;
import io.casehub.ops.api.deployment.CaseTypeNodeSpec;
import io.casehub.ops.api.deployment.ChannelNodeSpec;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ops.api.deployment.DetectionNodeSpec;
import io.casehub.ops.api.deployment.EndpointNodeSpec;
import io.casehub.ops.api.deployment.GoalEntry;
import io.casehub.ops.api.deployment.TrustPolicyNodeSpec;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

@ApplicationScoped
public class DeploymentGoalLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
                                                    .registerModule(new JavaTimeModule());

    public DeploymentGoals load(String path) {
        try (InputStream stream = resolveStream(path)) {
            return yamlMapper.readValue(stream, DeploymentGoals.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse deployment YAML: " + path, e);
        }
    }

    public DeploymentGoals loadDirectory(String directoryPath) {
        Path dir = Path.of(directoryPath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory: " + directoryPath);
        }
        var fragments = new ArrayList<DeploymentGoals>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.endsWith(".yaml") || name.endsWith(".yml");
            }).sorted().forEach(p -> {
                try {
                    fragments.add(yamlMapper.readValue(p.toFile(), DeploymentGoals.class));
                } catch (IOException e) {
                    throw new IllegalArgumentException("Failed to parse: " + p, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to list directory: " + directoryPath, e);
        }
        return merge(fragments.toArray(new DeploymentGoals[0]));
    }

    public DeploymentGoals merge(DeploymentGoals... fragments) {
        var agents      = new ArrayList<GoalEntry<AgentNodeSpec>>();
        var channels    = new ArrayList<GoalEntry<ChannelNodeSpec>>();
        var caseTypes   = new ArrayList<GoalEntry<CaseTypeNodeSpec>>();
        var trust       = new ArrayList<GoalEntry<TrustPolicyNodeSpec>>();
        var endpoints   = new ArrayList<GoalEntry<EndpointNodeSpec>>();
        var detections  = new ArrayList<GoalEntry<DetectionNodeSpec>>();
        var adaptations = new ArrayList<AdaptationRuleSpec>();
        for (var f : fragments) {
            agents.addAll(f.agents());
            channels.addAll(f.channels());
            caseTypes.addAll(f.caseTypes());
            trust.addAll(f.trust());
            endpoints.addAll(f.endpoints());
            detections.addAll(f.detections());
            adaptations.addAll(f.adaptations());
        }
        return new DeploymentGoals(agents, channels, caseTypes, trust, endpoints, detections, adaptations);
    }

    private InputStream resolveStream(String path) {
        InputStream classpath = Thread.currentThread().getContextClassLoader()
                                      .getResourceAsStream(path);
        if (classpath != null) {return classpath;}
        Path filePath = Path.of(path);
        if (Files.exists(filePath)) {
            try {
                return Files.newInputStream(filePath);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read file: " + path, e);
            }
        }
        throw new IllegalArgumentException("Deployment YAML not found: " + path);
    }
}
