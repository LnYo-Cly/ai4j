package io.github.lnyocly.ai4j.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defensive copy helpers for the library-neutral JSON surface.
 *
 * <p>Public semantic APIs carry JSON as {@code Map<String,Object>} plus the
 * standard JSON scalars and {@code List<Object>} arrays (design F2). Values are
 * deep-copied on store and on read so callers can never mutate kernel state
 * through a shared reference, and the kernel never mutates caller input
 * (design C11).</p>
 *
 * <p>The accepted value set is restricted to JSON scalars, lists and maps with
 * string keys. Arbitrary Java objects are rejected rather than silently
 * serialized (design 7.1).</p>
 */
public final class JsonValues {

    private JsonValues() {
    }

    /**
     * Deep-copy a JSON object document.
     *
     * @param source source map, may be {@code null}
     * @return an unmodifiable deep copy, or an empty map when {@code source} is {@code null}
     */
    public static Map<String, Object> copyObject(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("JSON object keys must not be null");
            }
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Deep-copy an arbitrary JSON value.
     *
     * @param value source value, may be {@code null}
     * @return an unmodifiable deep copy
     */
    @SuppressWarnings("unchecked")
    public static Object copyValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number) {
            if (value instanceof Double && (((Double) value).isNaN() || ((Double) value).isInfinite())) {
                throw new IllegalArgumentException("JSON numbers must be finite: " + value);
            }
            if (value instanceof Float && (((Float) value).isNaN() || ((Float) value).isInfinite())) {
                throw new IllegalArgumentException("JSON numbers must be finite: " + value);
            }
            return value;
        }
        if (value instanceof Map) {
            return copyObject((Map<String, Object>) value);
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object element : (List<Object>) value) {
                copy.add(copyValue(element));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value.getClass().isArray() || value instanceof Iterable) {
            throw new IllegalArgumentException(
                    "JSON arrays must be List<Object>, got " + value.getClass().getName());
        }
        throw new IllegalArgumentException(
                "Unsupported JSON value type: " + value.getClass().getName()
                        + " (only String, Number, Boolean, null, List<Object> and Map<String,Object> are allowed)");
    }
}
