package io.github.lnyocly.ai4j.agent.sandbox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative request for a sandbox session.
 */
public final class SandboxSpec {

    private final String providerId;
    private final String profile;
    private final String image;
    private final String workspaceId;
    private final Map<String, String> labels;
    private final Map<String, Object> config;

    private SandboxSpec(Builder builder) {
        this.providerId = trimToNull(builder.providerId);
        this.profile = trimToNull(builder.profile);
        this.image = trimToNull(builder.image);
        this.workspaceId = trimToNull(builder.workspaceId);
        this.labels = copyStringMap(builder.labels);
        this.config = copyObjectMap(builder.config);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProviderId() {
        return providerId;
    }

    public String getProfile() {
        return profile;
    }

    public String getImage() {
        return image;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public Map<String, String> getLabels() {
        return copyStringMap(labels);
    }

    public Map<String, Object> getConfig() {
        return copyObjectMap(config);
    }

    public SandboxSpec copy() {
        return builder()
                .providerId(providerId)
                .profile(profile)
                .image(image)
                .workspaceId(workspaceId)
                .labels(labels)
                .config(config)
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static Map<String, String> copyStringMap(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<String, String>();
        if (source != null) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (entry != null && entry.getKey() != null) {
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
                if (entry != null && entry.getKey() != null) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return copy;
    }

    public static final class Builder {
        private String providerId;
        private String profile;
        private String image;
        private String workspaceId;
        private Map<String, String> labels;
        private Map<String, Object> config;

        private Builder() {
        }

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder profile(String profile) {
            this.profile = profile;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder label(String key, String value) {
            if (labels == null) {
                labels = new LinkedHashMap<String, String>();
            }
            if (key != null) {
                labels.put(key, value);
            }
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder config(String key, Object value) {
            if (config == null) {
                config = new LinkedHashMap<String, Object>();
            }
            if (key != null) {
                config.put(key, value);
            }
            return this;
        }

        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }

        public SandboxSpec build() {
            return new SandboxSpec(this);
        }
    }
}
