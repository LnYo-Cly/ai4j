package io.github.lnyocly.ai4j.mcp.gateway;

import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.config.McpConfigIO;
import io.github.lnyocly.ai4j.mcp.config.McpConfigSource;
import io.github.lnyocly.ai4j.mcp.config.McpServerConfig;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.util.McpToolConversionSupport;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author cly
 * @Description 独立的MCP网关，管理多个MCP客户端连接，提供统一的工具调用接口
 */
public class McpGateway {

    private static final Logger log = LoggerFactory.getLogger(McpGateway.class);
    private static final String DEFAULT_CONFIG_FILE = "mcp-servers-config.json";

    // 全局实例管理
    private static volatile McpGateway globalInstance = null;
    private static final Object instanceLock = new Object();

    // 管理的MCP客户端
    // Key格式：
    // - 全局服务：serviceId (如 "github", "filesystem")
    // - 用户服务：user_{userId}_service_{serviceId} (如 "user_123_service_github")
    private final Map<String, McpClient> mcpClients;

    // 工具名称到客户端的映射
    // Key格式：
    // - 全局工具：toolName (如 "search_repositories")
    // - 用户工具：user_{userId}_tool_{toolName} (如 "user_123_tool_search_repositories")
    private final McpGatewayToolRegistry toolRegistry;

    // MCP服务器配置
    private McpServerConfig serverConfig;

    // 配置源（用于动态配置管理）
    private McpConfigSource configSource;

    private final McpGatewayClientFactory clientFactory;
    private final McpGatewayConfigSourceBinding configSourceBinding;

    // 网关是否已初始化
    private volatile boolean initialized = false;

    /**
     * 获取全局MCP网关实例
     */
    public static McpGateway getInstance() {
        if (globalInstance == null) {
            synchronized (instanceLock) {
                if (globalInstance == null) {
                    globalInstance = new McpGateway();
                    log.info("创建全局MCP网关实例");
                }
            }
        }
        return globalInstance;
    }

    /**
     * 设置全局MCP网关实例
     */
    public static void setGlobalInstance(McpGateway instance) {
        synchronized (instanceLock) {
            globalInstance = instance;
            log.info("设置全局MCP网关实例");
        }
    }

    /**
     * 清除全局实例（用于测试）
     */
    public static void clearGlobalInstance() {
        synchronized (instanceLock) {
            if (globalInstance != null) {
                try {
                    globalInstance.shutdown();
                } catch (Exception e) {
                    log.warn("关闭全局MCP网关实例时发生错误", e);
                }
                globalInstance = null;
                log.info("清除全局MCP网关实例");
            }
        }
    }

    /**
     * 检查网关是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    public McpGateway() {
        this(new McpGatewayClientFactory());
    }

    McpGateway(McpGatewayClientFactory clientFactory) {
        this.mcpClients = new ConcurrentHashMap<>();
        this.toolRegistry = new McpGatewayToolRegistry();
        this.clientFactory = clientFactory;
        this.configSourceBinding = new McpGatewayConfigSourceBinding(this, clientFactory);
    }

    /**
     * 设置配置源（用于动态配置管理）
     */
    public void setConfigSource(McpConfigSource configSource) {
        McpConfigSource previousConfigSource = this.configSource;
        this.configSource = configSource;
        configSourceBinding.rebind(previousConfigSource, configSource, initialized);
    }

    /**
     * 从配置源加载所有配置
     */
    private void loadConfigsFromSource() {
        configSourceBinding.loadAll(configSource);
    }

    /**
     * 初始化MCP网关，从配置文件加载MCP服务器配置
     */
    public CompletableFuture<Void> initialize() {
        return initialize(DEFAULT_CONFIG_FILE);
    }

    /**
     * 初始化MCP网关，从指定配置文件加载MCP服务器配置
     */
    public CompletableFuture<Void> initialize(String configFile) {
        return CompletableFuture.runAsync(() -> {
            if (initialized) {
                log.warn("MCP网关已经初始化");
                return;
            }

            log.info("初始化MCP网关，配置文件: {}", configFile);

            try {
                // 如果没有设置配置源，则从配置文件加载
                if (configSource == null) {
                    loadServerConfig(configFile);

                    // 启动配置的MCP服务器
                    if (serverConfig != null && serverConfig.getMcpServers() != null) {
                        startConfiguredServers();
                    }
                } else {
                    // 使用配置源
                    loadConfigsFromSource();
                }

                initialized = true;

                // 自动设置为全局实例（如果还没有全局实例的话）
                if (globalInstance == null) {
                    setGlobalInstance(this);
                }

                log.info("MCP网关初始化完成");

            } catch (Exception e) {
                log.error("初始化MCP网关失败", e);
                throw new RuntimeException("初始化MCP网关失败", e);
            }
        });
    }
    
