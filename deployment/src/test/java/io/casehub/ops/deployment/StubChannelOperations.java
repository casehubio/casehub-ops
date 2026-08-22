package io.casehub.ops.deployment;

import io.casehub.ops.deployment.handler.ChannelProvisionHandler;
import io.casehub.ops.testing.StubChannelStore;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.message.MessageType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class StubChannelOperations implements ChannelProvisionHandler.ChannelOperations {

    final Map<String, Channel> channels = new ConcurrentHashMap<>();
    final StubChannelStore channelStore;
    final String tenancyId;

    StubChannelOperations(StubChannelStore channelStore, String tenancyId) {
        this.channelStore = channelStore;
        this.tenancyId = tenancyId;
    }

    @Override
    public Optional<Channel> findByName(String name) {
        return Optional.ofNullable(channels.get(name));
    }

    @Override
    public Channel create(ChannelCreateRequest req) {
        Channel ch = Channel.builder(req.name())
                .id(UUID.randomUUID())
                .description(req.description())
                .semantic(req.semantic())
                .allowedTypes(req.allowedTypes())
                .deniedTypes(req.deniedTypes())
                .rateLimitPerChannel(req.rateLimitPerChannel())
                .rateLimitPerInstance(req.rateLimitPerInstance())
                .allowedWriters(req.allowedWriters())
                .adminInstances(req.adminInstances())
                .barrierContributors(req.barrierContributors())
                .build();
        channels.put(ch.name(), ch);
        channelStore.put(ch, tenancyId);
        return ch;
    }

    @Override
    public void delete(UUID channelId, boolean force) {
        channels.values().removeIf(ch -> ch.id().equals(channelId));
        channelStore.channels.entrySet().removeIf(e -> e.getValue().id().equals(channelId));
    }

    @Override
    public Channel setTypeConstraints(UUID channelId, Set<MessageType> allowed, Set<MessageType> denied) {
        for (Channel ch : channels.values()) {
            if (ch.id().equals(channelId)) {
                Channel updated = ch.toBuilder().allowedTypes(allowed).deniedTypes(denied).build();
                channels.put(updated.name(), updated);
                channelStore.put(updated, tenancyId);
                return updated;
            }
        }
        return null;
    }

    @Override
    public Channel setRateLimits(UUID channelId, Integer perChannel, Integer perInstance) {
        for (Channel ch : channels.values()) {
            if (ch.id().equals(channelId)) {
                Channel updated = ch.toBuilder().rateLimitPerChannel(perChannel).rateLimitPerInstance(perInstance).build();
                channels.put(updated.name(), updated);
                channelStore.put(updated, tenancyId);
                return updated;
            }
        }
        return null;
    }

    @Override
    public Channel setAllowedWriters(UUID channelId, List<String> allowedWriters) {
        for (Channel ch : channels.values()) {
            if (ch.id().equals(channelId)) {
                Channel updated = ch.toBuilder().allowedWriters(allowedWriters).build();
                channels.put(updated.name(), updated);
                channelStore.put(updated, tenancyId);
                return updated;
            }
        }
        return null;
    }

    @Override
    public Channel setAdminInstances(UUID channelId, List<String> adminInstances) {
        for (Channel ch : channels.values()) {
            if (ch.id().equals(channelId)) {
                Channel updated = ch.toBuilder().adminInstances(adminInstances).build();
                channels.put(updated.name(), updated);
                channelStore.put(updated, tenancyId);
                return updated;
            }
        }
        return null;
    }
}
