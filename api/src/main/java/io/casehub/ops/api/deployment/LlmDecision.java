package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmDecision(
        String action,
        Map<String, String> selectedCatalogEntry
) {
    public LlmDecision {
        Objects.requireNonNull(action, "action");
    }

    public boolean isDeploy() {
        return "deploy".equals(action);
    }

    public boolean isReject() {
        return "reject".equals(action);
    }
}
