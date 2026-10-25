package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Duration;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmEvaluatedTrigger(
        String agent,
        String catalog,
        double minConfidence,
        Duration cooldown,
        Duration timeout
) implements AdaptationTrigger {
    public LlmEvaluatedTrigger {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(timeout, "timeout");
        if (Double.isNaN(minConfidence) || minConfidence < 0.0 || minConfidence >= 1.0) {
            throw new IllegalArgumentException(
                    "minConfidence must be [0.0, 1.0), got: " + minConfidence);
        }
    }

    public Duration effectiveCooldown() {
        return cooldown != null ? cooldown : Duration.ZERO;
    }
}
