package io.casehub.ops.testing.env;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageResolverTest {

    @Test
    void imageRefFormatsConvention() {
        String ref = ImageResolver.imageRef("claudony", "0.4-SNAPSHOT");
        assertThat(ref).isEqualTo("casehubio/claudony:0.4-SNAPSHOT");
    }

    @Test
    void resolveThrowsWhenImageMissingAndFallbackDisabled() {
        assertThatThrownBy(() ->
            ImageResolver.resolve("nonexistent-service-xyz", "0.4-SNAPSHOT", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("nonexistent-service-xyz")
            .hasMessageContaining("casehubio/nonexistent-service-xyz:0.4-SNAPSHOT");
    }

    @Test
    void resolveReturnsFallbackWhenAllowedAndImageMissing() {
        String ref = ImageResolver.resolve("nonexistent-service-xyz", "0.4-SNAPSHOT", true);
        assertThat(ref).isEqualTo(ImageResolver.FALLBACK_IMAGE);
    }
}
