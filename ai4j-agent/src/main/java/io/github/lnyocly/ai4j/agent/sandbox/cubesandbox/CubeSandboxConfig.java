package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for the CubeSandbox adapter.
 *
 * <p>Connection values are resolved from explicit builder values or
 * environment variables, then task-local non-secret options in
 * {@link SandboxSpec#getConfig()} can narrow the sandbox request. Secrets are
 * kept out of {@link SandboxSpec} so they are not accidentally persisted in
 * session snapshots.</p>
 */
public final class CubeSandboxConfig {

    public static final String DEFAULT_PROVIDER_ID = "cubesandbox";
    public static final String DEFAULT_API_URL = "http://127.0.0.1:3000";
    public static final String DEFAULT_SANDBOX_DOMAIN = "cube.app";
    public static final int DEFAULT_ENVD_PORT = 49983;
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_REQUEST_TIMEOUT_MILLIS = 30000;
    public static final int DEFAULT_CONNECT_ENVELOPE_LIMIT_BYTES = 64 * 1024 * 1024;
    public static final String CUBE_ENVD_BASE_URL = "CUBE_ENVD_BASE_URL";

    private final String providerId;
    private final String apiUrl;
    private final String apiKey;
    private final String templateId;
    private final String sandboxDomain;
    private final String envdBaseUrl;
    private final int envdPort;
    private final int timeoutSeconds;
    private final int requestTimeoutMillis;
    private final boolean closeDestroysSandbox;
    private final boolean allowInternetAccessDefault;
    private final String user;
    private final int connectEnvelopeLimitBytes;
    private final SandboxSpec spec;

    private CubeSandboxConfig(Builder builder) {
        this.providerId = trimToDefault(builder.providerId, DEFAULT_PROVIDER_ID);
        this.apiUrl = stripTrailingSlash(trimToDefault(builder.apiUrl, DEFAULT_API_URL));
        this.apiKey = trimToNull(builder.apiKey);
        this.templateId = trimToNull(builder.templateId);
        this.sandboxDomain = trimToDefault(builder.sandboxDomain, DEFAULT_SANDBOX_DOMAIN);
        this.envdBaseUrl = stripTrailingSlash(trimToNull(builder.envdBaseUrl));
        this.envdPort = positiveOrDefault(builder.envdPort, DEFAULT_ENVD_PORT);
        this.timeoutSeconds = positiveOrDefault(builder.timeoutSeconds, DEFAULT_TIMEOUT_SECONDS);
        this.requestTimeoutMillis = positiveOrDefault(builder.requestTimeoutMillis, DEFAULT_REQUEST_TIMEOUT_MILLIS);
        this.closeDestroysSandbox = builder.closeDestroysSandbox == null || builder.closeDestroysSandbox.booleanValue();
        this.allowInternetAccessDefault = builder.allowInternetAccessDefault == null || builder.allowInternetAccessDefault.booleanValue();
        this.user = trimToDefault(builder.user, "root");
        this.connectEnvelopeLimitBytes = positiveOrDefault(builder.connectEnvelopeLimitBytes, DEFAULT_CONNECT_ENVELOPE_LIMIT_BYTES);
        this.spec = builder.spec == null
                ? SandboxSpec.builder().providerId(this.providerId).build()
                : builder.spec.copy();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CubeSandboxConfig fromEnvironment() {
        return fromEnvironment(null, System.getenv());
    }

    public static CubeSandboxConfig fromEnvironment(SandboxSpec spec) {
        return fromEnvironment(spec, System.getenv());
    }

    public static CubeSandboxConfig fromEnvironment(SandboxSpec spec, Map<String, String> env) {
        Map<String, String> effectiveEnv = env == null ? Collections.<String, String>emptyMap() : env;
        Builder builder = builder()
                .providerId(DEFAULT_PROVIDER_ID)
                .apiUrl(envValue(effectiveEnv, "CUBE_API_URL") != null
                        ? envValue(effectiveEnv, "CUBE_API_URL")
                        : envValue(effectiveEnv, "E2B_API_URL"))
                .apiKey(envValue(effectiveEnv, "CUBE_API_KEY") != null
                        ? envValue(effectiveEnv, "CUBE_API_KEY")
                        : envValue(effectiveEnv, "E2B_API_KEY"))
                .templateId(envValue(effectiveEnv, "CUBE_TEMPLATE_ID"))
                .sandboxDomain(envValue(effectiveEnv, "CUBE_SANDBOX_DOMAIN"))
                .envdBaseUrl(envValue(effectiveEnv, "CUBE_ENVD_BASE_URL"))
                .envdPort(parseInt(envValue(effectiveEnv, "CUBE_ENVD_PORT")))
                .timeoutSeconds(parseSeconds(envValue(effectiveEnv, "CUBE_TIMEOUT")))
                .requestTimeoutMillis(parseMillis(envValue(effectiveEnv, "CUBE_REQUEST_TIMEOUT")));
        CubeSandboxConfig config = builder.build();
        if (spec != null) {
            return config.withSpecOverrides(spec);
        }
        return config;
    }

    private static String envValue(Map<String, String> env, String key) {
        if (env == null) {
            return null;
        }
        return env.get(key);
    }

    public CubeSandboxConfig withSpecOverrides(SandboxSpec spec) {
        if (spec == null || spec.getConfig().isEmpty()) {
            return this;
        }
        Map<String, Object> config = spec.getConfig();
        Builder builder = toBuilder();
        builder.apiUrl(stringValue(config, "apiUrl", "apiURL", "baseUrl", "baseURL"));
        // Intentionally do not read apiKey from SandboxSpec. Specs are often
        // copied into session snapshots or task definitions; credentials should
        // come from the provider constructor or environment only.
        builder.templateId(stringValue(config, "templateId", "templateID"));
        builder.sandboxDomain(stringValue(config, "sandboxDomain", "domain"));
        builder.envdBaseUrl(stringValue(config, "envdBaseUrl"));
        builder.envdPort(intValue(config, "envdPort", "envdHTTPPort", "dataPort"));
        builder.timeoutSeconds(intValue(config, "timeoutSeconds", "timeout"));
        Integer requestTimeoutMillis = explicitMillisValue(config, "requestTimeoutMillis");
        if (requestTimeoutMillis == null) {
            requestTimeoutMillis = millisValue(config, "requestTimeout");
        }
        builder.requestTimeoutMillis(requestTimeoutMillis);
        applyBoolean(config, builder::closeDestroysSandbox, "closeDestroysSandbox", "destroyOnClose", "deleteOnClose");
        applyBoolean(config, builder::allowInternetAccessDefault, "allowInternetAccess", "allowInternetAccessDefault");
        builder.user(stringValue(config, "user"));
        builder.connectEnvelopeLimitBytes(intValue(config, "connectEnvelopeLimitBytes"));
        builder.spec(spec.copy());
        return builder.build();
    }

    public Builder toBuilder() {
        return builder()
                .providerId(providerId)
                .apiUrl(apiUrl)
                .apiKey(apiKey)
                .templateId(templateId)
                .sandboxDomain(sandboxDomain)
                .envdBaseUrl(envdBaseUrl)
                .envdPort(envdPort)
                .timeoutSeconds(timeoutSeconds)
                .requestTimeoutMillis(requestTimeoutMillis)
                .closeDestroysSandbox(closeDestroysSandbox)
                .allowInternetAccessDefault(allowInternetAccessDefault)
                .user(user)
                .connectEnvelopeLimitBytes(connectEnvelopeLimitBytes)
                .spec(spec);
    }

    public String getProviderId() {
        return providerId;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getSandboxDomain() {
        return sandboxDomain;
    }

    public String getEnvdBaseUrl() {
        return envdBaseUrl;
    }

    public int getEnvdPort() {
        return envdPort;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public boolean isCloseDestroysSandbox() {
        return closeDestroysSandbox;
    }

    public boolean isAllowInternetAccessDefault() {
        return allowInternetAccessDefault;
    }

    public String getUser() {
        return user;
    }

    public int getConnectEnvelopeLimitBytes() {
        return connectEnvelopeLimitBytes;
    }

    public SandboxSpec getSpec() {
        return spec.copy();
    }

    public Map<String, String> getLabels() {
        return safeConfigLabels();
    }

    public Map<String, String> getEnvironment() {
        return Collections.<String, String>emptyMap();
    }

    Map<String, String> safeConfigLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("sandboxDomain", sandboxDomain);
        labels.put("envdPort", String.valueOf(envdPort));
        labels.put("closeDestroysSandbox", String.valueOf(closeDestroysSandbox));
        return labels;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String result = value;
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value.intValue() <= 0 ? fallback : value.intValue();
    }

    private static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseSeconds(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(java.util.Locale.ENGLISH);
        try {
            if (trimmed.endsWith("ms")) {
                return Integer.valueOf(Math.max(1, (int) Math.ceil(Double.parseDouble(trimmed.substring(0, trimmed.length() - 2)) / 1000.0d)));
            }
            if (trimmed.endsWith("s")) {
                return Integer.valueOf((int) Math.ceil(Double.parseDouble(trimmed.substring(0, trimmed.length() - 1))));
            }
            if (trimmed.endsWith("m")) {
                return Integer.valueOf((int) Math.ceil(Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) * 60.0d));
            }
            return Integer.valueOf((int) Math.ceil(Double.parseDouble(trimmed)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseMillis(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(java.util.Locale.ENGLISH);
        try {
            if (trimmed.endsWith("ms")) {
                return Integer.valueOf((int) Math.ceil(Double.parseDouble(trimmed.substring(0, trimmed.length() - 2))));
            }
            if (trimmed.endsWith("s")) {
                return Integer.valueOf((int) Math.ceil(Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) * 1000.0d));
            }
            return Integer.valueOf((int) Math.ceil(Double.parseDouble(trimmed) * 1000.0d));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stringValue(Map<String, Object> config, String... keys) {
        Object value = objectValue(config, keys);
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private static Integer intValue(Map<String, Object> config, String... keys) {
        Object value = objectValue(config, keys);
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        return value == null ? null : parseInt(String.valueOf(value));
    }

    private static Integer millisValue(Map<String, Object> config, String... keys) {
        Object value = objectValue(config, keys);
        if (value instanceof Number) {
            Number number = (Number) value;
            if (number.doubleValue() > 0 && number.doubleValue() < 1000) {
                return Integer.valueOf((int) Math.ceil(number.doubleValue() * 1000.0d));
            }
            return Integer.valueOf(number.intValue());
        }
        return value == null ? null : parseMillis(String.valueOf(value));
    }

    private static Integer explicitMillisValue(Map<String, Object> config, String key) {
        Object value = objectValue(config, key);
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        if (value == null) {
            return null;
        }
        String text = trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(java.util.Locale.ENGLISH);
        if (lower.endsWith("s") && !lower.endsWith("ms")) {
            return parseMillis(text);
        }
        try {
            if (lower.endsWith("ms")) {
                return Integer.valueOf((int) Math.ceil(Double.parseDouble(lower.substring(0, lower.length() - 2))));
            }
            return Integer.valueOf((int) Math.ceil(Double.parseDouble(lower)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void applyBoolean(Map<String, Object> config, java.util.function.Consumer<Boolean> setter, String... keys) {
        Object value = objectValue(config, keys);
        if (value == null) {
            return;
        }
        if (value instanceof Boolean) {
            setter.accept((Boolean) value);
            return;
        }
        String text = trimToNull(String.valueOf(value));
        if (text != null) {
            setter.accept(Boolean.valueOf(text));
        }
    }

    private static Object objectValue(Map<String, Object> config, String... keys) {
        if (config == null) {
            return null;
        }
        for (String key : keys) {
            if (config.containsKey(key)) {
                return config.get(key);
            }
        }
        return null;
    }

    public static final class Builder {
        private String providerId;
        private String apiUrl;
        private String apiKey;
        private String templateId;
        private String sandboxDomain;
        private String envdBaseUrl;
        private Integer envdPort;
        private Integer timeoutSeconds;
        private Integer requestTimeoutMillis;
        private Boolean closeDestroysSandbox;
        private Boolean allowInternetAccessDefault;
        private String user;
        private Integer connectEnvelopeLimitBytes;
        private SandboxSpec spec;

        private Builder() {
        }

        public Builder providerId(String providerId) {
            if (providerId != null) {
                this.providerId = providerId;
            }
            return this;
        }

        public Builder apiUrl(String apiUrl) {
            if (apiUrl != null) {
                this.apiUrl = apiUrl;
            }
            return this;
        }

        public Builder apiKey(String apiKey) {
            if (apiKey != null) {
                this.apiKey = apiKey;
            }
            return this;
        }

        public Builder templateId(String templateId) {
            if (templateId != null) {
                this.templateId = templateId;
            }
            return this;
        }

        public Builder sandboxDomain(String sandboxDomain) {
            if (sandboxDomain != null) {
                this.sandboxDomain = sandboxDomain;
            }
            return this;
        }

        public Builder envdBaseUrl(String envdBaseUrl) {
            if (envdBaseUrl != null) {
                this.envdBaseUrl = envdBaseUrl;
            }
            return this;
        }

        public Builder envdPort(Integer envdPort) {
            if (envdPort != null) {
                this.envdPort = envdPort;
            }
            return this;
        }

        public Builder timeoutSeconds(Integer timeoutSeconds) {
            if (timeoutSeconds != null) {
                this.timeoutSeconds = timeoutSeconds;
            }
            return this;
        }

        public Builder requestTimeoutMillis(Integer requestTimeoutMillis) {
            if (requestTimeoutMillis != null) {
                this.requestTimeoutMillis = requestTimeoutMillis;
            }
            return this;
        }

        public Builder closeDestroysSandbox(Boolean closeDestroysSandbox) {
            if (closeDestroysSandbox != null) {
                this.closeDestroysSandbox = closeDestroysSandbox;
            }
            return this;
        }

        public Builder allowInternetAccessDefault(Boolean allowInternetAccessDefault) {
            if (allowInternetAccessDefault != null) {
                this.allowInternetAccessDefault = allowInternetAccessDefault;
            }
            return this;
        }

        public Builder user(String user) {
            if (user != null) {
                this.user = user;
            }
            return this;
        }

        public Builder connectEnvelopeLimitBytes(Integer connectEnvelopeLimitBytes) {
            if (connectEnvelopeLimitBytes != null) {
                this.connectEnvelopeLimitBytes = connectEnvelopeLimitBytes;
            }
            return this;
        }

        public Builder spec(SandboxSpec spec) {
            this.spec = spec;
            return this;
        }

        public CubeSandboxConfig build() {
            return new CubeSandboxConfig(this);
        }
    }
}
