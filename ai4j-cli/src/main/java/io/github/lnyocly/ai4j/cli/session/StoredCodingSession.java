package io.github.lnyocly.ai4j.cli.session;

import io.github.lnyocly.ai4j.coding.CodingSessionState;

public class StoredCodingSession {

    private String sessionId;
    private String rootSessionId;
    private String parentSessionId;
    private String provider;
    private String protocol;
    private String model;
    private String workspace;
    private String workspaceDescription;
    private String systemPrompt;
    private String instructions;
    private String summary;
    private int memoryItemCount;
    private int processCount;
    private int activeProcessCount;
    private int restoredProcessCount;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;
    private String storePath;
    private CodingSessionState state;

    public StoredCodingSession() {
    }

    public StoredCodingSession(String sessionId,
                               String rootSessionId,
                               String parentSessionId,
                               String provider,
                               String protocol,
                               String model,
                               String workspace,
                               String workspaceDescription,
                               String systemPrompt,
                               String instructions,
                               String summary,
                               int memoryItemCount,
                               int processCount,
                               int activeProcessCount,
                               int restoredProcessCount,
                               long createdAtEpochMs,
                               long updatedAtEpochMs,
                               String storePath,
                               CodingSessionState state) {
        this.sessionId = sessionId;
        this.rootSessionId = rootSessionId;
        this.parentSessionId = parentSessionId;
        this.provider = provider;
        this.protocol = protocol;
        this.model = model;
        this.workspace = workspace;
        this.workspaceDescription = workspaceDescription;
        this.systemPrompt = systemPrompt;
        this.instructions = instructions;
        this.summary = summary;
        this.memoryItemCount = memoryItemCount;
        this.processCount = processCount;
        this.activeProcessCount = activeProcessCount;
        this.restoredProcessCount = restoredProcessCount;
        this.createdAtEpochMs = createdAtEpochMs;
        this.updatedAtEpochMs = updatedAtEpochMs;
        this.storePath = storePath;
        this.state = state;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .sessionId(sessionId)
                .rootSessionId(rootSessionId)
                .parentSessionId(parentSessionId)
                .provider(provider)
                .protocol(protocol)
                .model(model)
                .workspace(workspace)
                .workspaceDescription(workspaceDescription)
                .systemPrompt(systemPrompt)
                .instructions(instructions)
                .summary(summary)
                .memoryItemCount(memoryItemCount)
                .processCount(processCount)
                .activeProcessCount(activeProcessCount)
                .restoredProcessCount(restoredProcessCount)
                .createdAtEpochMs(createdAtEpochMs)
                .updatedAtEpochMs(updatedAtEpochMs)
                .storePath(storePath)
                .state(state);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRootSessionId() {
        return rootSessionId;
    }

    public void setRootSessionId(String rootSessionId) {
        this.rootSessionId = rootSessionId;
    }

    public String getParentSessionId() {
        return parentSessionId;
    }

    public void setParentSessionId(String parentSessionId) {
        this.parentSessionId = parentSessionId;
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

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public String getWorkspaceDescription() {
        return workspaceDescription;
    }

    public void setWorkspaceDescription(String workspaceDescription) {
        this.workspaceDescription = workspaceDescription;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getMemoryItemCount() {
        return memoryItemCount;
    }

    public void setMemoryItemCount(int memoryItemCount) {
        this.memoryItemCount = memoryItemCount;
    }

    public int getProcessCount() {
        return processCount;
    }

    public void setProcessCount(int processCount) {
        this.processCount = processCount;
    }

    public int getActiveProcessCount() {
        return activeProcessCount;
    }

    public void setActiveProcessCount(int activeProcessCount) {
        this.activeProcessCount = activeProcessCount;
    }

    public int getRestoredProcessCount() {
        return restoredProcessCount;
    }

    public void setRestoredProcessCount(int restoredProcessCount) {
        this.restoredProcessCount = restoredProcessCount;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public void setCreatedAtEpochMs(long createdAtEpochMs) {
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setUpdatedAtEpochMs(long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    public String getStorePath() {
        return storePath;
    }

    public void setStorePath(String storePath) {
        this.storePath = storePath;
    }

    public CodingSessionState getState() {
        return state;
    }

    public void setState(CodingSessionState state) {
        this.state = state;
    }

    public static final class Builder {

        private String sessionId;
        private String rootSessionId;
        private String parentSessionId;
        private String provider;
        private String protocol;
        private String model;
        private String workspace;
        private String workspaceDescription;
        private String systemPrompt;
        private String instructions;
        private String summary;
        private int memoryItemCount;
        private int processCount;
        private int activeProcessCount;
        private int restoredProcessCount;
        private long createdAtEpochMs;
        private long updatedAtEpochMs;
        private String storePath;
        private CodingSessionState state;

        private Builder() {
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder rootSessionId(String rootSessionId) {
            this.rootSessionId = rootSessionId;
            return this;
        }

        public Builder parentSessionId(String parentSessionId) {
            this.parentSessionId = parentSessionId;
            return this;
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

        public Builder workspace(String workspace) {
            this.workspace = workspace;
            return this;
        }

        public Builder workspaceDescription(String workspaceDescription) {
            this.workspaceDescription = workspaceDescription;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder memoryItemCount(int memoryItemCount) {
            this.memoryItemCount = memoryItemCount;
            return this;
        }

        public Builder processCount(int processCount) {
            this.processCount = processCount;
            return this;
        }

        public Builder activeProcessCount(int activeProcessCount) {
            this.activeProcessCount = activeProcessCount;
            return this;
        }

        public Builder restoredProcessCount(int restoredProcessCount) {
            this.restoredProcessCount = restoredProcessCount;
            return this;
        }

        public Builder createdAtEpochMs(long createdAtEpochMs) {
            this.createdAtEpochMs = createdAtEpochMs;
            return this;
        }

        public Builder updatedAtEpochMs(long updatedAtEpochMs) {
            this.updatedAtEpochMs = updatedAtEpochMs;
            return this;
        }

        public Builder storePath(String storePath) {
            this.storePath = storePath;
            return this;
        }

        public Builder state(CodingSessionState state) {
            this.state = state;
            return this;
        }

        public StoredCodingSession build() {
            return new StoredCodingSession(
                    sessionId,
                    rootSessionId,
                    parentSessionId,
                    provider,
                    protocol,
                    model,
                    workspace,
                    workspaceDescription,
                    systemPrompt,
                    instructions,
                    summary,
                    memoryItemCount,
                    processCount,
                    activeProcessCount,
                    restoredProcessCount,
                    createdAtEpochMs,
                    updatedAtEpochMs,
                    storePath,
                    state
            );
        }
    }
}
