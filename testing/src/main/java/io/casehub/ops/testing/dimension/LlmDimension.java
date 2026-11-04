package io.casehub.ops.testing.dimension;

import io.casehub.ops.testing.env.FleetAssertions;
import io.casehub.ops.testing.env.HttpFleetExerciser;
import io.casehub.ops.testing.env.LlmResponse;

import java.util.Set;

public class LlmDimension implements TestDimension {

    @Override
    public String name() { return "llm"; }

    @Override
    public Set<String> requires() { return Set.of("deploy"); }

    @Override
    public void execute(ServiceUrls urls, DimensionState state) throws Exception {
        String serviceUrl = state.getString(DeployDimension.SERVICE_URL);
        LlmResponse response = HttpFleetExerciser.exercise(serviceUrl, "e2e-llm");
        FleetAssertions.assertResponseWellFormed(response);
    }
}
