package io.casehub.ops.api.deployment;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChannelNodeSpec(
        String name,
        String description,
        ChannelSemantic semantic,
        Set<MessageType> allowedTypes,
        Set<MessageType> deniedTypes,
        String allowedWriters,
        String adminInstances,
        String barrierContributors,
        Integer rateLimitPerChannel,
        Integer rateLimitPerInstance,
        String inboundConnectorId,
        String externalKey,
        String outboundConnectorId,
        String outboundDestination,
        List<String> protocols,
        @JsonAlias("protocol-participants") List<String> protocolParticipants,
        @JsonAlias("policy-overrides") Map<String, String> policyOverrides
) implements DeploymentNodeSpec {

    public ChannelNodeSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("channel name is required");
        }
        if (semantic == null) {
            throw new IllegalArgumentException("channel semantic is required");
        }
        allowedTypes = allowedTypes != null ? Set.copyOf(allowedTypes) : null;
        deniedTypes = deniedTypes != null ? Set.copyOf(deniedTypes) : null;
        protocols = protocols != null ? List.copyOf(protocols) : null;
        protocolParticipants = protocolParticipants != null ? List.copyOf(protocolParticipants) : null;
        policyOverrides = policyOverrides != null ? Map.copyOf(policyOverrides) : null;
    }

    @Override
    public String nodeId() {
        return name;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.of("channel");
    }
}
