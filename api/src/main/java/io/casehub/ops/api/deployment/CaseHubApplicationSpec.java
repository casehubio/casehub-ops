package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.NodeTypeId;

import java.util.List;
import java.util.Map;

@NodeTypeId("casehub_application")
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaseHubApplicationSpec(
    String applicationId,
    String source,
    String image,
    RuntimeConfig runtime,
    Map<String, Object> config,
    List<String> dependencies,
    int replicas
) implements DeploymentNodeSpec {

    public CaseHubApplicationSpec {
        if (applicationId == null || applicationId.isBlank())
            throw new IllegalArgumentException("applicationId is required");
        if (source == null && image == null)
            throw new IllegalArgumentException("source or image is required");
        config = config != null ? Map.copyOf(config) : Map.of();
        dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        if (replicas < 1) replicas = 1;
    }

    @Override
    public String nodeId() { return applicationId; }

    @Override
    public NodeType nodeType() { return NodeType.of("casehub_application"); }
}
