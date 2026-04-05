package io.github.lnyocly.ai4j.cli.provider;

import java.util.LinkedHashMap;
import java.util.Map;

public class CliProvidersConfig {

    private String defaultProfile;
    private Map<String, CliProviderProfile> profiles = new LinkedHashMap<String, CliProviderProfile>();

    public CliProvidersConfig() {
    }

    public CliProvidersConfig(String defaultProfile, Map<String, CliProviderProfile> profiles) {
        this.defaultProfile = defaultProfile;
        this.profiles = profiles == null
                ? new LinkedHashMap<String, CliProviderProfile>()
                : new LinkedHashMap<String, CliProviderProfile>(profiles);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .defaultProfile(defaultProfile)
                .profiles(profiles);
    }

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public Map<String, CliProviderProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, CliProviderProfile> profiles) {
        this.profiles = profiles == null
                ? new LinkedHashMap<String, CliProviderProfile>()
                : new LinkedHashMap<String, CliProviderProfile>(profiles);
    }

    public static final class Builder {

        private String defaultProfile;
        private Map<String, CliProviderProfile> profiles = new LinkedHashMap<String, CliProviderProfile>();

        private Builder() {
        }

        public Builder defaultProfile(String defaultProfile) {
            this.defaultProfile = defaultProfile;
            return this;
        }

        public Builder profiles(Map<String, CliProviderProfile> profiles) {
            this.profiles = profiles == null
                    ? new LinkedHashMap<String, CliProviderProfile>()
                    : new LinkedHashMap<String, CliProviderProfile>(profiles);
            return this;
        }

        public CliProvidersConfig build() {
            return new CliProvidersConfig(defaultProfile, profiles);
        }
    }
}
