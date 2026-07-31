package io.casehub.ops.api.lifecycle;

public interface DimensionStatus {
    Severity severity();
    String label();
    boolean isTerminal();
}
