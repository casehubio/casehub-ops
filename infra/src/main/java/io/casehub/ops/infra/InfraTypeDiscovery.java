package io.casehub.ops.infra;

import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.InfraNodeSpec;

import java.util.HashSet;
import java.util.Set;

final class InfraTypeDiscovery {

    private InfraTypeDiscovery() {}

    static Set<NodeType> discoverHandledTypes() {
        Set<NodeType> types = new HashSet<>();
        for (Class<?> permit : InfraNodeSpec.class.getPermittedSubclasses()) {
            NodeTypeId ann = permit.getAnnotation(NodeTypeId.class);
            if (ann != null) {
                types.add(NodeType.of(ann.value()));
            }
        }
        return Set.copyOf(types);
    }
}
