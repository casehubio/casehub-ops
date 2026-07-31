package io.casehub.ops.api.lifecycle;

import java.util.Map;

public record ServiceHealth(
        String serviceId,
        String serviceName,
        Map<DimensionType, DimensionStatus> dimensions,
        Severity overallSeverity
) {}
