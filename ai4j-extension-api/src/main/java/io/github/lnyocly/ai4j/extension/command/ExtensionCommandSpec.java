package io.github.lnyocly.ai4j.extension.command;

import io.github.lnyocly.ai4j.extension.ExtensionManifest;

public final class ExtensionCommandSpec {

    private final String name;
    private final String description;
    private final String usage;

    private ExtensionCommandSpec(Builder builder) {
        this.name = ExtensionManifest.requireCommandName(builder.name, "command name");
        this.description = ExtensionManifest.emptyToNull(builder.description);
        this.usage = ExtensionManifest.emptyToNull(builder.usage);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String description;
        private String usage;

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

        public Builder usage(String usage) {
            this.usage = usage;
            return this;
        }

        public ExtensionCommandSpec build() {
            return new ExtensionCommandSpec(this);
        }
    }
}
