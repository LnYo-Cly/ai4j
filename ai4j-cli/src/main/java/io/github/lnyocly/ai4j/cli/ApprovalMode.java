package io.github.lnyocly.ai4j.cli;

import java.util.Locale;

public enum ApprovalMode {
    AUTO("auto"),
    SAFE("safe"),
    MANUAL("manual");

    private final String value;

    ApprovalMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ApprovalMode parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return AUTO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ApprovalMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported approval mode: " + raw);
    }
}
