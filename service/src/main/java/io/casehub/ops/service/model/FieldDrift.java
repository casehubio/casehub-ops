package io.casehub.ops.service.model;

public record FieldDrift(String fieldName, String expectedValue, String actualValue) {}