    /**
     * 加载服务器配置文件
     */
    private void loadServerConfig(String configFile) {
        try {
            serverConfig = McpConfigIO.loadServerConfig(configFile, getClass().getClassLoader());
            if (serverConfig != null) {
                log.info("成功加载MCP服务器配置，共 {} 个服务器",
                    serverConfig.getMcpServers() != null ? serverConfig.getMcpServers().size() : 0);
            } else {
                log.info("未找到MCP服务器配置文件: {}", configFile);
            }
        } catch (Exception e) {
            log.warn("加载MCP服务器配置失败: {}", configFile, e);
        }
    }

    /**
     * 启动配置文件中的MCP服务器
     */
    private void startConfiguredServers() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        serverConfig.getMcpServers().forEach((serverId, serverInfo) -> {
            if (serverInfo.getEnabled() != null && serverInfo.getEnabled()) {
                CompletableFuture<Void> future = startMcpServer(serverId, serverInfo)
                        .exceptionally(throwable -> {
                            log.error("启动MCP服务器失败: {}", serverId, throwable);
                            return null;
                        });
                futures.add(future);
            }
        });

        // 等待所有服务器启动完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 启动单个MCP服务器
     */
    private CompletableFuture<Void> startMcpServer(String serverId, McpServerConfig.McpServerInfo serverInfo) {
        return CompletableFuture.runAsync(() -> {
            log.info("启动MCP服务器: {} ({})", serverId, serverInfo.getName());

            try {
                McpClient client = clientFactory.create(serverId, serverInfo);
                addMcpClient(serverId, client).join();

                log.info("MCP服务器启动成功: {}", serverId);
            } catch (Exception e) {
                log.error("启动MCP服务器失败: {}", serverId, e);
                throw new RuntimeException("启动MCP服务器失败: " + serverId, e);
            }
        });
    }

    /**
     * 添加全局MCP客户端
     */
    public CompletableFuture<Void> addMcpClient(String serviceId, McpClient client) {
        return addMcpClientInternal(serviceId, client);
    }

    /**
     * 添加用户级别的MCP客户端
     */
    public CompletableFuture<Void> addUserMcpClient(String userId, String serviceId, McpClient client) {
        String userClientKey = McpGatewayKeySupport.buildUserClientKey(userId, serviceId);
        return addMcpClientInternal(userClientKey, client);
    }

    /**
     * 内部统一的客户端添加方法
     */
    private CompletableFuture<Void> addMcpClientInternal(String clientKey, McpClient client) {
        return CompletableFuture.runAsync(() -> {
            log.info("添加MCP客户端: {}", clientKey);

            McpClient previousClient = mcpClients.put(clientKey, client);

            // 连接客户端并获取工具列表
            try {
                client.connect().join();
                toolRegistry.refresh(mcpClients).join();

                if (previousClient != null && previousClient != client) {
                    disconnectClientQuietly(clientKey, previousClient);
                }

                log.info("MCP客户端 {} 添加成功", clientKey);
            } catch (Exception e) {
                mcpClients.remove(clientKey, client);
                if (previousClient != null && previousClient != client) {
                    mcpClients.put(clientKey, previousClient);
                }
                disconnectClientQuietly(clientKey, client);
                log.error("添加MCP客户端失败: {}", clientKey, e);
                throw new RuntimeException("添加MCP客户端失败", e);
            }
        });
    }
    
    /**
     * 动态添加MCP服务器
     */
    public CompletableFuture<Void> addMcpServer(String serverId, McpServerConfig.McpServerInfo serverInfo) {
        return CompletableFuture.runAsync(() -> {
            log.info("动态添加MCP服务器: {}", serverId);

            try {
                McpClient client = clientFactory.create(serverId, serverInfo);
                addMcpClient(serverId, client).join();

                log.info("MCP服务器添加成功: {}", serverId);
            } catch (Exception e) {
                log.error("添加MCP服务器失败: {}", serverId, e);
                throw new RuntimeException("添加MCP服务器失败", e);
            }
        });
    }

