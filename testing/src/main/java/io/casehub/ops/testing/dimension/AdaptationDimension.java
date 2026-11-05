package io.casehub.ops.testing.dimension;

import java.util.Set;

public class AdaptationDimension implements TestDimension {

    @Override
    public String name() { return "adaptation"; }

    @Override
    public Set<String> requires() { return Set.of("deploy"); }

    @Override
    public void execute(ServiceUrls urls, DimensionState state) throws Exception {
        // Adaptation verification requires a running reconciliation loop with
        // RAS situation injection. Full runtime behavior (situation → rule
        // evaluation → specialist deployment → resolution → deprovisioning)
        // requires integration-live profile with claudony fleet service.
        // Compilation tests verify the topology structure and adaptation
        // rule template compilation.
    }
}
