package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Non-sensitive summary of the sandbox currently bound to one AgentSession.
 *
 * <p>This class intentionally stores only provider-neutral metadata that is safe
 * to persist in a session snapshot. It does not retain {@link SandboxSpec#getConfig()}
 * because provider configs may contain credentials, tokens, or tenant-specific
 * connection details.</p>
 */
public final class AgentSessionSandboxBinding {

    private static final String[] SENSITIVE_LABEL_PARTS = new String[]{
            "secret", "token", "key", "password", "passwd", "credential", "cookie", "authorization"
    };

    private final String providerId;
    private final String sandboxSessionId;
    private final SandboxStatus status;
    private final String profile;
    private final String image;
    private final String workspaceId;
    private final Map<String, String> labels;
    private final long boundAtEpochMs;
    private final long updatedAtEpochMs;

    private AgentSessionSandboxBinding(Builder builder) {
        long now = System.currentTimeMillis();
        this.providerId = trimToNull(builder.providerId);
        this.sandboxSessionId = trimToNull(builder.sandboxSessionId);
        this.status = builder.status;
        this.profile = trimToNull(builder.profile);
        this.image = trimToNull(builder.image);
        this.workspaceId = trimToNull(builder.workspaceId);
        this.labels = sanitizeLabels(builder.labels);
        this.boundAtEpochMs = builder.boundAtEpochMs == null || builder.boundAtEpochMs.longValue() <= 0
                ? now
                : builder.boundAtEpochMs.longValue();
        this.updatedAtEpochMs = builder.updatedAtEpochMs == null || builder.updatedAtEpochMs.longValue() <= 0
                ? this.boundAtEpochMs
                : builder.updatedAtEpochMs.longValue();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentSessionSandboxBinding from(SandboxSession session) {
        if (session == null) {
            return null;
        }
        SandboxSpec spec = session.getSpec();
        Builder builder = builder()
                .providerId(session.getProviderId())
                .sandboxSessionId(session.getSessionId())
                .status(session.getStatus());
        if (spec != null) {
            builder.profile(spec.getProfile())
                    .image(spec.getImage())
                    .workspaceId(spec.getWorkspaceId())
                    .labels(spec.getLabels());
        }
        return builder.build();
    }

    public AgentSessionSandboxBinding withStatus(SandboxStatus status) {
        return builder()
                .providerId(providerId)
                .sandboxSessionId(sandboxSessionId)
                .status(status)
                .profile(profile)
                .image(image)
                .workspaceId(workspaceId)
                .labels(labels)
                .boundAtEpochMs(boundAtEpochMs)
                .updatedAtEpochMs(System.currentTimeMillis())
                .build();
    }

    public AgentSessionSandboxBinding copy() {
        return builder()
                .providerId(providerId)
                .sandboxSessionId(sandboxSessionId)
                .status(status)
                .profile(profile)
                .image(image)
                .workspaceId(workspaceId)
                .labels(labels)
                .boundAtEpochMs(boundAtEpochMs)
                .updatedAtEpochMs(updatedAtEpochMs)
                .build();
    }

    public String getProviderId() {
        return providerId;
    }

    public String getSandboxSessionId() {
        return sandboxSessionId;
    }

    public SandboxStatus getStatus() {
        return status;
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
        return new LinkedHashMap<String, String>(labels);
    }

    public long getBoundAtEpochMs() {
        return boundAtEpochMs;
    }

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    private static Map<String, String> sanitizeLabels(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<String, String>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            if (key.isEmpty() || isSensitiveKey(key)) {
                continue;
            }
            copy.put(key, entry.getValue());
        }
        return copy;
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ENGLISH);
        for (String part : SENSITIVE_LABEL_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {
        private String providerId;
        private String sandboxSessionId;
        private SandboxStatus status;
        private String profile;
        private String image;
        private String workspaceId;
        private Map<String, String> labels;
        private Long boundAtEpochMs;
        private Long updatedAtEpochMs;

        private Builder() {
        }

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder sandboxSessionId(String sandboxSessionId) {
            this.sandboxSessionId = sandboxSessionId;
            return this;
        }

        public Builder status(SandboxStatus status) {
            this.status = status;
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

        public Builder boundAtEpochMs(long boundAtEpochMs) {
            this.boundAtEpochMs = Long.valueOf(boundAtEpochMs);
            return this;
        }

        public Builder updatedAtEpochMs(long updatedAtEpochMs) {
            this.updatedAtEpochMs = Long.valueOf(updatedAtEpochMs);
            return this;
        }

        public AgentSessionSandboxBinding build() {
            return new AgentSessionSandboxBinding(this);
        }
    }
}
