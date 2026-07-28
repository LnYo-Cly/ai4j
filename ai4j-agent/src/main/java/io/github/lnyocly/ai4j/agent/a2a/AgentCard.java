package io.github.lnyocly.ai4j.agent.a2a;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google A2A-compatible {@code AgentCard} — the capability declaration served at
 * {@code /.well-known/agent.json}. Deserialized by fastjson2.
 *
 * <p>Current contract fields:</p>
 * <ul>
 *   <li>Added {@code protocol} (replaces {@code protocolVersion})</li>
 *   <li>Added {@code agentUri} (agent:// URI scheme)</li>
 *   <li>Added {@code capabilities} (capability list)</li>
 *   <li>Added {@code skills} (skill definitions)</li>
 *   <li>Added {@code authentication} (auth metadata)</li>
 *   <li>Added {@code endpoints} (endpoint mapping)</li>
 *   <li>Added {@code supportedInterfaces} and default input/output modes</li>
 * </ul>
 *
 * <p>Backward compatibility: Old fields ({@code name}, {@code description}, {@code version},
 * {@code url}, {@code protocolVersion}) are retained.</p>
 */
public class AgentCard {

    // === Original fields (retained for backward compatibility) ===
    private String name;
    private String description;
    private String version;
    private String url;
    private String protocolVersion; // Deprecated: use 'protocol' instead

    // === A2A 1.0 required fields ===
    private String protocol;       // e.g., "a2a/1.0"
    private String agentUri;       // e.g., "agent://search"

    // === A2A 1.0 optional fields ===
    private List<String> capabilities; // e.g., ["search", "discovery"]
    private List<A2ASkill> skills;     // Skill definitions
    private Authentication authentication; // Auth metadata
    private Map<String, String> endpoints; // Endpoint mapping
    private List<SupportedInterface> supportedInterfaces;
    private List<String> defaultInputModes;
    private List<String> defaultOutputModes;

    public AgentCard() {
        this.capabilities = new ArrayList<String>();
        this.skills = new ArrayList<A2ASkill>();
        this.endpoints = new HashMap<String, String>();
        this.supportedInterfaces = new ArrayList<SupportedInterface>();
        this.defaultInputModes = new ArrayList<String>();
        this.defaultOutputModes = new ArrayList<String>();
    }

    // === Original getters/setters (backward compatibility) ===
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
        // Auto-sync to new field for compatibility
        if (protocolVersion != null && protocol == null) {
            this.protocol = "a2a/" + protocolVersion;
        }
    }

    // === A2A 1.0 getters/setters ===
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getAgentUri() { return agentUri; }
    public void setAgentUri(String agentUri) { this.agentUri = agentUri; }

    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities != null ? capabilities : new ArrayList<String>();
    }

    public List<A2ASkill> getSkills() { return skills; }
    public void setSkills(List<A2ASkill> skills) {
        this.skills = skills != null ? skills : new ArrayList<A2ASkill>();
    }

    public Authentication getAuthentication() { return authentication; }
    public void setAuthentication(Authentication authentication) { this.authentication = authentication; }

    public Map<String, String> getEndpoints() { return endpoints; }
    public void setEndpoints(Map<String, String> endpoints) {
        this.endpoints = endpoints != null ? endpoints : new HashMap<String, String>();
    }

    public List<SupportedInterface> getSupportedInterfaces() { return supportedInterfaces; }
    public void setSupportedInterfaces(List<SupportedInterface> supportedInterfaces) {
        this.supportedInterfaces = supportedInterfaces != null
            ? supportedInterfaces : new ArrayList<SupportedInterface>();
    }

    public List<String> getDefaultInputModes() { return defaultInputModes; }
    public void setDefaultInputModes(List<String> defaultInputModes) {
        this.defaultInputModes = defaultInputModes != null
            ? defaultInputModes : new ArrayList<String>();
    }

    public List<String> getDefaultOutputModes() { return defaultOutputModes; }
    public void setDefaultOutputModes(List<String> defaultOutputModes) {
        this.defaultOutputModes = defaultOutputModes != null
            ? defaultOutputModes : new ArrayList<String>();
    }

    public AgentCard withCapability(String capability) {
        if (capability != null && !capability.trim().isEmpty()) {
            getCapabilities().add(capability.trim());
        }
        return this;
    }

    public AgentCard withSkill(String name, String description) {
        if (name != null && !name.trim().isEmpty()) {
            getSkills().add(new A2ASkill(name.trim(), description == null ? "" : description.trim()));
        }
        return this;
    }

    public AgentCard withEndpoint(String name, String endpoint) {
        if (name != null && !name.trim().isEmpty() && endpoint != null && !endpoint.trim().isEmpty()) {
            getEndpoints().put(name.trim(), endpoint.trim());
        }
        return this;
    }

    public AgentCard withSupportedInterface(String url, String protocolBinding, String protocolVersion) {
        if (url != null && !url.trim().isEmpty()
            && protocolBinding != null && !protocolBinding.trim().isEmpty()) {
            getSupportedInterfaces().add(new SupportedInterface(url.trim(), protocolBinding.trim(), protocolVersion));
        }
        return this;
    }

    /**
     * Authentication metadata for A2A 1.0.
     */
    public static class Authentication {
        private String type;        // e.g., "api-key"
        private String header;      // e.g., "X-API-Key"
        private String obtainAt;    // e.g., "https://marketplace.example.com"

        public Authentication() {
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getHeader() { return header; }
        public void setHeader(String header) { this.header = header; }

        public String getObtainAt() { return obtainAt; }
        public void setObtainAt(String obtainAt) { this.obtainAt = obtainAt; }
    }

    /** A standard A2A transport endpoint advertised by an AgentCard. */
    public static class SupportedInterface {
        private String url;
        private String protocolBinding;
        private String protocolVersion;

        public SupportedInterface() {
        }

        public SupportedInterface(String url, String protocolBinding, String protocolVersion) {
            this.url = url;
            this.protocolBinding = protocolBinding;
            this.protocolVersion = protocolVersion;
        }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getProtocolBinding() { return protocolBinding; }
        public void setProtocolBinding(String protocolBinding) { this.protocolBinding = protocolBinding; }

        public String getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }
    }
}
