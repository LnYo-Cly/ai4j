package io.github.lnyocly.ai4j.extension.validation;

public enum ExtensionValidationSeverity {
    ERROR("error"),
    WARNING("warning");

    private final String id;

    ExtensionValidationSeverity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
