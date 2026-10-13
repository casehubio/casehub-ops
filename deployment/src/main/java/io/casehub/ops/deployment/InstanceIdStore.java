package io.casehub.ops.deployment;

import io.casehub.desiredstate.api.NodeId;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InstanceIdStore {
    private final ConcurrentHashMap<NodeId, String> instances = new ConcurrentHashMap<>();

    public void record(NodeId nodeId, String instanceId) {
        instances.put(nodeId, instanceId);
    }

    public Optional<String> get(NodeId nodeId) {
        return Optional.ofNullable(instances.get(nodeId));
    }

    public void remove(NodeId nodeId) {
        instances.remove(nodeId);
    }
}
