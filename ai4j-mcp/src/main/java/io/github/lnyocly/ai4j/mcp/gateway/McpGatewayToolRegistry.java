package io.github.lnyocly.ai4j.mcp.gateway;

import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.util.McpToolConversionSupport;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP gateway 工具注册表，负责工具缓存与客户端映射
 */
public class McpGatewayToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayToolRegistry.class);

    private final Map<String, String> toolToClientMap = new ConcurrentHashMap<String, String>();
    private volatile List<Tool.Function> availableTools;

    public List<Tool.Function> getAvailableToolsCache() {
        return availableTools;
    }

    public String getClientId(String toolKey) {
        return toolToClientMap.get(toolKey);
    }

    public Map<String, String> snapshotMappings() {
        return new HashMap<String, String>(toolToClientMap);
    }

    public void clearClientMappings(String clientKey) {
        toolToClientMap.entrySet().removeIf(entry -> clientKey.equals(entry.getValue()));
        availableTools = null;
        log.debug("清理客户端工具映射: {}", clientKey);
    }

    public void clearAll() {
        toolToClientMap.clear();
        availableTools = null;
    }

    public CompletableFuture<Void> refresh(Map<String, McpClient> mcpClients) {
        return CompletableFuture.runAsync(() -> {
            log.info("刷新MCP工具映射");

            List<CompletableFuture<Void>> futures = new ArrayList<CompletableFuture<Void>>();
            List<Tool.Function> refreshedTools = Collections.synchronizedList(new ArrayList<Tool.Function>());
            Map<String, String> refreshedMappings = new ConcurrentHashMap<String, String>();

            mcpClients.forEach((clientId, client) -> {
                if (client.isConnected()) {
                    CompletableFuture<Void> future = client.getAvailableTools()
                            .thenAccept(tools -> registerClientTools(clientId, tools, refreshedTools, refreshedMappings))
                            .exceptionally(throwable -> {
                                log.error("获取客户端工具列表失败: {}", clientId, throwable);
                                return null;
                            });
                    futures.add(future);
                }
            });

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            toolToClientMap.clear();
            toolToClientMap.putAll(refreshedMappings);
            availableTools = new ArrayList<Tool.Function>(refreshedTools);
            log.info("工具映射刷新完成，共 {} 个工具", refreshedTools.size());
        });
    }

    private void registerClientTools(
            String clientId,
            List<McpToolDefinition> tools,
            List<Tool.Function> refreshedTools,
            Map<String, String> refreshedMappings) {
        for (McpToolDefinition tool : tools) {
            refreshedTools.add(McpToolConversionSupport.convertToOpenAiTool(tool));

            if (McpGatewayKeySupport.isUserClientKey(clientId)) {
                String userId = McpGatewayKeySupport.extractUserIdFromClientKey(clientId);
                String userToolKey = McpGatewayKeySupport.buildUserToolKey(userId, tool.getName());
                refreshedMappings.put(userToolKey, clientId);
                log.debug("建立用户工具映射: {} -> {}", userToolKey, clientId);
            } else {
                refreshedMappings.put(tool.getName(), clientId);
                log.debug("建立全局工具映射: {} -> {}", tool.getName(), clientId);
            }
        }
    }
}
