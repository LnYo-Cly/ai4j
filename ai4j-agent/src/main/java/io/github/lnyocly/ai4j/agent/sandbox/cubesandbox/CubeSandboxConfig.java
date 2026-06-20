package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.util.LinkedHashMap;
import java.util.Locale;
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
    public static final String DEFAULT_PROXY_SCHEME = "http";
    public static final int DEFAULT_ENVD_PORT = 49983;
    public static final int DEFAULT_PROXY_PORT_HTTP = 80;
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_REQUEST_TIMEOUT_MILLIS = 30000;
    public static final int DEFAULT_CONNECT_ENVELOPE_LIMIT_BYTES = 64 * 1024 * 1024;

    private final String providerId;
    private final String apiUrl;
    private final String apiKey;
    private final String templateId;
    private final String proxyNodeIp;
    private final int proxyPortHttp;
    private final String proxyScheme;
    private final String sandboxDomain;
    private final int envdPort;
    private final int timeoutSeconds;
    private final int requestTimeoutMillis;
    private final boolean closeDestroysSandbox;
    private final boolean allowInternetAccessDefault;
    private final String user;
    private final int connectEnvelopeLimitBytes;

    private CubeSandboxConfig(Builder builder) {
        this.providerId = trimToDefault(builder.providerId, DEFAULT_PROVIDER_ID);
        this.apiUrl = stripTrailingSlash(trimToDefault(builder.apiUrl, DEFAULT_API_URL));
        this.apiKey = trimToNull(builder.apiKey);
        this.templateId = trimToNull(builder.templateId);
        this.proxyNodeIp = trimToNull(builder.proxyNodeIp);
        this.proxyPortHttp = positiveOrDefault(builder.proxyPortHttp, DEFAULT_PROXY_PORT_HTTP);
        this.proxyScheme = normalizeScheme(builder.proxyScheme, this.proxyPortHttp);
        this.sandboxDomain = trimToDefault(builder.sandboxDomain, DEFAULT_SANDBOX_DOMAIN);
        this.envdPort = positiveOrDefault(builder.envdPort, DEFAULT_ENVD_PORT);
        this.timeoutSeconds = positiveOrDefault(builder.timeoutSeconds, DEFAULT_TIMEOUT_SECONDS);
        this.requestTimeoutMillis = positiveOrDefault(builder.requestTimeoutMillis, DEFAULT_REQUEST_TIMEOUT_MILLIS);
        this.closeDestroysSandbox = builder.closeDestroysSandbox == null || builder.closeDestroysSandbox.booleanValue();
        this.allowInternetAccessDefault = builder.allowInternetAccessDefault == null || builder.allowInternetAccessDefault.booleanValue();
        this.user = trimToDefault(builder.user, "root");
        this.connectEnvelopeLimitBytes = positiveOrDefault(builder.connectEnvelopeLimitBytes, DEFAULT_CONNECT_ENVELOPE_LIMIT_BYTES);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CubeSandboxConfig fromEnvironment() {
        return builder()
                .apiUrl(firstEnv("CUBE_API_URL", "E2B_API_URL"))
                .apiKey(firstEnv("CUBE_API_KEY", "E2B_API_KEY"))
                .templateId(env("CUBE_TEMPLATE_ID"))
                .proxyNodeIp(env("CUBE_PROXY_NODE_IP"))
                .proxyPortHttp(parseInt(env("CUBE_PROXY_PORT_HTTP")))
                .proxyScheme(env("CUBE_PROXY_SCHEME"))
                .sandboxDomain(env("CUBE_SANDBOX_DOMAIN"))
                .envdPort(parseInt(env("CUBE_ENVD_PORT")))
                .timeoutSeconds(parseSeconds(env("CUBE_TIMEOUT")))
                .requestTimeoutMillis(parseMillis(env("CUBE_REQUEST_TIMEOUT")))
                .build();
    }

    public CubeSandboxConfig withSpec(SandboxSpec spec) {
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
        builder.proxyNodeIp(stringValue(config, "proxyNodeIp", "proxyNodeIP"));
        builder.proxyPortHttp(intValue(config, "proxyPortHttp", "proxyPortHTTP", "proxyPort"));
        builder.proxyScheme(stringValue(config, "proxyScheme"));
        builder.sandboxDomain(stringValue(config, "sandboxDomain", "domain"));
        builder.envdPort(intValue(config, "envdPort", "envdHTTPPort", "dataPort"));
        builder.timeoutSeconds(intValue(config, "timeoutSeconds", "timeout"));
        Integer requestTimeoutMillis = explicitMillisValue(config, "requestTimeoutMillis");
        if (requestTimeoutMillis == null) {
            requestTimeoutMillis = millisValue(config, "requestTimeout");
        }
        builder.requestTimeoutMillis(requestTimeoutMillis);
        builder.closeDestroysSandbox(booleanValue(config, "closeDestroysSandbox", "destroyOnClose"));
        builder.allowInternetAccessDefault(booleanValue(config, "allowInternetAccess", "allowInternetAccessDefault"));
        builder.user(stringValue(config, "user"));
        builder.connectEnvelopeLimitBytes(intValue(config, "connectEnvelopeLimitBytes"));
        return builder.build();
    }

    public Builder toBuilder() {
        return builder()
                .providerId(providerId)
                .apiUrl(apiUrl)
                .apiKey(apiKey)
                .templateId(templateId)
                .proxyNodeIp(proxyNodeIp)
                .proxyPortHttp(proxyPortHttp)
                .proxyScheme(proxyScheme)
                .sandboxDomain(sandboxDomain)
                .envdPort(envdPort)
                .timeoutSeconds(timeoutSeconds)
                .requestTimeoutMillis(requestTimeoutMillis)
                .closeDestroysSandbox(closeDestroysSandbox)
                .allowInternetAccessDefault(allowInternetAccessDefault)
                .user(user)
                .connectEnvelopeLimitBytes(connectEnvelopeLimitBytes);
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

    public String getProxyNodeIp() {
        return proxyNodeIp;
    }

    public int getProxyPortHttp() {
        return proxyPortHttp;
    }

    public String getProxyScheme() {
        return proxyScheme;
    }

    public String getSandboxDomain() {
        return sandboxDomain;
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

    Map<String, String> safeConfigLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("sandboxDomain", sandboxDomain);
        labels.put("envdPort", String.valueOf(envdPort));
        labels.put("proxyScheme", proxyScheme);
        labels.put("closeDestroysSandbox", String.valueOf(closeDestroysSandbox));
        return labels;
    }

    private static String firstEnv(String first, String second) {
        String value = env(first);
        return value == null ? env(second) : value;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return trimToNull(value);
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
        String result = value;
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value.intValue() <= 0 ? fallback : value.intValue();
    }

    private static String normalizeScheme(String value, int port) {
        String scheme = trimToNull(value);
        if (scheme != null) {
            String lower = scheme.toLowerCase(Locale.ENGLISH);
            if ("http".equals(lower) || "https".equals(lower)) {
                return lower;
            }
        }
        return port == 443 ? "https" : DEFAULT_PROXY_SCHEME;
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
        String trimmed = value.trim().toLowerCase(Locale.ENGLISH);
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
        String trimmed = value.trim().toLowerCase(Locale.ENGLISH);
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
        String lower = text.toLowerCase(Locale.ENGLISH);
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

    private static Boolean booleanValue(Map<String, Object> config, String... keys) {
        Object value = objectValue(config, keys);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return null;
        }
        String text = trimToNull(String.valueOf(value));
        return text == null ? null : Boolean.valueOf(text);
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
        private String proxyNodeIp;
        private Integer proxyPortHttp;
        private String proxyScheme;
        private String sandboxDomain;
        private Integer envdPort;
        private Integer timeoutSeconds;
        private Integer requestTimeoutMillis;
        private Boolean closeDestroysSandbox;
        private Boolean allowInternetAccessDefault;
        private String user;
        private Integer connectEnvelopeLimitBytes;

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

        public Builder proxyNodeIp(String proxyNodeIp) {
            if (proxyNodeIp != null) {
                this.proxyNodeIp = proxyNodeIp;
            }
            return this;
        }

        public Builder proxyPortHttp(Integer proxyPortHttp) {
            if (proxyPortHttp != null) {
                this.proxyPortHttp = proxyPortHttp;
            }
            return this;
        }

        public Builder proxyScheme(String proxyScheme) {
            if (proxyScheme != null) {
                this.proxyScheme = proxyScheme;
            }
            return this;
        }

        public Builder sandboxDomain(String sandboxDomain) {
            if (sandboxDomain != null) {
                this.sandboxDomain = sandboxDomain;
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

        public CubeSandboxConfig build() {
            return new CubeSandboxConfig(this);
        }
    }
}
