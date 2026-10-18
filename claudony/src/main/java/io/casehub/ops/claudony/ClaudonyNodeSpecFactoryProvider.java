package io.casehub.ops.claudony;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.NodeSpecFactory;
import io.casehub.desiredstate.api.NodeSpecFactoryProvider;
import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;

import java.util.HashMap;
import java.util.Map;

public class ClaudonyNodeSpecFactoryProvider implements NodeSpecFactoryProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Map<String, NodeSpecFactory> provide() {
        Map<String, NodeSpecFactory> factories = new HashMap<>();
        for (Class<?> permit : ClaudonyNodeSpec.class.getPermittedSubclasses()) {
            NodeTypeId ann = permit.getAnnotation(NodeTypeId.class);
            if (ann != null) {
                @SuppressWarnings("unchecked")
                var specClass = (Class<? extends ClaudonyNodeSpec>) permit;
                factories.put(ann.value(), specMap -> MAPPER.convertValue(specMap, specClass));
            }
        }
        return factories;
    }
}
