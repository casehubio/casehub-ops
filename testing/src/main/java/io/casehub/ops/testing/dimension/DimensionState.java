package io.casehub.ops.testing.dimension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class DimensionState {

    private final Map<String, Object> state = new LinkedHashMap<>();

    public void put(String key, Object value) {
        Objects.requireNonNull(value, "DimensionState value for key '" + key + "' must not be null");
        state.put(key, value);
    }

    public String getString(String key) {
        Object v = state.get(key);
        if (v == null) throw new IllegalStateException("Missing state key: " + key);
        return v.toString();
    }

    public <T> T get(String key, Class<T> type) {
        Object v = state.get(key);
        if (v == null) throw new IllegalStateException("Missing state key: " + key);
        return type.cast(v);
    }

    public boolean has(String key) {
        return state.containsKey(key);
    }
}
