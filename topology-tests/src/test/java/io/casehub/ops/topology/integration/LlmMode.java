package io.casehub.ops.topology.integration;

import io.casehub.platform.agent.config.Manifest;
import io.casehub.platform.agent.config.ManifestLoader;

public enum LlmMode {
    FULL,
    NO_PROVIDER;

    private static LlmMode cached;

    public static LlmMode detect() {
        if (cached != null) {return cached;}
        ManifestLoader loader   = new ManifestLoader();
        Manifest       manifest = loader.load(null, null);
        cached = manifest.providers().isEmpty() ? NO_PROVIDER : FULL;
        return cached;
    }
}
