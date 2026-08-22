package io.casehub.ops.testing;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class StubAgentRegistry implements AgentRegistry {

    private final Map<String, AgentDescriptor> agents = new ConcurrentHashMap<>();

    @Override
    public void register(AgentDescriptor descriptor) {
        agents.put(key(descriptor.agentId(), descriptor.tenancyId()), descriptor);
    }

    @Override
    public Optional<AgentDescriptor> findById(String agentId, String tenancyId) {
        return Optional.ofNullable(agents.get(key(agentId, tenancyId)));
    }

    @Override
    public List<AgentMatch> find(AgentQuery query) {
        return agents.values().stream()
                .map(d -> new AgentMatch(d, null))
                .collect(Collectors.toList());
    }

    public boolean contains(String agentId, String tenancyId) {
        return agents.containsKey(key(agentId, tenancyId));
    }

    private static String key(String agentId, String tenancyId) {
        return agentId + ":" + tenancyId;
    }
}
