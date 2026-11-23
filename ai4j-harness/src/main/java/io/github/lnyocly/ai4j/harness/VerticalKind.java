package io.github.lnyocly.ai4j.harness;

import java.util.function.Predicate;

/**
 * Strongly-typed vertical domain artifact schema descriptor ({@code VerticalKind<T>}).
 */
public final class VerticalKind<T> {

    private final String kindId;
    private final int schemaVersion;
    private final Class<T> payloadType;
    private final String description;
    private final Predicate<T> validator;

    private VerticalKind(String kindId,
                         int schemaVersion,
                         Class<T> payloadType,
                         String description,
                         Predicate<T> validator) {
        this.kindId = HarnessSupport.requireText(kindId, "vertical kind id");
        if (schemaVersion <= 0) {
            throw new HarnessValidationException("schemaVersion must be >= 1");
        }
        if (payloadType == null) {
            throw new HarnessValidationException("payloadType is required");
        }
        this.schemaVersion = schemaVersion;
        this.payloadType = payloadType;
        this.description = description;
        this.validator = validator;
    }

    public static <T> VerticalKind<T> of(String kindId, int schemaVersion, Class<T> payloadType) {
        return new VerticalKind<T>(kindId, schemaVersion, payloadType, null, null);
    }

    public static <T> VerticalKind<T> of(String kindId,
                                         int schemaVersion,
                                         Class<T> payloadType,
                                         String description,
                                         Predicate<T> validator) {
        return new VerticalKind<T>(kindId, schemaVersion, payloadType, description, validator);
    }

    public String getKindId() {
        return kindId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Class<T> getPayloadType() {
        return payloadType;
    }

    public String getDescription() {
        return description;
    }

    public String schemaKey() {
        return kindId + "@v" + schemaVersion;
    }

    public void validate(T payload) {
        if (payload == null) {
            throw new HarnessValidationException("typed artifact payload is required for " + schemaKey());
        }
        if (!payloadType.isInstance(payload)) {
            throw new HarnessValidationException("payload does not match " + payloadType.getName());
        }
        if (validator != null && !validator.test(payload)) {
            throw new HarnessValidationException("typed artifact validation failed for " + schemaKey());
        }
    }
}
