package io.casehub.ops.api.lifecycle;

import java.time.Instant;

public class DimensionSection {

    @FunctionalInterface
    public interface ContextWriter {
        void write(String key, Object value);
    }

    @FunctionalInterface
    public interface ContextReader {
        Object read(String key);
    }

    private final DimensionType type;
    private final String prefix;
    private final ContextWriter writer;
    private final ContextReader reader;
    private volatile Instant lastUpdated;

    public DimensionSection(DimensionType type, ContextWriter writer, ContextReader reader) {
        this.type = type;
        this.prefix = type.contextPrefix();
        this.writer = writer;
        this.reader = reader;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return (T) reader.read(prefix + key);
    }

    public void put(String key, Object value) {
        writer.write(prefix + key, value);
        lastUpdated = Instant.now();
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    public DimensionType type() {
        return type;
    }
}
