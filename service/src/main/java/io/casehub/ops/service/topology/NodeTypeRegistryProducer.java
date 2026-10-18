package io.casehub.ops.service.topology;

import io.casehub.desiredstate.api.NodeSpecFactory;
import io.casehub.desiredstate.api.NodeSpecFactoryProvider;
import io.casehub.desiredstate.api.NodeTypeId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class NodeTypeRegistryProducer {

    @Inject
    Instance<NodeSpecFactoryProvider> factoryProviders;

    public static Map<String, String> scanSealedInterfaces(Class<?>... sealedInterfaces) {
        Map<String, String> registry = new HashMap<>();
        for (Class<?> sealedIface : sealedInterfaces) {
            if (sealedIface.isSealed()) {
                for (Class<?> permit : sealedIface.getPermittedSubclasses()) {
                    NodeTypeId ann = permit.getAnnotation(NodeTypeId.class);
                    if (ann != null) {
                        registry.put(ann.value(), permit.getName());
                    }
                }
            }
        }
        return registry;
    }

    public Map<String, NodeSpecFactory> collectFactories(NodeSpecFactoryProvider... providers) {
        Map<String, NodeSpecFactory> factories = new HashMap<>();
        for (NodeSpecFactoryProvider provider : providers) {
            factories.putAll(provider.provide());
        }
        return factories;
    }

    public Map<String, NodeSpecFactory> collectFactoriesFromCdi() {
        Map<String, NodeSpecFactory> factories = new HashMap<>();
        if (factoryProviders != null) {
            for (NodeSpecFactoryProvider provider : factoryProviders) {
                factories.putAll(provider.provide());
            }
        }
        return factories;
    }
}
