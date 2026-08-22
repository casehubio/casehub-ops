package io.casehub.ops.testing;

import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointQuery;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.path.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class StubEndpointRegistry implements EndpointRegistry {

    private final Map<String, EndpointDescriptor> endpoints = new ConcurrentHashMap<>();

    @Override
    public void register(EndpointDescriptor endpoint) {
        endpoints.put(key(endpoint.path(), endpoint.tenancyId()), endpoint);
    }

    @Override
    public Optional<EndpointDescriptor> resolve(Path path, String tenancyId) {
        return Optional.ofNullable(endpoints.get(key(path, tenancyId)));
    }

    @Override
    public List<EndpointDescriptor> discover(EndpointQuery query) {
        return new ArrayList<>(endpoints.values());
    }

    @Override
    public void deregister(Path path, String tenancyId) {
        endpoints.remove(key(path, tenancyId));
    }

    public boolean contains(String path, String tenancyId) {
        return endpoints.containsKey(path + ":" + tenancyId);
    }

    private static String key(Path path, String tenancyId) {
        return path.value() + ":" + tenancyId;
    }
}
