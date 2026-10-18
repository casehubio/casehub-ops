package io.casehub.ops.api.claudony;

import java.util.List;

public record ClaudonyGoals(
    List<ClaudonyGoalEntry<ClaudonyAppNodeSpec>> apps,
    List<ClaudonyGoalEntry<ClaudonyPoolNodeSpec>> pools
) {
    public ClaudonyGoals {
        apps = apps != null ? List.copyOf(apps) : List.of();
        pools = pools != null ? List.copyOf(pools) : List.of();
    }
}
