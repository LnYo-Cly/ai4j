package io.github.lnyocly.ai4j.mcp.gateway;

import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.config.McpConfigSource;
import io.github.lnyocly.ai4j.mcp.config.McpServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * MCP Gateway 与配置源之间的桥接器
 */
class McpGatewayConfigSourceBinding {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayConfigSourceBinding.class);

    private final McpGateway gateway;
    private final McpGatewayClientFactory clientFactory;
    private final McpConfigSource.ConfigChangeListener configChangeListener;

    McpGatewayConfigSourceBinding(McpGateway gateway, McpGatewayClientFactory clientFactory) {
        this.gateway = gateway;
        this.clientFactory = clientFactory;
        this.configChangeListener = new McpConfigSource.ConfigChangeListener() {
            @Override
            public void onConfigAdded(String serverId, McpServerConfig.McpServerInfo config) {
                tryAddOrReplace(serverId, config, "动态添加MCP服务成功");
            }

            @Override
            public void onConfigRemoved(String serverId) {
                gateway.removeMcpClient(serverId).join();
                log.info("动态移除MCP服务: {}", serverId);
            }

            @Override
            public void onConfigUpdated(String serverId, McpServerConfig.McpServerInfo config) {
                tryAddOrReplace(serverId, config, "动态更新MCP服务成功");
            }
        };
    }

    public void rebind(McpConfigSource currentSource, McpConfigSource nextSource, boolean initialized) {
        if (currentSource == nextSource) {
            return;
        }
        if (currentSource != null) {
            currentSource.removeConfigChangeListener(configChangeListener);
        }
        if (nextSource != null) {
            nextSource.addConfigChangeListener(configChangeListener);
            if (initialized) {
                loadAll(nextSource);
            }
        }
    }

    public void loadAll(McpConfigSource configSource) {
        if (configSource == null) {
            return;
        }
        try {
            Map<String, McpServerConfig.McpServerInfo> configs = configSource.getAllConfigs();
            configs.forEach((serverId, config) -> {
                try {
                    McpClient client = clientFactory.create(serverId, config);
                    gateway.addMcpClient(serverId, client).join();
                } catch (Exception e) {
                    log.error("从配置源加载MCP服务失败: {}", serverId, e);
                }
            });
            log.info("从配置源加载了 {} 个MCP服务", configs.size());
        } catch (Exception e) {
            log.error("从配置源加载配置失败", e);
        }
    }

    private void tryAddOrReplace(String serverId, McpServerConfig.McpServerInfo config, String successLog) {
        try {
            McpClient client = clientFactory.create(serverId, config);
            gateway.addMcpClient(serverId, client).join();
            log.info("{}: {}", successLog, serverId);
        } catch (Exception e) {
            log.error("{}: {}", successLog.replace("成功", "失败"), serverId, e);
        }
    }
}
