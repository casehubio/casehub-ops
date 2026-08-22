package io.casehub.ops.testing;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StubChannelStore implements CrossTenantChannelStore {

    public final Map<String, Channel> channels = new ConcurrentHashMap<>();

    @Override
    public Optional<Channel> findByNameAndTenancy(String name, String tenancyId) {
        return Optional.ofNullable(channels.get(key(name, tenancyId)));
    }

    @Override
    public List<Channel> listAll() {
        return new ArrayList<>(channels.values());
    }

    @Override
    public Optional<Channel> findById(UUID id) {
        return channels.values().stream()
                .filter(ch -> ch.id().equals(id))
                .findFirst();
    }

    public void put(Channel channel, String tenancyId) {
        channels.put(key(channel.name(), tenancyId), channel);
    }

    private static String key(String name, String tenancyId) {
        return name + ":" + tenancyId;
    }
}
