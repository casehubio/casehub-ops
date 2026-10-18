package io.casehub.ops.api.claudony;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudonyGoalEntry<S extends ClaudonyNodeSpec>(S spec, List<String> dependsOn) {
    public ClaudonyGoalEntry {
        dependsOn = dependsOn != null ? List.copyOf(dependsOn) : List.of();
    }
}
