package io.casehub.ops.service.rest.dto;

public record CreateApplicationRequest(String name, String description, String servicesJson) {}
