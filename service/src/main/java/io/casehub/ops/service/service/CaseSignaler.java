package io.casehub.ops.service.service;

import java.util.UUID;

@FunctionalInterface
public interface CaseSignaler {
    void signal(UUID caseId, String path, Object value);
}
