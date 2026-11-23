package io.github.lnyocly.ai4j.harness;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable versioned structured fact schema ({@code FactType<T>}).
 */
public final class FactType<T> {

    private final String typeId;
    private final int schemaVersion;
    private final Class<T> payloadType;
    private final Set<String> requiredFields;
    private final String description;

    private FactType(String typeId,
                     int schemaVersion,
                     Class<T> payloadType,
                     Set<String> requiredFields,
                     String description) {
        this.typeId = HarnessSupport.requireText(typeId, "fact type id");
        if (schemaVersion <= 0) {
            throw new HarnessValidationException("fact schemaVersion must be >= 1");
        }
        if (payloadType == null) {
            throw new HarnessValidationException("fact payloadType is required");
        }
        this.schemaVersion = schemaVersion;
        this.payloadType = payloadType;
        Set<String> normalized = new LinkedHashSet<String>();
        if (requiredFields != null) {
            for (String field : requiredFields) {
                String trimmed = HarnessSupport.trimToNull(field);
                if (trimmed != null) {
                    normalized.add(trimmed);
                }
            }
        }
        this.requiredFields = Collections.unmodifiableSet(normalized);
        this.description = description;
    }

    public static <T> FactType<T> of(String typeId, int schemaVersion, Class<T> payloadType) {
        return new FactType<T>(typeId, schemaVersion, payloadType, null, null);
    }

    public static <T> FactType<T> of(String typeId,
                                     int schemaVersion,
                                     Class<T> payloadType,
                                     Set<String> requiredFields,
                                     String description) {
        return new FactType<T>(typeId, schemaVersion, payloadType, requiredFields, description);
    }

    public String getTypeId() {
        return typeId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Class<T> getPayloadType() {
        return payloadType;
    }

    public Set<String> getRequiredFields() {
        return requiredFields;
    }

    public String getDescription() {
        return description;
    }

    public String schemaKey() {
        return typeId + "@v" + schemaVersion;
    }

    public String encodeAndValidate(T payload) {
        if (payload == null) {
            throw new HarnessValidationException("typed fact payload is required for " + schemaKey());
        }
        if (!payloadType.isInstance(payload)) {
            throw new HarnessValidationException("typed fact payload must be " + payloadType.getName());
        }
        String json = HarnessJson.toJson(payload);
        if (!requiredFields.isEmpty()) {
            Map<String, Object> asMap = HarnessJson.parseMap(json);
            for (String field : requiredFields) {
                if (asMap == null || !asMap.containsKey(field) || asMap.get(field) == null) {
                    throw new HarnessValidationException(
                            "missing required field '" + field + "' for FactType " + schemaKey());
                }
            }
        }
        return json;
    }
}