    /**
     * 移除全局MCP客户端
     */
    public CompletableFuture<Void> removeMcpClient(String serviceId) {
        return removeMcpClientInternal(serviceId);
    }

    /**
     * 移除用户级别的MCP客户端
     */
    public CompletableFuture<Void> removeUserMcpClient(String userId, String serviceId) {
        String userClientKey = McpGatewayKeySupport.buildUserClientKey(userId, serviceId);
        return removeMcpClientInternal(userClientKey);
    }

    /**
     * 内部统一的客户端移除方法
     */
    private CompletableFuture<Void> removeMcpClientInternal(String clientKey) {
        return CompletableFuture.runAsync(() -> {
            McpClient client = mcpClients.remove(clientKey);
            if (client != null) {
                log.info("移除MCP客户端: {}", clientKey);

                try {
                    client.disconnect().join();
                    toolRegistry.refresh(mcpClients).join();

                    log.info("MCP客户端 {} 移除成功", clientKey);
                } catch (Exception e) {
                    log.error("移除MCP客户端时发生错误: {}", clientKey, e);
                }
            }
        });
    }
    
    /**
     * 获取所有可用的工具（转换为OpenAI Tool格式）
     */
    public CompletableFuture<List<Tool.Function>> getAvailableTools() {
        if (toolRegistry.getAvailableToolsCache() != null) {
            return CompletableFuture.completedFuture(toolRegistry.getAvailableToolsCache());
        }

        return toolRegistry.refresh(mcpClients)
                .thenApply(v -> toolRegistry.getAvailableToolsCache());
    }

    /**
     * 获取所有可用的工具（转换为OpenAI Tool格式）
     */
    public CompletableFuture<List<Tool.Function>> getAvailableTools(List<String> serviceIds) {
        if (serviceIds != null && !serviceIds.isEmpty()) {
            return CompletableFuture.completedFuture(mcpClients.entrySet().stream().filter(entry -> serviceIds.contains(entry.getKey())).map(entry -> {
                return entry.getValue().getAvailableTools().join();
            }).flatMap(list -> list.stream().map(McpToolConversionSupport::convertToOpenAiTool)).collect(Collectors.toList()));
        }
        if (toolRegistry.getAvailableToolsCache() != null) {
            return CompletableFuture.completedFuture(toolRegistry.getAvailableToolsCache());
        }

        return toolRegistry.refresh(mcpClients)
                .thenApply(v -> toolRegistry.getAvailableToolsCache());
    }

    /**
     * 获取用户的可用工具（包括用户专属工具和全局工具）
     */
    public CompletableFuture<List<Tool.Function>> getUserAvailableTools(List<String> serviceIds, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Tool.Function> userTools = new ArrayList<>();
            String userPrefix = McpGatewayKeySupport.buildUserPrefix(userId);

            // 获取用户专属工具
            mcpClients.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(userPrefix))
                .forEach(entry -> {
                    try {
                        List<McpToolDefinition> clientTools = entry.getValue().getAvailableTools().join();
                        for (McpToolDefinition tool : clientTools) {
                            userTools.add(McpToolConversionSupport.convertToOpenAiTool(tool));
                        }
                    } catch (Exception e) {
                        log.error("获取用户工具列表失败: clientKey={}", entry.getKey(), e);
                    }
                });

            List<String> filterIds = serviceIds != null ? serviceIds : new ArrayList<>();
            // 获取全局工具
            mcpClients.entrySet().stream()
                .filter(entry -> {
                    if (McpGatewayKeySupport.isUserClientKey(entry.getKey())) {
                        return false;
                    }
                    if (filterIds.isEmpty()) {
                        return true;
                    }
                    return filterIds.contains(entry.getKey());
                })
                .forEach(entry -> {
                    try {
                        List<McpToolDefinition> clientTools = entry.getValue().getAvailableTools().join();
                        for (McpToolDefinition tool : clientTools) {
                            userTools.add(McpToolConversionSupport.convertToOpenAiTool(tool));
                        }
                    } catch (Exception e) {
                        log.error("获取全局工具列表失败: clientKey={}", entry.getKey(), e);
                    }
                });

