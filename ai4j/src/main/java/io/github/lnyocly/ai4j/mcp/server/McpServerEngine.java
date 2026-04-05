package io.github.lnyocly.ai4j.mcp.server;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.entity.McpError;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpPrompt;
import io.github.lnyocly.ai4j.mcp.entity.McpPromptResult;
import io.github.lnyocly.ai4j.mcp.entity.McpResource;
import io.github.lnyocly.ai4j.mcp.entity.McpResourceContent;
import io.github.lnyocly.ai4j.mcp.entity.McpResponse;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.util.McpPromptAdapter;
import io.github.lnyocly.ai4j.mcp.util.McpResourceAdapter;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务端公共协议处理引擎
 */
public class McpServerEngine {

    private static final Logger log = LoggerFactory.getLogger(McpServerEngine.class);
    private static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";

    private final String serverName;
    private final String serverVersion;
    private final List<String> supportedProtocolVersions;
    private final String defaultProtocolVersion;
    private final boolean initializationRequired;
    private final boolean pingEnabled;
    private final boolean toolsListChanged;

    public McpServerEngine(
            String serverName,
            String serverVersion,
            List<String> supportedProtocolVersions,
            String defaultProtocolVersion,
            boolean initializationRequired,
            boolean pingEnabled,
            boolean toolsListChanged) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.supportedProtocolVersions = supportedProtocolVersions != null
                ? new ArrayList<String>(supportedProtocolVersions)
                : new ArrayList<String>();
        this.defaultProtocolVersion = defaultProtocolVersion != null
                ? defaultProtocolVersion
                : DEFAULT_PROTOCOL_VERSION;
        this.initializationRequired = initializationRequired;
        this.pingEnabled = pingEnabled;
        this.toolsListChanged = toolsListChanged;
    }

    public McpMessage processMessage(McpMessage message, McpServerSessionState session) {
        if (message == null) {
            return createErrorResponse(null, -32600, "Invalid Request");
        }

        if (message.isRequest()) {
            String method = message.getMethod();

            if ("initialize".equals(method)) {
                return handleInitialize(message, session);
            }
            if ("tools/list".equals(method)) {
                return handleToolsList(message, session);
            }
            if ("tools/call".equals(method)) {
                return handleToolsCall(message, session);
            }
            if ("resources/list".equals(method)) {
                return handleResourcesList(message, session);
            }
            if ("resources/read".equals(method)) {
                return handleResourcesRead(message, session);
            }
            if ("prompts/list".equals(method)) {
                return handlePromptsList(message, session);
            }
            if ("prompts/get".equals(method)) {
                return handlePromptsGet(message, session);
            }
            if ("ping".equals(method) && pingEnabled) {
                return handlePing(message);
            }

            return createErrorResponse(message.getId(), -32601, "Method not found: " + method);
        }

        if (message.isNotification()) {
            handleNotification(message, session);
            return null;
        }

        return createErrorResponse(message.getId(), -32600, "Invalid Request");
    }

    private McpMessage handleInitialize(McpMessage message, McpServerSessionState session) {
        try {
            Map<String, Object> params = asMap(message.getParams());
            String requestedVersion = params != null ? stringValue(params.get("protocolVersion")) : null;
            String protocolVersion = resolveProtocolVersion(requestedVersion);
            Map<String, Object> capabilities = buildCapabilities();

            Map<String, Object> serverInfo = new HashMap<String, Object>();
            serverInfo.put("name", serverName);
            serverInfo.put("version", serverVersion);

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("protocolVersion", protocolVersion);
            result.put("capabilities", capabilities);
            result.put("serverInfo", serverInfo);

            if (session != null) {
                session.setInitialized(true);
                session.getCapabilities().clear();
                session.getCapabilities().putAll(capabilities);
            }

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            log.error("处理初始化请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private McpMessage handleToolsList(McpMessage message, McpServerSessionState session) {
        McpMessage initError = requireInitialization(message, session);
        if (initError != null) {
            return initError;
        }

        try {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("tools", convertToMcpToolDefinitions());

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            log.error("处理工具列表请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private McpMessage handleToolsCall(McpMessage message, McpServerSessionState session) {
        McpMessage initError = requireInitialization(message, session);
        if (initError != null) {
            return initError;
        }

        try {
            Map<String, Object> params = asMap(message.getParams());
            if (params == null) {
                return createErrorResponse(message.getId(), -32602, "Invalid params: object is required");
            }

            String toolName = stringValue(params.get("name"));
            if (toolName == null || toolName.isEmpty()) {
                return createErrorResponse(message.getId(), -32602, "Invalid params: name is required");
            }

            Object arguments = params.get("arguments");
            String result = ToolUtil.invoke(toolName, arguments != null ? JSON.toJSONString(arguments) : "{}");

            Map<String, Object> textContent = new HashMap<String, Object>();
            textContent.put("type", "text");
            textContent.put("text", result != null ? result : "");

            Map<String, Object> responseData = new HashMap<String, Object>();
            responseData.put("content", Arrays.asList(textContent));
            responseData.put("isError", false);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(responseData);
            return response;
        } catch (Exception e) {
            log.error("处理工具调用请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private McpMessage handleResourcesList(McpMessage message, McpServerSessionState session) {
        McpMessage initError = requireInitialization(message, session);
        if (initError != null) {
            return initError;
        }

        try {
            List<McpResource> resources = McpResourceAdapter.getAllMcpResources();

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("resources", resources);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            log.error("处理资源列表请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private McpMessage handleResourcesRead(McpMessage message, McpServerSessionState session) {
        McpMessage initError = requireInitialization(message, session);
        if (initError != null) {
            return initError;
        }

        try {
            Map<String, Object> params = asMap(message.getParams());
            String uri = params != null ? stringValue(params.get("uri")) : null;

            if (uri == null || uri.isEmpty()) {
                return createErrorResponse(message.getId(), -32602, "Invalid params: uri is required");
            }

            McpResourceContent resourceContent = McpResourceAdapter.readMcpResource(uri);

            Map<String, Object> content = new HashMap<String, Object>();
            content.put("uri", resourceContent.getUri());
            content.put("mimeType", resourceContent.getMimeType());

            Object contents = resourceContent.getContents();
            if (contents instanceof String) {
                content.put("text", contents);
            } else {
                content.put("text", JSON.toJSONString(contents));
            }

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("contents", Arrays.asList(content));

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            log.error("处理资源读取请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private McpMessage handlePromptsList(McpMessage message, McpServerSessionState session) {
        McpMessage initError = requireInitialization(message, session);
        if (initError != null) {
            return initError;
        }

        try {
            List<McpPrompt> prompts = McpPromptAdapter.getAllMcpPrompts();

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("prompts", prompts);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            log.error("处理提示词列表请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private McpMessage handlePromptsGet(McpMessage message, McpServerSessionState session) {
        McpMessage initError = requireInitialization(message, session);
        if (initError != null) {
            return initError;
        }

        try {
            Map<String, Object> params = asMap(message.getParams());
            String name = params != null ? stringValue(params.get("name")) : null;
            Map<String, Object> arguments = params != null ? asMap(params.get("arguments")) : null;

            if (name == null || name.isEmpty()) {
                return createErrorResponse(message.getId(), -32602, "Invalid params: name is required");
            }

            McpPromptResult promptResult = McpPromptAdapter.getMcpPrompt(name, arguments);

            Map<String, Object> content = new HashMap<String, Object>();
            content.put("type", "text");
            content.put("text", promptResult.getContent());

            Map<String, Object> userMessage = new HashMap<String, Object>();
            userMessage.put("role", "user");
            userMessage.put("content", content);

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("description", promptResult.getDescription());
            result.put("messages", Arrays.asList(userMessage));

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            log.error("处理提示词获取请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private McpMessage handlePing(McpMessage message) {
        try {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "pong");

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            log.error("处理 ping 请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private void handleNotification(McpMessage message, McpServerSessionState session) {
        if ("notifications/initialized".equals(message.getMethod()) && session != null) {
            session.setInitialized(true);
        }
    }

    private McpMessage requireInitialization(McpMessage message, McpServerSessionState session) {
        if (initializationRequired && (session == null || !session.isInitialized())) {
            return createErrorResponse(message != null ? message.getId() : null, -32002, "Server not initialized");
        }
        return null;
    }

    private String resolveProtocolVersion(String requestedVersion) {
        if (requestedVersion != null && supportedProtocolVersions.contains(requestedVersion)) {
            return requestedVersion;
        }
        if (!supportedProtocolVersions.isEmpty() && supportedProtocolVersions.contains(defaultProtocolVersion)) {
            return defaultProtocolVersion;
        }
        if (!supportedProtocolVersions.isEmpty()) {
            return supportedProtocolVersions.get(0);
        }
        return requestedVersion != null ? requestedVersion : defaultProtocolVersion;
    }

    private Map<String, Object> buildCapabilities() {
        Map<String, Object> capabilities = new HashMap<String, Object>();

        Map<String, Object> toolsCapability = new HashMap<String, Object>();
        if (toolsListChanged) {
            toolsCapability.put("listChanged", true);
        }
        capabilities.put("tools", toolsCapability);

        Map<String, Object> resourcesCapability = new HashMap<String, Object>();
        resourcesCapability.put("subscribe", true);
        resourcesCapability.put("listChanged", true);
        capabilities.put("resources", resourcesCapability);

        Map<String, Object> promptsCapability = new HashMap<String, Object>();
        promptsCapability.put("listChanged", true);
        capabilities.put("prompts", promptsCapability);

        return capabilities;
    }

    private McpResponse createErrorResponse(Object id, int code, String message) {
        McpError error = new McpError();
        error.setCode(code);
        error.setMessage(message);

        McpResponse response = new McpResponse();
        response.setId(id);
        response.setError(error);
        return response;
    }

    private List<McpToolDefinition> convertToMcpToolDefinitions() {
        List<McpToolDefinition> mcpTools = new ArrayList<McpToolDefinition>();

        try {
            List<Tool> tools = ToolUtil.getLocalMcpTools();
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

    private Map<String, Object> convertParametersToInputSchema(Tool.Function.Parameter parameters) {
        Map<String, Object> schema = new HashMap<String, Object>();

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

    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("MCP消息参数必须为对象");
        }

        Map<String, Object> result = new HashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
