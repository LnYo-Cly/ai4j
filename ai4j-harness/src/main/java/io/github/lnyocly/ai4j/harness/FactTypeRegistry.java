package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry enforcing immutability of registered {@link FactType} schema versions.
 */
public final class FactTypeRegistry {

    private final ConcurrentMap<String, FactType<?>> types =
            new ConcurrentHashMap<String, FactType<?>>();

    public <T> FactType<T> register(FactType<T> factType) {
        if (factType == null) {
            throw new HarnessValidationException("factType is required");
        }
        String key = factType.schemaKey();
        FactType<?> existing = types.putIfAbsent(key, factType);
        if (existing != null) {
            boolean sameClass = existing.getPayloadType().equals(factType.getPayloadType());
            boolean sameFields = existing.getRequiredFields().equals(factType.getRequiredFields());
            if (!sameClass || !sameFields) {
                throw new HarnessConflictException(
                        "immutable FactType schema already registered and cannot be altered: " + key);
            }
        }
        return factType;
    }

    @SuppressWarnings("unchecked")
    public <T> FactType<T> get(String typeId, int schemaVersion, Class<T> expectedType) {
        String key = HarnessSupport.requireText(typeId, "fact type id") + "@v" + schemaVersion;
        FactType<?> found = types.get(key);
        if (found == null) {
            return null;
        }
        if (expectedType != null && !expectedType.equals(found.getPayloadType())) {
            throw new HarnessConflictException("FactType class mismatch for " + key);
        }
        return (FactType<T>) found;
    }

    public List<FactType<?>> list() {
        return Collections.unmodifiableList(new ArrayList<FactType<?>>(types.values()));
    }
}
