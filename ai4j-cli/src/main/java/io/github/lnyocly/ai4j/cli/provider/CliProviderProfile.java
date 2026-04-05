package io.github.lnyocly.ai4j.cli.provider;

public class CliProviderProfile {

    private String provider;
    private String protocol;
    private String model;
    private String baseUrl;
    private String apiKey;

    public CliProviderProfile() {
    }

    public CliProviderProfile(String provider, String protocol, String model, String baseUrl, String apiKey) {
        this.provider = provider;
        this.protocol = protocol;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .provider(provider)
                .protocol(protocol)
                .model(model)
                .baseUrl(baseUrl)
                .apiKey(apiKey);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public static final class Builder {

        private String provider;
        private String protocol;
        private String model;
        private String baseUrl;
        private String apiKey;

        private Builder() {
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public CliProviderProfile build() {
            return new CliProviderProfile(provider, protocol, model, baseUrl, apiKey);
        }
    }
}
