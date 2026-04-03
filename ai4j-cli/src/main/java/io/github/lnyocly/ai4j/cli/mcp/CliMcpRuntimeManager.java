package io.github.lnyocly.ai4j.cli.mcp;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import io.github.lnyocly.ai4j.mcp.transport.McpTransportFactory;
import io.github.lnyocly.ai4j.mcp.transport.TransportConfig;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CliMcpRuntimeManager implements AutoCloseable {

    public static final String STATE_CONNECTED = "connected";
    public static final String STATE_DISABLED = "disabled";
    public static final String STATE_PAUSED = "paused";
    public static final String STATE_ERROR = "error";
    public static final String STATE_MISSING = "missing";

    private static final Set<String> RESERVED_TOOL_NAMES = new LinkedHashSet<String>(Arrays.asList(
            CodingToolNames.BASH,
            CodingToolNames.READ_FILE,
            CodingToolNames.WRITE_FILE,
            CodingToolNames.APPLY_PATCH
    ));

    private final CliResolvedMcpConfig resolvedConfig;
    private final ClientFactory clientFactory;
    private final Map<String, CliMcpConnectionHandle> handlesByServerName = new LinkedHashMap<String, CliMcpConnectionHandle>();
    private final Map<String, CliMcpConnectionHandle> handlesByToolName = new LinkedHashMap<String, CliMcpConnectionHandle>();
    private List<CliMcpStatusSnapshot> statuses = Collections.emptyList();
    private AgentToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;

    public static CliMcpRuntimeManager initialize(Path workspaceRoot, Collection<String> pausedServerNames) {
        CliResolvedMcpConfig resolvedConfig = new CliMcpConfigManager(workspaceRoot).resolve(pausedServerNames);
        return initialize(resolvedConfig);
    }

    public static CliMcpRuntimeManager initialize(CliResolvedMcpConfig resolvedConfig) {
        if (!hasRelevantConfiguration(resolvedConfig)) {
            return null;
        }
        CliMcpRuntimeManager runtimeManager = new CliMcpRuntimeManager(resolvedConfig);
        runtimeManager.start();
        return runtimeManager;
    }

    CliMcpRuntimeManager(CliResolvedMcpConfig resolvedConfig) {
        this(resolvedConfig, new DefaultClientFactory());
    }

    CliMcpRuntimeManager(CliResolvedMcpConfig resolvedConfig, ClientFactory clientFactory) {
        this.resolvedConfig = resolvedConfig == null
                ? new CliResolvedMcpConfig(null, null, null, null)
                : resolvedConfig;
        this.clientFactory = clientFactory == null ? new DefaultClientFactory() : clientFactory;
    }

    public void start() {
        close();
        List<CliMcpStatusSnapshot> nextStatuses = new ArrayList<CliMcpStatusSnapshot>();
        Map<String, String> claimedToolOwners = new LinkedHashMap<String, String>();

        for (CliResolvedMcpServer server : resolvedConfig.getServers().values()) {
            CliMcpConnectionHandle handle = new CliMcpConnectionHandle(server);
            handlesByServerName.put(server.getName(), handle);

            if (!server.isWorkspaceEnabled()) {
                handle.setState(STATE_DISABLED);
                nextStatuses.add(handle.toStatusSnapshot());
                continue;
            }
            if (server.isSessionPaused()) {
                handle.setState(STATE_PAUSED);
                nextStatuses.add(handle.toStatusSnapshot());
                continue;
            }
            if (!server.isValid()) {
                handle.setState(STATE_ERROR);
                handle.setErrorSummary(server.getValidationError());
                nextStatuses.add(handle.toStatusSnapshot());
                continue;
            }

            try {
                ClientSession clientSession = clientFactory.create(server);
                handle.setClientSession(clientSession);
                clientSession.connect();

                List<McpToolDefinition> toolDefinitions = clientSession.listTools();
                validateToolNames(server, toolDefinitions, claimedToolOwners);
                List<Tool> tools = convertTools(toolDefinitions);
                handle.setTools(tools);
                handle.setState(STATE_CONNECTED);

                for (Tool tool : tools) {
                    if (tool == null || tool.getFunction() == null || isBlank(tool.getFunction().getName())) {
                        continue;
                    }
                    String toolName = tool.getFunction().getName().trim();
                    claimedToolOwners.put(toolName.toLowerCase(Locale.ROOT), server.getName());
                    handlesByToolName.put(toolName, handle);
                }
            } catch (Exception ex) {
                handle.setState(STATE_ERROR);
                handle.setErrorSummary(safeMessage(ex));
                handle.closeQuietly();
            }
            nextStatuses.add(handle.toStatusSnapshot());
        }

        for (String missing : resolvedConfig.getUnknownEnabledServerNames()) {
            nextStatuses.add(new CliMcpStatusSnapshot(missing, null, STATE_MISSING, 0,
                    "workspace references undefined MCP server", true, false));
        }

        rebuildToolView();
        statuses = Collections.unmodifiableList(nextStatuses);
    }

    public AgentToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    public List<CliMcpStatusSnapshot> getStatuses() {
        return statuses;
    }

    public List<String> buildStartupWarnings() {
        if (statuses == null || statuses.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> warnings = new ArrayList<String>();
        for (CliMcpStatusSnapshot status : statuses) {
            if (status == null) {
                continue;
            }
            if (!STATE_ERROR.equals(status.getState()) && !STATE_MISSING.equals(status.getState())) {
                continue;
            }
            warnings.add("MCP unavailable: "
                    + firstNonBlank(status.getServerName(), "(unknown)")
                    + " ("
                    + firstNonBlank(status.getErrorSummary(), "unknown MCP error")
                    + ")");
        }
        return warnings.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(warnings);
    }

    public boolean hasStatuses() {
        return statuses != null && !statuses.isEmpty();
    }

    public String findServerNameByToolName(String toolName) {
        if (isBlank(toolName)) {
            return null;
        }
        CliMcpConnectionHandle handle = handlesByToolName.get(toolName.trim());
        return handle == null ? null : handle.getServerName();
    }

    @Override
    public void close() {
        for (CliMcpConnectionHandle handle : handlesByServerName.values()) {
            if (handle != null) {
                handle.closeQuietly();
            }
        }
        handlesByServerName.clear();
        handlesByToolName.clear();
        toolRegistry = null;
        toolExecutor = null;
        if (statuses == null) {
            statuses = Collections.emptyList();
        }
    }

    private void rebuildToolView() {
        List<Object> tools = new ArrayList<Object>();
        for (CliMcpConnectionHandle handle : handlesByServerName.values()) {
            if (!STATE_CONNECTED.equals(handle.getState()) || handle.getTools().isEmpty()) {
                continue;
            }
            tools.addAll(handle.getTools());
        }
        if (tools.isEmpty()) {
            toolRegistry = null;
            toolExecutor = null;
            return;
        }
        toolRegistry = new StaticToolRegistry(tools);
        toolExecutor = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) throws Exception {
                if (call == null || isBlank(call.getName())) {
                    throw new IllegalArgumentException("MCP tool call is missing tool name");
                }
                CliMcpConnectionHandle handle = handlesByToolName.get(call.getName());
                if (handle == null || handle.getClientSession() == null) {
                    throw new IllegalArgumentException("Unknown MCP tool: " + call.getName());
                }
                Object arguments = parseArguments(call.getArguments());
                return handle.getClientSession().callTool(call.getName(), arguments);
            }
        };
    }

    private void validateToolNames(CliResolvedMcpServer server,
                                   List<McpToolDefinition> toolDefinitions,
                                   Map<String, String> claimedToolOwners) {
        Set<String> localToolNames = new LinkedHashSet<String>();
        if (toolDefinitions == null) {
            return;
        }
        for (McpToolDefinition toolDefinition : toolDefinitions) {
            String toolName = toolDefinition == null ? null : normalizeToolName(toolDefinition.getName());
            if (isBlank(toolName)) {
                throw new IllegalStateException("MCP server " + server.getName() + " returned a tool without a name");
            }
            String normalizedToolName = toolName.toLowerCase(Locale.ROOT);
            if (RESERVED_TOOL_NAMES.contains(normalizedToolName)) {
                throw new IllegalStateException("MCP tool name conflicts with built-in tool: " + toolName);
            }
            if (!localToolNames.add(normalizedToolName)) {
                throw new IllegalStateException("MCP server " + server.getName() + " returned duplicate tool: " + toolName);
            }
            String existingOwner = claimedToolOwners.get(normalizedToolName);
            if (!isBlank(existingOwner)) {
                throw new IllegalStateException("MCP tool name conflict: " + toolName + " already provided by " + existingOwner);
            }
        }
    }

    private List<Tool> convertTools(List<McpToolDefinition> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Tool> tools = new ArrayList<Tool>();
        for (McpToolDefinition toolDefinition : toolDefinitions) {
            if (toolDefinition == null || isBlank(toolDefinition.getName())) {
                continue;
            }
            Tool tool = new Tool();
            tool.setType("function");
            Tool.Function function = new Tool.Function();
            function.setName(normalizeToolName(toolDefinition.getName()));
            function.setDescription(toolDefinition.getDescription());
            function.setParameters(convertInputSchema(toolDefinition.getInputSchema()));
            tool.setFunction(function);
            tools.add(tool);
        }
        return tools;
    }

    private Tool.Function.Parameter convertInputSchema(Map<String, Object> inputSchema) {
        Tool.Function.Parameter parameter = new Tool.Function.Parameter();
        parameter.setType("object");
        if (inputSchema == null || inputSchema.isEmpty()) {
            return parameter;
        }

        Object properties = inputSchema.get("properties");
        if (properties instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propertyMap = (Map<String, Object>) properties;
            Map<String, Tool.Function.Property> convertedProperties = new LinkedHashMap<String, Tool.Function.Property>();
            for (Map.Entry<String, Object> entry : propertyMap.entrySet()) {
                if (isBlank(entry.getKey()) || !(entry.getValue() instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> rawProperty = (Map<String, Object>) entry.getValue();
                convertedProperties.put(entry.getKey(), convertProperty(rawProperty));
            }
            parameter.setProperties(convertedProperties);
        }

        Object required = inputSchema.get("required");
        if (required instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> requiredNames = (List<String>) required;
            parameter.setRequired(requiredNames);
        }
        return parameter;
    }

    private Tool.Function.Property convertProperty(Map<String, Object> rawProperty) {
        Tool.Function.Property property = new Tool.Function.Property();
        property.setType(asString(rawProperty.get("type")));
        property.setDescription(asString(rawProperty.get("description")));

        Object enumValues = rawProperty.get("enum");
        if (enumValues instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) enumValues;
            property.setEnumValues(values);
        }

        Object items = rawProperty.get("items");
        if (items instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawItems = (Map<String, Object>) items;
            property.setItems(convertProperty(rawItems));
        }
        return property;
    }

    private Object parseArguments(String rawArguments) {
        if (isBlank(rawArguments)) {
            return Collections.emptyMap();
        }
        try {
            return JSON.parseObject(rawArguments, Map.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid MCP tool arguments: " + safeMessage(ex), ex);
        }
    }

    private String normalizeToolName(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeMessage(Throwable throwable) {
        String message = null;
        Throwable last = throwable;
        Throwable current = throwable;
        while (current != null) {
            if (!isBlank(current.getMessage())) {
                message = current.getMessage().trim();
            }
            last = current;
            current = current.getCause();
        }
        return isBlank(message)
                ? (last == null ? "unknown MCP error" : last.getClass().getSimpleName())
                : message;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasRelevantConfiguration(CliResolvedMcpConfig resolvedConfig) {
        if (resolvedConfig == null) {
            return false;
        }
        if (!resolvedConfig.getServers().isEmpty()) {
            return true;
        }
        return !resolvedConfig.getUnknownEnabledServerNames().isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    interface ClientFactory {

        ClientSession create(CliResolvedMcpServer server) throws Exception;
    }

    interface ClientSession extends AutoCloseable {

        void connect() throws Exception;

        List<McpToolDefinition> listTools() throws Exception;

        String callTool(String toolName, Object arguments) throws Exception;

        @Override
        void close() throws Exception;
    }

    private static final class DefaultClientFactory implements ClientFactory {

        @Override
        public ClientSession create(CliResolvedMcpServer server) throws Exception {
            CliMcpServerDefinition definition = server == null ? null : server.getDefinition();
            if (definition == null) {
                throw new IllegalArgumentException("missing MCP definition");
            }

            TransportConfig config = new TransportConfig();
            config.setType(definition.getType());
            config.setUrl(definition.getUrl());
            config.setCommand(definition.getCommand());
            config.setArgs(definition.getArgs());
            config.setEnv(definition.getEnv());
            config.setHeaders(definition.getHeaders());

            McpTransport transport = McpTransportFactory.createTransport(definition.getType(), config);
            McpClient client = new McpClient("ai4j-cli-" + server.getName(), "2.0.0", transport, false);
            return new McpClientSessionAdapter(client);
        }
    }

    private static final class McpClientSessionAdapter implements ClientSession {

        private final McpClient delegate;

        private McpClientSessionAdapter(McpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public void connect() {
            delegate.connect().join();
        }

        @Override
        public List<McpToolDefinition> listTools() {
            return delegate.getAvailableTools().join();
        }

        @Override
        public String callTool(String toolName, Object arguments) {
            return delegate.callTool(toolName, arguments).join();
        }

        @Override
        public void close() {
            delegate.disconnect().join();
        }
    }
}

