package io.github.lnyocly.ai4j.extension.tool;

import io.github.lnyocly.ai4j.extension.ExtensionManifest;

public final class ExtensionToolSpec {

    private final String name;
    private final String description;
    private final String inputSchema;

    private ExtensionToolSpec(Builder builder) {
        this.name = ExtensionManifest.requireToolName(builder.name, "tool name");
        this.description = ExtensionManifest.emptyToNull(builder.description);
        this.inputSchema = ExtensionManifest.emptyToNull(builder.inputSchema);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String description;
        private String inputSchema;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(String inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public ExtensionToolSpec build() {
            return new ExtensionToolSpec(this);
        }
    }
}
