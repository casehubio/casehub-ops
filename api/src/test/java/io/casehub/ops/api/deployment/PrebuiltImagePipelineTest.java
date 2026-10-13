package io.casehub.ops.api.deployment;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PrebuiltImagePipelineTest {

    @Test
    void returnsImageRefFromSpec() {
        var pipeline = new PrebuiltImagePipeline();
        var spec = new CaseHubApplicationSpec(
            "app-1", null, "ghcr.io/acme/app:1.0",
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        var result = pipeline.build(spec);
        assertThat(result).isInstanceOf(BuildResult.Success.class);
        assertThat(((BuildResult.Success) result).imageRef())
            .isEqualTo("ghcr.io/acme/app:1.0");
    }

    @Test
    void failsWhenNoImageSet() {
        var pipeline = new PrebuiltImagePipeline();
        var spec = new CaseHubApplicationSpec(
            "app-1", "./apps/test", null,
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        var result = pipeline.build(spec);
        assertThat(result).isInstanceOf(BuildResult.Failed.class);
        assertThat(((BuildResult.Failed) result).reason()).contains("no image");
    }
}
