package io.github.lnyocly.ai4j.preset;

import io.github.lnyocly.ai4j.model.JsonValues;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.ProviderProfile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure data declaration of provider dialect switches and capabilities on top of a
 * wire protocol.
 */
public final class ProtocolQuirks {

    public static final ProtocolQuirks DEFAULTS = builder().build();

    private final boolean supportsDeveloperRole;
    private final boolean supportsStreamIncludeUsage;
    private final boolean supportsPromptCaching;
    private final boolean requiresMaxTokens;
    private final boolean requiresThoughtSignatureRoundTrip;
    private final boolean supportsParallelToolCalls;
    private final boolean supportsStrictSchema;
    private final String reasoningFieldName;
    private final String maxTokensParameter;
    private final Map<String, Object> defaultProviderOptions;

    private ProtocolQuirks(Builder builder) {
        this.supportsDeveloperRole = builder.supportsDeveloperRole;
        this.supportsStreamIncludeUsage = builder.supportsStreamIncludeUsage;
        this.supportsPromptCaching = builder.supportsPromptCaching;
        this.requiresMaxTokens = builder.requiresMaxTokens;
        this.requiresThoughtSignatureRoundTrip = builder.requiresThoughtSignatureRoundTrip;
        this.supportsParallelToolCalls = builder.supportsParallelToolCalls;
        this.supportsStrictSchema = builder.supportsStrictSchema;
        this.reasoningFieldName = builder.reasoningFieldName;
        this.maxTokensParameter = builder.maxTokensParameter;
        this.defaultProviderOptions = JsonValues.copyObject(builder.defaultProviderOptions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSupportsDeveloperRole() {
        return supportsDeveloperRole;
    }

    public boolean isSupportsStreamIncludeUsage() {
        return supportsStreamIncludeUsage;
    }

    public boolean isSupportsPromptCaching() {
        return supportsPromptCaching;
    }

    public boolean isRequiresMaxTokens() {
        return requiresMaxTokens;
    }

    public boolean isRequiresThoughtSignatureRoundTrip() {
        return requiresThoughtSignatureRoundTrip;
    }

    public boolean isSupportsParallelToolCalls() {
        return supportsParallelToolCalls;
    }

    public boolean isSupportsStrictSchema() {
        return supportsStrictSchema;
    }

    public String getReasoningFieldName() {
        return reasoningFieldName;
    }

    public String getMaxTokensParameter() {
        return maxTokensParameter;
    }

    public Map<String, Object> getDefaultProviderOptions() {
        return defaultProviderOptions;
    }

    /** Project these dialect quirks onto a kernel {@link ProviderProfile}. */
    public ProviderProfile toProviderProfile(String profileName) {
        ProviderProfile.Builder profile = ProviderProfile.builder(
                profileName == null || profileName.trim().isEmpty() ? "CUSTOM" : profileName);
        profile.maxTokensParameter(maxTokensParameter);
        profile.includeStreamOptions(supportsStreamIncludeUsage);
        if (!supportsParallelToolCalls) {
            profile.disable(Capability.PARALLEL_TOOL_CALLS);
        }
        if (!supportsStrictSchema) {
            profile.disable(Capability.STRICT_SCHEMA);
        }
        for (Map.Entry<String, Object> entry : defaultProviderOptions.entrySet()) {
            profile.defaultProviderOption(entry.getKey(), entry.getValue());
        }
        return profile.build();
    }

    /** Fluent builder. */
    public static final class Builder {

        private boolean supportsDeveloperRole = true;
        private boolean supportsStreamIncludeUsage = true;
        private boolean supportsPromptCaching;
        private boolean requiresMaxTokens;
        private boolean requiresThoughtSignatureRoundTrip;
        private boolean supportsParallelToolCalls = true;
        private boolean supportsStrictSchema = true;
        private String reasoningFieldName;
        private String maxTokensParameter = "max_tokens";
        private final Map<String, Object> defaultProviderOptions = new LinkedHashMap<String, Object>();

        private Builder() {
        }

        public Builder supportsDeveloperRole(boolean supportsDeveloperRole) {
            this.supportsDeveloperRole = supportsDeveloperRole;
            return this;
        }

        public Builder supportsStreamIncludeUsage(boolean supportsStreamIncludeUsage) {
            this.supportsStreamIncludeUsage = supportsStreamIncludeUsage;
            return this;
        }

        public Builder supportsPromptCaching(boolean supportsPromptCaching) {
            this.supportsPromptCaching = supportsPromptCaching;
            return this;
        }

        public Builder requiresMaxTokens(boolean requiresMaxTokens) {
            this.requiresMaxTokens = requiresMaxTokens;
            return this;
        }

        public Builder requiresThoughtSignatureRoundTrip(boolean requiresThoughtSignatureRoundTrip) {
            this.requiresThoughtSignatureRoundTrip = requiresThoughtSignatureRoundTrip;
            return this;
        }

        public Builder supportsParallelToolCalls(boolean supportsParallelToolCalls) {
            this.supportsParallelToolCalls = supportsParallelToolCalls;
            return this;
        }

        public Builder supportsStrictSchema(boolean supportsStrictSchema) {
            this.supportsStrictSchema = supportsStrictSchema;
            return this;
        }

        public Builder reasoningFieldName(String reasoningFieldName) {
            this.reasoningFieldName = reasoningFieldName;
            return this;
        }

        public Builder maxTokensParameter(String maxTokensParameter) {
            this.maxTokensParameter = maxTokensParameter;
            return this;
        }

        public Builder defaultProviderOption(String key, Object value) {
            if (key != null) {
                this.defaultProviderOptions.put(key, value);
            }
            return this;
        }

        public Builder defaultProviderOptions(Map<String, Object> options) {
            if (options != null) {
                this.defaultProviderOptions.putAll(options);
            }
            return this;
        }

        public ProtocolQuirks build() {
            return new ProtocolQuirks(this);
        }
    }
}
