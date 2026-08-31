package io.casehub.ops.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.NodeSpecFactory;
import io.casehub.desiredstate.api.NodeSpecFactoryProvider;
import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.InfraNodeSpec;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.HashMap;
import java.util.Map;

public class InfraNodeSpecFactoryProvider implements NodeSpecFactoryProvider {

    private static final String DEFAULT_BACKEND_KEY = "casehub.desiredstate.infra.default-backend";
    private static final String DEFAULT_BACKEND_FALLBACK = "standalone";

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, NodeSpecFactory> provide() {
        String defaultBackend = ConfigProvider.getConfig()
                .getOptionalValue(DEFAULT_BACKEND_KEY, String.class)
                .orElse(DEFAULT_BACKEND_FALLBACK);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, NodeSpecFactory> factories = new HashMap<>();

        for (Class<?> permit : InfraNodeSpec.class.getPermittedSubclasses()) {
            NodeTypeId ann = permit.getAnnotation(NodeTypeId.class);
            if (ann != null) {
                factories.put(ann.value(), new InfraWrappingFactory(
                        (Class<? extends InfraNodeSpec>) permit, mapper, defaultBackend));
            }
        }
        return factories;
    }
}
