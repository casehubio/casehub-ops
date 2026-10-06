package io.casehub.ops.service.model;

import java.util.List;

public record NodeDrift(String nodeId, List<FieldDrift> fields) {}
