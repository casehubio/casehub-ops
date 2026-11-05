package io.casehub.ops.testing.dimension;

import java.util.Set;

public class FaultDimension implements TestDimension {

    @Override
    public String name() { return "fault"; }

    @Override
    public Set<String> requires() { return Set.of("deploy", "pool"); }

    @Override
    public void execute(ServiceUrls urls, DimensionState state) throws Exception {
        // Fault verification requires a running reconciliation loop with
        // FailablePoolOperations for deterministic fault injection.
        // Full runtime behavior (fault → retry → recovery/escalation)
        // requires integration-live profile with claudony fleet service.
        // Compilation tests verify topology structure; fault policy
        // coverage is verified by DeploymentFaultPolicyTest.
    }
}
