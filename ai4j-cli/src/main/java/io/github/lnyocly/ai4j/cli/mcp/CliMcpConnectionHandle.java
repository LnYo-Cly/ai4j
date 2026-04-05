package io.github.lnyocly.ai4j.cli.mcp;

import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CliMcpConnectionHandle {

    private final CliResolvedMcpServer server;
    private CliMcpRuntimeManager.ClientSession clientSession;
    private String state;
    private String errorSummary;
    private List<Tool> tools = Collections.emptyList();

    CliMcpConnectionHandle(CliResolvedMcpServer server) {
        this.server = server;
    }

    String getServerName() {
        return server == null ? null : server.getName();
    }

    String getTransportType() {
        return server == null ? null : server.getTransportType();
    }

    CliResolvedMcpServer getServer() {
        return server;
    }

    CliMcpRuntimeManager.ClientSession getClientSession() {
        return clientSession;
    }

    void setClientSession(CliMcpRuntimeManager.ClientSession clientSession) {
        this.clientSession = clientSession;
    }

    String getState() {
        return state;
    }

    void setState(String state) {
        this.state = state;
    }

    String getErrorSummary() {
        return errorSummary;
    }

    void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    List<Tool> getTools() {
        return tools;
    }

    void setTools(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            this.tools = Collections.emptyList();
            return;
        }
        this.tools = Collections.unmodifiableList(new ArrayList<Tool>(tools));
    }

    CliMcpStatusSnapshot toStatusSnapshot() {
        return new CliMcpStatusSnapshot(
                getServerName(),
                getTransportType(),
                state,
                tools == null ? 0 : tools.size(),
                errorSummary,
                server != null && server.isWorkspaceEnabled(),
                server != null && server.isSessionPaused()
        );
    }

    void closeQuietly() {
        CliMcpRuntimeManager.ClientSession session = clientSession;
        clientSession = null;
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }
}

