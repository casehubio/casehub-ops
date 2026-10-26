package io.casehub.ops.testing.env;

public enum TargetEnvironment {
    LOCAL, K8S, PODMAN;

    private static final String PROPERTY = "casehub.topology.target-environment";

    public static TargetEnvironment current() {
        String value = System.getProperty(PROPERTY, "local");
        return TargetEnvironment.valueOf(value.toUpperCase().replace("-", "_"));
    }
}