            return userTools;
        });
    }

    /**
     * 调用全局工具
     */
    public CompletableFuture<String> callTool(String toolName, Object arguments) {
        return callToolInternal(toolName, arguments);
    }

    /**
     * 调用用户工具（优先使用用户专属，回退到全局）
     */
    public CompletableFuture<String> callUserTool(String userId, String toolName, Object arguments) {
        String userToolKey = McpGatewayKeySupport.buildUserToolKey(userId, toolName);

        // 先尝试用户专属工具
        String clientId = toolRegistry.getClientId(userToolKey);
        if (clientId != null) {
            log.debug("找到用户专属工具: {} -> {}", userToolKey, clientId);
            return callToolInternal(toolName, arguments, clientId);
        }

        // 回退到全局工具
        log.debug("未找到用户专属工具，尝试全局工具: {}", toolName);
        return callToolInternal(toolName, arguments);
    }

    /**
     * 内部工具调用方法（查找全局工具）
     */
    private CompletableFuture<String> callToolInternal(String toolName, Object arguments) {
        String clientId = toolRegistry.getClientId(toolName);
        if (clientId == null) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("工具不存在: " + toolName));
            return future;
        }

        return callToolInternal(toolName, arguments, clientId);
    }

    /**
     * 内部工具调用方法（指定客户端）
     */
    private CompletableFuture<String> callToolInternal(String toolName, Object arguments, String clientId) {
        McpClient client = mcpClients.get(clientId);
        if (client == null || !client.isConnected()) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("MCP客户端不可用: " + clientId));
            return future;
        }

        log.info("调用MCP工具: {} 通过客户端: {}", toolName, clientId);

        return client.callTool(toolName, arguments)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("调用MCP工具失败: {}", toolName, throwable);
                    } else {
                        log.debug("MCP工具调用成功: {}", toolName);
                    }
                });
    }

    /**
     * 清除用户的所有MCP客户端
     */
    public CompletableFuture<Void> clearUserMcpClients(String userId) {
        return CompletableFuture.runAsync(() -> {
            String userPrefix = McpGatewayKeySupport.buildUserPrefix(userId);

            List<String> userClientKeys = mcpClients.keySet().stream()
                .filter(key -> key.startsWith(userPrefix))
                .collect(Collectors.toList());

            for (String clientKey : userClientKeys) {
                try {
                    removeMcpClientInternal(clientKey).join();
                } catch (Exception e) {
                    log.error("清除用户MCP客户端失败: clientKey={}", clientKey, e);
                }
            }

            log.info("清除用户所有MCP客户端完成: userId={}, count={}", userId, userClientKeys.size());
        });
    }
    
    /**
     * 获取网关状态信息
     */
    public Map<String, Object> getGatewayStatus() {
        Map<String, Object> status = new HashMap<>();

        // 统计全局和用户客户端
        long globalClients = mcpClients.keySet().stream()
            .filter(key -> !McpGatewayKeySupport.isUserClientKey(key))
            .count();

        long userClients = mcpClients.keySet().stream()
            .filter(McpGatewayKeySupport::isUserClientKey)
            .count();

        status.put("totalClients", mcpClients.size());
        status.put("globalClients", globalClients);
        status.put("userClients", userClients);
        status.put("connectedClients", mcpClients.values().stream()
                .mapToLong(client -> client.isConnected() ? 1 : 0)
                .sum());
        status.put("totalTools", toolRegistry.snapshotMappings().size());

        Map<String, Object> clientStatus = new HashMap<>();
        mcpClients.forEach((id, client) -> {
            Map<String, Object> info = new HashMap<>();
            info.put("connected", client.isConnected());
            info.put("initialized", client.isInitialized());
            info.put("type", McpGatewayKeySupport.isUserClientKey(id) ? "user" : "global");
            clientStatus.put(id, info);
        });
        status.put("clients", clientStatus);

        return status;
    }

    /**
     * 获取工具名称到客户端ID的映射
     */
    public Map<String, String> getToolToClientMap() {
        return toolRegistry.snapshotMappings();
    }
    
    /**
     * 关闭网关，断开所有客户端连接
     */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            log.info("关闭MCP网关");
            
            List<CompletableFuture<Void>> futures = mcpClients.values().stream()
                    .map(McpClient::disconnect)
                    .collect(Collectors.toList());
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            mcpClients.clear();
            toolRegistry.clearAll();
            
            log.info("MCP网关已关闭");
        });
    }

    private void disconnectClientQuietly(String clientKey, McpClient client) {
        if (client == null) {
            return;
        }
        try {
            client.disconnect().join();
        } catch (Exception e) {
            log.warn("关闭旧MCP客户端失败: {}", clientKey, e);
        }
    }
}
