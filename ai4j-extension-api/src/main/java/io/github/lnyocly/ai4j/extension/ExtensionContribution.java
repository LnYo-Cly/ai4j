package io.github.lnyocly.ai4j.extension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Manifest-level description of a contribution provided by an extension.
 *
 * <p>This is intentionally metadata only. Host modules still decide whether a
 * contribution is installed, enabled, exposed, or bound to a concrete runtime
 * registry.</p>
 */
public final class ExtensionContribution {

    private final ExtensionContributionType type;
    private final String name;
    private final String description;
    private final List<String> permissions;
    private final boolean requiresExplicitActivation;

    private ExtensionContribution(Builder builder) {
        if (builder.type == null) {
            throw new IllegalArgumentException("extension contribution type must not be null");
        }
        this.type = builder.type;
        this.name = ExtensionManifest.requireResourceName(builder.name, "extension contribution name");
        this.description = ExtensionManifest.emptyToNull(builder.description);
        this.permissions = Collections.unmodifiableList(copyNormalized(builder.permissions));
        this.requiresExplicitActivation = builder.requiresExplicitActivation;
    }

    public ExtensionContributionType getType() {
        return type;
    }

    public String getTypeId() {
        return type.getId();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public boolean isRequiresExplicitActivation() {
        return requiresExplicitActivation;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<String> copyNormalized(Collection<String> values) {
        List<String> copy = new ArrayList<String>();
        if (values == null) {
            return copy;
        }
        for (String value : values) {
            String normalized = ExtensionManifest.emptyToNull(value);
            if (normalized != null) {
                copy.add(normalized);
            }
        }
        return copy;
    }

    public static final class Builder {
        private ExtensionContributionType type;
        private String name;
        private String description;
        private final List<String> permissions = new ArrayList<String>();
        private boolean requiresExplicitActivation = true;

        private Builder() {
        }

        public Builder type(ExtensionContributionType type) {
            this.type = type;
            return this;
        }

        public Builder type(String type) {
            this.type = ExtensionContributionType.fromId(type);
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder permission(String permission) {
            String normalized = ExtensionManifest.emptyToNull(permission);
            if (normalized != null) {
                this.permissions.add(normalized);
            }
            return this;
        }

        public Builder permissions(Collection<String> permissions) {
            if (permissions != null) {
                for (String permission : permissions) {
                    permission(permission);
                }
            }
            return this;
        }

        public Builder requiresExplicitActivation(boolean requiresExplicitActivation) {
            this.requiresExplicitActivation = requiresExplicitActivation;
            return this;
        }

        public ExtensionContribution build() {
            return new ExtensionContribution(this);
        }
    }
}
