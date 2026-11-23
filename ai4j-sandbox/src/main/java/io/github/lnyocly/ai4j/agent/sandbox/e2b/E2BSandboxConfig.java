package io.github.lnyocly.ai4j.agent.sandbox.e2b;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Connection and creation settings for the E2B sandbox provider.
 *
 * <p>E2B exposes two surfaces: a main control API (default {@code https://api.e2b.app},
 * authenticated with {@code X-API-Key}) used to create/delete sandboxes, and a per-sandbox
 * execution host (default {@code https://49983-&lt;sandboxID&gt;.e2b.app}, authenticated with
 * {@code Authorization: Bearer &lt;apiKey&gt;}) that speaks the Connect server-streaming
 * {@code process.Process/Start} protocol.</p>
 */
public final class E2BSandboxConfig {

    public static final String DEFAULT_PROVIDER_ID = "e2b";
    public static final String DEFAULT_DOMAIN = "e2b.app";
    public static final String DEFAULT_TEMPLATE_ID = "base";
    public static final int DEFAULT_ENVD_PORT = 49983;
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final String providerId;
    private final String apiKey;
    private final String apiDomain;
    private final String apiUrl;
    private final String templateId;
    private final Integer timeoutSeconds;
    private final Integer envdPort;
    private final String sandboxUrl;
    private final String envdAccessToken;
    private final boolean useShellWrap;
    private final boolean deleteOnClose;
    private final long connectTimeoutMillis;
    private final long readTimeoutMillis;
    private final long startTimeoutMillis;
    private final long pollIntervalMillis;
    private final Map<String, String> labels;
    private final Map<String, String> environment;
    private final Map<String, Object> createOptions;
    private final SandboxSpec spec;

    private E2BSandboxConfig(Builder builder) {
        this.providerId = firstNonBlank(builder.providerId, DEFAULT_PROVIDER_ID);
        this.apiKey = trimToNull(builder.apiKey);
        this.apiDomain = trimTrailingSlash(firstNonBlank(builder.apiDomain, DEFAULT_DOMAIN));
        this.apiUrl = trimTrailingSlash(firstNonBlank(builder.apiUrl, "https://api." + this.apiDomain));
        this.templateId = firstNonBlank(builder.templateId, DEFAULT_TEMPLATE_ID);
        this.timeoutSeconds = builder.timeoutSeconds == null ? Integer.valueOf(DEFAULT_TIMEOUT_SECONDS) : builder.timeoutSeconds;
        this.envdPort = builder.envdPort == null ? Integer.valueOf(DEFAULT_ENVD_PORT) : builder.envdPort;
        this.sandboxUrl = trimTrailingSlash(builder.sandboxUrl);
        this.envdAccessToken = trimToNull(builder.envdAccessToken);
        this.useShellWrap = builder.useShellWrap == null || builder.useShellWrap.booleanValue();
        this.deleteOnClose = builder.deleteOnClose == null || builder.deleteOnClose.booleanValue();
        this.connectTimeoutMillis = positiveOrDefault(builder.connectTimeoutMillis, 10000L);
        this.readTimeoutMillis = positiveOrDefault(builder.readTimeoutMillis, 120000L);
        this.startTimeoutMillis = nonNegativeOrDefault(builder.startTimeoutMillis, 60000L);
        this.pollIntervalMillis = positiveOrDefault(builder.pollIntervalMillis, 1000L);
        this.labels = copyStringMap(builder.labels);
        this.environment = copyStringMap(builder.environment);
        this.createOptions = copyObjectMap(builder.createOptions);
        this.spec = builder.spec == null
                ? SandboxSpec.builder().providerId(this.providerId).build()
                : builder.spec.copy();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static E2BSandboxConfig fromEnvironment(SandboxSpec spec) {
        return fromEnvironment(spec, System.getenv());
    }

    public static E2BSandboxConfig fromEnvironment(SandboxSpec spec, Map<String, String> env) {
        Builder builder = builder()
                .providerId(DEFAULT_PROVIDER_ID)
                .apiKey(envValue(env, "E2B_API_KEY"))
                .apiDomain(firstNonBlank(envValue(env, "E2B_DOMAIN"), DEFAULT_DOMAIN))
                .apiUrl(envValue(env, "E2B_API_URL"))
                .templateId(firstNonBlank(envValue(env, "E2B_TEMPLATE_ID"), DEFAULT_TEMPLATE_ID))
                .envdAccessToken(envValue(env, "E2B_ACCESS_TOKEN"))
                .sandboxUrl(envValue(env, "E2B_SANDBOX_URL"));
        String envdPort = envValue(env, "E2B_ENVD_PORT");
        if (envdPort != null) {
            builder.envdPort(asInt(envdPort));
        }
        String timeout = envValue(env, "E2B_TIMEOUT");
        if (timeout != null) {
            builder.timeoutSeconds(asInt(timeout));
        }
        applySpec(builder, spec);
        return builder.build();
    }

    private static void applySpec(Builder builder, SandboxSpec spec) {
        if (spec == null) {
            return;
        }
        builder.spec(spec.copy());
        if (spec.getProviderId() != null) {
            builder.providerId(spec.getProviderId());
        }
        if (spec.getImage() != null) {
            builder.templateId(spec.getImage());
        }
        builder.labels(spec.getLabels());

        Map<String, Object> config = spec.getConfig();
        builder.apiKey(asString(config.get("apiKey")));
        builder.apiDomain(asString(config.get("apiDomain")));
        builder.apiUrl(asString(config.get("apiUrl")));
        builder.templateId(firstNonBlank(asString(config.get("templateId")), asString(config.get("templateID"))));
        builder.envdPort(asInt(config.get("envdPort")));
        builder.timeoutSeconds(asInt(config.get("timeoutSeconds")));
        builder.sandboxUrl(asString(config.get("sandboxUrl")));
        builder.envdAccessToken(asString(config.get("envdAccessToken")));
        builder.useShellWrap(asBoolean(config.get("useShellWrap")));
        builder.deleteOnClose(asBoolean(config.get("deleteOnClose")));
        builder.connectTimeoutMillis(asLong(config.get("connectTimeoutMillis")));
        builder.readTimeoutMillis(asLong(config.get("readTimeoutMillis")));
        builder.startTimeoutMillis(asLong(config.get("startTimeoutMillis")));
        builder.pollIntervalMillis(asLong(config.get("pollIntervalMillis")));
        builder.environment(asStringMap(config.get("env")));

        Map<String, Object> createOptions = new LinkedHashMap<String, Object>();
        copyKnownCreateOption(config, createOptions, "public");
        copyKnownCreateOption(config, createOptions, "secure");
        copyKnownCreateOption(config, createOptions, "metadata");
        copyKnownCreateOption(config, createOptions, "resources");
        builder.createOptions(createOptions);
    }

    private static void copyKnownCreateOption(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    public String getProviderId() {
        return providerId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiDomain() {
        return apiDomain;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getTemplateId() {
        return templateId;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Integer getEnvdPort() {
        return envdPort;
    }

    public String getSandboxUrl() {
        return sandboxUrl;
    }

    public String getEnvdAccessToken() {
        return envdAccessToken;
    }

    public boolean isUseShellWrap() {
        return useShellWrap;
    }

    public boolean isDeleteOnClose() {
        return deleteOnClose;
    }

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public long getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public long getStartTimeoutMillis() {
        return startTimeoutMillis;
    }

    public long getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public Map<String, String> getLabels() {
        return copyStringMap(labels);
    }

    public Map<String, String> getEnvironment() {
        return copyStringMap(environment);
    }

    public Map<String, Object> getCreateOptions() {
        return copyObjectMap(createOptions);
    }

    public SandboxSpec getSpec() {
        return spec.copy();
    }

    /**
     * Builds the per-sandbox execution host URL for a given sandbox id.
     */
    public String buildSandboxHost(String sandboxId) {
        if (sandboxUrl != null) {
            return sandboxUrl;
        }
        String id = trimToNull(sandboxId);
        if (id == null) {
            return null;
        }
        return "https://" + envdPort.intValue() + "-" + id + "." + apiDomain;
    }

    E2BSandboxConfig withSpecOverrides(SandboxSpec spec) {
        if (spec == null) {
            return this;
        }
        Builder builder = builder()
                .providerId(providerId)
                .apiKey(apiKey)
                .apiDomain(apiDomain)
                .apiUrl(apiUrl)
                .templateId(templateId)
                .envdPort(envdPort)
                .timeoutSeconds(timeoutSeconds)
                .sandboxUrl(sandboxUrl)
                .envdAccessToken(envdAccessToken)
                .useShellWrap(Boolean.valueOf(useShellWrap))
                .deleteOnClose(Boolean.valueOf(deleteOnClose))
                .connectTimeoutMillis(Long.valueOf(connectTimeoutMillis))
                .readTimeoutMillis(Long.valueOf(readTimeoutMillis))
                .startTimeoutMillis(Long.valueOf(startTimeoutMillis))
                .pollIntervalMillis(Long.valueOf(pollIntervalMillis))
                .labels(labels)
                .environment(environment)
                .createOptions(createOptions)
                .spec(this.spec);
        applySpec(builder, spec);
        builder.apiKey(firstNonBlank(apiKey, builder.apiKey));
        builder.apiUrl(firstNonBlank(apiUrl, builder.apiUrl));
        builder.templateId(firstNonBlank(templateId, builder.templateId));
        return builder.build();
    }

    E2BSandboxConfig withDeleteOnClose(boolean deleteOnClose) {
        return builder()
                .providerId(providerId)
                .apiKey(apiKey)
                .apiDomain(apiDomain)
                .apiUrl(apiUrl)
                .templateId(templateId)
                .envdPort(envdPort)
                .timeoutSeconds(timeoutSeconds)
                .sandboxUrl(sandboxUrl)
                .envdAccessToken(envdAccessToken)
                .useShellWrap(useShellWrap)
                .deleteOnClose(deleteOnClose)
                .connectTimeoutMillis(connectTimeoutMillis)
                .readTimeoutMillis(readTimeoutMillis)
                .startTimeoutMillis(startTimeoutMillis)
                .pollIntervalMillis(pollIntervalMillis)
                .labels(labels)
                .environment(environment)
                .createOptions(createOptions)
                .spec(spec)
                .build();
    }

    static String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String trimTrailingSlash(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    static Map<String, String> copyStringMap(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<String, String>();
        if (source != null) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (entry != null && trimToNull(entry.getKey()) != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return copy;
    }

    static Map<String, Object> copyObjectMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (source != null) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry != null && trimToNull(entry.getKey()) != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return copy;
    }

    static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return trimToNull(text);
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = asString(value);
        return text == null ? null : Boolean.valueOf(text);
    }

    static Integer asInt(Object value) {
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        String text = asString(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long asLong(Object value) {
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        String text = asString(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> asStringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (!(value instanceof Map)) {
            return result;
        }
        Map<Object, Object> source = (Map<Object, Object>) value;
        for (Map.Entry<Object, Object> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private static long positiveOrDefault(Long value, long fallback) {
        return value != null && value.longValue() > 0L ? value.longValue() : fallback;
    }

    private static long nonNegativeOrDefault(Long value, long fallback) {
        return value != null && value.longValue() >= 0L ? value.longValue() : fallback;
    }

    private static String envValue(Map<String, String> env, String key) {
        return env == null ? null : env.get(key);
    }

    public static final class Builder {
        private String providerId;
        private String apiKey;
        private String apiDomain;
        private String apiUrl;
        private String templateId;
        private Integer timeoutSeconds;
        private Integer envdPort;
        private String sandboxUrl;
        private String envdAccessToken;
        private Boolean useShellWrap;
        private Boolean deleteOnClose;
        private Long connectTimeoutMillis;
        private Long readTimeoutMillis;
        private Long startTimeoutMillis;
        private Long pollIntervalMillis;
        private Map<String, String> labels;
        private Map<String, String> environment;
        private Map<String, Object> createOptions;
        private SandboxSpec spec;

        private Builder() {
        }

        public Builder providerId(String providerId) {
            if (providerId != null) {
                this.providerId = providerId;
            }
            return this;
        }

        public Builder apiKey(String apiKey) {
            if (apiKey != null) {
                this.apiKey = apiKey;
            }
            return this;
        }

        public Builder apiDomain(String apiDomain) {
            if (apiDomain != null) {
                this.apiDomain = apiDomain;
            }
            return this;
        }

        public Builder apiUrl(String apiUrl) {
            if (apiUrl != null) {
                this.apiUrl = apiUrl;
            }
            return this;
        }

        public Builder templateId(String templateId) {
            if (templateId != null) {
                this.templateId = templateId;
            }
            return this;
        }

        public Builder timeoutSeconds(Integer timeoutSeconds) {
            if (timeoutSeconds != null) {
                this.timeoutSeconds = timeoutSeconds;
            }
            return this;
        }

        public Builder envdPort(Integer envdPort) {
            if (envdPort != null) {
                this.envdPort = envdPort;
            }
            return this;
        }

        public Builder sandboxUrl(String sandboxUrl) {
            if (sandboxUrl != null) {
                this.sandboxUrl = sandboxUrl;
            }
            return this;
        }

        public Builder envdAccessToken(String envdAccessToken) {
            if (envdAccessToken != null) {
                this.envdAccessToken = envdAccessToken;
            }
            return this;
        }

        public Builder useShellWrap(Boolean useShellWrap) {
            if (useShellWrap != null) {
                this.useShellWrap = useShellWrap;
            }
            return this;
        }

        public Builder deleteOnClose(Boolean deleteOnClose) {
            if (deleteOnClose != null) {
                this.deleteOnClose = deleteOnClose;
            }
            return this;
        }

        public Builder connectTimeoutMillis(Long connectTimeoutMillis) {
            if (connectTimeoutMillis != null) {
                this.connectTimeoutMillis = connectTimeoutMillis;
            }
            return this;
        }

        public Builder readTimeoutMillis(Long readTimeoutMillis) {
            if (readTimeoutMillis != null) {
                this.readTimeoutMillis = readTimeoutMillis;
            }
            return this;
        }

        public Builder startTimeoutMillis(Long startTimeoutMillis) {
            if (startTimeoutMillis != null) {
                this.startTimeoutMillis = startTimeoutMillis;
            }
            return this;
        }

        public Builder pollIntervalMillis(Long pollIntervalMillis) {
            if (pollIntervalMillis != null) {
                this.pollIntervalMillis = pollIntervalMillis;
            }
            return this;
        }

        public Builder label(String key, String value) {
            if (labels == null) {
                labels = new LinkedHashMap<String, String>();
            }
            if (key != null && value != null) {
                labels.put(key, value);
            }
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            if (labels != null) {
                if (this.labels == null) {
                    this.labels = new LinkedHashMap<String, String>();
                }
                this.labels.putAll(copyStringMap(labels));
            }
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            if (environment != null) {
                if (this.environment == null) {
                    this.environment = new LinkedHashMap<String, String>();
                }
                this.environment.putAll(copyStringMap(environment));
            }
            return this;
        }

        public Builder createOptions(Map<String, Object> createOptions) {
            if (createOptions != null) {
                if (this.createOptions == null) {
                    this.createOptions = new LinkedHashMap<String, Object>();
                }
                this.createOptions.putAll(copyObjectMap(createOptions));
            }
            return this;
        }

        public Builder spec(SandboxSpec spec) {
            this.spec = spec;
            return this;
        }

        public E2BSandboxConfig build() {
            return new E2BSandboxConfig(this);
        }
    }
}
