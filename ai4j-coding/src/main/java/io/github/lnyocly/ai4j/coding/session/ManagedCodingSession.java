package io.github.lnyocly.ai4j.coding.session;

import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.CodingSessionSnapshot;

public class ManagedCodingSession implements AutoCloseable {

    private final CodingSession session;
    private final String provider;
    private final String protocol;
    private final String model;
    private final String workspace;
    private final String workspaceDescription;
    private final String systemPrompt;
    private final String instructions;
    private final String rootSessionId;
    private final String parentSessionId;
    private final long createdAtEpochMs;
    private long updatedAtEpochMs;
    private final String runId;

    public ManagedCodingSession(CodingSession session,
                                String provider,
                                String protocol,
                                String model,
                                String workspace,
                                String workspaceDescription,
                                String systemPrompt,
                                String instructions,
                                String rootSessionId,
                                String parentSessionId,
                                long createdAtEpochMs,
                                long updatedAtEpochMs) {
        this(session, provider, protocol, model, workspace, workspaceDescription, systemPrompt, instructions,
                rootSessionId, parentSessionId, createdAtEpochMs, updatedAtEpochMs, null);
    }

    public ManagedCodingSession(CodingSession session,
                                String provider,
                                String protocol,
                                String model,
                                String workspace,
                                String workspaceDescription,
                                String systemPrompt,
                                String instructions,
                                String rootSessionId,
                                String parentSessionId,
                                long createdAtEpochMs,
                                long updatedAtEpochMs,
                                String runId) {
        this.session = session;
        this.provider = provider;
        this.protocol = protocol;
        this.model = model;
        this.workspace = workspace;
        this.workspaceDescription = workspaceDescription;
        this.systemPrompt = systemPrompt;
        this.instructions = instructions;
        this.rootSessionId = rootSessionId;
        this.parentSessionId = parentSessionId;
        this.createdAtEpochMs = createdAtEpochMs;
        this.updatedAtEpochMs = updatedAtEpochMs;
        this.runId = runId;
    }

    public CodingSession getSession() {
        return session;
    }

    public String getSessionId() {
        return session == null ? null : session.getSessionId();
    }

    public String getProvider() {
        return provider;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getModel() {
        return model;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getWorkspaceDescription() {
        return workspaceDescription;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getRootSessionId() {
        return rootSessionId;
    }

    public String getParentSessionId() {
        return parentSessionId;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public String getRunId() {
        if (runId != null && !runId.trim().isEmpty()) {
            return runId;
        }
        return session == null ? null : session.getRunId();
    }

    public void touch(long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    public CodingSessionDescriptor toDescriptor() {
        CodingSessionSnapshot snapshot = session == null ? null : session.snapshot();
        return CodingSessionDescriptor.builder()
                .sessionId(getSessionId())
                .rootSessionId(rootSessionId)
                .parentSessionId(parentSessionId)
                .provider(provider)
                .protocol(protocol)
                .model(model)
                .workspace(workspace)
                .summary(snapshot == null ? null : snapshot.getSummary())
                .memoryItemCount(snapshot == null ? 0 : snapshot.getMemoryItemCount())
                .processCount(snapshot == null ? 0 : snapshot.getProcessCount())
                .activeProcessCount(snapshot == null ? 0 : snapshot.getActiveProcessCount())
                .restoredProcessCount(snapshot == null ? 0 : snapshot.getRestoredProcessCount())
                .createdAtEpochMs(createdAtEpochMs)
                .updatedAtEpochMs(updatedAtEpochMs)
                .build();
    }

    @Override
    public void close() {
        if (session != null) {
            session.close();
        }
    }
}
