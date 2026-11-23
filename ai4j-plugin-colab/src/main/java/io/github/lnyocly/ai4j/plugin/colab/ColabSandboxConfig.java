package io.github.lnyocly.ai4j.plugin.colab;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration model for connecting {@code ai4j-plugin-colab} to a Google Colab / Jupyter runtime server.
 */
public final class ColabSandboxConfig {

    public static final String DEFAULT_KERNEL_NAME = "python3";
    public static final String DEFAULT_SESSION_NAME = "ai4j-colab-session";
    public static final String DEFAULT_NOTEBOOK_PATH = "/ai4j_colab_session.ipynb";
    public static final long DEFAULT_CONNECT_TIMEOUT_MS = 15_000L;
    public static final long DEFAULT_READ_TIMEOUT_MS = 60_000L;
    public static final long DEFAULT_EXECUTION_TIMEOUT_MS = 120_000L;

    public static final String ENV_COLAB_API_URL = "COLAB_API_URL";
    public static final String ENV_COLAB_JUPYTER_URL = "COLAB_JUPYTER_URL";
    public static final String ENV_JUPYTER_URL = "JUPYTER_URL";
    public static final String ENV_COLAB_API_TOKEN = "COLAB_API_TOKEN";
    public static final String ENV_JUPYTER_TOKEN = "JUPYTER_TOKEN";
    public static final String ENV_COLAB_KERNEL_NAME = "COLAB_KERNEL_NAME";

    private final String baseUrl;
    private final String apiToken;
    private final String kernelName;
    private final String sessionName;
    private final String notebookPath;
    private final long connectTimeoutMillis;
    private final long readTimeoutMillis;
    private final long executionTimeoutMillis;
    private final Map<String, String> headers;

