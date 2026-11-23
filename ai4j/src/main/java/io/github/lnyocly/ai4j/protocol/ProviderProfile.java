package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.JsonValues;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Data-driven provider parameter policy.
 *
 * <p>A profile holds no vendor branches: it narrows capabilities and renames or
 * defaults wire parameters, so a standard-shape vendor onboards through
 * endpoint configuration alone (design A1). Vendor code belongs only in a
 * profile when the parameter policy genuinely differs.</p>
 */
public final class ProviderProfile {

    /** No narrowing: the protocol baseline applies unchanged. */
    public static final ProviderProfile CUSTOM = new Builder("CUSTOM").build();

    private final String name;
    private final String maxTokensParameter;
    private final boolean includeStreamOptions;
    private final Set<Capability> disabledCapabilities;
    private final Map<String, Object> defaultProviderOptions;

    private ProviderProfile(String name, String maxTokensParameter, boolean includeStreamOptions,
                            Set<Capability> disabledCapabilities,
                            Map<String, Object> defaultProviderOptions) {
        this.name = Objects.requireNonNull(name, "name is required");
        this.maxTokensParameter = maxTokensParameter;
        this.includeStreamOptions = includeStreamOptions;
        this.disabledCapabilities = disabledCapabilities == null
                ? Collections.<Capability>emptySet()
                : Collections.unmodifiableSet(disabledCapabilities);
        this.defaultProviderOptions = JsonValues.copyObject(defaultProviderOptions);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    /** Wire parameter name for the output-token limit; {@code null} disables it. */
    public String getMaxTokensParameter() {
        return maxTokensParameter;
    }

    public boolean isIncludeStreamOptions() {
        return includeStreamOptions;
    }

    public Set<Capability> getDisabledCapabilities() {
        return disabledCapabilities;
    }

    public Map<String, Object> getDefaultProviderOptions() {
        return defaultProviderOptions;
    }

    @Override
    public String toString() {
        return "ProviderProfile{" + name + '}';
    }

    /** Fluent builder. */
    public static final class Builder {

        private final String name;
        private String maxTokensParameter = "max_tokens";
        private boolean includeStreamOptions = true;
        private final java.util.EnumSet<Capability> disabled = java.util.EnumSet.noneOf(Capability.class);
        private final Map<String, Object> defaultProviderOptions = new LinkedHashMap<String, Object>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder maxTokensParameter(String maxTokensParameter) {
            this.maxTokensParameter = maxTokensParameter;
            return this;
        }

        public Builder includeStreamOptions(boolean includeStreamOptions) {
            this.includeStreamOptions = includeStreamOptions;
            return this;
        }

        public Builder disable(Capability capability) {
            this.disabled.add(capability);
            return this;
        }

        public Builder defaultProviderOption(String key, Object value) {
            this.defaultProviderOptions.put(key, value);
            return this;
        }

        public ProviderProfile build() {
            return new ProviderProfile(name, maxTokensParameter, includeStreamOptions,
                    disabled, defaultProviderOptions);
        }
    }
}
