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

    @Test
    void localSource_failsWhenImageMissing() {
        assertThatThrownBy(() ->
                                   ImageResolver.resolveFromSource("nonexistent-app", "1.0", ImageSource.LOCAL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found locally");
    }

    @Test
    void fallbackSource_returnsNginxAlpine() {
        String image = ImageResolver.resolveFromSource("any-app", "1.0", ImageSource.FALLBACK);
        assertThat(image).isEqualTo("nginx:alpine");
    }

    @Test
    void registrySource_failsWithoutRegistry() {
        assertThatThrownBy(() ->
                                   ImageResolver.resolveFromSource("nonexistent-app", "1.0", ImageSource.REGISTRY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pull failed");
    }

    @Test
    void legacyProperty_mapsToFallback() {
        System.setProperty(ImageResolver.FALLBACK_PROPERTY, "true");
        try {
            ImageSource source = ImageResolver.currentSource();
            assertThat(source).isEqualTo(ImageSource.FALLBACK);
        } finally {
            System.clearProperty(ImageResolver.FALLBACK_PROPERTY);
        }
    }

    @Test
    void imageSourceProperty_takesPrecedence() {
        System.setProperty("casehub.topology.image-source", "registry");
        System.setProperty(ImageResolver.FALLBACK_PROPERTY, "true");
        try {
            ImageSource source = ImageResolver.currentSource();
            assertThat(source).isEqualTo(ImageSource.REGISTRY);
        } finally {
            System.clearProperty("casehub.topology.image-source");
            System.clearProperty(ImageResolver.FALLBACK_PROPERTY);
        }
    }

    @Test
    void defaultSource_isLocal() {
        ImageSource source = ImageResolver.currentSource();
        assertThat(source).isEqualTo(ImageSource.LOCAL);
    }
}
