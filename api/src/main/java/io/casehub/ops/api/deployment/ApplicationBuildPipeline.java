package io.casehub.ops.api.deployment;

public interface ApplicationBuildPipeline {
    BuildResult build(CaseHubApplicationSpec spec);
}
