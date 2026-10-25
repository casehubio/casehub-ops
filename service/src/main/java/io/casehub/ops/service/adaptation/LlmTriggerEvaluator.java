package io.casehub.ops.service.adaptation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ops.api.deployment.LlmDecision;
import io.casehub.ops.api.deployment.LlmEvaluatedTrigger;
import io.casehub.ras.api.ActiveSituation;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LlmTriggerEvaluator {

    private static final Logger LOG = Logger.getLogger(LlmTriggerEvaluator.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    @FunctionalInterface
    public interface AgentInvoker {
        Optional<String> invoke(String agentName, String prompt, Duration timeout);
    }

    private final AgentInvoker invoker;

    public LlmTriggerEvaluator(AgentInvoker invoker) {
        this.invoker = Objects.requireNonNull(invoker, "invoker");
    }

    public Optional<LlmDecision> evaluate(
            LlmEvaluatedTrigger trigger,
            ActiveSituation situation,
            List<Map<String, String>> catalogEntries) {

        String prompt = buildPrompt(situation, catalogEntries);

        Optional<String> rawResponse;
        try {
            rawResponse = invoker.invoke(trigger.agent(), prompt, trigger.timeout());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Agent invocation failed for " +
                    trigger.agent() + ": " + e.getMessage(), e);
            return Optional.empty();
        }

        if (rawResponse.isEmpty()) {
            return Optional.empty();
        }

        try {
            JsonNode node = JSON.readTree(rawResponse.get());
            String action = node.path("action").asText();

            if ("reject".equals(action)) {
                return Optional.of(new LlmDecision("reject", null));
            }

            if ("deploy".equals(action)) {
                String entryName = node.path("entry").asText();
                Optional<Map<String, String>> matched = lookupCatalogEntry(entryName, catalogEntries);

                if (matched.isEmpty()) {
                    LOG.warning("LLM returned unknown catalog entry '" + entryName +
                            "' — treating as reject");
                    return Optional.of(new LlmDecision("reject", null));
                }
                return Optional.of(new LlmDecision("deploy", matched.get()));
            }

            LOG.warning("Unknown LLM action: " + action);
            return Optional.empty();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse LLM response: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<Map<String, String>> lookupCatalogEntry(
            String entryName, List<Map<String, String>> catalogEntries) {
        for (Map<String, String> entry : catalogEntries) {
            if (entry.containsValue(entryName)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private String buildPrompt(ActiveSituation situation,
                               List<Map<String, String>> catalogEntries) {
        var sb = new StringBuilder();
        sb.append("A situation has been detected that may require deploying a specialist.\n\n");
        sb.append("Situation:\n");
        sb.append("  ID: ").append(situation.situationId()).append("\n");
        sb.append("  Confidence: ").append(situation.confidence()).append("\n");
        sb.append("  Evidence: ").append(situation.evidence()).append("\n\n");
        sb.append("Available specialists:\n");
        for (var entry : catalogEntries) {
            sb.append("  - ").append(entry).append("\n");
        }
        sb.append("\nRespond with JSON: {\"action\": \"deploy\", \"entry\": \"<name>\"} ");
        sb.append("or {\"action\": \"reject\"}\n");
        return sb.toString();
    }
}
