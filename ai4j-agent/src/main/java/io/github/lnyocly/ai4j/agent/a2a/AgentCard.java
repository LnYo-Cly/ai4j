package io.github.lnyocly.ai4j.agent.a2a;

/**
 * Minimal Google A2A {@code AgentCard} — the standardized capability declaration served at
 * {@code /.well-known/agent.json}. Fields per the A2A spec; deserialized by fastjson2.
 */
public class AgentCard {

    private String name;
    private String description;
    private String version;
    private String url;
    private String protocolVersion;

    public AgentCard() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }
}
