package io.casehub.ops.api.deployment;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@DefaultBean
public class PrebuiltImagePipeline implements ApplicationBuildPipeline {

    @Override
    public BuildResult build(CaseHubApplicationSpec spec) {
        if (spec.image() == null || spec.image().isBlank()) {
            return new BuildResult.Failed(
                "PrebuiltImagePipeline: no image set on spec '" + spec.applicationId()
                + "' — source-based builds require a real ApplicationBuildPipeline implementation");
        }
        return new BuildResult.Success(spec.image());
    }
}
