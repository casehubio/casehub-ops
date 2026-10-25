package io.casehub.ops.service.adaptation;

import io.casehub.ops.api.deployment.LlmDecision;
import io.casehub.ops.api.deployment.LlmEvaluatedTrigger;
import io.casehub.ras.api.ActiveSituation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LlmTriggerEvaluatorTest {

    private static final LlmEvaluatedTrigger TRIGGER = new LlmEvaluatedTrigger(
            "deployer", "specialist-catalog", 0.5, null, Duration.ofSeconds(30));

    private static ActiveSituation situation(String id, double confidence) {
        return new ActiveSituation(id, "_singleton", "test",
                confidence, Map.of(), Instant.now(), Instant.now(), 0);
    }

    private static Map<String, String> catalogEntry(String name, String role, String domain) {
        var map = new LinkedHashMap<String, String>();
        map.put("name", name);
        map.put("role", role);
        map.put("domain", domain);
        return map;
    }

    @Test
    void deployDecisionWithMatchingCatalogEntry() {
        var catalog = List.of(
                catalogEntry("product-x-specialist", "specialist", "product-x"),
                catalogEntry("product-y-specialist", "specialist", "product-y"));

        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) ->
                        Optional.of("{\"action\": \"deploy\", \"entry\": \"product-x-specialist\"}"));

        Optional<LlmDecision> result = evaluator.evaluate(
                TRIGGER, situation("surge-alert", 0.8), catalog);

        assertThat(result).isPresent();
        assertThat(result.get().isDeploy()).isTrue();
        assertThat(result.get().selectedCatalogEntry().get("name"))
                .isEqualTo("product-x-specialist");
        assertThat(result.get().selectedCatalogEntry().get("domain"))
                .isEqualTo("product-x");
    }

    @Test
    void rejectDecision() {
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) ->
                        Optional.of("{\"action\": \"reject\"}"));

        Optional<LlmDecision> result = evaluator.evaluate(
                TRIGGER, situation("minor-alert", 0.6),
                List.of(catalogEntry("spec1", "role1", "domain1")));

        assertThat(result).isPresent();
        assertThat(result.get().isReject()).isTrue();
        assertThat(result.get().selectedCatalogEntry()).isNull();
    }

    @Test
    void agentUnavailableReturnsEmpty() {
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) -> Optional.empty());

        Optional<LlmDecision> result = evaluator.evaluate(
                TRIGGER, situation("alert", 0.8),
                List.of(catalogEntry("spec1", "role1", "domain1")));

        assertThat(result).isEmpty();
    }

    @Test
    void malformedJsonReturnsEmpty() {
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) ->
                        Optional.of("this is not json at all"));

        Optional<LlmDecision> result = evaluator.evaluate(
                TRIGGER, situation("alert", 0.8),
                List.of(catalogEntry("spec1", "role1", "domain1")));

        assertThat(result).isEmpty();
    }

    @Test
    void hallucinatedEntryTreatedAsReject() {
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) ->
                        Optional.of("{\"action\": \"deploy\", \"entry\": \"nonexistent-specialist\"}"));

        Optional<LlmDecision> result = evaluator.evaluate(
                TRIGGER, situation("alert", 0.8),
                List.of(catalogEntry("real-specialist", "role", "domain")));

        assertThat(result).isPresent();
        assertThat(result.get().isReject()).isTrue();
    }

    @Test
    void invokerExceptionReturnsEmpty() {
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) -> {
                    throw new RuntimeException("Connection refused");
                });

        Optional<LlmDecision> result = evaluator.evaluate(
                TRIGGER, situation("alert", 0.8),
                List.of(catalogEntry("spec1", "role1", "domain1")));

        assertThat(result).isEmpty();
    }

    @Test
    void unknownActionReturnsEmpty() {
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) ->
                        Optional.of("{\"action\": \"unknown_action\"}"));

        Optional<LlmDecision> result = evaluator.evaluate(
                TRIGGER, situation("alert", 0.8),
                List.of(catalogEntry("spec1", "role1", "domain1")));

        assertThat(result).isEmpty();
    }

    @Test
    void promptIncludesSituationAndCatalog() {
        var capturedPrompt = new String[1];
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) -> {
                    capturedPrompt[0] = prompt;
                    return Optional.of("{\"action\": \"reject\"}");
                });

        evaluator.evaluate(TRIGGER, situation("surge-42", 0.85),
                List.of(catalogEntry("specialist-a", "role-a", "domain-a")));

        assertThat(capturedPrompt[0]).contains("surge-42");
        assertThat(capturedPrompt[0]).contains("0.85");
        assertThat(capturedPrompt[0]).contains("specialist-a");
    }

    @Test
    void passesCorrectAgentNameAndTimeout() {
        var capturedAgent = new String[1];
        var capturedTimeout = new Duration[1];
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) -> {
                    capturedAgent[0] = agentName;
                    capturedTimeout[0] = timeout;
                    return Optional.of("{\"action\": \"reject\"}");
                });

        evaluator.evaluate(TRIGGER, situation("alert", 0.8),
                List.of(catalogEntry("spec1", "role1", "domain1")));

        assertThat(capturedAgent[0]).isEqualTo("deployer");
        assertThat(capturedTimeout[0]).isEqualTo(Duration.ofSeconds(30));
    }
}
