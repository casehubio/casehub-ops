package io.casehub.ops.api.lifecycle;

public record GanglionBinding(
        String situationType,
        DimensionType dimension,
        String contextKey,
        DimensionStatus conditionStatus
) {}
