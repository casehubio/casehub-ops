package io.casehub.ops.service.rest.dto;

public record ScaleServiceRequest(int targetReplicas, String reason) {}
