package io.github.lnyocly.ai4j.extension.prompt;

import io.github.lnyocly.ai4j.extension.ExtensionManifest;

public final class ExtensionPromptResource {

    private final String name;
    private final String description;
    private final String resourcePath;

    private ExtensionPromptResource(Builder builder) {
        this.name = ExtensionManifest.requireId(builder.name, "prompt name");
        this.description = ExtensionManifest.emptyToNull(builder.description);
        this.resourcePath = ExtensionManifest.requireId(builder.resourcePath, "prompt resource path");
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String description;
        private String resourcePath;

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

        public Builder resourcePath(String resourcePath) {
            this.resourcePath = resourcePath;
            return this;
        }

        public ExtensionPromptResource build() {
            return new ExtensionPromptResource(this);
        }
    }
}
