package io.github.lnyocly.ai4j.mcp.transport;

/**
 * Selects the Streamable HTTP wire contract. {@link #AUTO} is a connection
 * strategy: it probes only {@code server/discover}, then selects a concrete
 * profile before application traffic is sent.
 */
public enum McpProtocolProfile {

    AUTO(null, false, true),
    MODERN_2026_07_28("2026-07-28", true),
    LEGACY_2025_11_25("2025-11-25", false),
    LEGACY_2025_06_18("2025-06-18", false),
    LEGACY_2025_03_26("2025-03-26", false),
    LEGACY_2024_11_05("2024-11-05", false);

    private final String protocolVersion;
    private final boolean modern;
    private final boolean automatic;

    McpProtocolProfile(String protocolVersion, boolean modern) {
        this(protocolVersion, modern, false);
    }

    McpProtocolProfile(String protocolVersion, boolean modern, boolean automatic) {
        this.protocolVersion = protocolVersion;
        this.modern = modern;
        this.automatic = automatic;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public boolean isModern() {
        return modern;
    }

    /** Returns whether this value needs protocol negotiation before use. */
    public boolean isAutomatic() {
        return automatic;
    }

    /** True for a server profile that can accept modern requests. */
    public boolean supportsModern() {
        return modern || automatic;
    }

    /** True for a server profile that can accept initialization-era requests. */
    public boolean supportsLegacy() {
        return !modern;
    }

    /** Resolves a concrete profile advertised by an initialization-era server. */
    public static McpProtocolProfile fromProtocolVersion(String protocolVersion) {
        if (protocolVersion == null) {
            return null;
        }
        for (McpProtocolProfile profile : values()) {
            if (protocolVersion.equals(profile.protocolVersion)) {
                return profile;
            }
        }
        return null;
    }

    /** Whether this client profile can accept the server's legacy selection. */
    public boolean acceptsLegacyVersion(McpProtocolProfile selectedProfile) {
        if (selectedProfile == null || selectedProfile.isModern() || selectedProfile.isAutomatic()
                || isModern()) {
            return false;
        }
        if (isAutomatic() || this == LEGACY_2025_11_25) {
            return true;
        }
        if (this == LEGACY_2025_06_18) {
            return selectedProfile != LEGACY_2025_11_25;
        }
        if (this == LEGACY_2025_03_26) {
            return selectedProfile == LEGACY_2025_03_26
                    || selectedProfile == LEGACY_2024_11_05;
        }
        return this == selectedProfile;
    }

    /** 2025-06-18 and later legacy Streamable HTTP requests carry this header after initialize. */
    public boolean requiresLegacyProtocolVersionHeader() {
        return this == LEGACY_2025_11_25 || this == LEGACY_2025_06_18;
    }
}
