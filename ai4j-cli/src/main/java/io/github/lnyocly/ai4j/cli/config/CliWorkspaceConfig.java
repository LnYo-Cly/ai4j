package io.github.lnyocly.ai4j.cli.config;

import java.util.ArrayList;
import java.util.List;

public class CliWorkspaceConfig {

    private String activeProfile;
    private String modelOverride;
    private Boolean experimentalSubagentsEnabled;
    private Boolean experimentalAgentTeamsEnabled;
    private List<String> enabledMcpServers;
    private List<String> skillDirectories;
    private List<String> agentDirectories;

    public CliWorkspaceConfig() {
    }

    public CliWorkspaceConfig(String activeProfile,
                              String modelOverride,
                              Boolean experimentalSubagentsEnabled,
                              Boolean experimentalAgentTeamsEnabled,
                              List<String> enabledMcpServers,
                              List<String> skillDirectories,
                              List<String> agentDirectories) {
        this.activeProfile = activeProfile;
        this.modelOverride = modelOverride;
        this.experimentalSubagentsEnabled = experimentalSubagentsEnabled;
        this.experimentalAgentTeamsEnabled = experimentalAgentTeamsEnabled;
        this.enabledMcpServers = copy(enabledMcpServers);
        this.skillDirectories = copy(skillDirectories);
        this.agentDirectories = copy(agentDirectories);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .activeProfile(activeProfile)
                .modelOverride(modelOverride)
                .experimentalSubagentsEnabled(experimentalSubagentsEnabled)
                .experimentalAgentTeamsEnabled(experimentalAgentTeamsEnabled)
                .enabledMcpServers(enabledMcpServers)
                .skillDirectories(skillDirectories)
                .agentDirectories(agentDirectories);
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public void setActiveProfile(String activeProfile) {
        this.activeProfile = activeProfile;
    }

    public String getModelOverride() {
        return modelOverride;
    }

    public void setModelOverride(String modelOverride) {
        this.modelOverride = modelOverride;
    }

    public Boolean getExperimentalSubagentsEnabled() {
        return experimentalSubagentsEnabled;
    }

    public void setExperimentalSubagentsEnabled(Boolean experimentalSubagentsEnabled) {
        this.experimentalSubagentsEnabled = experimentalSubagentsEnabled;
    }

    public Boolean getExperimentalAgentTeamsEnabled() {
        return experimentalAgentTeamsEnabled;
    }

    public void setExperimentalAgentTeamsEnabled(Boolean experimentalAgentTeamsEnabled) {
        this.experimentalAgentTeamsEnabled = experimentalAgentTeamsEnabled;
    }

    public List<String> getEnabledMcpServers() {
        return copy(enabledMcpServers);
    }

    public void setEnabledMcpServers(List<String> enabledMcpServers) {
        this.enabledMcpServers = copy(enabledMcpServers);
    }

    public List<String> getSkillDirectories() {
        return copy(skillDirectories);
    }

    public void setSkillDirectories(List<String> skillDirectories) {
        this.skillDirectories = copy(skillDirectories);
    }

    public List<String> getAgentDirectories() {
        return copy(agentDirectories);
    }

    public void setAgentDirectories(List<String> agentDirectories) {
        this.agentDirectories = copy(agentDirectories);
    }

    private List<String> copy(List<String> values) {
        return values == null ? null : new ArrayList<String>(values);
    }

    public static final class Builder {

        private String activeProfile;
        private String modelOverride;
        private Boolean experimentalSubagentsEnabled;
        private Boolean experimentalAgentTeamsEnabled;
        private List<String> enabledMcpServers;
        private List<String> skillDirectories;
        private List<String> agentDirectories;

        private Builder() {
        }

        public Builder activeProfile(String activeProfile) {
            this.activeProfile = activeProfile;
            return this;
        }

        public Builder modelOverride(String modelOverride) {
            this.modelOverride = modelOverride;
            return this;
        }

        public Builder experimentalSubagentsEnabled(Boolean experimentalSubagentsEnabled) {
            this.experimentalSubagentsEnabled = experimentalSubagentsEnabled;
            return this;
        }

        public Builder experimentalAgentTeamsEnabled(Boolean experimentalAgentTeamsEnabled) {
            this.experimentalAgentTeamsEnabled = experimentalAgentTeamsEnabled;
            return this;
        }

        public Builder enabledMcpServers(List<String> enabledMcpServers) {
            this.enabledMcpServers = enabledMcpServers == null ? null : new ArrayList<String>(enabledMcpServers);
            return this;
        }

        public Builder skillDirectories(List<String> skillDirectories) {
            this.skillDirectories = skillDirectories == null ? null : new ArrayList<String>(skillDirectories);
            return this;
        }

        public Builder agentDirectories(List<String> agentDirectories) {
            this.agentDirectories = agentDirectories == null ? null : new ArrayList<String>(agentDirectories);
            return this;
        }

        public CliWorkspaceConfig build() {
            return new CliWorkspaceConfig(
                    activeProfile,
                    modelOverride,
                    experimentalSubagentsEnabled,
                    experimentalAgentTeamsEnabled,
                    enabledMcpServers,
                    skillDirectories,
                    agentDirectories
            );
        }
    }
}
