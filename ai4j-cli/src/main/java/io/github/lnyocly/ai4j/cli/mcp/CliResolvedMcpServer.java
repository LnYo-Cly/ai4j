package io.github.lnyocly.ai4j.cli.mcp;

public final class CliResolvedMcpServer {

    private final String name;
    private final String transportType;
    private final boolean workspaceEnabled;
    private final boolean sessionPaused;
    private final boolean active;
    private final String validationError;
    private final CliMcpServerDefinition definition;

    public CliResolvedMcpServer(String name,
                                String transportType,
                                boolean workspaceEnabled,
                                boolean sessionPaused,
                                boolean active,
                                String validationError,
                                CliMcpServerDefinition definition) {
        this.name = name;
        this.transportType = transportType;
        this.workspaceEnabled = workspaceEnabled;
        this.sessionPaused = sessionPaused;
        this.active = active;
        this.validationError = validationError;
        this.definition = definition;
    }

    public String getName() {
        return name;
    }

    public String getTransportType() {
        return transportType;
    }

    public boolean isWorkspaceEnabled() {
        return workspaceEnabled;
    }

    public boolean isSessionPaused() {
        return sessionPaused;
    }

    public boolean isActive() {
        return active;
    }

    public String getValidationError() {
        return validationError;
    }

    public CliMcpServerDefinition getDefinition() {
        return definition;
    }

    public boolean isValid() {
        return validationError == null || validationError.isEmpty();
    }
}

