package io.casehub.ops.api.lifecycle;

import java.time.Instant;
import java.util.UUID;

public record CaseRef(UUID caseId, String bindingName, Instant createdAt) {}
