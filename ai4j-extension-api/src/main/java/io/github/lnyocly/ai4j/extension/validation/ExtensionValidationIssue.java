package io.github.lnyocly.ai4j.extension.validation;

import io.github.lnyocly.ai4j.extension.ExtensionManifest;

public final class ExtensionValidationIssue {

    private final ExtensionValidationSeverity severity;
    private final String code;
    private final String message;
    private final String target;

    private ExtensionValidationIssue(Builder builder) {
        if (builder.severity == null) {
            throw new IllegalArgumentException("validation issue severity must not be null");
        }
        this.severity = builder.severity;
        this.code = ExtensionManifest.requireId(builder.code, "validation issue code");
        this.message = ExtensionManifest.requireId(builder.message, "validation issue message");
        this.target = ExtensionManifest.emptyToNull(builder.target);
    }

    public ExtensionValidationSeverity getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTarget() {
        return target;
    }

    public boolean isError() {
        return ExtensionValidationSeverity.ERROR.equals(severity);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ExtensionValidationSeverity severity;
        private String code;
        private String message;
        private String target;

        private Builder() {
        }

        public Builder severity(ExtensionValidationSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public ExtensionValidationIssue build() {
            return new ExtensionValidationIssue(this);
        }
    }
}
