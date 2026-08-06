package io.github.lnyocly.ai4j.mcp.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务端会话状态
 */
public class McpServerSessionState {

    private final String sessionId;
    private volatile boolean initialized;
    private volatile String protocolVersion;
    private final Map<String, Object> capabilities = new ConcurrentHashMap<String, Object>();

    public McpServerSessionState(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }
}
