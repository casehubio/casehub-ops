package io.casehub.ops.api.deployment;

public sealed interface BuildResult {
    record Success(String imageRef) implements BuildResult {}
    record Failed(String reason) implements BuildResult {}
}