    private ColabSandboxConfig(Builder builder) {
        this.baseUrl = trimTrailingSlashes(builder.baseUrl);
        this.apiToken = trimToNull(builder.apiToken);
        this.kernelName = firstNonBlank(builder.kernelName, DEFAULT_KERNEL_NAME);
        this.sessionName = firstNonBlank(builder.sessionName, DEFAULT_SESSION_NAME);
        this.notebookPath = firstNonBlank(builder.notebookPath, DEFAULT_NOTEBOOK_PATH);
        this.connectTimeoutMillis = positiveOrDefault(builder.connectTimeoutMillis, DEFAULT_CONNECT_TIMEOUT_MS);
        this.readTimeoutMillis = positiveOrDefault(builder.readTimeoutMillis, DEFAULT_READ_TIMEOUT_MS);
        this.executionTimeoutMillis = positiveOrDefault(builder.executionTimeoutMillis, DEFAULT_EXECUTION_TIMEOUT_MS);
        this.headers = builder.headers == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.headers));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ColabSandboxConfig from(SandboxSpec spec, Map<String, String> env) {
        Map<String, String> resolvedEnv = env == null ? System.getenv() : env;
        Map<String, Object> specConfig = spec == null || spec.getConfig() == null
                ? Collections.<String, Object>emptyMap()
                : spec.getConfig();

        String baseUrl = firstNonBlank(
                stringValue(specConfig.get("baseUrl")),
                stringValue(specConfig.get("apiUrl")),
                stringValue(specConfig.get("jupyterUrl")),
                stringValue(specConfig.get("url")),
                stringValue(specConfig.get("endpoint")),
                resolvedEnv.get(ENV_COLAB_API_URL),
                resolvedEnv.get(ENV_COLAB_JUPYTER_URL),
                resolvedEnv.get(ENV_JUPYTER_URL)
        );
        String apiToken = firstNonBlank(
                stringValue(specConfig.get("apiToken")),
                stringValue(specConfig.get("token")),
                stringValue(specConfig.get("jupyterToken")),
                resolvedEnv.get(ENV_COLAB_API_TOKEN),
                resolvedEnv.get(ENV_JUPYTER_TOKEN)
        );
        String kernelName = firstNonBlank(
                stringValue(specConfig.get("kernelName")),
                stringValue(specConfig.get("kernel")),
                resolvedEnv.get(ENV_COLAB_KERNEL_NAME),
                DEFAULT_KERNEL_NAME
        );
        String sessionName = firstNonBlank(
                stringValue(specConfig.get("sessionName")),
                spec == null ? null : spec.getWorkspaceId(),
                DEFAULT_SESSION_NAME
        );
        String notebookPath = firstNonBlank(
                stringValue(specConfig.get("notebookPath")),
                stringValue(specConfig.get("path")),
                DEFAULT_NOTEBOOK_PATH
        );

        Long connectTimeout = longValue(specConfig.get("connectTimeoutMillis"), specConfig.get("connectTimeoutMs"));
        Long readTimeout = longValue(specConfig.get("readTimeoutMillis"), specConfig.get("readTimeoutMs"));
        Long executionTimeout = longValue(specConfig.get("executionTimeoutMillis"), specConfig.get("executionTimeoutMs"));

        Builder builder = builder()
                .baseUrl(baseUrl)
                .apiToken(apiToken)
                .kernelName(kernelName)
                .sessionName(sessionName)
                .notebookPath(notebookPath);
        if (connectTimeout != null) {
            builder.connectTimeoutMillis(connectTimeout.longValue());
        }
        if (readTimeout != null) {
            builder.readTimeoutMillis(readTimeout.longValue());
        }
        if (executionTimeout != null) {
            builder.executionTimeoutMillis(executionTimeout.longValue());
        }
        Object rawHeaders = specConfig.get("headers");
        if (rawHeaders instanceof Map) {
            Map<String, String> headerMap = new LinkedHashMap<String, String>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawHeaders).entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String key = String.valueOf(entry.getKey()).trim();
                    String val = String.valueOf(entry.getValue()).trim();
                    if (!key.isEmpty() && !val.isEmpty()) {
                        headerMap.put(key, val);
                    }
                }
            }
            builder.headers(headerMap);
        }
        return builder.build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getKernelName() {
        return kernelName;
    }

    public String getSessionName() {
        return sessionName;
    }

    public String getNotebookPath() {
        return notebookPath;
    }

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public long getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public long getExecutionTimeoutMillis() {
        return executionTimeoutMillis;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String resolveAuthorizationHeader() {
        if (apiToken == null) {
            return null;
        }
        String lower = apiToken.toLowerCase(Locale.ROOT);
        if (lower.startsWith("token ") || lower.startsWith("bearer ")) {
            return apiToken;
        }
        return "token " + apiToken;
    }

    private static long positiveOrDefault(long candidate, long fallback) {
        return candidate > 0L ? candidate : fallback;
    }

    private static Long longValue(Object primary, Object secondary) {
        Long first = parseLong(primary);
        return first != null ? first : parseLong(secondary);
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(text));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimTrailingSlashes(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {

        private String baseUrl;
        private String apiToken;
        private String kernelName = DEFAULT_KERNEL_NAME;
        private String sessionName = DEFAULT_SESSION_NAME;
        private String notebookPath = DEFAULT_NOTEBOOK_PATH;
        private long connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MS;
        private long readTimeoutMillis = DEFAULT_READ_TIMEOUT_MS;
        private long executionTimeoutMillis = DEFAULT_EXECUTION_TIMEOUT_MS;
        private Map<String, String> headers;

        private Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        public Builder kernelName(String kernelName) {
            this.kernelName = kernelName;
            return this;
        }

        public Builder sessionName(String sessionName) {
            this.sessionName = sessionName;
            return this;
        }

        public Builder notebookPath(String notebookPath) {
            this.notebookPath = notebookPath;
            return this;
        }

        public Builder connectTimeoutMillis(long connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            return this;
        }

        public Builder readTimeoutMillis(long readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
            return this;
        }

        public Builder executionTimeoutMillis(long executionTimeoutMillis) {
            this.executionTimeoutMillis = executionTimeoutMillis;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers == null ? null : new LinkedHashMap<String, String>(headers);
            return this;
        }

        public ColabSandboxConfig build() {
            return new ColabSandboxConfig(this);
        }
    }
}
