package io.casehub.ops.testing.env;

public final class ImageResolver {

    public static final String FALLBACK_IMAGE = "nginx:alpine";
    public static final String FALLBACK_PROPERTY = "casehub.topology.allow-image-fallback";

    private ImageResolver() {}

    public static String imageRef(String serviceName, String version) {
        return "casehubio/" + serviceName + ":" + version;
    }

    public static String resolve(String serviceName, String version) {
        boolean allowFallback = Boolean.getBoolean(FALLBACK_PROPERTY);
        return resolve(serviceName, version, allowFallback);
    }

    public static String resolve(String serviceName, String version, boolean allowFallback) {
        String ref = imageRef(serviceName, version);
        if (imageExistsLocally(ref)) {
            return ref;
        }
        if (allowFallback) {
            System.out.printf("  [image-resolver] %s not found locally, falling back to %s%n",
                    ref, FALLBACK_IMAGE);
            return FALLBACK_IMAGE;
        }
        throw new IllegalStateException(
                "Image " + ref + " not found locally.\n" +
                "Build it: cd <family-root>/" + serviceName +
                " && mvn -pl app package -DskipTests && docker build -t " + ref + " .\n" +
                "Or pass -D" + FALLBACK_PROPERTY + "=true to use " + FALLBACK_IMAGE + " instead.");
    }

    static boolean imageExistsLocally(String imageRef) {
        try {
            Process proc = new ProcessBuilder("docker", "images", "-q", imageRef)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes()).trim();
            int exit = proc.waitFor();
            return exit == 0 && !output.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
