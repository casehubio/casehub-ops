package io.casehub.ops.api.lifecycle;

public enum Severity {
    OK,
    INFO,
    WARNING,
    CRITICAL;

    public static Severity worstOf(Severity a, Severity b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
