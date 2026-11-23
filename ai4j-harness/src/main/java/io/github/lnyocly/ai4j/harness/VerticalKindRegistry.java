package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry of immutable versioned {@link VerticalKind} schemas.
 */
public final class VerticalKindRegistry {

    private final ConcurrentMap<String, VerticalKind<?>> kinds =
            new ConcurrentHashMap<String, VerticalKind<?>>();

    public <T> VerticalKind<T> register(VerticalKind<T> kind) {
        if (kind == null) {
            throw new HarnessValidationException("vertical kind is required");
        }
        String key = kind.schemaKey();
        VerticalKind<?> existing = kinds.putIfAbsent(key, kind);
        if (existing != null && !existing.getPayloadType().equals(kind.getPayloadType())) {
            throw new HarnessConflictException("immutable VerticalKind schema already registered with a different type: " + key);
        }
        return kind;
    }

    @SuppressWarnings("unchecked")
    public <T> VerticalKind<T> get(String kindId, int schemaVersion, Class<T> expectedType) {
        String key = HarnessSupport.requireText(kindId, "vertical kind id") + "@v" + schemaVersion;
        VerticalKind<?> found = kinds.get(key);
        if (found == null) {
            return null;
        }
        if (expectedType != null && !expectedType.equals(found.getPayloadType())) {
            throw new HarnessConflictException("VerticalKind type mismatch for " + key);
        }
        return (VerticalKind<T>) found;
    }

    public List<VerticalKind<?>> list() {
        return Collections.unmodifiableList(new ArrayList<VerticalKind<?>>(kinds.values()));
    }
}
