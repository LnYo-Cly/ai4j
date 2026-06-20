package io.github.lnyocly.ai4j.agent.runner;

import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative request for a remote Agent Runner session.
 */
public final class AgentRunnerSpec {

    private final String providerId;
    private final String profile;
    private final String runnerImage;
    private final String workspaceId;
    private final AgentBlueprint blueprint;
    private final SandboxSpec sandboxSpec;
    private final Map<String, String> labels;
    private final Map<String, Object> config;

    private AgentRunnerSpec(Builder builder) {
        this.providerId = AgentRunnerCopies.trimToNull(builder.providerId);
        this.profile = AgentRunnerCopies.trimToNull(builder.profile);
        this.runnerImage = AgentRunnerCopies.trimToNull(builder.runnerImage);
        this.workspaceId = AgentRunnerCopies.trimToNull(builder.workspaceId);
        this.blueprint = builder.blueprint;
        this.sandboxSpec = builder.sandboxSpec == null ? null : builder.sandboxSpec.copy();
        this.labels = AgentRunnerCopies.copyStringMap(builder.labels);
        this.config = AgentRunnerCopies.copyObjectMap(builder.config);
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

    public String getRunnerImage() {
        return runnerImage;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public AgentBlueprint getBlueprint() {
        return blueprint;
    }

    public SandboxSpec getSandboxSpec() {
        return sandboxSpec == null ? null : sandboxSpec.copy();
    }

    public Map<String, String> getLabels() {
        return AgentRunnerCopies.copyStringMap(labels);
    }

    public Map<String, Object> getConfig() {
        return AgentRunnerCopies.copyObjectMap(config);
    }

    public AgentRunnerSpec copy() {
        return builder()
                .providerId(providerId)
                .profile(profile)
                .runnerImage(runnerImage)
                .workspaceId(workspaceId)
                .blueprint(blueprint)
                .sandboxSpec(sandboxSpec)
                .labels(labels)
                .config(config)
                .build();
    }

    public static final class Builder {
        private String providerId;
        private String profile;
        private String runnerImage;
        private String workspaceId;
        private AgentBlueprint blueprint;
        private SandboxSpec sandboxSpec;
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

        public Builder runnerImage(String runnerImage) {
            this.runnerImage = runnerImage;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder blueprint(AgentBlueprint blueprint) {
            this.blueprint = blueprint;
            return this;
        }

        public Builder sandboxSpec(SandboxSpec sandboxSpec) {
            this.sandboxSpec = sandboxSpec;
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

        public AgentRunnerSpec build() {
            return new AgentRunnerSpec(this);
        }
    }
}
