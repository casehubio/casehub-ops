package io.casehub.ops.api.deployment;

import java.util.Optional;

public interface PoolOperations {

    String backendId();

    Optional<PoolInfo> getPool(String applicationId, String poolId);

    PoolInfo createPool(PoolCreateRequest request);

    PoolInfo updatePool(String applicationId, String poolId, PoolUpdateRequest request);

    void destroyPool(String applicationId, String poolId);
}
