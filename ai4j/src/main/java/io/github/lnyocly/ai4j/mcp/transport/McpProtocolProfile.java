package io.github.lnyocly.ai4j.mcp.transport;

/**
 * Selects the Streamable HTTP wire contract explicitly. The 2026 revision is
 * stateless; the legacy profile preserves the pre-2026 session handshake.
 */
public enum McpProtocolProfile {

    MODERN_2026_07_28("2026-07-28", true),
    LEGACY_2025_03_26("2025-03-26", false);

    private final String protocolVersion;
    private final boolean modern;

    McpProtocolProfile(String protocolVersion, boolean modern) {
        this.protocolVersion = protocolVersion;
        this.modern = modern;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public boolean isModern() {
        return modern;
    }
}
