package io.casehub.ops.testing.dimension;

import io.casehub.ops.testing.env.FleetAssertions;
import io.casehub.ops.testing.env.HttpFleetExerciser;
import io.casehub.ops.testing.env.LlmResponse;
import io.casehub.ops.testing.env.ServiceHealthWaiter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FleetDimension implements TestDimension {

    @Override
    public String name() { return "fleet"; }

    @Override
    public Set<String> requires() { return Set.of("deploy"); }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ServiceUrls urls, DimensionState state) throws Exception {
        List<String> containerNames = state.get("containerNames", List.class);

        List<LlmResponse> responses = new ArrayList<>();
        for (String name : containerNames) {
            String serviceUrl = urls.serviceUrl(name);
            ServiceHealthWaiter.waitForReady(serviceUrl, "/q/health/ready",
                    Duration.ofSeconds(30));
            LlmResponse response = HttpFleetExerciser.exercise(serviceUrl, name);
            FleetAssertions.assertResponseWellFormed(response);
            responses.add(response);
        }

        FleetAssertions.assertRouteConsistent(responses);
    }
}
