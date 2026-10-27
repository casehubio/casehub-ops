package io.casehub.ops.testing.env;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmAssertionsTest {

    @Test
    void assertSubstantive_passesForValidResponse() {
        var response = new LlmResponse("app-1", "analyst", "analysis",
                "Check the error logs and identify the root cause pattern.",
                "claude", "inst-1");
        assertThatCode(() -> LlmAssertions.assertSubstantive(response))
                .doesNotThrowAnyException();
    }

    @Test
    void assertSubstantive_failsForBlankContent() {
        var response = new LlmResponse("app-1", "analyst", "analysis",
                "", "claude", "inst-1");
        assertThatThrownBy(() -> LlmAssertions.assertSubstantive(response))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertDomainRelevant_passesForAnalysisDomain() {
        var response = new LlmResponse("app-1", "analyst", "analysis",
                "I would analyze the error patterns and investigate the root cause.",
                "claude", "inst-1");
        assertThatCode(() -> LlmAssertions.assertDomainRelevant(response))
                .doesNotThrowAnyException();
    }

    @Test
    void assertDomainRelevant_skipsKeywordCheckForSimulatedBackend() {
        var response = new LlmResponse("app-1", "analyst", "analysis",
                "This is a simulated response text.",
                "simulation", "inst-1");
        assertThatCode(() -> LlmAssertions.assertDomainRelevant(response))
                .doesNotThrowAnyException();
    }

    @Test
    void assertDifferentiated_passesForDistinctResponses() {
        var r1 = new LlmResponse("app-1", "analyst", "analysis",
                "Analyze the logs for patterns.", "claude", "inst-1");
        var r2 = new LlmResponse("app-2", "engineer", "software-engineering",
                "Check the code for the bug.", "claude", "inst-1");
        assertThatCode(() -> LlmAssertions.assertDifferentiated(List.of(r1, r2)))
                .doesNotThrowAnyException();
    }

    @Test
    void assertDifferentiated_failsForIdenticalResponses() {
        var r1 = new LlmResponse("app-1", "analyst", "analysis",
                "Same response text.", "claude", "inst-1");
        var r2 = new LlmResponse("app-2", "engineer", "software-engineering",
                "Same response text.", "claude", "inst-1");
        assertThatThrownBy(() -> LlmAssertions.assertDifferentiated(List.of(r1, r2)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertRouteConsistent_passesForSameBackend() {
        var r1 = new LlmResponse("app-1", "analyst", "analysis",
                "Response one.", "claude", "inst-1");
        var r2 = new LlmResponse("app-2", "engineer", "software-engineering",
                "Response two.", "claude", "inst-2");
        assertThatCode(() -> LlmAssertions.assertRouteConsistent(List.of(r1, r2)))
                .doesNotThrowAnyException();
    }

    @Test
    void assertRouteConsistent_failsForDifferentBackends() {
        var r1 = new LlmResponse("app-1", "analyst", "analysis",
                "Response one.", "claude", "inst-1");
        var r2 = new LlmResponse("app-2", "engineer", "software-engineering",
                "Response two.", "openai", "inst-2");
        assertThatThrownBy(() -> LlmAssertions.assertRouteConsistent(List.of(r1, r2)))
                .isInstanceOf(AssertionError.class);
    }
}
