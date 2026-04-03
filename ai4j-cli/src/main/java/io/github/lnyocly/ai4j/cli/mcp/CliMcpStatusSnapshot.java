package io.github.lnyocly.ai4j.cli.mcp;

public final class CliMcpStatusSnapshot {

    private final String serverName;
    private final String transportType;
    private final String state;
    private final int toolCount;
    private final String errorSummary;
    private final boolean workspaceEnabled;
    private final boolean sessionPaused;

    public CliMcpStatusSnapshot(String serverName,
                                String transportType,
                                String state,
                                int toolCount,
                                String errorSummary,
                                boolean workspaceEnabled,
                                boolean sessionPaused) {
        this.serverName = serverName;
        this.transportType = transportType;
        this.state = state;
        this.toolCount = toolCount;
        this.errorSummary = errorSummary;
        this.workspaceEnabled = workspaceEnabled;
        this.sessionPaused = sessionPaused;
    }

    public String getServerName() {
        return serverName;
    }

    public String getTransportType() {
        return transportType;
    }

    public String getState() {
        return state;
    }

    public int getToolCount() {
        return toolCount;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public boolean isWorkspaceEnabled() {
        return workspaceEnabled;
    }

    public boolean isSessionPaused() {
        return sessionPaused;
    }
}

