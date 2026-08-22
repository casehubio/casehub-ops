package io.casehub.ops.testing;

import io.casehub.qhorus.api.channel.ChannelConnectorBinding;
import io.casehub.qhorus.api.store.ChannelBindingStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StubChannelBindingStore implements ChannelBindingStore {

    final Map<UUID, ChannelConnectorBinding> bindings = new ConcurrentHashMap<>();

    @Override
    public Optional<ChannelConnectorBinding> findByChannelId(UUID channelId) {
        return Optional.ofNullable(bindings.get(channelId));
    }

    @Override
    public Optional<ChannelConnectorBinding> findByKey(String inboundConnectorId, String externalKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void put(ChannelConnectorBinding binding) {
        bindings.put(binding.channelId(), binding);
    }

    @Override
    public void delete(UUID channelId) {
        bindings.remove(channelId);
    }

    @Override
    public Map<UUID, ChannelConnectorBinding> findAll() {
        return new HashMap<>(bindings);
    }
}
