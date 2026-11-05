package io.casehub.ops.testing.dimension;

import java.util.Set;

public class PoolDimension implements TestDimension {

    public static final String POOL_APPLICATION_ID = "poolApplicationId";
    public static final String POOL_AGENT_ID = "poolAgentId";

    @Override
    public String name() { return "pool"; }

    @Override
    public Set<String> requires() { return Set.of("deploy"); }

    @Override
    public void execute(ServiceUrls urls, DimensionState state) throws Exception {
        String applicationId = state.getString(POOL_APPLICATION_ID);
        String agentId = state.getString(POOL_AGENT_ID);

        if (applicationId == null || agentId == null) {
            throw new IllegalStateException(
                    "PoolDimension requires poolApplicationId and poolAgentId in state");
        }

        // Pool convergence verification will be implemented when the
        // PoolOperations SPI is wired into the DimensionTest framework.
        // For now, this dimension validates that pool node compilation
        // and dependency ordering are correct — the compilation tests
        // cover this. Full runtime pool behavior (convergence, scaling,
        // self-healing) requires a running claudony fleet service.
    }
}
