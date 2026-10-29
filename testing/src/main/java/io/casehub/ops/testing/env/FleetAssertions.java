package io.casehub.ops.testing.env;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public final class FleetAssertions {

    private FleetAssertions() {}

    public static void assertResponseWellFormed(LlmResponse response) {
        assertThat(response.backendKey())
                .as("LLM %s must have a backend key", response.appId())
                .isNotNull();
        assertThat(response.instanceId())
                .as("LLM %s must have an instance ID", response.appId())
                .isNotNull();
        assertThat(response.content())
                .as("LLM %s must produce non-blank content", response.appId())
                .isNotBlank();
    }

    public static void assertRouteConsistent(List<LlmResponse> responses) {
        long distinctBackends = responses.stream()
                .map(LlmResponse::backendKey)
                .distinct()
                .count();
        assertThat(distinctBackends)
                .as("All fleet members should route through the same backend — got: %s",
                        responses.stream().map(r -> r.appId() + "→" + r.backendKey()).toList())
                .isEqualTo(1);
    }
}
