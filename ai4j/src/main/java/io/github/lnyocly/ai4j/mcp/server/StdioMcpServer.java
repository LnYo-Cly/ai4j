package io.github.lnyocly.ai4j.mcp.server;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.entity.*;
import io.github.lnyocly.ai4j.mcp.util.McpResourceAdapter;
import io.github.lnyocly.ai4j.mcp.util.McpPromptAdapter;
import io.github.lnyocly.ai4j.mcp.entity.McpResourceContent;
import io.github.lnyocly.ai4j.mcp.entity.McpPromptResult;
import io.github.lnyocly.ai4j.mcp.util.McpToolAdapter;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.utils.ToolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stdio MCP服务器实现
 * 直接处理标准输入输出的MCP服务器，不使用传输层抽象
 *
 * @Author cly
 */
public class StdioMcpServer implements McpServer {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpServer.class);

    private final String serverName;
    private final String serverVersion;
    private final AtomicBoolean initialized;
    private final AtomicBoolean running;
    
    public StdioMcpServer(String serverName, String serverVersion) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.initialized = new AtomicBoolean(false);
        this.running = new AtomicBoolean(false);

        // 扫描并注册MCP工具
        McpToolAdapter.scanAndRegisterMcpTools();

        log.info("Stdio MCP服务器已创建: {} v{}", serverName, serverVersion);
    }
    
    /**
     * 启动MCP服务器
     */
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("启动Stdio MCP服务器: {} v{}", serverName, serverVersion);

                try {
                    // 直接监听stdin，处理输入
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String line;

                    log.info("Stdio MCP服务器启动成功，等待stdin输入...");

                    while (running.get() && (line = reader.readLine()) != null) {
                        try {
                            if (!line.trim().isEmpty()) {
                                McpMessage message = JSON.parseObject(line, McpMessage.class);
                                handleMessage(message);
                            }
                        } catch (Exception e) {
                            log.error("处理stdin消息失败: {}", line, e);
                            sendErrorResponse(null, e);
                        }
                    }

                } catch (Exception e) {
                    running.set(false);
                    log.error("启动Stdio MCP服务器失败", e);
                    throw new RuntimeException("启动Stdio MCP服务器失败", e);
                }
            }
        });
    }
    
    /**
     * 停止MCP服务器
     */
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(true, false)) {
                log.info("停止Stdio MCP服务器");
                initialized.set(false);
                log.info("Stdio MCP服务器已停止");
            }
        });
    }
    
    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 获取服务器信息
     */
    public String getServerInfo() {
        return String.format("%s v%s (stdio)", serverName, serverVersion);
    }

    /**
     * 获取服务器名称
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * 获取服务器版本
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * 处理MCP消息
     */
    private void handleMessage(McpMessage message) {
        try {
            log.debug("处理Stdio消息: {}", message);

            if (message.isRequest()) {
                handleRequest(message);
            } else if (message.isNotification()) {
                handleNotification(message);
            } else {
                log.warn("收到未知类型的消息: {}", message);
            }

        } catch (Exception e) {
            log.error("处理Stdio消息时发生错误", e);
            sendErrorResponse(message, e);
        }
    }
    
    /**
     * 处理请求消息
     */
    private void handleRequest(McpMessage message) {
        String method = message.getMethod();
        
        switch (method) {
            case "initialize":
                handleInitialize(message);
                break;
            case "tools/list":
                handleToolsList(message);
                break;
            case "tools/call":
                handleToolsCall(message);
                break;
            case "resources/list":
                handleResourcesList(message);
                break;
            case "resources/read":
                handleResourcesRead(message);
                break;
            case "prompts/list":
                handlePromptsList(message);
                break;
            case "prompts/get":
                handlePromptsGet(message);
                break;
            default:
                sendMethodNotFoundError(message);
                break;
        }
    }
    
    /**
     * 处理通知消息
     */
    private void handleNotification(McpMessage message) {
        String method = message.getMethod();
        log.debug("处理通知: {}", method);
        
        switch (method) {
            case "notifications/initialized":
                initialized.set(true);
                log.info("客户端初始化完成");
                break;
            default:
                log.debug("忽略未知通知: {}", method);
                break;
        }
    }
    
    /**
     * 处理初始化请求
     */
    private void handleInitialize(McpMessage message) {
        log.info("处理初始化请求");
        
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", serverName);
        serverInfo.put("version", serverVersion);
        result.put("serverInfo", serverInfo);
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", new HashMap<>());

        // 添加资源能力
        Map<String, Object> resourcesCapability = new HashMap<>();
        resourcesCapability.put("subscribe", true);
        resourcesCapability.put("listChanged", true);
        capabilities.put("resources", resourcesCapability);

        // 添加提示词能力
        Map<String, Object> promptsCapability = new HashMap<>();
        promptsCapability.put("listChanged", true);
        capabilities.put("prompts", promptsCapability);

        result.put("capabilities", capabilities);
        
        McpResponse response = new McpResponse();
        response.setId(message.getId());
        response.setResult(result);
        
        sendResponse(response);
    }
    
    /**
     * 处理工具列表请求
     */
    private void handleToolsList(McpMessage message) {
        log.debug("处理工具列表请求");

        try {
            List<McpToolDefinition> tools = convertToMcpToolDefinitions();
            
            Map<String, Object> result = new HashMap<>();
            result.put("tools", tools);
            
            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            
            sendResponse(response);
            
        } catch (Exception e) {
            log.error("获取工具列表失败", e);
            sendErrorResponse(message, e);
        }
    }
    
    /**
     * 处理工具调用请求
     */
    private void handleToolsCall(McpMessage message) {
        log.debug("处理工具调用请求");
        
        try {
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            if (params == null) {
                throw new IllegalArgumentException("工具调用参数不能为空");
            }
            
            String toolName = (String) params.get("name");
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
            
            if (toolName == null) {
                throw new IllegalArgumentException("工具名称不能为空");
            }
            
            // 调用工具
            String argumentsJson = arguments != null ? JSON.toJSONString(arguments) : "{}";
            String result = ToolUtil.invoke(toolName, argumentsJson);
            
            // 构建响应
            Map<String, Object> responseResult = new HashMap<>();
            Map<String, Object> contentItem = new HashMap<>();
            contentItem.put("type", "text");
            contentItem.put("text", result != null ? result.toString() : "null");
            responseResult.put("content", Arrays.asList(contentItem));
            
            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(responseResult);
            
            sendResponse(response);
            
        } catch (Exception e) {
            log.error("工具调用失败", e);
            sendErrorResponse(message, e);
        }
    }

    /**
     * 处理资源列表请求
     */
    private void handleResourcesList(McpMessage message) {
        log.debug("处理资源列表请求");

        try {
            List<io.github.lnyocly.ai4j.mcp.entity.McpResource> resources = McpResourceAdapter.getAllMcpResources();

            Map<String, Object> result = new HashMap<>();
            result.put("resources", resources);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);

            sendResponse(response);

        } catch (Exception e) {
            log.error("获取资源列表失败", e);
            sendErrorResponse(message, e);
        }
    }

    /**
     * 处理资源读取请求
     */
    private void handleResourcesRead(McpMessage message) {
        log.debug("处理资源读取请求");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            String uri = (String) params.get("uri");

            if (uri == null || uri.isEmpty()) {
                throw new IllegalArgumentException("资源URI不能为空");
            }

            log.info("读取资源: {}", uri);

            // 读取资源内容
            McpResourceContent resourceContent = McpResourceAdapter.readMcpResource(uri);

            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();

            Map<String, Object> content = new HashMap<>();
            content.put("uri", resourceContent.getUri());
            content.put("mimeType", resourceContent.getMimeType());

            // 根据内容类型设置text或blob
            Object contentData = resourceContent.getContents();
            if (contentData instanceof String) {
                content.put("text", contentData);
            } else {
                // 对于非字符串内容，转换为JSON字符串
                content.put("text", JSON.toJSONString(contentData));
            }

            contents.add(content);
            result.put("contents", contents);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);

            sendResponse(response);

        } catch (Exception e) {
            log.error("读取资源失败", e);
            sendErrorResponse(message, e);
        }
    }

    /**
     * 处理提示词列表请求
     */
    private void handlePromptsList(McpMessage message) {
        log.debug("处理提示词列表请求");

        try {
            List<io.github.lnyocly.ai4j.mcp.entity.McpPrompt> prompts = McpPromptAdapter.getAllMcpPrompts();

            Map<String, Object> result = new HashMap<>();
            result.put("prompts", prompts);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);

            sendResponse(response);

        } catch (Exception e) {
            log.error("获取提示词列表失败", e);
            sendErrorResponse(message, e);
        }
    }

    /**
     * 处理提示词获取请求
     */
    private void handlePromptsGet(McpMessage message) {
        log.debug("处理提示词获取请求");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            String name = (String) params.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("提示词名称不能为空");
            }

            log.info("获取提示词: {} 参数: {}", name, arguments);

            // 获取提示词内容
            McpPromptResult promptResult = McpPromptAdapter.getMcpPrompt(name, arguments);

            Map<String, Object> result = new HashMap<>();
            result.put("description", promptResult.getDescription());

            // 构建消息列表
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message1 = new HashMap<>();
            message1.put("role", "user");

            Map<String, Object> content = new HashMap<>();
            content.put("type", "text");
            content.put("text", promptResult.getContent());
            message1.put("content", content);

            messages.add(message1);
            result.put("messages", messages);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);

            sendResponse(response);

        } catch (Exception e) {
            log.error("获取提示词失败", e);
            sendErrorResponse(message, e);
        }
    }

    /**
     * 发送响应
     */
    private void sendResponse(McpResponse response) {
        try {
            // 直接输出到stdout
            String jsonResponse = JSON.toJSONString(response);
            System.out.println(jsonResponse);
            System.out.flush();
            log.debug("发送响应到stdout: {}", jsonResponse);
        } catch (Exception e) {
            log.error("发送响应失败", e);
        }
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(McpMessage originalMessage, Exception error) {
        try {
            McpResponse errorResponse = new McpResponse();
            if (originalMessage != null) {
                errorResponse.setId(originalMessage.getId());
            }

            McpError mcpError = new McpError();
            mcpError.setCode(-32603);
            mcpError.setMessage("Internal error: " + error.getMessage());
            errorResponse.setError(mcpError);

            sendResponse(errorResponse);
        } catch (Exception e) {
            log.error("发送错误响应失败", e);
        }
    }
    
    /**
     * 发送方法未找到错误
     */
    private void sendMethodNotFoundError(McpMessage message) {
        McpResponse errorResponse = new McpResponse();
        errorResponse.setId(message.getId());
        
        McpError mcpError = new McpError();
        mcpError.setCode(-32601);
        mcpError.setMessage("Method not found: " + message.getMethod());
        errorResponse.setError(mcpError);
        
        sendResponse(errorResponse);
    }

    /**
     * 转换Tool列表为McpToolDefinition列表
     */
    private List<McpToolDefinition> convertToMcpToolDefinitions() {
        List<McpToolDefinition> mcpTools = new ArrayList<>();

        try {
            // 获取所有本地MCP工具（空列表表示获取所有本地MCP工具）
            List<Tool> tools = ToolUtil.getAllTools(new ArrayList<>(), new ArrayList<>());
            for (Tool tool : tools) {
                if (tool.getFunction() != null) {
                    McpToolDefinition mcpTool = McpToolDefinition.builder()
                            .name(tool.getFunction().getName())
                            .description(tool.getFunction().getDescription())
                            .inputSchema(convertParametersToInputSchema(tool.getFunction().getParameters()))
                            .build();
                    mcpTools.add(mcpTool);
                }
            }
        } catch (Exception e) {
            log.error("转换工具列表失败", e);
        }

        return mcpTools;
    }

    /**
     * 转换Tool.Function.Parameter为输入Schema
     */
    private Map<String, Object> convertParametersToInputSchema(Tool.Function.Parameter parameters) {
        Map<String, Object> schema = new HashMap<>();

        if (parameters != null) {
            schema.put("type", parameters.getType());
            if (parameters.getProperties() != null) {
                schema.put("properties", parameters.getProperties());
            }
            if (parameters.getRequired() != null) {
                schema.put("required", parameters.getRequired());
            }
        }

        return schema;
    }
}
