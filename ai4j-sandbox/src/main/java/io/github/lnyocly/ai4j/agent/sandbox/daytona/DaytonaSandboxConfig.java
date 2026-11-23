package io.github.lnyocly.ai4j.agent.sandbox.daytona;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Connection and creation settings for the Daytona sandbox provider.
 */
public final class DaytonaSandboxConfig {

    public static final String DEFAULT_PROVIDER_ID = "daytona";
    public static final String DEFAULT_API_URL = "https://app.daytona.io/api";

    private final String providerId;
    private final String apiKey;
    private final String apiUrl;
    private final String toolboxProxyUrl;
    private final String organizationId;
    private final String target;
    private final String snapshot;
    private final String user;
    private final String sandboxId;
    private final String sandboxName;
    private final boolean createIfMissing;
    private final boolean deleteOnClose;
    private final long connectTimeoutMillis;
    private final long readTimeoutMillis;
    private final long startTimeoutMillis;
    private final long pollIntervalMillis;
    private final Map<String, String> labels;
    private final Map<String, String> environment;
    private final Map<String, Object> createOptions;
    private final SandboxSpec spec;

    private DaytonaSandboxConfig(Builder builder) {
        this.providerId = firstNonBlank(builder.providerId, DEFAULT_PROVIDER_ID);
        this.apiKey = trimToNull(builder.apiKey);
        this.apiUrl = trimTrailingSlash(firstNonBlank(builder.apiUrl, DEFAULT_API_URL));
        this.toolboxProxyUrl = trimTrailingSlash(builder.toolboxProxyUrl);
        this.organizationId = trimToNull(builder.organizationId);
        this.target = trimToNull(builder.target);
        this.snapshot = trimToNull(builder.snapshot);
        this.user = trimToNull(builder.user);
        this.sandboxId = trimToNull(builder.sandboxId);
        this.sandboxName = trimToNull(builder.sandboxName);
        this.createIfMissing = builder.createIfMissing == null || builder.createIfMissing.booleanValue();
        this.deleteOnClose = builder.deleteOnClose != null && builder.deleteOnClose.booleanValue();
        this.connectTimeoutMillis = positiveOrDefault(builder.connectTimeoutMillis, 10000L);
        this.readTimeoutMillis = positiveOrDefault(builder.readTimeoutMillis, 60000L);
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

    public static DaytonaSandboxConfig fromEnvironment(SandboxSpec spec) {
        return fromEnvironment(spec, System.getenv());
    }

    public static DaytonaSandboxConfig fromEnvironment(SandboxSpec spec, Map<String, String> env) {
        Builder builder = builder()
                .providerId(DEFAULT_PROVIDER_ID)
                .apiKey(envValue(env, "DAYTONA_API_KEY"))
                .apiUrl(firstNonBlank(envValue(env, "DAYTONA_API_URL"), DEFAULT_API_URL))
                .toolboxProxyUrl(envValue(env, "DAYTONA_TOOLBOX_PROXY_URL"))
                .organizationId(envValue(env, "DAYTONA_ORGANIZATION_ID"))
                .target(envValue(env, "DAYTONA_TARGET"));
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
            builder.snapshot(spec.getImage());
        }
        if (spec.getWorkspaceId() != null) {
            builder.sandboxName(spec.getWorkspaceId());
        }
        builder.labels(spec.getLabels());

        Map<String, Object> config = spec.getConfig();
        builder.apiKey(asString(config.get("apiKey")));
        builder.apiUrl(asString(config.get("apiUrl")));
        builder.toolboxProxyUrl(asString(config.get("toolboxProxyUrl")));
        builder.organizationId(asString(config.get("organizationId")));
        builder.target(asString(config.get("target")));
        builder.snapshot(firstNonBlank(asString(config.get("snapshot")), asString(config.get("image"))));
        builder.user(asString(config.get("user")));
        builder.sandboxId(asString(config.get("sandboxId")));
        builder.sandboxName(firstNonBlank(asString(config.get("sandboxName")), asString(config.get("name"))));
        builder.createIfMissing(asBoolean(config.get("createIfMissing")));
        builder.deleteOnClose(asBoolean(config.get("deleteOnClose")));
        builder.connectTimeoutMillis(asLong(config.get("connectTimeoutMillis")));
        builder.readTimeoutMillis(asLong(config.get("readTimeoutMillis")));
        builder.startTimeoutMillis(asLong(config.get("startTimeoutMillis")));
        builder.pollIntervalMillis(asLong(config.get("pollIntervalMillis")));
        builder.environment(asStringMap(config.get("env")));

        Map<String, Object> createOptions = new LinkedHashMap<String, Object>();
        copyKnownCreateOption(config, createOptions, "public");
        copyKnownCreateOption(config, createOptions, "networkBlockAll");
        copyKnownCreateOption(config, createOptions, "networkAllowList");
        copyKnownCreateOption(config, createOptions, "cpu");
        copyKnownCreateOption(config, createOptions, "gpu");
        copyKnownCreateOption(config, createOptions, "gpuType");
        copyKnownCreateOption(config, createOptions, "memory");
        copyKnownCreateOption(config, createOptions, "disk");
        copyKnownCreateOption(config, createOptions, "autoStopInterval");
        copyKnownCreateOption(config, createOptions, "autoArchiveInterval");
        copyKnownCreateOption(config, createOptions, "autoDeleteInterval");
        copyKnownCreateOption(config, createOptions, "linkedSandbox");
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

    public String getApiUrl() {
        return apiUrl;
    }

    public String getToolboxProxyUrl() {
        return toolboxProxyUrl;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getTarget() {
        return target;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public String getUser() {
        return user;
    }

    public String getSandboxId() {
        return sandboxId;
    }

    public String getSandboxName() {
        return sandboxName;
    }

    public String getAttachNameOrId() {
        return firstNonBlank(sandboxId, sandboxName);
    }

    public boolean isCreateIfMissing() {
        return createIfMissing;
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

    DaytonaSandboxConfig withSpecOverrides(SandboxSpec spec) {
        if (spec == null) {
            return this;
        }
        Builder builder = builder()
                .providerId(providerId)
                .apiKey(apiKey)
                .apiUrl(apiUrl)
                .toolboxProxyUrl(toolboxProxyUrl)
                .organizationId(organizationId)
                .target(target)
                .snapshot(snapshot)
                .user(user)
                .sandboxId(sandboxId)
                .sandboxName(sandboxName)
                .createIfMissing(Boolean.valueOf(createIfMissing))
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
        builder.toolboxProxyUrl(firstNonBlank(toolboxProxyUrl, builder.toolboxProxyUrl));
        builder.organizationId(firstNonBlank(organizationId, builder.organizationId));
        return builder.build();
    }

    DaytonaSandboxConfig withDeleteOnClose(boolean deleteOnClose) {
        return builder()
                .providerId(providerId)
                .apiKey(apiKey)
                .apiUrl(apiUrl)
                .toolboxProxyUrl(toolboxProxyUrl)
                .organizationId(organizationId)
                .target(target)
                .snapshot(snapshot)
                .user(user)
                .sandboxId(sandboxId)
                .sandboxName(sandboxName)
                .createIfMissing(createIfMissing)
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
        private String apiUrl;
        private String toolboxProxyUrl;
        private String organizationId;
        private String target;
        private String snapshot;
        private String user;
        private String sandboxId;
        private String sandboxName;
        private Boolean createIfMissing;
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
            this.providerId = providerId;
            return this;
        }

        public Builder apiKey(String apiKey) {
            if (apiKey != null) {
                this.apiKey = apiKey;
            }
            return this;
        }

        public Builder apiUrl(String apiUrl) {
            if (apiUrl != null) {
                this.apiUrl = apiUrl;
            }
            return this;
        }

        public Builder toolboxProxyUrl(String toolboxProxyUrl) {
            if (toolboxProxyUrl != null) {
                this.toolboxProxyUrl = toolboxProxyUrl;
            }
            return this;
        }

        public Builder organizationId(String organizationId) {
            if (organizationId != null) {
                this.organizationId = organizationId;
            }
            return this;
        }

        public Builder target(String target) {
            if (target != null) {
                this.target = target;
            }
            return this;
        }

        public Builder snapshot(String snapshot) {
            if (snapshot != null) {
                this.snapshot = snapshot;
            }
            return this;
        }

        public Builder user(String user) {
            if (user != null) {
                this.user = user;
            }
            return this;
        }

        public Builder sandboxId(String sandboxId) {
            if (sandboxId != null) {
                this.sandboxId = sandboxId;
            }
            return this;
        }

        public Builder sandboxName(String sandboxName) {
            if (sandboxName != null) {
                this.sandboxName = sandboxName;
            }
            return this;
        }

        public Builder createIfMissing(Boolean createIfMissing) {
            if (createIfMissing != null) {
                this.createIfMissing = createIfMissing;
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

        public DaytonaSandboxConfig build() {
            return new DaytonaSandboxConfig(this);
        }
    }
}
