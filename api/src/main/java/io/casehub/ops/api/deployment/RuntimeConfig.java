package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeConfig(
    String engine,
    List<String> modules
) {
    public RuntimeConfig {
        if (engine == null || engine.isBlank())
            throw new IllegalArgumentException("engine version is required");
        modules = modules != null ? List.copyOf(modules) : List.of();
    }
}
