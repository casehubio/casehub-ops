package io.casehub.ops.app.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CveRecord(
        String cveId,
        CveSeverity severity,
        String affectedImage,
        List<String> affectedServices,
        String fixedInTag,
        CveStatus status,
        UUID applicationId,
        String tenancyId,
        Instant detectedAt) {}
