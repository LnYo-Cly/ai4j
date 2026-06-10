package io.github.lnyocly.ai4j.extension.skill;

import io.github.lnyocly.ai4j.extension.ExtensionManifest;

public final class ExtensionSkillResource {

    private final String name;
    private final String description;
    private final String resourcePath;
    private final String extensionId;

    private ExtensionSkillResource(Builder builder) {
        this.name = ExtensionManifest.requireResourceName(builder.name, "skill name");
        this.description = ExtensionManifest.emptyToNull(builder.description);
        this.resourcePath = ExtensionManifest.requireId(builder.resourcePath, "skill resource path");
        this.extensionId = normalizeExtensionId(builder.extensionId);
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

    public String getExtensionId() {
        return extensionId;
    }

    public ExtensionSkillResource withExtensionId(String extensionId) {
        return builder()
                .name(name)
                .description(description)
                .resourcePath(resourcePath)
                .extensionId(extensionId)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String normalizeExtensionId(String extensionId) {
        String normalized = ExtensionManifest.emptyToNull(extensionId);
        return normalized == null ? null : ExtensionManifest.requireExtensionId(normalized, "skill extension id");
    }

    public static final class Builder {
        private String name;
        private String description;
        private String resourcePath;
        private String extensionId;

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

        public Builder extensionId(String extensionId) {
            this.extensionId = extensionId;
            return this;
        }

        public ExtensionSkillResource build() {
            return new ExtensionSkillResource(this);
        }
    }
}
