package io.casehub.ops.service.topology;

public record TopologyDescriptor(
    String name,
    String path,
    String source
) {}
