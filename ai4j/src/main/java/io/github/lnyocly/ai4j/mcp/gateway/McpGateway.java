package io.github.lnyocly.ai4j.mcp.gateway;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.config.McpConfigSource;
import io.github.lnyocly.ai4j.mcp.config.McpServerConfig;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.transport.McpTransportFactory;
import io.github.lnyocly.ai4j.mcp.transport.TransportConfig;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final Map<String, String> toolToClientMap;

    // 缓存的所有可用工具
    private volatile List<Tool.Function> availableTools;

    // MCP服务器配置
    private McpServerConfig serverConfig;

    // 配置源（用于动态配置管理）
    private McpConfigSource configSource;

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
        this.mcpClients = new ConcurrentHashMap<>();
        this.toolToClientMap = new ConcurrentHashMap<>();
    }

    /**
     * 设置配置源（用于动态配置管理）
     */
    public void setConfigSource(McpConfigSource configSource) {
        this.configSource = configSource;

        if (configSource != null) {
            // 添加配置变更监听器
            configSource.addConfigChangeListener(new McpConfigSource.ConfigChangeListener() {
                @Override
                public void onConfigAdded(String serverId, McpServerConfig.McpServerInfo config) {
                    try {
                        McpClient client = createMcpClient(serverId, config);
                        addMcpClient(serverId, client).join();
                        log.info("动态添加MCP服务成功: {}", serverId);
                    } catch (Exception e) {
                        log.error("动态添加MCP服务失败: {}", serverId, e);
                    }
                }

                @Override
                public void onConfigRemoved(String serverId) {
                    removeMcpClient(serverId);
                    log.info("动态移除MCP服务: {}", serverId);
                }

                @Override
                public void onConfigUpdated(String serverId, McpServerConfig.McpServerInfo config) {
                    removeMcpClient(serverId);
                    try {
                        McpClient client = createMcpClient(serverId, config);
                        addMcpClient(serverId, client).join();
                        log.info("动态更新MCP服务成功: {}", serverId);
                    } catch (Exception e) {
                        log.error("动态更新MCP服务失败: {}", serverId, e);
                    }
                }
            });

            // 从配置源加载初始配置
            loadConfigsFromSource();
        }
    }

    /**
     * 从配置源加载所有配置
     */
    private void loadConfigsFromSource() {
        if (configSource != null) {
            try {
                Map<String, McpServerConfig.McpServerInfo> configs = configSource.getAllConfigs();
                configs.forEach((serverId, config) -> {
                    try {
                        McpClient client = createMcpClient(serverId, config);
                        addMcpClient(serverId, client).join();
                    } catch (Exception e) {
                        log.error("从配置源加载MCP服务失败: {}", serverId, e);
                    }
                });
                log.info("从配置源加载了 {} 个MCP服务", configs.size());
            } catch (Exception e) {
                log.error("从配置源加载配置失败", e);
            }
        }
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
            String configContent = loadConfigContent(configFile);
            if (configContent != null && !configContent.trim().isEmpty()) {
                serverConfig = JSON.parseObject(configContent, McpServerConfig.class);
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
     * 加载配置文件内容
     */
    private String loadConfigContent(String configFile) throws IOException {
        // 首先尝试从类路径加载
        InputStream is = getClass().getClassLoader().getResourceAsStream(configFile);
        if (is != null) {
            try {
                byte[] bytes = readAllBytes(is);
                return new String(bytes, StandardCharsets.UTF_8);
            } finally {
                is.close();
            }
        }

        // 然后尝试从文件系统加载
        Path configPath = Paths.get(configFile);
        if (Files.exists(configPath)) {
            return readFileToString(configPath);
        }

        return null;
    }

    /**
     * JDK 8 兼容的读取所有字节方法
     */
    private byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((bytesRead = is.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }

    /**
     * JDK 8 兼容的读取文件为字符串方法
     */
    private String readFileToString(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
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
                McpClient client = createMcpClient(serverId, serverInfo);
                addMcpClient(serverId, client).join();

                log.info("MCP服务器启动成功: {}", serverId);
            } catch (Exception e) {
                log.error("启动MCP服务器失败: {}", serverId, e);
                throw new RuntimeException("启动MCP服务器失败: " + serverId, e);
            }
        });
    }

    /**
     * 根据配置创建MCP客户端
     */
    private McpClient createMcpClient(String serverId, McpServerConfig.McpServerInfo serverInfo) {
        // 优先使用type字段，向后兼容transport字段
        String transportType = serverInfo.getType() != null ? serverInfo.getType() :
                              (serverInfo.getTransport() != null ? serverInfo.getTransport() : "stdio");

        try {
            // 使用新的传输层工厂创建传输层
            TransportConfig config = createTransportConfig(serverInfo);
            McpTransport transport = McpTransportFactory.createTransport(transportType, config);

            return new McpClient(serverId, "1.0.0", transport);

        } catch (Exception e) {
            log.error("创建MCP客户端失败: serverId={}, transportType={}", serverId, transportType, e);
            throw new RuntimeException("创建MCP客户端失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据服务器信息创建传输配置
     */
    private TransportConfig createTransportConfig(McpServerConfig.McpServerInfo serverInfo) {
        String transportType = serverInfo.getType() != null ? serverInfo.getType() :
                              (serverInfo.getTransport() != null ? serverInfo.getTransport() : "stdio");

        switch (transportType.toLowerCase()) {
            case "stdio":
                return TransportConfig.stdio(serverInfo.getCommand(), serverInfo.getArgs(), serverInfo.getEnv());
            case "sse":
                return TransportConfig.sse(serverInfo);
            case "streamable_http":
            case "http":
                return TransportConfig.streamableHttp(serverInfo.getUrl());
            default:
                throw new IllegalArgumentException("不支持的传输类型: " + transportType);
        }
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
        String userClientKey = buildUserClientKey(userId, serviceId);
        return addMcpClientInternal(userClientKey, client);
    }

    /**
     * 内部统一的客户端添加方法
     */
    private CompletableFuture<Void> addMcpClientInternal(String clientKey, McpClient client) {
        return CompletableFuture.runAsync(() -> {
            log.info("添加MCP客户端: {}", clientKey);

            mcpClients.put(clientKey, client);

            // 连接客户端并获取工具列表
            try {
                client.connect().join();
                refreshToolMappings().join();

                log.info("MCP客户端 {} 添加成功", clientKey);
            } catch (Exception e) {
                mcpClients.remove(clientKey);
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
                McpClient client = createMcpClient(serverId, serverInfo);
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
        String userClientKey = buildUserClientKey(userId, serviceId);
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

                    // 清理工具映射
                    cleanupToolMappings(clientKey);

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
        if (availableTools != null) {
            return CompletableFuture.completedFuture(availableTools);
        }
        
        return refreshToolMappings()
                .thenApply(v -> availableTools);
    }

    /**
     * 获取所有可用的工具（转换为OpenAI Tool格式）
     */
    public CompletableFuture<List<Tool.Function>> getAvailableTools(List<String> serviceIds) {
        if (serviceIds != null && !serviceIds.isEmpty()) {
            return CompletableFuture.completedFuture(mcpClients.entrySet().stream().filter(entry -> serviceIds.contains(entry.getKey())).map(entry -> {
                return entry.getValue().getAvailableTools().join();
            }).flatMap(list -> list.stream().map(this::convertToOpenAiTool)).collect(Collectors.toList()));
        }
        if (availableTools != null) {
            return CompletableFuture.completedFuture(availableTools);
        }

        return refreshToolMappings()
                .thenApply(v -> availableTools);
    }

    /**
     * 获取用户的可用工具（包括用户专属工具和全局工具）
     */
    public CompletableFuture<List<Tool.Function>> getUserAvailableTools(List<String> serviceIds, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Tool.Function> userTools = new ArrayList<>();
            String userPrefix = buildUserPrefix(userId);

            // 获取用户专属工具
            mcpClients.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(userPrefix))
                .forEach(entry -> {
                    try {
                        List<McpToolDefinition> clientTools = entry.getValue().getAvailableTools().join();
                        for (McpToolDefinition tool : clientTools) {
                            userTools.add(convertToOpenAiTool(tool));
                        }
                    } catch (Exception e) {
                        log.error("获取用户工具列表失败: clientKey={}", entry.getKey(), e);
                    }
                });

            List<String> filterIds = serviceIds != null ? serviceIds : new ArrayList<>();
            // 获取全局工具
            mcpClients.entrySet().stream()
                .filter(entry -> {
                    if (isUserClientKey(entry.getKey())) return false;
                    return filterIds.contains(entry.getKey());
                })
                .forEach(entry -> {
                    try {
                        List<McpToolDefinition> clientTools = entry.getValue().getAvailableTools().join();
                        for (McpToolDefinition tool : clientTools) {
                            userTools.add(convertToOpenAiTool(tool));
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
        String userToolKey = buildUserToolKey(userId, toolName);

        // 先尝试用户专属工具
        String clientId = toolToClientMap.get(userToolKey);
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
        String clientId = toolToClientMap.get(toolName);
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
            String userPrefix = buildUserPrefix(userId);

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
            .filter(key -> !isUserClientKey(key))
            .count();

        long userClients = mcpClients.keySet().stream()
            .filter(this::isUserClientKey)
            .count();

        status.put("totalClients", mcpClients.size());
        status.put("globalClients", globalClients);
        status.put("userClients", userClients);
        status.put("connectedClients", mcpClients.values().stream()
                .mapToLong(client -> client.isConnected() ? 1 : 0)
                .sum());
        status.put("totalTools", toolToClientMap.size());

        Map<String, Object> clientStatus = new HashMap<>();
        mcpClients.forEach((id, client) -> {
            Map<String, Object> info = new HashMap<>();
            info.put("connected", client.isConnected());
            info.put("initialized", client.isInitialized());
            info.put("type", isUserClientKey(id) ? "user" : "global");
            clientStatus.put(id, info);
        });
        status.put("clients", clientStatus);

        return status;
    }

    /**
     * 获取工具名称到客户端ID的映射
     */
    public Map<String, String> getToolToClientMap() {
        return new HashMap<>(toolToClientMap);
    }
    
    /**
     * 刷新工具映射
     */
    private CompletableFuture<Void> refreshToolMappings() {
        return CompletableFuture.runAsync(() -> {
            log.info("刷新MCP工具映射");
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            List<Tool.Function> allTools = new ArrayList<>();
            
            mcpClients.forEach((clientId, client) -> {
                if (client.isConnected()) {
                    CompletableFuture<Void> future = client.getAvailableTools()
                            .thenAccept(tools -> {
                                synchronized (allTools) {
                                    for (McpToolDefinition tool : tools) {
                                        // 将MCP工具转换为OpenAI Tool格式
                                        Tool.Function function = convertToOpenAiTool(tool);
                                        allTools.add(function);
                                        
                                        // 建立工具名称到客户端的映射
                                        if (isUserClientKey(clientId)) {
                                            // 用户客户端：建立用户工具映射
                                            String userId = extractUserIdFromClientKey(clientId);
                                            String userToolKey = buildUserToolKey(userId, tool.getName());
                                            toolToClientMap.put(userToolKey, clientId);
                                            log.debug("建立用户工具映射: {} -> {}", userToolKey, clientId);
                                        } else {
                                            // 全局客户端：建立全局工具映射
                                            toolToClientMap.put(tool.getName(), clientId);
                                            log.debug("建立全局工具映射: {} -> {}", tool.getName(), clientId);
                                        }
                                    }
                                }
                            })
                            .exceptionally(throwable -> {
                                log.error("获取客户端工具列表失败: {}", clientId, throwable);
                                return null;
                            });
                    
                    futures.add(future);
                }
            });
            
            // 等待所有客户端的工具列表获取完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            availableTools = allTools;
            log.info("工具映射刷新完成，共 {} 个工具", allTools.size());
        });
    }

    /**
     * 清理指定客户端的工具映射
     */
    private void cleanupToolMappings(String clientKey) {
        toolToClientMap.entrySet().removeIf(entry -> entry.getValue().equals(clientKey));
        availableTools = null; // 清除缓存
        log.debug("清理客户端工具映射: {}", clientKey);
    }

    /**
     * 将MCP工具定义转换为OpenAI Tool格式
     */
    private Tool.Function convertToOpenAiTool(McpToolDefinition mcpTool) {
        Tool.Function function = new Tool.Function();
        function.setName(mcpTool.getName());
        function.setDescription(mcpTool.getDescription());
        
        // 转换输入Schema
        if (mcpTool.getInputSchema() != null) {
            Tool.Function.Parameter parameter = convertInputSchema(mcpTool.getInputSchema());
            function.setParameters(parameter);
        }
        
        return function;
    }
    
    /**
     * 转换输入Schema格式
     */
    private Tool.Function.Parameter convertInputSchema(Map<String, Object> inputSchema) {
        Tool.Function.Parameter parameter = new Tool.Function.Parameter();
        parameter.setType("object");
        
        // 提取properties和required
        Object properties = inputSchema.get("properties");
        Object required = inputSchema.get("required");
        
        if (properties instanceof Map) {
            Map<String, Tool.Function.Property> convertedProperties = new HashMap<>();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> propsMap = (Map<String, Object>) properties;
            
            propsMap.forEach((key, value) -> {
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propMap = (Map<String, Object>) value;

                    Tool.Function.Property property = convertToProperty(propMap);
                    convertedProperties.put(key, property);
                }
            });
            
            parameter.setProperties(convertedProperties);
        }
        
        if (required instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> requiredList = (List<String>) required;
            parameter.setRequired(requiredList);
        }
        
        return parameter;
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
            toolToClientMap.clear();
            availableTools = null;
            
            log.info("MCP网关已关闭");
        });
    }

    /**
     * 将Map转换为Tool.Function.Property对象
     */
    private Tool.Function.Property convertToProperty(Map<String, Object> propMap) {
        Tool.Function.Property property = new Tool.Function.Property();
        property.setType((String) propMap.get("type"));
        property.setDescription((String) propMap.get("description"));

        // 处理枚举值
        Object enumObj = propMap.get("enum");
        if (enumObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) enumObj;
            property.setEnumValues(enumValues);
        }

        // 处理数组的items属性
        Object itemsObj = propMap.get("items");
        if (itemsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> itemsMap = (Map<String, Object>) itemsObj;
            Tool.Function.Property items = convertToProperty(itemsMap);
            property.setItems(items);
        }

        return property;
    }

    // ========== 辅助方法 ==========

    /**
     * 构建用户客户端Key
     */
    private String buildUserClientKey(String userId, String serviceId) {
        return "user_" + userId + "_service_" + serviceId;
    }

    /**
     * 构建用户工具Key
     */
    private String buildUserToolKey(String userId, String toolName) {
        return "user_" + userId + "_tool_" + toolName;
    }

    /**
     * 构建用户前缀
     */
    private String buildUserPrefix(String userId) {
        return "user_" + userId + "_";
    }

    /**
     * 判断是否为用户客户端Key
     */
    private boolean isUserClientKey(String clientKey) {
        return clientKey.startsWith("user_") && clientKey.contains("_service_");
    }

    /**
     * 从用户客户端Key中提取用户ID
     */
    private String extractUserIdFromClientKey(String clientKey) {
        if (!isUserClientKey(clientKey)) {
            throw new IllegalArgumentException("不是有效的用户客户端Key: " + clientKey);
        }

        // 格式：user_{userId}_service_{serviceId}
        String withoutPrefix = clientKey.substring(5); // 去掉 "user_"
        int serviceIndex = withoutPrefix.indexOf("_service_");
        if (serviceIndex > 0) {
            return withoutPrefix.substring(0, serviceIndex);
        }

        throw new IllegalArgumentException("无法从客户端Key中提取用户ID: " + clientKey);
    }

    /**
     * 从用户客户端Key中提取服务ID
     */
    private String extractServiceIdFromClientKey(String clientKey) {
        if (!isUserClientKey(clientKey)) {
            throw new IllegalArgumentException("不是有效的用户客户端Key: " + clientKey);
        }

        // 格式：user_{userId}_service_{serviceId}
        int serviceIndex = clientKey.indexOf("_service_");
        if (serviceIndex > 0) {
            return clientKey.substring(serviceIndex + 9); // 去掉 "_service_"
        }

        throw new IllegalArgumentException("无法从客户端Key中提取服务ID: " + clientKey);
    }
}
