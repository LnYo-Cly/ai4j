package io.github.lnyocly.ai4j.mcp.gateway;

import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.config.McpServerConfig;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import io.github.lnyocly.ai4j.mcp.transport.McpTransportFactory;
import io.github.lnyocly.ai4j.mcp.transport.TransportConfig;
import io.github.lnyocly.ai4j.mcp.util.McpTypeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP Gateway 客户端创建器
 */
class McpGatewayClientFactory {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayClientFactory.class);
    private static final String DEFAULT_CLIENT_VERSION = "1.0.0";

    private final String clientVersion;

    McpGatewayClientFactory() {
        this(DEFAULT_CLIENT_VERSION);
    }

    McpGatewayClientFactory(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public McpClient create(String serverId, McpServerConfig.McpServerInfo serverInfo) {
        String transportType = McpTypeSupport.resolveType(serverInfo);

        try {
            TransportConfig config = TransportConfig.fromServerInfo(serverInfo);
            McpTransportFactory.TransportType factoryType = McpTransportFactory.TransportType.fromString(transportType);
            McpTransportFactory.validateConfig(factoryType, config);
            McpTransport transport = McpTransportFactory.createTransport(factoryType, config);
            return new McpClient(serverId, clientVersion, transport);
        } catch (Exception e) {
            log.error("创建MCP客户端失败: serverId={}, transportType={}", serverId, transportType, e);
            throw new RuntimeException("创建MCP客户端失败: " + e.getMessage(), e);
        }
    }
}
