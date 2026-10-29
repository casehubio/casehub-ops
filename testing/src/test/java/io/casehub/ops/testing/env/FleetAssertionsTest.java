package io.casehub.ops.testing.env;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FleetAssertionsTest {

    @Test
    void assertResponseWellFormed_passesForValidResponse() {
        var response = new LlmResponse("app-1", "analyst", "analysis",
                "Some substantive content here", "claude-3", "inst-1");
        assertThatCode(() -> FleetAssertions.assertResponseWellFormed(response))
                .doesNotThrowAnyException();
    }

    @Test
    void assertResponseWellFormed_failsForNullBackendKey() {
        var response = new LlmResponse("app-1", "analyst", "analysis",
                "Some content", null, "inst-1");
        assertThatThrownBy(() -> FleetAssertions.assertResponseWellFormed(response))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertResponseWellFormed_failsForNullInstanceId() {
        var response = new LlmResponse("app-1", "analyst", "analysis",
                "Some content", "claude-3", null);
        assertThatThrownBy(() -> FleetAssertions.assertResponseWellFormed(response))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertResponseWellFormed_failsForBlankContent() {
        var response = new LlmResponse("app-1", "analyst", "analysis",
                "   ", "claude-3", "inst-1");
        assertThatThrownBy(() -> FleetAssertions.assertResponseWellFormed(response))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertRouteConsistent_passesForSameBackend() {
        var r1 = new LlmResponse("app-1", "analyst", "analysis",
                "Content 1", "claude-3", "inst-1");
        var r2 = new LlmResponse("app-2", "coder", "software-engineering",
                "Content 2", "claude-3", "inst-2");
        assertThatCode(() -> FleetAssertions.assertRouteConsistent(List.of(r1, r2)))
                .doesNotThrowAnyException();
    }

    @Test
    void assertRouteConsistent_failsForDifferentBackends() {
        var r1 = new LlmResponse("app-1", "analyst", "analysis",
                "Content 1", "claude-3", "inst-1");
        var r2 = new LlmResponse("app-2", "coder", "software-engineering",
                "Content 2", "openai-4", "inst-2");
        assertThatThrownBy(() -> FleetAssertions.assertRouteConsistent(List.of(r1, r2)))
                .isInstanceOf(AssertionError.class);
    }
}
