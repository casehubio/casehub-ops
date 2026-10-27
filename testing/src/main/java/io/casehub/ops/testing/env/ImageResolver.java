package io.casehub.ops.testing.env;

public final class ImageResolver {

    public static final String FALLBACK_IMAGE        = "nginx:alpine";
    public static final String FALLBACK_PROPERTY     = "casehub.topology.allow-image-fallback";
    public static final String IMAGE_SOURCE_PROPERTY = "casehub.topology.image-source";
    public static final String REGISTRY_PREFIX       = "ghcr.io/casehubio/";

    private ImageResolver() {}

    public static String imageRef(String serviceName, String version) {
        return "casehubio/" + serviceName + ":" + version;
    }

    public static ImageSource currentSource() {
        String sourceValue = System.getProperty(IMAGE_SOURCE_PROPERTY);
        if (sourceValue != null) {
            return ImageSource.valueOf(sourceValue.toUpperCase());
        }
        if (Boolean.getBoolean(FALLBACK_PROPERTY)) {
            return ImageSource.FALLBACK;
        }
        return ImageSource.LOCAL;
    }

    public static String resolve(String serviceName, String version) {
        return resolveFromSource(serviceName, version, currentSource());
    }

    public static String resolve(String serviceName, String version, boolean allowFallback) {
        ImageSource source = allowFallback ? ImageSource.FALLBACK : ImageSource.LOCAL;
        return resolveFromSource(serviceName, version, source);
    }

    public static String resolveFromSource(String serviceName, String version, ImageSource source) {
        return switch (source) {
            case LOCAL -> resolveLocal(serviceName, version);
            case REGISTRY -> resolveRegistry(serviceName, version);
            case FALLBACK -> FALLBACK_IMAGE;
        };
    }

    private static String resolveLocal(String serviceName, String version) {
        String ref = imageRef(serviceName, version);
        if (imageExistsLocally(ref)) {
            return ref;
        }
        throw new IllegalStateException(
                "Image " + ref + " not found locally.\n" +
                "Build it: cd <family-root>/" + serviceName +
                " && mvn -pl app package -DskipTests && docker build -t " + ref + " .\n" +
                "Or set -D" + IMAGE_SOURCE_PROPERTY + "=fallback to use " + FALLBACK_IMAGE + ".");
    }

    private static String resolveRegistry(String serviceName, String version) {
        String registryRef = REGISTRY_PREFIX + serviceName + ":" + version;
        if (pullImage(registryRef)) {
            String localRef = imageRef(serviceName, version);
            tagImage(registryRef, localRef);
            return localRef;
        }
        throw new IllegalStateException(
                "Registry pull failed for " + registryRef + ".\n" +
                "Ensure the image is published to ghcr.io and GITHUB_TOKEN is configured.");
    }

    static boolean imageExistsLocally(String imageRef) {
        try {
            Process proc = new ProcessBuilder("docker", "images", "-q", imageRef)
                                   .redirectErrorStream(true).start();
            String output = new String(proc.getInputStream().readAllBytes()).trim();
            boolean finished = proc.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
            if (!finished) {
                proc.destroyForcibly();
                return false;
            }
            int exit = proc.exitValue();
            return exit == 0 && !output.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean pullImage(String imageRef) {
        try {
            Process proc = new ProcessBuilder("docker", "pull", imageRef)
                                   .redirectErrorStream(true).start();
            java.util.concurrent.Future<String> outputFuture =
                    java.util.concurrent.Executors.newSingleThreadExecutor().submit(
                            () -> new String(proc.getInputStream().readAllBytes()).trim());
            boolean finished = proc.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
            if (!finished) {
                proc.destroyForcibly();
                return false;
            }
            String output = outputFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
            int exit = proc.exitValue();
            if (exit == 0) {
                System.out.printf("  [image-resolver] pulled %s (digest: %s)%n",
                                  imageRef, extractDigest(output));
            }
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void tagImage(String source, String target) {
        try {
            Process proc = new ProcessBuilder("docker", "tag", source, target)
                    .redirectErrorStream(true).start();
            int exit = proc.waitFor();
            if (exit != 0) {
                System.err.printf("  [image-resolver] docker tag %s → %s failed (exit %d)%n",
                        source, target, exit);
            }
        } catch (Exception e) {
            System.err.printf("  [image-resolver] docker tag failed: %s%n", e.getMessage());
        }
    }

    private static String extractDigest(String pullOutput) {
        int idx = pullOutput.indexOf("Digest:");
        if (idx >= 0) {
            int end = pullOutput.indexOf('\n', idx);
            return end > idx ? pullOutput.substring(idx + 8, end).trim() : pullOutput.substring(idx + 8).trim();
        }
        return "unknown";
    }
}
