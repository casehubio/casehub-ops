package io.casehub.ops.testing.env;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public final class LlmAssertions {

    private LlmAssertions() {}

    public static void assertSubstantive(LlmResponse response) {
        assertThat(response.content())
                .as("LLM %s (role=%s, domain=%s) must produce substantive output",
                        response.appId(), response.role(), response.domain())
                .isNotBlank()
                .hasSizeGreaterThan(20);
    }

    public static void assertDomainRelevant(LlmResponse response) {
        assertSubstantive(response);
        if (response.isSimulated()) return;
        String keywords = domainKeywords(response.domain());
        assertThat(response.content().toLowerCase())
                .as("LLM %s response should reflect domain '%s':\n  \"%s\"",
                        response.appId(), response.domain(), response.content())
                .containsPattern(keywords);
    }

    public static void assertDifferentiated(List<LlmResponse> responses) {
        assertThat(responses).hasSizeGreaterThan(1);
        if (responses.stream().anyMatch(LlmResponse::isSimulated)) return;
        long distinct = responses.stream()
                .map(r -> r.content().trim().toLowerCase())
                .distinct()
                .count();
        assertThat(distinct)
                .as("Fleet members with different roles must produce different responses")
                .isGreaterThan(1);
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

    public static void assertFleetOperational(List<LlmResponse> responses) {
        for (LlmResponse r : responses) {
            assertDomainRelevant(r);
        }
        assertDifferentiated(responses);
        assertRouteConsistent(responses);
    }

    private static String domainKeywords(String domain) {
        return switch (domain) {
            case "analysis", "data-analysis" ->
                    "analy|data|investigat|insight|pattern|examin|root.cause|diagnos|metric|trend|correlat";
            case "software-engineering" ->
                    "code|software|debug|log|stack|implement|fix|program|engineer|develop|deploy|error.handl|trace|exception";
            case "general" ->
                    "assess|monitor|gather|information|coordinat|team|communicat|priorit|triage|respond|address|status|check|review|help|assist";
            case "knowledge-discovery" ->
                    "research|knowledge|discover|learn|study|explor|understand|literature|methodolog|hypothes|investigat";
            default -> ".+";
        };
    }
}
