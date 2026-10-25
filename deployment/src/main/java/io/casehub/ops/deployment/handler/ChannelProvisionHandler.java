package io.casehub.ops.deployment.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.ops.api.deployment.ChannelNodeSpec;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.runtime.channel.ChannelService;

@ApplicationScoped
public class ChannelProvisionHandler {

    public interface ChannelOperations {
        Optional<Channel> findByName(String name);
        Channel create(ChannelCreateRequest req);
        void delete(UUID channelId, boolean force);
        Channel setTypeConstraints(UUID channelId, Set<MessageType> allowed, Set<MessageType> denied);
        Channel setRateLimits(UUID channelId, Integer perChannel, Integer perInstance);
        Channel setAllowedWriters(UUID channelId, List<String> allowedWriters);
        Channel setAdminInstances(UUID channelId, List<String> adminInstances);
        Channel setProtocols(UUID channelId, List<String> protocols);
        Channel setProtocolParticipants(UUID channelId, List<String> protocolParticipants);
        Channel setPolicyOverrides(UUID channelId, Map<String, String> policyOverrides);
    }

    private final ChannelOperations ops;

    @Inject
    public ChannelProvisionHandler(ChannelService channelService) {
        this.ops = new ChannelOperations() {
            @Override public Optional<Channel> findByName(String name) { return channelService.findByName(name); }
            @Override public Channel create(ChannelCreateRequest req) { return channelService.create(req); }
            @Override public void delete(UUID channelId, boolean force) { channelService.delete(channelId, force); }
            @Override public Channel setTypeConstraints(UUID id, Set<MessageType> a, Set<MessageType> d) { return channelService.setTypeConstraints(id, a, d); }
            @Override public Channel setRateLimits(UUID id, Integer pc, Integer pi) { return channelService.setRateLimits(id, pc, pi); }
            @Override public Channel setAllowedWriters(UUID id, List<String> w) { return channelService.setAllowedWriters(id, w); }
            @Override public Channel setAdminInstances(UUID id, List<String> a) { return channelService.setAdminInstances(id, a); }
            @Override public Channel setProtocols(UUID id, List<String> p) { return channelService.setProtocols(id, p); }
            @Override public Channel setProtocolParticipants(UUID id, List<String> p) { return channelService.setProtocolParticipants(id, p); }
            @Override public Channel setPolicyOverrides(UUID id, Map<String, String> p) { return channelService.setPolicyOverrides(id, p); }
        };
    }

    public ChannelProvisionHandler(ChannelOperations ops) {
        this.ops = ops;
    }

    public ProvisionResult provision(ChannelNodeSpec spec, ProvisionContext context) {
        Optional<Channel> existing = ops.findByName(spec.name());
        if (existing.isPresent()) {
            updateMutableFields(existing.get(), spec);
            return new ProvisionResult.Success();
        }

        var request = ChannelCreateRequest.builder(spec.name())
                .description(spec.description())
                .semantic(spec.semantic())
                .barrierContributors(toList(spec.barrierContributors()))
                .allowedWriters(toList(spec.allowedWriters()))
                .adminInstances(toList(spec.adminInstances()))
                .rateLimitPerChannel(spec.rateLimitPerChannel())
                .rateLimitPerInstance(spec.rateLimitPerInstance())
                .allowedTypes(spec.allowedTypes())
                .deniedTypes(spec.deniedTypes())
                .protocols(spec.protocols())
                .protocolParticipants(spec.protocolParticipants())
                .inboundConnectorId(spec.inboundConnectorId())
                .externalKey(spec.externalKey())
                .outboundConnectorId(spec.outboundConnectorId())
                .outboundDestination(spec.outboundDestination())
                .build();
        Channel created = ops.create(request);
        if (spec.policyOverrides() != null) {
            ops.setPolicyOverrides(created.id(), spec.policyOverrides());
        }
        return new ProvisionResult.Success();
    }

    public DeprovisionResult deprovision(ChannelNodeSpec spec, DeprovisionContext context) {
        Optional<Channel> existing = ops.findByName(spec.name());
        if (existing.isPresent()) {
            ops.delete(existing.get().id(), true);
        }
        return new DeprovisionResult.Success();
    }

    private void updateMutableFields(Channel existing, ChannelNodeSpec spec) {
        UUID channelId = existing.id();
        ops.setTypeConstraints(channelId, spec.allowedTypes(), spec.deniedTypes());
        ops.setRateLimits(channelId, spec.rateLimitPerChannel(), spec.rateLimitPerInstance());
        ops.setAllowedWriters(channelId, toList(spec.allowedWriters()));
        ops.setAdminInstances(channelId, toList(spec.adminInstances()));
        ops.setProtocols(channelId, spec.protocols());
        ops.setProtocolParticipants(channelId, spec.protocolParticipants());
        ops.setPolicyOverrides(channelId, computePolicyOverrideDiff(spec.policyOverrides(), existing.policyOverrides()));
    }

    private static Map<String, String> computePolicyOverrideDiff(Map<String, String> desired, Map<String, String> actual) {
        if (desired == null) {
            return null;
        }
        Map<String, String> diff = new LinkedHashMap<>(desired);
        if (actual != null) {
            for (String key : actual.keySet()) {
                if (!desired.containsKey(key)) {
                    diff.put(key, null);
                }
            }
        }
        return diff;
    }

    private static List<String> toList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return List.of(csv.split(","));
    }
}
