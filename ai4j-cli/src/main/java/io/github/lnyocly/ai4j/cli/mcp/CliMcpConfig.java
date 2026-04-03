package io.github.lnyocly.ai4j.cli.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

public class CliMcpConfig {

    private Map<String, CliMcpServerDefinition> mcpServers = new LinkedHashMap<String, CliMcpServerDefinition>();

    public CliMcpConfig() {
    }

    public CliMcpConfig(Map<String, CliMcpServerDefinition> mcpServers) {
        this.mcpServers = mcpServers == null
                ? new LinkedHashMap<String, CliMcpServerDefinition>()
                : new LinkedHashMap<String, CliMcpServerDefinition>(mcpServers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().mcpServers(mcpServers);
    }

    public Map<String, CliMcpServerDefinition> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(Map<String, CliMcpServerDefinition> mcpServers) {
        this.mcpServers = mcpServers == null
                ? new LinkedHashMap<String, CliMcpServerDefinition>()
                : new LinkedHashMap<String, CliMcpServerDefinition>(mcpServers);
    }

    public static final class Builder {

        private Map<String, CliMcpServerDefinition> mcpServers = new LinkedHashMap<String, CliMcpServerDefinition>();

        private Builder() {
        }

        public Builder mcpServers(Map<String, CliMcpServerDefinition> mcpServers) {
            this.mcpServers = mcpServers == null
                    ? new LinkedHashMap<String, CliMcpServerDefinition>()
                    : new LinkedHashMap<String, CliMcpServerDefinition>(mcpServers);
            return this;
        }

        public CliMcpConfig build() {
            return new CliMcpConfig(mcpServers);
        }
    }
}
