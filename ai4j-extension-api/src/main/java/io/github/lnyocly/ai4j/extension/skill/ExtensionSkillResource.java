package io.github.lnyocly.ai4j.extension.skill;

import io.github.lnyocly.ai4j.extension.ExtensionManifest;

public final class ExtensionSkillResource {

    private final String name;
    private final String description;
    private final String resourcePath;

    private ExtensionSkillResource(Builder builder) {
        this.name = ExtensionManifest.requireId(builder.name, "skill name");
        this.description = ExtensionManifest.emptyToNull(builder.description);
        this.resourcePath = ExtensionManifest.requireId(builder.resourcePath, "skill resource path");
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

        public ExtensionSkillResource build() {
            return new ExtensionSkillResource(this);
        }
    }
}
