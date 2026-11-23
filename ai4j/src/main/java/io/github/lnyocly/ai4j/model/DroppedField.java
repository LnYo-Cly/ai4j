package io.github.lnyocly.ai4j.model;

import java.util.Objects;

/**
 * An explicit record that a request field could not be represented on the
 * target wire (design 5.1 invariant 2: never a silent drop).
 */
public final class DroppedField {

    private final String field;
    private final String reason;

    private DroppedField(String field, String reason) {
        this.field = Objects.requireNonNull(field, "field is required");
        this.reason = Objects.requireNonNull(reason, "reason is required");
    }

    public static DroppedField of(String field, String reason) {
        return new DroppedField(field, reason);
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "DroppedField{" + field + ": " + reason + '}';
    }
}
