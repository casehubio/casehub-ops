package io.casehub.ops.testing.dimension;

import io.casehub.ops.api.deployment.PoolCreateRequest;
import io.casehub.ops.api.deployment.PoolInfo;
import io.casehub.ops.api.deployment.PoolOperations;
import io.casehub.ops.api.deployment.PoolUpdateRequest;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class FailablePoolOperations implements PoolOperations {

    private final PoolOperations delegate;
    private final AtomicInteger failuresRemaining = new AtomicInteger(0);

    public FailablePoolOperations(PoolOperations delegate) {
        this.delegate = delegate;
    }

    public void injectFailures(int count) {
        failuresRemaining.set(count);
    }

    @Override
    public String backendId() { return delegate.backendId(); }

    @Override
    public Optional<PoolInfo> getPool(String applicationId, String poolId) {
        return delegate.getPool(applicationId, poolId);
    }

    @Override
    public PoolInfo createPool(PoolCreateRequest request) {
        if (failuresRemaining.getAndDecrement() > 0) {
            throw new RuntimeException("Injected pool provisioning failure");
        }
        return delegate.createPool(request);
    }

    @Override
    public PoolInfo updatePool(String applicationId, String poolId, PoolUpdateRequest request) {
        if (failuresRemaining.getAndDecrement() > 0) {
            throw new RuntimeException("Injected pool update failure");
        }
        return delegate.updatePool(applicationId, poolId, request);
    }

    @Override
    public void destroyPool(String applicationId, String poolId) {
        delegate.destroyPool(applicationId, poolId);
    }
}
