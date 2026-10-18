package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ClaudonySpecHashStore {
    private final ConcurrentHashMap<NodeId, Integer> hashes = new ConcurrentHashMap<>();

    public void record(NodeId id, NodeSpec spec) { hashes.put(id, spec.hashCode()); }
    public void remove(NodeId id) { hashes.remove(id); }
    public boolean hasDrifted(NodeId id, NodeSpec spec) {
        Integer stored = hashes.get(id);
        return stored == null || !stored.equals(spec.hashCode());
    }
}
