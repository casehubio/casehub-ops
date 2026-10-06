package io.casehub.ops.service.service;

import io.casehub.ops.service.case_.ScalingPolicy;

import java.util.UUID;

public record ScalingRequestedEvent(
        UUID appCaseId,
        String applicationId,
        String tenancyId,
        String serviceId,
        int targetReplicas,
        int currentReplicas,
        String reason,
        ScalingPolicy policy) {}
