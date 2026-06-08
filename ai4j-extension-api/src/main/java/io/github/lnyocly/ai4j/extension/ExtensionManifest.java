package io.github.lnyocly.ai4j.extension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ExtensionManifest {

    private final String id;
    private final String name;
    private final String version;
    private final String vendor;
    private final Set<ExtensionCapability> capabilities;
    private final List<String> permissions;
    private final String configPrefix;

    private ExtensionManifest(Builder builder) {
        this.id = requireId(builder.id, "extension id");
        this.name = emptyToNull(builder.name);
        this.version = emptyToNull(builder.version);
        this.vendor = emptyToNull(builder.vendor);
        this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<ExtensionCapability>(builder.capabilities));
        this.permissions = Collections.unmodifiableList(copyNormalized(builder.permissions));
        this.configPrefix = emptyToNull(builder.configPrefix);
        if (this.capabilities.isEmpty()) {
            throw new IllegalArgumentException("extension capabilities must not be empty");
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getVendor() {
        return vendor;
    }

    public Set<ExtensionCapability> getCapabilities() {
        return capabilities;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public String getConfigPrefix() {
        return configPrefix;
    }

    public boolean hasCapability(ExtensionCapability capability) {
        return capability != null && capabilities.contains(capability);
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
            String normalized = emptyToNull(value);
            if (normalized != null) {
                copy.add(normalized);
            }
        }
        return copy;
    }

    public static String requireId(String value, String field) {
        String normalized = emptyToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    public static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {
        private String id;
        private String name;
        private String version;
        private String vendor;
        private final Set<ExtensionCapability> capabilities = new LinkedHashSet<ExtensionCapability>();
        private final List<String> permissions = new ArrayList<String>();
        private String configPrefix;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder vendor(String vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder capability(ExtensionCapability capability) {
            if (capability != null) {
                this.capabilities.add(capability);
            }
            return this;
        }

        public Builder capability(String capability) {
            this.capabilities.add(ExtensionCapability.fromId(capability));
            return this;
        }

        public Builder capabilities(Collection<ExtensionCapability> capabilities) {
            if (capabilities != null) {
                for (ExtensionCapability capability : capabilities) {
                    capability(capability);
                }
            }
            return this;
        }

        public Builder permission(String permission) {
            String normalized = emptyToNull(permission);
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

        public Builder configPrefix(String configPrefix) {
            this.configPrefix = configPrefix;
            return this;
        }

        public ExtensionManifest build() {
            return new ExtensionManifest(this);
        }
    }
}
