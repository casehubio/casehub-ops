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
        if (!manifest.providers().isEmpty()) {
            cached = FULL;
        } else {
            cached = claudeCliAvailable() ? FULL : NO_PROVIDER;
        }
        return cached;
    }

    private static boolean claudeCliAvailable() {
        try {
            Process p = new ProcessBuilder("claude", "--version")
                                .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
