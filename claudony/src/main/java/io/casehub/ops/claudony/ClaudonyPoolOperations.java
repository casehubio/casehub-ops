package io.casehub.ops.claudony;

import io.casehub.ops.api.deployment.PoolCreateRequest;
import io.casehub.ops.api.deployment.PoolInfo;
import io.casehub.ops.api.deployment.PoolOperations;
import io.casehub.ops.api.deployment.PoolUpdateRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class ClaudonyPoolOperations implements PoolOperations {

    private final ClaudonyFleetClient fleetClient;
    private final ConcurrentHashMap<String, ReentrantLock> appLocks = new ConcurrentHashMap<>();

    @Inject
    public ClaudonyPoolOperations(ClaudonyFleetClient fleetClient) {
        this.fleetClient = fleetClient;
    }

    @Override
    public String backendId() {
        return "claudony";
    }

    @Override
    public Optional<PoolInfo> getPool(String applicationId, String poolId) {
        var appInfo = fleetClient.getApp(applicationId);
        if (appInfo.isEmpty()) return Optional.empty();
        return appInfo.get().pools().stream()
                .filter(p -> poolId.equals(p.agentId()))
                .findFirst()
                .map(p -> new PoolInfo(applicationId, p.agentId(), "ACTIVE",
                        p.minActive(), p.maxActive(), null, null, null, null, null));
    }

    @Override
    public PoolInfo createPool(PoolCreateRequest request) {
        var lock = appLocks.computeIfAbsent(request.applicationId(), k -> new ReentrantLock());
        lock.lock();
        try {
            var appInfo = fleetClient.getApp(request.applicationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "parent app not registered: " + request.applicationId()));
            var pools = new ArrayList<>(appInfo.pools().stream()
                    .filter(p -> !request.agentId().equals(p.agentId()))
                    .map(p -> new ClaudonyAppSpec.PoolSpec(p.agentId(), p.minActive(), p.maxActive()))
                    .toList());
            pools.add(new ClaudonyAppSpec.PoolSpec(request.agentId(), request.minActive(), request.maxActive()));

            var updated = new ClaudonyAppSpec(
                    appInfo.name(), appInfo.namespace(), appInfo.displayName(),
                    appInfo.description(), appInfo.image(), appInfo.config(), pools);
            fleetClient.updateApp(request.applicationId(), updated);
            return new PoolInfo(request.applicationId(), request.agentId(), "ACTIVE",
                    request.minActive(), request.maxActive(), null, null, null, null, null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PoolInfo updatePool(String applicationId, String poolId, PoolUpdateRequest request) {
        var lock = appLocks.computeIfAbsent(applicationId, k -> new ReentrantLock());
        lock.lock();
        try {
            var appInfo = fleetClient.getApp(applicationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "parent app not registered: " + applicationId));
            var pools = new ArrayList<>(appInfo.pools().stream()
                    .filter(p -> !poolId.equals(p.agentId()))
                    .map(p -> new ClaudonyAppSpec.PoolSpec(p.agentId(), p.minActive(), p.maxActive()))
                    .toList());
            var existing = appInfo.pools().stream()
                    .filter(p -> poolId.equals(p.agentId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("pool not found: " + poolId));
            int min = request.minActive() != null ? request.minActive() : existing.minActive();
            int max = request.maxActive() != null ? request.maxActive() : existing.maxActive();
            pools.add(new ClaudonyAppSpec.PoolSpec(poolId, min, max));

            var updated = new ClaudonyAppSpec(
                    appInfo.name(), appInfo.namespace(), appInfo.displayName(),
                    appInfo.description(), appInfo.image(), appInfo.config(), pools);
            fleetClient.updateApp(applicationId, updated);
            return new PoolInfo(applicationId, poolId, "ACTIVE", min, max, null, null, null, null, null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void destroyPool(String applicationId, String poolId) {
        var lock = appLocks.computeIfAbsent(applicationId, k -> new ReentrantLock());
        lock.lock();
        try {
            var appInfo = fleetClient.getApp(applicationId);
            if (appInfo.isEmpty()) return;
            var pools = appInfo.get().pools().stream()
                    .filter(p -> !poolId.equals(p.agentId()))
                    .map(p -> new ClaudonyAppSpec.PoolSpec(p.agentId(), p.minActive(), p.maxActive()))
                    .toList();
            var updated = new ClaudonyAppSpec(
                    appInfo.get().name(), appInfo.get().namespace(), appInfo.get().displayName(),
                    appInfo.get().description(), appInfo.get().image(), appInfo.get().config(), pools);
            fleetClient.updateApp(applicationId, updated);
        } finally {
            lock.unlock();
        }
    }
}
