package io.github.lnyocly.ai4j.cli.acp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.cli.ApprovalMode;
import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpServerDefinition;
import io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpConfig;
import io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpServer;
import io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager;
import io.github.lnyocly.ai4j.cli.provider.CliProviderProfile;
import io.github.lnyocly.ai4j.cli.provider.CliProvidersConfig;
import io.github.lnyocly.ai4j.cli.provider.CliResolvedProviderConfig;
import io.github.lnyocly.ai4j.cli.runtime.HeadlessCodingSessionRuntime;
import io.github.lnyocly.ai4j.cli.runtime.HeadlessTurnObserver;
import io.github.lnyocly.ai4j.cli.runtime.CodingTaskSessionEventBridge;
import io.github.lnyocly.ai4j.cli.session.CodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.DefaultCodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.FileCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.FileSessionEventStore;
import io.github.lnyocly.ai4j.cli.session.InMemoryCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.InMemorySessionEventStore;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.coding.runtime.CodingRuntime;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import io.github.lnyocly.ai4j.service.PlatformType;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class AcpJsonRpcServer implements Closeable {

    interface AgentFactoryProvider {

        CodingCliAgentFactory create(CodeCommandOptions options,
                                     AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                                     CliResolvedMcpConfig resolvedMcpConfig);
    }

    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_SESSION_NEW = "session/new";
    private static final String METHOD_SESSION_LOAD = "session/load";
    private static final String METHOD_SESSION_LIST = "session/list";
    private static final String METHOD_SESSION_PROMPT = "session/prompt";
    private static final String METHOD_SESSION_SET_MODE = "session/set_mode";
    private static final String METHOD_SESSION_SET_CONFIG_OPTION = "session/set_config_option";
    private static final String METHOD_SESSION_CANCEL = "session/cancel";
    private static final String METHOD_SESSION_UPDATE = "session/update";
    private static final String METHOD_SESSION_REQUEST_PERMISSION = "session/request_permission";

    private final InputStream inputStream;
    private final PrintWriter stdout;
    private final PrintWriter stderr;
    private final CodeCommandOptions baseOptions;
    private final AgentFactoryProvider agentFactoryProvider;
    private final Object writeLock = new Object();
    private final AtomicLong outboundRequestIds = new AtomicLong(1L);
    private final ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService promptExecutor = Executors.newCachedThreadPool();
    private final ConcurrentMap<String, SessionHandle> sessions = new ConcurrentHashMap<String, SessionHandle>();
    private final ConcurrentMap<String, CompletableFuture<JSONObject>> pendingClientResponses = new ConcurrentHashMap<String, CompletableFuture<JSONObject>>();

    public AcpJsonRpcServer(InputStream inputStream,
                            OutputStream outputStream,
                            OutputStream errorStream,
                            CodeCommandOptions baseOptions) {
        this(inputStream, outputStream, errorStream, baseOptions, new AgentFactoryProvider() {
            @Override
            public CodingCliAgentFactory create(CodeCommandOptions options,
                                                AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                                                CliResolvedMcpConfig resolvedMcpConfig) {
                return new AcpCodingCliAgentFactory(permissionGateway, resolvedMcpConfig);
            }
        });
    }

    AcpJsonRpcServer(InputStream inputStream,
                     OutputStream outputStream,
                     OutputStream errorStream,
                     CodeCommandOptions baseOptions,
                     AgentFactoryProvider agentFactoryProvider) {
        this.inputStream = inputStream;
        this.stdout = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);
        this.stderr = new PrintWriter(new OutputStreamWriter(errorStream, StandardCharsets.UTF_8), true);
        this.baseOptions = baseOptions;
        this.agentFactoryProvider = agentFactoryProvider;
    }

    public int run() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line == null ? null : line.trim();
                if (trimmed == null || trimmed.isEmpty()) {
                    continue;
                }
                JSONObject message;
                try {
                    message = JSON.parseObject(trimmed);
                } catch (Exception ex) {
                    logError("Invalid ACP JSON message: " + safeMessage(ex));
                    continue;
                }
                if (message != null) {
                    handleMessage(message);
                }
            }
            return 0;
        } catch (IOException ex) {
            logError("ACP server failed: " + safeMessage(ex));
            return 1;
        } finally {
            awaitPendingWork();
            close();
        }
    }

    private void handleMessage(final JSONObject message) {
        if (message.containsKey("method")) {
            final String method = message.getString("method");
            final Object id = message.get("id");
            final JSONObject params = message.getJSONObject("params");
            requestExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (id == null) {
                        handleNotification(method, params);
                    } else {
                        handleRequest(id, method, params);
                    }
                }
            });
            return;
        }

        if (message.containsKey("id")) {
            CompletableFuture<JSONObject> future = pendingClientResponses.remove(requestIdKey(message.get("id")));
            if (future != null) {
                future.complete(message);
            }
        }
    }

    private void handleRequest(Object id, String method, JSONObject params) {
        try {
            if (METHOD_INITIALIZE.equals(method)) {
                sendResponse(id, buildInitializeResponse(params));
                return;
            }
            if (METHOD_SESSION_NEW.equals(method)) {
                SessionHandle handle = createSession(params, false);
                sendResponse(id, handle.buildSessionOpenResult());
                handle.sendAvailableCommandsUpdate();
                return;
            }
            if (METHOD_SESSION_LOAD.equals(method)) {
                SessionHandle handle = createSession(params, true);
                handle.replayHistory();
                sendResponse(id, handle.buildSessionOpenResult());
                handle.sendAvailableCommandsUpdate();
                return;
            }
            if (METHOD_SESSION_LIST.equals(method)) {
                sendResponse(id, newMap("sessions", listSessions(params)));
                return;
            }
            if (METHOD_SESSION_PROMPT.equals(method)) {
                promptSession(id, params);
                return;
            }
            if (METHOD_SESSION_SET_MODE.equals(method)) {
                sendResponse(id, setSessionMode(params));
                return;
            }
            if (METHOD_SESSION_SET_CONFIG_OPTION.equals(method)) {
                sendResponse(id, setSessionConfigOption(params));
                return;
            }
            if (METHOD_SESSION_CANCEL.equals(method)) {
                cancelSession(params);
                sendResponse(id, Collections.<String, Object>emptyMap());
                return;
            }
            sendError(id, -32601, "Method not found: " + method);
        } catch (Exception ex) {
            sendError(id, -32000, safeMessage(ex));
        }
    }

    private void handleNotification(String method, JSONObject params) {
        try {
            if (METHOD_SESSION_CANCEL.equals(method)) {
                cancelSession(params);
                return;
            }
            logError("Ignoring unsupported ACP notification: " + method);
        } catch (Exception ex) {
            logError("Failed to handle ACP notification " + method + ": " + safeMessage(ex));
        }
    }

    private Map<String, Object> buildInitializeResponse(JSONObject params) {
        int protocolVersion = 1;
        if (params != null && params.getIntValue("protocolVersion") > 0) {
            protocolVersion = params.getIntValue("protocolVersion");
        }
        return newMap(
                "protocolVersion", protocolVersion,
                "agentInfo", newMap(
                        "name", "ai4j-cli",
                        "version", "2.0.0"
                ),
                "agentCapabilities", newMap(
                        "loadSession", Boolean.TRUE,
                        "mcpCapabilities", newMap(
                                "http", Boolean.TRUE,
                                "sse", Boolean.TRUE
                        ),
                        "promptCapabilities", newMap(
                                "audio", Boolean.FALSE,
                                "embeddedContext", Boolean.FALSE,
                                "image", Boolean.FALSE
                        ),
                        "sessionCapabilities", newMap(
                                "list", new LinkedHashMap<String, Object>()
                        )
                ),
                "authMethods", Collections.emptyList()
        );
    }

    private void sendResponse(Object id, Map<String, Object> result) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("result", result == null ? Collections.emptyMap() : result);
        writeMessage(payload);
    }

    private void sendError(Object id, int code, String message) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("error", newMap(
                "code", code,
                "message", firstNonBlank(message, "unknown ACP error")
        ));
        writeMessage(payload);
    }

    private void sendNotification(String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        payload.put("params", params == null ? Collections.emptyMap() : params);
        writeMessage(payload);
    }

    private void sendRequest(String id, String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("method", method);
        payload.put("params", params == null ? Collections.emptyMap() : params);
        writeMessage(payload);
    }

    private void writeMessage(Map<String, Object> payload) {
        synchronized (writeLock) {
            stdout.println(JSON.toJSONString(payload));
            stdout.flush();
        }
    }

    private void sendSessionUpdate(String sessionId, Map<String, Object> update) {
        sendNotification(METHOD_SESSION_UPDATE, newMap(
                "sessionId", sessionId,
                "update", update
        ));
    }

    private void sendAvailableCommandsUpdate(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        sendSessionUpdate(sessionId, newMap(
                "sessionUpdate", "available_commands_update",
                "availableCommands", AcpSlashCommandSupport.availableCommands()
        ));
    }

    private SessionHandle requireSession(String sessionId) {
        SessionHandle handle = sessions.get(sessionId);
        if (handle == null) {
            throw new IllegalArgumentException("Unknown ACP session: " + sessionId);
        }
        return handle;
    }

    private void logError(String message) {
        synchronized (writeLock) {
            stderr.println(firstNonBlank(message, "unknown ACP error"));
            stderr.flush();
        }
    }

    private SessionHandle createSession(JSONObject params, boolean loadExisting) throws Exception {
        String cwd = requireAbsolutePath(params == null ? null : params.getString("cwd"));
        String requestedSessionId = params == null ? null : trimToNull(params.getString("sessionId"));
        CodeCommandOptions sessionOptions = resolveSessionOptions(cwd, requestedSessionId, loadExisting ? requestedSessionId : null);
        CliResolvedMcpConfig resolvedMcpConfig = resolveMcpConfig(params == null ? null : params.getJSONArray("mcpServers"));
        final AtomicReference<String> permissionSessionIdRef = new AtomicReference<String>(requestedSessionId);
        AcpToolApprovalDecorator.PermissionGateway permissionGateway = new AcpToolApprovalDecorator.PermissionGateway() {
            @Override
            public AcpToolApprovalDecorator.PermissionDecision requestApproval(String toolName,
                                                                              AgentToolCall call,
                                                                              Map<String, Object> rawInput) throws Exception {
                return requestPermission(permissionSessionIdRef.get(), toolName, call, rawInput);
            }
        };
        CodingCliAgentFactory factory = agentFactoryProvider.create(sessionOptions, permissionGateway, resolvedMcpConfig);
        CodingCliAgentFactory.PreparedCodingAgent prepared = factory.prepare(sessionOptions, null, null, Collections.<String>emptySet());
        logMcpWarnings(prepared.getMcpRuntimeManager());
        CodingSessionManager sessionManager = createSessionManager(sessionOptions);
        ManagedCodingSession session = loadExisting
                ? sessionManager.resume(prepared.getAgent(), prepared.getProtocol(), sessionOptions, requestedSessionId)
                : sessionManager.create(prepared.getAgent(), prepared.getProtocol(), sessionOptions);
        permissionSessionIdRef.set(session.getSessionId());
        SessionHandle handle = new SessionHandle(sessionOptions, sessionManager, factory, permissionGateway, prepared, session, resolvedMcpConfig);
        sessions.put(session.getSessionId(), handle);
        if (!isBlank(requestedSessionId) && !session.getSessionId().equals(requestedSessionId)) {
            sessions.put(requestedSessionId, handle);
        }
        return handle;
    }

    private void promptSession(final Object requestId, JSONObject params) throws Exception {
        String sessionId = requiredString(params, "sessionId");
        final SessionHandle handle = requireSession(sessionId);
        final String input = flattenPrompt(requiredArray(params, "prompt"));
        handle.startPrompt(new Runnable() {
            @Override
            public void run() {
                HeadlessCodingSessionRuntime.PromptControl promptControl = handle.beginPrompt();
                try {
                    HeadlessCodingSessionRuntime.PromptResult result = AcpSlashCommandSupport.supports(input)
                            ? handle.runSlashCommand(input, promptControl)
                            : handle.runPrompt(input, promptControl);
                    sendResponse(requestId, newMap("stopReason", result.getStopReason()));
                } catch (Exception ex) {
                    sendError(requestId, -32000, safeMessage(ex));
                }
            }
        });
    }

    private Map<String, Object> setSessionMode(JSONObject params) throws Exception {
        String sessionId = requiredString(params, "sessionId");
        String modeId = requiredString(params, "modeId");
        SessionHandle handle = requireSession(sessionId);
        handle.setMode(modeId);
        return Collections.<String, Object>emptyMap();
    }

    private Map<String, Object> setSessionConfigOption(JSONObject params) throws Exception {
        String sessionId = requiredString(params, "sessionId");
        String configId = requiredString(params, "configId");
        Object valueObject = params == null ? null : params.get("value");
        if (valueObject == null) {
            throw new IllegalArgumentException("Missing required field: value");
        }
        String value = String.valueOf(valueObject);
        SessionHandle handle = requireSession(sessionId);
        return newMap(
                "configOptions", handle.setConfigOption(configId, value)
        );
    }

    private void cancelSession(JSONObject params) {
        if (params == null) {
            return;
        }
        String sessionId = trimToNull(params.getString("sessionId"));
        if (sessionId == null) {
            return;
        }
        SessionHandle handle = sessions.get(sessionId);
        if (handle != null) {
            handle.cancel();
        }
        for (CompletableFuture<JSONObject> future : pendingClientResponses.values()) {
            future.complete(buildCancelledPermissionResponse());
        }
    }

    private List<Map<String, Object>> listSessions(JSONObject params) throws Exception {
        String cwd = requireAbsolutePath(params == null ? null : params.getString("cwd"));
        CodingSessionManager sessionManager = createSessionManager(resolveSessionOptions(cwd, null, null));
        List<CodingSessionDescriptor> descriptors = sessionManager.list();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (CodingSessionDescriptor descriptor : descriptors) {
            result.add(newMap(
                    "sessionId", descriptor.getSessionId(),
                    "title", firstNonBlank(descriptor.getSummary(), descriptor.getSessionId()),
                    "createdAt", descriptor.getCreatedAtEpochMs(),
                    "updatedAt", descriptor.getUpdatedAtEpochMs()
            ));
        }
        return result;
    }

    private AcpToolApprovalDecorator.PermissionDecision requestPermission(String sessionId,
                                                                          String toolName,
                                                                          AgentToolCall call,
                                                                          Map<String, Object> rawInput) throws Exception {
        String requestId = String.valueOf(outboundRequestIds.getAndIncrement());
        CompletableFuture<JSONObject> future = new CompletableFuture<JSONObject>();
        pendingClientResponses.put(requestIdKey(requestId), future);
        sendRequest(requestId, METHOD_SESSION_REQUEST_PERMISSION, newMap(
                "sessionId", sessionId,
                "toolCall", buildPermissionToolCall(toolName, call, rawInput),
                "options", buildPermissionOptions()
        ));
        JSONObject response = future.get();
        JSONObject result = response == null ? null : response.getJSONObject("result");
        JSONObject outcome = result == null ? null : result.getJSONObject("outcome");
        if (outcome == null) {
            return new AcpToolApprovalDecorator.PermissionDecision(false, null);
        }
        String kind = outcome.getString("outcome");
        if ("selected".equals(kind)) {
            String optionId = outcome.getString("optionId");
            boolean approved = "allow_once".equals(optionId) || "allow_always".equals(optionId);
            return new AcpToolApprovalDecorator.PermissionDecision(approved, optionId);
        }
        return new AcpToolApprovalDecorator.PermissionDecision(false, "cancelled");
    }

    private Map<String, Object> buildPermissionToolCall(String toolName,
                                                        AgentToolCall call,
                                                        Map<String, Object> rawInput) {
        return newMap(
                "toolCallId", call == null ? UUID.randomUUID().toString() : firstNonBlank(call.getCallId(), UUID.randomUUID().toString()),
                "kind", mapToolKind(toolName),
                "title", firstNonBlank(toolName, call == null ? null : call.getName(), "tool"),
                "rawInput", rawInput
        );
    }

    private List<Map<String, Object>> buildPermissionOptions() {
        return Arrays.asList(
                newMap("optionId", "allow_once", "name", "Allow once", "kind", "allow_once"),
                newMap("optionId", "allow_always", "name", "Always allow", "kind", "allow_always"),
                newMap("optionId", "reject_once", "name", "Reject once", "kind", "reject_once"),
                newMap("optionId", "reject_always", "name", "Always reject", "kind", "reject_always")
        );
    }

    private JSONObject buildCancelledPermissionResponse() {
        JSONObject response = new JSONObject();
        JSONObject result = new JSONObject();
        JSONObject outcome = new JSONObject();
        outcome.put("outcome", "cancelled");
        result.put("outcome", outcome);
        response.put("result", result);
        return response;
    }

    private void logMcpWarnings(CliMcpRuntimeManager runtimeManager) {
        if (runtimeManager == null) {
            return;
        }
        for (String warning : runtimeManager.buildStartupWarnings()) {
            logError("Warning: " + warning);
        }
    }

    private CliResolvedMcpConfig resolveMcpConfig(JSONArray mcpServers) {
        if (mcpServers == null || mcpServers.isEmpty()) {
            return null;
        }
        Map<String, CliResolvedMcpServer> servers = new LinkedHashMap<String, CliResolvedMcpServer>();
        List<String> enabled = new ArrayList<String>();
        for (int i = 0; i < mcpServers.size(); i++) {
            JSONObject server = mcpServers.getJSONObject(i);
            if (server == null) {
                continue;
            }
            String name = firstNonBlank(trimToNull(server.getString("name")), "mcp-" + (i + 1));
            CliMcpServerDefinition definition = CliMcpServerDefinition.builder()
                    .type(normalizeTransportType(server.getString("type"), server.getString("command")))
                    .url(trimToNull(server.getString("url")))
                    .command(trimToNull(server.getString("command")))
                    .args(toStringList(server.getJSONArray("args")))
                    .env(toStringMap(server.getJSONObject("env")))
                    .cwd(trimToNull(server.getString("cwd")))
                    .headers(toStringMap(server.getJSONObject("headers")))
                    .build();
            String validationError = validateDefinition(definition);
            servers.put(name, new CliResolvedMcpServer(
                    name,
                    definition.getType(),
                    true,
                    false,
                    validationError == null,
                    validationError,
                    definition
            ));
            enabled.add(name);
        }
        return new CliResolvedMcpConfig(servers, enabled, Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    private CodeCommandOptions resolveSessionOptions(String workspace, String sessionId, String resumeSessionId) {
        String resolvedWorkspace = firstNonBlank(workspace, baseOptions == null ? null : baseOptions.getWorkspace(), Paths.get(".").toAbsolutePath().normalize().toString());
        Path workspacePath = Paths.get(resolvedWorkspace).toAbsolutePath().normalize();
        String defaultBaseStoreDir = baseOptions == null || isBlank(baseOptions.getWorkspace())
                ? null
                : Paths.get(baseOptions.getWorkspace()).resolve(".ai4j").resolve("sessions").toAbsolutePath().normalize().toString();
        String configuredStoreDir = baseOptions == null ? null : baseOptions.getSessionStoreDir();
        String resolvedStoreDir = configuredStoreDir;
        if (isBlank(resolvedStoreDir) || resolvedStoreDir.equals(defaultBaseStoreDir)) {
            resolvedStoreDir = workspacePath.resolve(".ai4j").resolve("sessions").toString();
        }
        if (baseOptions == null) {
            throw new IllegalStateException("ACP base options are required");
        }
        return baseOptions.withSessionContext(workspacePath.toString(), sessionId, resumeSessionId, resolvedStoreDir);
    }

    private CodingSessionManager createSessionManager(CodeCommandOptions options) {
        if (options.isNoSession()) {
            Path directory = Paths.get(options.getWorkspace()).resolve(".ai4j").resolve("memory-sessions");
            return new DefaultCodingSessionManager(
                    new InMemoryCodingSessionStore(directory),
                    new InMemorySessionEventStore()
            );
        }
        Path sessionDirectory = Paths.get(options.getSessionStoreDir());
        return new DefaultCodingSessionManager(
                new FileCodingSessionStore(sessionDirectory),
                new FileSessionEventStore(sessionDirectory.resolve("events"))
        );
    }

    private String flattenPrompt(JSONArray blocks) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            JSONObject block = blocks.getJSONObject(i);
            if (block == null) {
                continue;
            }
            String type = normalizeType(block.getString("type"));
            if ("text".equals(type)) {
                appendBlock(builder, block.getString("text"));
                continue;
            }
            if ("resource_link".equals(type) || "resource".equals(type)) {
                JSONObject resource = "resource".equals(type) ? block.getJSONObject("resource") : block;
                if (resource != null) {
                    appendBlock(builder, "[Resource] " + firstNonBlank(resource.getString("name"), resource.getString("title"), resource.getString("uri")));
                    appendBlock(builder, resource.getString("text"));
                }
            }
        }
        return builder.toString().trim();
    }

    private void appendBlock(StringBuilder builder, String text) {
        if (isBlank(text)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(text);
    }

    private String normalizeType(String type) {
        return type == null ? null : type.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private String mapToolKind(String toolName) {
        String normalized = toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
        if ("write_file".equals(normalized) || "apply_patch".equals(normalized)) {
            return "edit";
        }
        if ("read_file".equals(normalized)) {
            return "read";
        }
        return "other";
    }

    private String validateDefinition(CliMcpServerDefinition definition) {
        if (definition == null) {
            return "missing MCP server definition";
        }
        String type = trimToNull(definition.getType());
        if (type == null) {
            return "missing MCP transport type";
        }
        if ("stdio".equals(type)) {
            return isBlank(definition.getCommand()) ? "stdio transport requires command" : null;
        }
        if ("sse".equals(type) || "streamable_http".equals(type)) {
            return isBlank(definition.getUrl()) ? type + " transport requires url" : null;
        }
        return "unsupported MCP transport: " + type;
    }

    private String normalizeTransportType(String type, String command) {
        String normalized = trimToNull(type);
        if (normalized == null) {
            return isBlank(command) ? null : "stdio";
        }
        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        return "http".equals(lowerCase) ? "streamable_http" : lowerCase;
    }

    private List<String> toStringList(JSONArray values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> items = new ArrayList<String>();
        for (int i = 0; i < values.size(); i++) {
            String value = trimToNull(values.getString(i));
            if (value != null) {
                items.add(value);
            }
        }
        return items.isEmpty() ? null : items;
    }

    private Map<String, String> toStringMap(JSONObject object) {
        if (object == null || object.isEmpty()) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            String key = trimToNull(entry.getKey());
            if (key != null) {
                map.put(key, entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            }
        }
        return map.isEmpty() ? null : map;
    }

    private Map<String, Object> newMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (values == null) {
            return map;
        }
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object key = values[i];
            if (key != null) {
                map.put(String.valueOf(key), values[i + 1]);
            }
        }
        return map;
    }

    private String requestIdKey(Object id) {
        return id == null ? "null" : String.valueOf(id);
    }

    private String requiredString(JSONObject params, String key) {
        String value = params == null ? null : trimToNull(params.getString(key));
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value;
    }

    private JSONArray requiredArray(JSONObject params, String key) {
        JSONArray value = params == null ? null : params.getJSONArray(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value;
    }

    private String requireAbsolutePath(String path) {
        String value = trimToNull(path);
        if (value == null) {
            throw new IllegalArgumentException("cwd is required");
        }
        Path resolved = Paths.get(value);
        if (!resolved.isAbsolute()) {
            throw new IllegalArgumentException("cwd must be an absolute path");
        }
        return resolved.normalize().toString();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    private String clip(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars));
    }

    private String clipPreserveNewlines(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars));
    }

    private String safeMessage(Throwable throwable) {
        String message = null;
        Throwable current = throwable;
        Throwable last = throwable;
        while (current != null) {
            if (!isBlank(current.getMessage())) {
                message = current.getMessage().trim();
            }
            last = current;
            current = current.getCause();
        }
        return isBlank(message)
                ? (last == null ? "unknown ACP error" : last.getClass().getSimpleName())
                : message;
    }

    private Map<String, Object> textContent(String text) {
        return newMap(
                "type", "text",
                "text", text == null ? "" : text
        );
    }

    private List<Map<String, Object>> toolCallTextContent(String text) {
        if (isBlank(text)) {
            return null;
        }
        return Collections.singletonList(newMap(
                "type", "content",
                "content", textContent(text)
        ));
    }

    private Object parseJsonOrText(String value) {
        if (isBlank(value)) {
            return Collections.emptyMap();
        }
        try {
            return JSON.parse(value);
        } catch (Exception ex) {
            return newMap("text", value);
        }
    }

    private Map<String, Object> toStructuredSessionUpdate(SessionEvent event) {
        if (event == null || event.getType() == null) {
            return null;
        }
        if (event.getType() == SessionEventType.TASK_CREATED) {
            return newMap(
                    "sessionUpdate", "tool_call",
                    "toolCallId", payloadText(event, "callId", payloadText(event, "taskId", null)),
                    "title", payloadText(event, "title", event.getSummary()),
                    "kind", "other",
                    "status", mapTaskStatusValue(payloadText(event, "status", null)),
                    "rawInput", buildTaskRawInput(event)
            );
        }
        if (event.getType() == SessionEventType.TASK_UPDATED) {
            String text = buildTaskUpdateText(event);
            return newMap(
                    "sessionUpdate", "tool_call_update",
                    "toolCallId", payloadText(event, "callId", payloadText(event, "taskId", null)),
                    "status", mapTaskStatusValue(payloadText(event, "status", null)),
                    "content", toolCallTextContent(text),
                    "rawOutput", buildTaskRawOutput(event)
            );
        }
        if (event.getType() == SessionEventType.TEAM_MESSAGE) {
            return toTeamMessageAcpUpdate(event);
        }
        if (event.getType() == SessionEventType.AUTO_CONTINUE
                || event.getType() == SessionEventType.AUTO_STOP
                || event.getType() == SessionEventType.BLOCKED) {
            return newMap(
                    "sessionUpdate", "agent_message_chunk",
                    "content", textContent(firstNonBlank(event.getSummary(), event.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')))
            );
        }
        return null;
    }

    private String payloadText(SessionEvent event, String key, String defaultValue) {
        if (event == null || event.getPayload() == null || key == null) {
            return defaultValue;
        }
        Object value = event.getPayload().get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private Object payloadValue(SessionEvent event, String key) {
        if (event == null || event.getPayload() == null || key == null) {
            return null;
        }
        return event.getPayload().get(key);
    }

    private Map<String, Object> buildTaskRawInput(SessionEvent event) {
        return newMap(
                "taskId", payloadValue(event, "taskId"),
                "definition", payloadValue(event, "tool"),
                "subagent", payloadValue(event, "subagent"),
                "memberId", payloadValue(event, "memberId"),
                "memberName", payloadValue(event, "memberName"),
                "task", payloadValue(event, "task"),
                "context", payloadValue(event, "context"),
                "dependsOn", payloadValue(event, "dependsOn"),
                "childSessionId", payloadValue(event, "childSessionId"),
                "background", payloadValue(event, "background"),
                "sessionMode", payloadValue(event, "sessionMode"),
                "depth", payloadValue(event, "depth"),
                "phase", payloadValue(event, "phase"),
                "percent", payloadValue(event, "percent")
        );
    }

    private Map<String, Object> buildTaskRawOutput(SessionEvent event) {
        String text = extractTaskPrimaryText(event);
        return newMap(
                "text", text,
                "taskId", payloadValue(event, "taskId"),
                "status", payloadValue(event, "status"),
                "phase", payloadValue(event, "phase"),
                "percent", payloadValue(event, "percent"),
                "detail", payloadValue(event, "detail"),
                "memberId", payloadValue(event, "memberId"),
                "memberName", payloadValue(event, "memberName"),
                "heartbeatCount", payloadValue(event, "heartbeatCount"),
                "updatedAtEpochMs", payloadValue(event, "updatedAtEpochMs"),
                "lastHeartbeatTime", payloadValue(event, "lastHeartbeatTime"),
                "durationMillis", payloadValue(event, "durationMillis"),
                "output", payloadValue(event, "output"),
                "error", payloadValue(event, "error")
        );
    }

    private String buildTaskUpdateText(SessionEvent event) {
        String text = extractTaskPrimaryText(event);
        if (!isTeamTaskEvent(event)) {
            return text;
        }
        String member = firstNonBlank(payloadText(event, "memberName", null), payloadText(event, "memberId", null));
        String phase = payloadText(event, "phase", null);
        String status = payloadText(event, "status", null);
        String percent = payloadText(event, "percent", null);
        StringBuilder prefix = new StringBuilder();
        if (!isBlank(member)) {
            prefix.append('[').append(member).append("] ");
        }
        if (!isBlank(phase)) {
            prefix.append(phase);
        } else if (!isBlank(status)) {
            prefix.append(status);
        }
        if (!isBlank(percent)) {
            if (prefix.length() > 0) {
                prefix.append(' ');
            }
            prefix.append(percent).append('%');
        }
        if (prefix.length() == 0) {
            return text;
        }
        if (isBlank(text)) {
            return prefix.toString();
        }
        return prefix.append(" - ").append(text).toString();
    }

    private String extractTaskPrimaryText(SessionEvent event) {
        return firstNonBlank(
                payloadText(event, "error", null),
                payloadText(event, "output", null),
                payloadText(event, "detail", event.getSummary())
        );
    }

    private boolean isTeamTaskEvent(SessionEvent event) {
        String callId = payloadText(event, "callId", null);
        String taskId = payloadText(event, "taskId", null);
        String title = payloadText(event, "title", null);
        if (!isBlank(callId) && callId.startsWith("team-task:")) {
            return true;
        }
        if (!isBlank(taskId) && taskId.startsWith("team-task:")) {
            return true;
        }
        if (!isBlank(title) && title.startsWith("Team task")) {
            return true;
        }
        return !isBlank(payloadText(event, "memberId", null))
                || !isBlank(payloadText(event, "memberName", null))
                || !isBlank(payloadText(event, "heartbeatCount", null));
    }

    private String mapTaskStatusValue(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return "pending";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("running".equals(lower) || "in_progress".equals(lower) || "in-progress".equals(lower) || "started".equals(lower)) {
            return "in_progress";
        }
        if ("completed".equals(lower) || "fallback".equals(lower)) {
            return "completed";
        }
        if ("failed".equals(lower) || "cancelled".equals(lower) || "canceled".equals(lower) || "error".equals(lower)) {
            return "failed";
        }
        return "pending";
    }

    private Map<String, Object> toTeamMessageAcpUpdate(SessionEvent event) {
        if (event == null) {
            return null;
        }
        String taskId = payloadText(event, "taskId", null);
        String toolCallId = firstNonBlank(payloadText(event, "callId", null),
                isBlank(taskId) ? null : "team-task:" + taskId);
        String messageType = trimToNull(payloadText(event, "messageType", null));
        String fromMemberId = trimToNull(payloadText(event, "fromMemberId", null));
        String toMemberId = trimToNull(payloadText(event, "toMemberId", null));
        String text = firstNonBlank(
                payloadText(event, "content", null),
                payloadText(event, "detail", event.getSummary())
        );
        String rendered = formatTeamMessageText(messageType, fromMemberId, toMemberId, text);
        if (isBlank(toolCallId)) {
            return newMap(
                    "sessionUpdate", "agent_message_chunk",
                    "content", textContent(rendered)
            );
        }
        return newMap(
                "sessionUpdate", "tool_call_update",
                "toolCallId", toolCallId,
                "content", toolCallTextContent(rendered),
                "rawOutput", newMap(
                        "type", "team_message",
                        "messageId", payloadText(event, "messageId", null),
                        "taskId", taskId,
                        "fromMemberId", fromMemberId,
                        "toMemberId", toMemberId,
                        "messageType", messageType,
                        "text", text
                )
        );
    }

    private String formatTeamMessageText(String messageType, String fromMemberId, String toMemberId, String text) {
        String route = formatTeamMessageRoute(fromMemberId, toMemberId);
        String prefix;
        if (!isBlank(route) && !isBlank(messageType)) {
            prefix = "[" + messageType + "] " + route;
        } else if (!isBlank(messageType)) {
            prefix = "[" + messageType + "]";
        } else {
            prefix = route;
        }
        if (isBlank(prefix)) {
            return text;
        }
        if (isBlank(text)) {
            return prefix;
        }
        return prefix + "\n" + text;
    }

    private String formatTeamMessageRoute(String fromMemberId, String toMemberId) {
        if (isBlank(fromMemberId) && isBlank(toMemberId)) {
            return null;
        }
        return firstNonBlank(fromMemberId, "?") + " -> " + firstNonBlank(toMemberId, "?");
    }

    private void awaitPendingWork() {
        requestExecutor.shutdown();
        try {
            requestExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        promptExecutor.shutdown();
        try {
            promptExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        for (SessionHandle handle : new LinkedHashSet<SessionHandle>(sessions.values())) {
            if (handle != null) {
                handle.close();
            }
        }
        sessions.clear();
        for (CompletableFuture<JSONObject> future : pendingClientResponses.values()) {
            future.complete(buildCancelledPermissionResponse());
        }
        pendingClientResponses.clear();
        requestExecutor.shutdownNow();
        promptExecutor.shutdownNow();
    }

    private final class SessionHandle implements Closeable {

        private volatile CodeCommandOptions options;
        private volatile CodeCommandOptions runtimeOptions;
        private volatile CodeCommandOptions pendingRuntimeOptions;
        private final CodingSessionManager sessionManager;
        private volatile CodingCliAgentFactory factory;
        private final AcpToolApprovalDecorator.PermissionGateway permissionGateway;
        private volatile CodingCliAgentFactory.PreparedCodingAgent prepared;
        private volatile ManagedCodingSession session;
        private volatile HeadlessCodingSessionRuntime runtime;
        private volatile CodingRuntime codingRuntime;
        private volatile CodingTaskSessionEventBridge taskEventBridge;
        private final CliResolvedMcpConfig resolvedMcpConfig;
        private final CliProviderConfigManager providerConfigManager;
        private volatile HeadlessCodingSessionRuntime.PromptControl activePrompt;

        private SessionHandle(CodeCommandOptions options,
                              CodingSessionManager sessionManager,
                              CodingCliAgentFactory factory,
                              AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                              CodingCliAgentFactory.PreparedCodingAgent prepared,
                              ManagedCodingSession session,
                              CliResolvedMcpConfig resolvedMcpConfig) {
            this.options = options;
            this.runtimeOptions = options;
            this.sessionManager = sessionManager;
            this.factory = factory;
            this.permissionGateway = permissionGateway;
            this.prepared = prepared;
            this.session = session;
            this.resolvedMcpConfig = resolvedMcpConfig;
            this.providerConfigManager = new CliProviderConfigManager(Paths.get(firstNonBlank(
                    options == null ? null : options.getWorkspace(),
                    session == null ? null : session.getWorkspace(),
                    "."
            )));
            this.runtime = new HeadlessCodingSessionRuntime(options, sessionManager);
            this.codingRuntime = prepared == null || prepared.getAgent() == null ? null : prepared.getAgent().getRuntime();
            this.taskEventBridge = registerTaskEventBridge();
        }

        private ManagedCodingSession getSession() {
            return session;
        }

        private Map<String, Object> buildSessionOpenResult() {
            ManagedCodingSession currentSession = session;
            return newMap(
                    "sessionId", currentSession == null ? null : currentSession.getSessionId(),
                    "configOptions", buildConfigOptions(),
                    "modes", buildModes()
            );
        }

        private synchronized Map<String, Object> buildModes() {
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (ApprovalMode mode : ApprovalMode.values()) {
                if (mode == null) {
                    continue;
                }
                items.add(newMap(
                        "id", mode.getValue(),
                        "name", approvalModeName(mode),
                        "description", approvalModeDescription(mode)
                ));
            }
            return newMap(
                    "currentModeId", currentApprovalMode().getValue(),
                    "availableModes", items
            );
        }

        private synchronized List<Map<String, Object>> buildConfigOptions() {
            List<Map<String, Object>> configOptions = new ArrayList<Map<String, Object>>();
            configOptions.add(newMap(
                    "id", "mode",
                    "name", "Permission Mode",
                    "description", "Controls how tool approval is handled in this ACP session.",
                    "category", "mode",
                    "currentValue", currentApprovalMode().getValue(),
                    "type", "select",
                    "options", buildModeOptionValues()
            ));
            configOptions.add(newMap(
                    "id", "model",
                    "name", "Model",
                    "description", "Selects the effective model for subsequent turns in this ACP session.",
                    "category", "model",
                    "currentValue", options == null ? null : options.getModel(),
                    "type", "select",
                    "options", buildModelOptionValues()
            ));
            return configOptions;
        }

        private synchronized List<Map<String, Object>> setConfigOption(String configId, String value) throws Exception {
            String normalizedId = trimToNull(configId);
            if ("mode".equalsIgnoreCase(normalizedId)) {
                applyModeChange(value, true);
                return buildConfigOptions();
            }
            if ("model".equalsIgnoreCase(normalizedId)) {
                applyModelChange(value, true, true);
                return buildConfigOptions();
            }
            throw new IllegalArgumentException("Unknown session config option: " + configId);
        }

        private synchronized void setMode(String modeId) throws Exception {
            applyModeChange(modeId, true);
        }

        private void startPrompt(Runnable runnable) {
            promptExecutor.submit(runnable);
        }

        private synchronized HeadlessCodingSessionRuntime.PromptControl beginPrompt() {
            if (activePrompt != null && !activePrompt.isCancelled()) {
                throw new IllegalStateException("session already has an active prompt");
            }
            activePrompt = new HeadlessCodingSessionRuntime.PromptControl();
            return activePrompt;
        }

        private HeadlessCodingSessionRuntime.PromptResult runPrompt(String input,
                                                                    HeadlessCodingSessionRuntime.PromptControl promptControl) throws Exception {
            try {
                ManagedCodingSession currentSession = session;
                HeadlessCodingSessionRuntime currentRuntime = runtime;
                return currentRuntime.runPrompt(currentSession, input, promptControl, new AcpTurnObserver(currentSession.getSessionId()));
            } finally {
                completePrompt(promptControl);
            }
        }

        private HeadlessCodingSessionRuntime.PromptResult runSlashCommand(String input,
                                                                          HeadlessCodingSessionRuntime.PromptControl promptControl) throws Exception {
            try {
                ManagedCodingSession currentSession = session;
                String turnId = UUID.randomUUID().toString();
                appendSimpleEvent(SessionEventType.USER_MESSAGE, turnId, clip(input, 200), newMap(
                        "input", clipPreserveNewlines(input, options != null && options.isVerbose() ? 4000 : 1200)
                ));
                sendSessionUpdate(currentSession.getSessionId(), newMap(
                        "sessionUpdate", "user_message_chunk",
                        "content", textContent(input)
                ));

                AcpSlashCommandSupport.ExecutionResult result = AcpSlashCommandSupport.execute(buildSlashCommandContext(), input);
                String output = result == null ? null : result.getOutput();
                appendSimpleEvent(SessionEventType.ASSISTANT_MESSAGE, turnId, clip(output, 200), newMap(
                        "kind", "command",
                        "output", clipPreserveNewlines(output, options != null && options.isVerbose() ? 4000 : 1200)
                ));
                if (!isBlank(output)) {
                    sendSessionUpdate(session.getSessionId(), newMap(
                            "sessionUpdate", "agent_message_chunk",
                            "content", textContent(output)
                    ));
                }
                persistSessionIfConfigured();
                return HeadlessCodingSessionRuntime.PromptResult.completed(turnId, output, null);
            } finally {
                completePrompt(promptControl);
            }
        }

        private void cancel() {
            HeadlessCodingSessionRuntime.PromptControl control = activePrompt;
            if (control != null) {
                control.cancel();
            }
        }

        private void replayHistory() throws IOException {
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), null, null);
            if (events == null) {
                return;
            }
            for (SessionEvent event : events) {
                Map<String, Object> update = toHistoryUpdate(event);
                if (update != null) {
                    sendSessionUpdate(session.getSessionId(), update);
                }
            }
        }

        private void sendAvailableCommandsUpdate() {
            AcpJsonRpcServer.this.sendAvailableCommandsUpdate(session == null ? null : session.getSessionId());
        }

        private void appendSimpleEvent(SessionEventType type, String turnId, String summary, Map<String, Object> payload) {
            if (sessionManager == null || session == null || type == null) {
                return;
            }
            try {
                sessionManager.appendEvent(session.getSessionId(), SessionEvent.builder()
                        .sessionId(session.getSessionId())
                        .type(type)
                        .turnId(turnId)
                        .summary(summary)
                        .payload(payload)
                        .build());
            } catch (IOException ignored) {
            }
        }

        private void persistSessionIfConfigured() {
            if (sessionManager == null || session == null || options == null || !options.isAutoSaveSession()) {
                return;
            }
            try {
                sessionManager.save(session);
            } catch (IOException ignored) {
            }
        }

        private AcpSlashCommandSupport.Context buildSlashCommandContext() {
            return new AcpSlashCommandSupport.Context(
                    session,
                    sessionManager,
                    options,
                    prepared,
                    prepared == null ? null : prepared.getMcpRuntimeManager(),
                    new AcpSlashCommandSupport.RuntimeCommandHandler() {
                        @Override
                        public String executeProviders() {
                            return executeProvidersCommand();
                        }

                        @Override
                        public String executeProvider(String argument) throws Exception {
                            return executeProviderCommand(argument);
                        }

                        @Override
                        public String executeModel(String argument) throws Exception {
                            return executeModelCommand(argument);
                        }
                    }
            );
        }

        private synchronized String executeProvidersCommand() {
            return renderProvidersOutput();
        }

        private synchronized String executeProviderCommand(String argument) throws Exception {
            if (isBlank(argument)) {
                return renderCurrentProviderOutput();
            }
            String trimmed = argument.trim();
            String[] parts = trimmed.split("\\s+", 2);
            String action = parts[0].toLowerCase(Locale.ROOT);
            String value = parts.length > 1 ? trimToNull(parts[1]) : null;
            if ("use".equals(action)) {
                return switchToProviderProfile(value);
            }
            if ("save".equals(action)) {
                return saveCurrentProviderProfile(value);
            }
            if ("add".equals(action)) {
                return addProviderProfile(value);
            }
            if ("edit".equals(action)) {
                return editProviderProfile(value);
            }
            if ("default".equals(action)) {
                return setDefaultProviderProfile(value);
            }
            if ("remove".equals(action)) {
                return removeProviderProfile(value);
            }
            return "Unknown /provider action: " + action
                    + ". Use /provider, /providers, /provider use <name>, /provider save <name>, "
                    + "/provider add <name> ..., /provider edit <name> ..., /provider default <name|clear>, "
                    + "or /provider remove <name>.";
        }

        private synchronized String executeModelCommand(String argument) throws Exception {
            if (isBlank(argument)) {
                return renderModelOutput();
            }
            applyModelChange(argument, true, false);
            persistSessionIfConfigured();
            return renderModelOutput();
        }

        private void applyModeChange(String rawModeId, boolean emitUpdates) throws Exception {
            ApprovalMode nextMode = ApprovalMode.parse(rawModeId);
            CodeCommandOptions currentOptions = options;
            if (currentOptions != null && nextMode == currentOptions.getApprovalMode()) {
                if (emitUpdates) {
                    emitModeUpdate();
                    emitConfigOptionUpdate();
                }
                return;
            }
            applySessionOptionsChange(currentOptions.withApprovalMode(nextMode), emitUpdates, emitUpdates);
        }

        private void applyModelChange(String rawValue,
                                      boolean emitUpdates,
                                      boolean requireListedOption) throws Exception {
            String normalized = trimToNull(rawValue);
            if (normalized == null) {
                throw new IllegalArgumentException("Model value is required");
            }
            CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
            if ("reset".equalsIgnoreCase(normalized)) {
                workspaceConfig.setModelOverride(null);
                providerConfigManager.saveWorkspaceConfig(workspaceConfig);
                applySessionOptionsChange(resolveCurrentProviderOptions(null), false, emitUpdates);
            } else {
                if (requireListedOption && !isSupportedModelValue(normalized)) {
                    throw new IllegalArgumentException("Unsupported model for current ACP session: " + normalized);
                }
                workspaceConfig.setModelOverride(normalized);
                providerConfigManager.saveWorkspaceConfig(workspaceConfig);
                CodeCommandOptions currentOptions = options;
                CliProtocol currentProtocol = currentProtocol();
                applySessionOptionsChange(currentOptions.withRuntime(
                        currentOptions == null ? null : currentOptions.getProvider(),
                        currentProtocol,
                        normalized,
                        currentOptions == null ? null : currentOptions.getApiKey(),
                        currentOptions == null ? null : currentOptions.getBaseUrl()
                ), false, emitUpdates);
            }
        }

        private synchronized String switchToProviderProfile(String profileName) throws Exception {
            if (isBlank(profileName)) {
                return "Usage: /provider use <profile-name>";
            }
            String normalizedName = profileName.trim();
            CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
            CliProviderProfile profile = providersConfig.getProfiles().get(normalizedName);
            if (profile == null) {
                return "Unknown provider profile: " + normalizedName;
            }
            CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
            workspaceConfig.setActiveProfile(normalizedName);
            providerConfigManager.saveWorkspaceConfig(workspaceConfig);
            rebindSession(resolveProfileRuntimeOptions(profile, workspaceConfig));
            emitConfigOptionUpdate();
            persistSessionIfConfigured();
            return renderCurrentProviderOutput();
        }

        private synchronized String saveCurrentProviderProfile(String profileName) throws IOException {
            if (isBlank(profileName)) {
                return "Usage: /provider save <profile-name>";
            }
            String normalizedName = profileName.trim();
            CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
            CliProtocol currentProtocol = currentProtocol();
            providersConfig.getProfiles().put(normalizedName, CliProviderProfile.builder()
                    .provider(options == null || options.getProvider() == null ? null : options.getProvider().getPlatform())
                    .protocol(currentProtocol == null ? null : currentProtocol.getValue())
                    .model(options == null ? null : options.getModel())
                    .baseUrl(options == null ? null : options.getBaseUrl())
                    .apiKey(options == null ? null : options.getApiKey())
                    .build());
            if (isBlank(providersConfig.getDefaultProfile())) {
                providersConfig.setDefaultProfile(normalizedName);
            }
            providerConfigManager.saveProvidersConfig(providersConfig);
            return "provider saved: " + normalizedName + " -> " + providerConfigManager.globalProvidersPath();
        }

        private synchronized String addProviderProfile(String rawArguments) throws IOException {
            String usage = "Usage: /provider add <profile-name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]";
            ProviderProfileMutation mutation = parseProviderProfileMutation(rawArguments);
            if (mutation == null) {
                return usage;
            }
            CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
            if (providersConfig.getProfiles().containsKey(mutation.profileName)) {
                return "Provider profile already exists: " + mutation.profileName + ". Use /provider edit " + mutation.profileName + " ...";
            }
            if (isBlank(mutation.provider)) {
                return usage;
            }
            PlatformType provider = parseProviderType(mutation.provider);
            if (provider == null) {
                return "Unsupported provider: " + mutation.provider;
            }
            String baseUrlValue = mutation.clearBaseUrl ? null : mutation.baseUrl;
            CliProtocol protocolValue = parseProviderProtocol(firstNonBlank(
                    mutation.protocol,
                    CliProtocol.defaultProtocol(provider, baseUrlValue).getValue()
            ));
            if (protocolValue == null) {
                return "Unsupported protocol: " + mutation.protocol;
            }
            if (!isSupportedProviderProtocol(provider, protocolValue)) {
                return "Provider " + provider.getPlatform() + " does not support responses protocol in ai4j-cli yet";
            }
            providersConfig.getProfiles().put(mutation.profileName, CliProviderProfile.builder()
                    .provider(provider.getPlatform())
                    .protocol(protocolValue.getValue())
                    .model(mutation.clearModel ? null : mutation.model)
                    .baseUrl(mutation.clearBaseUrl ? null : mutation.baseUrl)
                    .apiKey(mutation.clearApiKey ? null : mutation.apiKey)
                    .build());
            if (isBlank(providersConfig.getDefaultProfile())) {
                providersConfig.setDefaultProfile(mutation.profileName);
            }
            providerConfigManager.saveProvidersConfig(providersConfig);
            return "provider added: " + mutation.profileName + " -> " + providerConfigManager.globalProvidersPath();
        }

        private synchronized String editProviderProfile(String rawArguments) throws Exception {
            String usage = "Usage: /provider edit <profile-name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]";
            ProviderProfileMutation mutation = parseProviderProfileMutation(rawArguments);
            if (mutation == null || !mutation.hasAnyFieldChanges()) {
                return usage;
            }
            CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
            CliProviderProfile existing = providersConfig.getProfiles().get(mutation.profileName);
            if (existing == null) {
                return "Unknown provider profile: " + mutation.profileName;
            }
            String effectiveProfileBeforeEdit = resolveEffectiveProfileName();
            PlatformType provider = parseProviderType(firstNonBlank(mutation.provider, existing.getProvider()));
            if (provider == null) {
                return "Unsupported provider: " + firstNonBlank(mutation.provider, existing.getProvider());
            }
            String baseUrlValue = mutation.clearBaseUrl ? null : firstNonBlank(mutation.baseUrl, existing.getBaseUrl());
            String protocolRaw = mutation.protocol;
            if (isBlank(protocolRaw)) {
                protocolRaw = firstNonBlank(
                        normalizeStoredProtocol(existing.getProtocol(), provider, baseUrlValue),
                        CliProtocol.defaultProtocol(provider, baseUrlValue).getValue()
                );
            }
            CliProtocol protocolValue = parseProviderProtocol(protocolRaw);
            if (protocolValue == null) {
                return "Unsupported protocol: " + protocolRaw;
            }
            if (!isSupportedProviderProtocol(provider, protocolValue)) {
                return "Provider " + provider.getPlatform() + " does not support responses protocol in ai4j-cli yet";
            }
            existing.setProvider(provider.getPlatform());
            existing.setProtocol(protocolValue.getValue());
            if (mutation.clearModel) {
                existing.setModel(null);
            } else if (mutation.model != null) {
                existing.setModel(mutation.model);
            }
            if (mutation.clearBaseUrl) {
                existing.setBaseUrl(null);
            } else if (mutation.baseUrl != null) {
                existing.setBaseUrl(mutation.baseUrl);
            }
            if (mutation.clearApiKey) {
                existing.setApiKey(null);
            } else if (mutation.apiKey != null) {
                existing.setApiKey(mutation.apiKey);
            }
            providersConfig.getProfiles().put(mutation.profileName, existing);
            providerConfigManager.saveProvidersConfig(providersConfig);
            if (mutation.profileName.equals(effectiveProfileBeforeEdit)) {
                CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
                rebindSession(resolveProfileRuntimeOptions(existing, workspaceConfig));
                emitConfigOptionUpdate();
                persistSessionIfConfigured();
                return "provider updated: " + mutation.profileName + " -> " + providerConfigManager.globalProvidersPath()
                        + "\n" + renderCurrentProviderOutput();
            }
            return "provider updated: " + mutation.profileName + " -> " + providerConfigManager.globalProvidersPath();
        }

        private synchronized String removeProviderProfile(String profileName) throws IOException {
            if (isBlank(profileName)) {
                return "Usage: /provider remove <profile-name>";
            }
            String normalizedName = profileName.trim();
            CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
            if (providersConfig.getProfiles().remove(normalizedName) == null) {
                return "Unknown provider profile: " + normalizedName;
            }
            if (normalizedName.equals(providersConfig.getDefaultProfile())) {
                providersConfig.setDefaultProfile(null);
            }
            providerConfigManager.saveProvidersConfig(providersConfig);
            CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
            if (normalizedName.equals(workspaceConfig.getActiveProfile())) {
                workspaceConfig.setActiveProfile(null);
                providerConfigManager.saveWorkspaceConfig(workspaceConfig);
            }
            return "provider removed: " + normalizedName;
        }

        private synchronized String setDefaultProviderProfile(String profileName) throws IOException {
            if (isBlank(profileName)) {
                return "Usage: /provider default <profile-name|clear>";
            }
            String normalizedName = profileName.trim();
            CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
            if ("clear".equalsIgnoreCase(normalizedName)) {
                providersConfig.setDefaultProfile(null);
                providerConfigManager.saveProvidersConfig(providersConfig);
                return "provider default cleared";
            }
            if (!providersConfig.getProfiles().containsKey(normalizedName)) {
                return "Unknown provider profile: " + normalizedName;
            }
            providersConfig.setDefaultProfile(normalizedName);
            providerConfigManager.saveProvidersConfig(providersConfig);
            return "provider default: " + normalizedName;
        }

        private String renderProvidersOutput() {
            CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
            CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
            if (providersConfig.getProfiles().isEmpty()) {
                return "providers: (none)";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("providers:\n");
            List<String> names = new ArrayList<String>(providersConfig.getProfiles().keySet());
            Collections.sort(names);
            for (String name : names) {
                CliProviderProfile profile = providersConfig.getProfiles().get(name);
                builder.append("- ").append(name);
                if (name.equals(workspaceConfig.getActiveProfile())) {
                    builder.append(" [active]");
                }
                if (name.equals(providersConfig.getDefaultProfile())) {
                    builder.append(" [default]");
                }
                builder.append(" | provider=").append(profile == null ? null : profile.getProvider());
                builder.append(", protocol=").append(profile == null ? null : profile.getProtocol());
                builder.append(", model=").append(profile == null ? null : profile.getModel());
                if (!isBlank(profile == null ? null : profile.getBaseUrl())) {
                    builder.append(", baseUrl=").append(profile.getBaseUrl());
                }
                builder.append('\n');
            }
            return builder.toString().trim();
        }

        private String renderCurrentProviderOutput() {
            CodeCommandOptions currentOptions = options;
            CliProtocol currentProtocol = currentProtocol();
            CliResolvedProviderConfig resolved = providerConfigManager.resolve(
                    currentOptions == null || currentOptions.getProvider() == null ? null : currentOptions.getProvider().getPlatform(),
                    currentProtocol == null ? null : currentProtocol.getValue(),
                    null,
                    currentOptions == null ? null : currentOptions.getApiKey(),
                    currentOptions == null ? null : currentOptions.getBaseUrl(),
                    Collections.<String, String>emptyMap(),
                    new Properties()
            );
            StringBuilder builder = new StringBuilder();
            builder.append("provider:\n");
            builder.append("- activeProfile=").append(firstNonBlank(resolved.getActiveProfile(), "(none)")).append('\n');
            builder.append("- defaultProfile=").append(firstNonBlank(resolved.getDefaultProfile(), "(none)")).append('\n');
            builder.append("- effectiveProfile=").append(firstNonBlank(resolved.getEffectiveProfile(), "(none)")).append('\n');
            builder.append("- provider=").append(currentOptions == null || currentOptions.getProvider() == null ? null : currentOptions.getProvider().getPlatform())
                    .append(", protocol=").append(currentProtocol == null ? null : currentProtocol.getValue())
                    .append(", model=").append(currentOptions == null ? null : currentOptions.getModel()).append('\n');
            builder.append("- baseUrl=").append(firstNonBlank(currentOptions == null ? null : currentOptions.getBaseUrl(), "(default)")).append('\n');
            builder.append("- apiKey=").append(isBlank(currentOptions == null ? null : currentOptions.getApiKey()) ? "(missing)" : maskSecret(currentOptions.getApiKey())).append('\n');
            builder.append("- store=").append(providerConfigManager.globalProvidersPath());
            return builder.toString().trim();
        }

        private String renderModelOutput() {
            CodeCommandOptions currentOptions = options;
            CliProtocol currentProtocol = currentProtocol();
            CliResolvedProviderConfig resolved = providerConfigManager.resolve(
                    currentOptions == null || currentOptions.getProvider() == null ? null : currentOptions.getProvider().getPlatform(),
                    currentProtocol == null ? null : currentProtocol.getValue(),
                    null,
                    currentOptions == null ? null : currentOptions.getApiKey(),
                    currentOptions == null ? null : currentOptions.getBaseUrl(),
                    Collections.<String, String>emptyMap(),
                    new Properties()
            );
            StringBuilder builder = new StringBuilder();
            builder.append("model:\n");
            builder.append("- current=").append(currentOptions == null ? null : currentOptions.getModel()).append('\n');
            builder.append("- override=").append(firstNonBlank(resolved.getModelOverride(), "(none)")).append('\n');
            builder.append("- profile=").append(firstNonBlank(resolved.getEffectiveProfile(), "(none)")).append('\n');
            builder.append("- workspaceConfig=").append(providerConfigManager.workspaceConfigPath());
            return builder.toString().trim();
        }

        private List<Map<String, Object>> buildModeOptionValues() {
            List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
            for (ApprovalMode mode : ApprovalMode.values()) {
                if (mode == null) {
                    continue;
                }
                values.add(newMap(
                        "value", mode.getValue(),
                        "name", approvalModeName(mode),
                        "description", approvalModeDescription(mode)
                ));
            }
            return values;
        }

        private List<Map<String, Object>> buildModelOptionValues() {
            LinkedHashMap<String, Map<String, Object>> values = new LinkedHashMap<String, Map<String, Object>>();
            CliResolvedProviderConfig resolved = providerConfigManager.resolve(
                    options == null || options.getProvider() == null ? null : options.getProvider().getPlatform(),
                    currentProtocol() == null ? null : currentProtocol().getValue(),
                    null,
                    options == null ? null : options.getApiKey(),
                    options == null ? null : options.getBaseUrl(),
                    Collections.<String, String>emptyMap(),
                    new Properties()
            );
            String currentProvider = options != null && options.getProvider() != null
                    ? options.getProvider().getPlatform()
                    : (resolved.getProvider() == null ? null : resolved.getProvider().getPlatform());
            addModelOptionValue(values, resolved.getModelOverride(), "Current workspace override");
            addModelOptionValue(values, options == null ? null : options.getModel(), "Current effective model");

            CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
            CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
            addProfileModelOptionValue(values, workspaceConfig == null ? null : workspaceConfig.getActiveProfile(), currentProvider, "Active profile");
            addProfileModelOptionValue(values, providersConfig.getDefaultProfile(), currentProvider, "Default profile");
            for (String profileName : providerConfigManager.listProfileNames()) {
                addProfileModelOptionValue(values, profileName, currentProvider, "Saved profile");
            }
            return new ArrayList<Map<String, Object>>(values.values());
        }

        private void addProfileModelOptionValue(LinkedHashMap<String, Map<String, Object>> values,
                                                String profileName,
                                                String provider,
                                                String label) {
            if (values == null || isBlank(profileName)) {
                return;
            }
            CliProviderProfile profile = providerConfigManager.getProfile(profileName);
            if (profile == null || isBlank(profile.getModel())) {
                return;
            }
            if (!isBlank(provider) && !provider.equalsIgnoreCase(firstNonBlank(profile.getProvider(), provider))) {
                return;
            }
            addModelOptionValue(values, profile.getModel(), label + " " + profileName);
        }

        private void addModelOptionValue(LinkedHashMap<String, Map<String, Object>> values,
                                         String model,
                                         String description) {
            if (values == null || isBlank(model)) {
                return;
            }
            String normalizedKey = model.trim().toLowerCase(Locale.ROOT);
            if (values.containsKey(normalizedKey)) {
                return;
            }
            values.put(normalizedKey, newMap(
                    "value", model.trim(),
                    "name", model.trim(),
                    "description", description
            ));
        }

        private boolean isSupportedModelValue(String value) {
            if (isBlank(value)) {
                return false;
            }
            String normalized = value.trim();
            if (options != null && normalized.equals(options.getModel())) {
                return true;
            }
            for (Map<String, Object> option : buildModelOptionValues()) {
                if (option == null) {
                    continue;
                }
                Object candidate = option.get("value");
                if (candidate != null && normalized.equals(String.valueOf(candidate))) {
                    return true;
                }
            }
            return false;
        }

        private ApprovalMode currentApprovalMode() {
            return options == null ? ApprovalMode.AUTO : options.getApprovalMode();
        }

        private String approvalModeName(ApprovalMode mode) {
            if (mode == ApprovalMode.MANUAL) {
                return "Manual";
            }
            if (mode == ApprovalMode.SAFE) {
                return "Safe";
            }
            return "Auto";
        }

        private String approvalModeDescription(ApprovalMode mode) {
            if (mode == ApprovalMode.MANUAL) {
                return "Require user approval for every tool call.";
            }
            if (mode == ApprovalMode.SAFE) {
                return "Ask for approval on editing and command execution actions.";
            }
            return "Run tools automatically unless the ACP client enforces its own checks.";
        }

        private void emitModeUpdate() {
            ManagedCodingSession currentSession = session;
            if (currentSession == null || isBlank(currentSession.getSessionId())) {
                return;
            }
            sendSessionUpdate(currentSession.getSessionId(), newMap(
                    "sessionUpdate", "current_mode_update",
                    "currentModeId", currentApprovalMode().getValue(),
                    "modeId", currentApprovalMode().getValue()
            ));
        }

        private void emitConfigOptionUpdate() {
            ManagedCodingSession currentSession = session;
            if (currentSession == null || isBlank(currentSession.getSessionId())) {
                return;
            }
            sendSessionUpdate(currentSession.getSessionId(), newMap(
                    "sessionUpdate", "config_option_update",
                    "configOptions", buildConfigOptions()
            ));
        }

        private String resolveEffectiveProfileName() {
            CliWorkspaceConfig workspaceConfig = providerConfigManager.loadWorkspaceConfig();
            CliProvidersConfig providersConfig = providerConfigManager.loadProvidersConfig();
            String activeProfile = workspaceConfig == null ? null : workspaceConfig.getActiveProfile();
            if (!isBlank(activeProfile) && providersConfig.getProfiles().containsKey(activeProfile)) {
                return activeProfile;
            }
            String defaultProfile = providersConfig.getDefaultProfile();
            return !isBlank(defaultProfile) && providersConfig.getProfiles().containsKey(defaultProfile) ? defaultProfile : null;
        }

        private CodeCommandOptions resolveCurrentProviderOptions(String modelOverride) {
            CodeCommandOptions currentOptions = options;
            CliProtocol currentProtocol = currentProtocol();
            CliResolvedProviderConfig resolved = providerConfigManager.resolve(
                    currentOptions == null || currentOptions.getProvider() == null ? null : currentOptions.getProvider().getPlatform(),
                    currentProtocol == null ? null : currentProtocol.getValue(),
                    modelOverride,
                    currentOptions == null ? null : currentOptions.getApiKey(),
                    currentOptions == null ? null : currentOptions.getBaseUrl(),
                    Collections.<String, String>emptyMap(),
                    new Properties()
            );
            return currentOptions.withRuntime(
                    resolved.getProvider(),
                    resolved.getProtocol(),
                    resolved.getModel(),
                    resolved.getApiKey(),
                    resolved.getBaseUrl()
            );
        }

        private CodeCommandOptions resolveProfileRuntimeOptions(CliProviderProfile profile,
                                                               CliWorkspaceConfig workspaceConfig) {
            CodeCommandOptions currentOptions = options;
            PlatformType currentProvider = currentOptions == null ? null : currentOptions.getProvider();
            PlatformType provider = parseProviderType(firstNonBlank(
                    profile == null ? null : profile.getProvider(),
                    currentProvider == null ? null : currentProvider.getPlatform()
            ));
            if (provider == null) {
                provider = currentProvider == null ? PlatformType.OPENAI : currentProvider;
            }
            String baseUrl = firstNonBlank(profile == null ? null : profile.getBaseUrl(), currentOptions == null ? null : currentOptions.getBaseUrl());
            String protocolRaw = firstNonBlank(
                    normalizeStoredProtocol(profile == null ? null : profile.getProtocol(), provider, baseUrl),
                    currentProtocol() == null ? null : currentProtocol().getValue(),
                    CliProtocol.defaultProtocol(provider, baseUrl).getValue()
            );
            CliProtocol protocolValue = parseProviderProtocol(protocolRaw);
            if (protocolValue == null) {
                protocolValue = currentProtocol() == null ? CliProtocol.defaultProtocol(provider, baseUrl) : currentProtocol();
            }
            String model = firstNonBlank(
                    workspaceConfig == null ? null : workspaceConfig.getModelOverride(),
                    profile == null ? null : profile.getModel(),
                    currentOptions == null ? null : currentOptions.getModel()
            );
            String apiKey = firstNonBlank(profile == null ? null : profile.getApiKey(), currentOptions == null ? null : currentOptions.getApiKey());
            return currentOptions.withRuntime(provider, protocolValue, model, apiKey, baseUrl);
        }

        private void rebindSession(CodeCommandOptions nextOptions) throws Exception {
            if (agentFactoryProvider == null) {
                throw new IllegalStateException("ACP runtime switching is unavailable");
            }
            CodingCliAgentFactory nextFactory = agentFactoryProvider.create(nextOptions, permissionGateway, resolvedMcpConfig);
            CodingCliAgentFactory.PreparedCodingAgent nextPrepared = nextFactory.prepare(nextOptions, null, null, Collections.<String>emptySet());
            ManagedCodingSession previousSession = session;
            CodingCliAgentFactory.PreparedCodingAgent previousPrepared = prepared;
            CodingRuntime previousRuntime = codingRuntime;
            CodingTaskSessionEventBridge previousBridge = taskEventBridge;

            ManagedCodingSession rebound = previousSession;
            if (previousSession != null && previousSession.getSession() != null) {
                io.github.lnyocly.ai4j.coding.CodingSessionState state = previousSession.getSession().exportState();
                io.github.lnyocly.ai4j.coding.CodingSession nextSession = nextPrepared.getAgent().newSession(previousSession.getSessionId(), state);
                rebound = new ManagedCodingSession(
                        nextSession,
                        nextOptions.getProvider() == null ? null : nextOptions.getProvider().getPlatform(),
                        nextPrepared.getProtocol() == null ? null : nextPrepared.getProtocol().getValue(),
                        nextOptions.getModel(),
                        nextOptions.getWorkspace(),
                        nextOptions.getWorkspaceDescription(),
                        nextOptions.getSystemPrompt(),
                        nextOptions.getInstructions(),
                        firstNonBlank(previousSession.getRootSessionId(), previousSession.getSessionId()),
                        previousSession.getParentSessionId(),
                        previousSession.getCreatedAtEpochMs(),
                        previousSession.getUpdatedAtEpochMs()
                );
            }

            if (previousRuntime != null && previousBridge != null) {
                previousRuntime.removeListener(previousBridge);
            }
            if (previousPrepared != null && previousPrepared.getMcpRuntimeManager() != null) {
                previousPrepared.getMcpRuntimeManager().close();
            }
            if (previousSession != null) {
                previousSession.close();
            }

            this.options = nextOptions;
            this.runtimeOptions = nextOptions;
            this.factory = nextFactory;
            this.prepared = nextPrepared;
            this.session = rebound;
            this.runtime = new HeadlessCodingSessionRuntime(nextOptions, sessionManager);
            this.codingRuntime = nextPrepared == null || nextPrepared.getAgent() == null ? null : nextPrepared.getAgent().getRuntime();
            this.taskEventBridge = registerTaskEventBridge();
        }

        private void applySessionOptionsChange(CodeCommandOptions nextOptions,
                                               boolean emitModeUpdate,
                                               boolean emitConfigOptionUpdate) throws Exception {
            if (nextOptions == null) {
                throw new IllegalArgumentException("ACP session configuration is unavailable");
            }
            boolean promptActive;
            boolean changed;
            synchronized (this) {
                changed = !sameRuntimeConfig(options, nextOptions);
                promptActive = hasActivePrompt();
                if (promptActive) {
                    options = nextOptions;
                    pendingRuntimeOptions = nextOptions;
                }
            }
            if (!promptActive && changed) {
                rebindSession(nextOptions);
            } else if (!promptActive) {
                options = nextOptions;
            }
            if (emitModeUpdate) {
                emitModeUpdate();
            }
            if (emitConfigOptionUpdate) {
                emitConfigOptionUpdate();
            }
            persistSessionIfConfigured();
        }

        private void completePrompt(HeadlessCodingSessionRuntime.PromptControl promptControl) {
            CodeCommandOptions deferredOptions = null;
            synchronized (this) {
                if (activePrompt == promptControl) {
                    activePrompt = null;
                }
                if (pendingRuntimeOptions != null) {
                    deferredOptions = pendingRuntimeOptions;
                    pendingRuntimeOptions = null;
                }
            }
            if (deferredOptions == null || deferredOptions != options || sameRuntimeConfig(runtimeOptions, deferredOptions)) {
                return;
            }
            try {
                rebindSession(deferredOptions);
            } catch (Exception ex) {
                rollbackDeferredSessionOptions(deferredOptions, ex);
            }
        }

        private void rollbackDeferredSessionOptions(CodeCommandOptions deferredOptions, Exception ex) {
            CodeCommandOptions boundOptions = runtimeOptions;
            boolean modeChanged = boundOptions != null
                    && deferredOptions != null
                    && boundOptions.getApprovalMode() != deferredOptions.getApprovalMode();
            synchronized (this) {
                if (options == deferredOptions) {
                    options = boundOptions;
                }
            }
            if (modeChanged) {
                emitModeUpdate();
            }
            emitConfigOptionUpdate();
            logError("Failed to apply deferred ACP session configuration: " + safeMessage(ex));
        }

        private boolean hasActivePrompt() {
            return activePrompt != null && !activePrompt.isCancelled();
        }

        private boolean sameRuntimeConfig(CodeCommandOptions left, CodeCommandOptions right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            return samePlatform(left.getProvider(), right.getProvider())
                    && sameProtocol(left.getProtocol(), right.getProtocol())
                    && sameValue(left.getModel(), right.getModel())
                    && sameValue(left.getApiKey(), right.getApiKey())
                    && sameValue(left.getBaseUrl(), right.getBaseUrl())
                    && left.getApprovalMode() == right.getApprovalMode();
        }

        private boolean samePlatform(PlatformType left, PlatformType right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            return sameValue(left.getPlatform(), right.getPlatform());
        }

        private boolean sameProtocol(CliProtocol left, CliProtocol right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            return sameValue(left.getValue(), right.getValue());
        }

        private boolean sameValue(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }

        private ProviderProfileMutation parseProviderProfileMutation(String rawArguments) {
            if (isBlank(rawArguments)) {
                return null;
            }
            String[] tokens = rawArguments.trim().split("\\s+");
            if (tokens.length == 0 || isBlank(tokens[0])) {
                return null;
            }
            ProviderProfileMutation mutation = new ProviderProfileMutation(tokens[0].trim());
            for (int i = 1; i < tokens.length; i++) {
                String token = tokens[i];
                if ("--provider".equalsIgnoreCase(token)) {
                    String value = requireProviderMutationValue(tokens, ++i);
                    if (value == null) {
                        return null;
                    }
                    mutation.provider = value;
                    continue;
                }
                if ("--protocol".equalsIgnoreCase(token)) {
                    String value = requireProviderMutationValue(tokens, ++i);
                    if (value == null) {
                        return null;
                    }
                    mutation.protocol = value;
                    continue;
                }
                if ("--model".equalsIgnoreCase(token)) {
                    String value = requireProviderMutationValue(tokens, ++i);
                    if (value == null) {
                        return null;
                    }
                    mutation.model = value;
                    mutation.clearModel = false;
                    continue;
                }
                if ("--base-url".equalsIgnoreCase(token)) {
                    String value = requireProviderMutationValue(tokens, ++i);
                    if (value == null) {
                        return null;
                    }
                    mutation.baseUrl = value;
                    mutation.clearBaseUrl = false;
                    continue;
                }
                if ("--api-key".equalsIgnoreCase(token)) {
                    String value = requireProviderMutationValue(tokens, ++i);
                    if (value == null) {
                        return null;
                    }
                    mutation.apiKey = value;
                    mutation.clearApiKey = false;
                    continue;
                }
                if ("--clear-model".equalsIgnoreCase(token)) {
                    mutation.model = null;
                    mutation.clearModel = true;
                    continue;
                }
                if ("--clear-base-url".equalsIgnoreCase(token)) {
                    mutation.baseUrl = null;
                    mutation.clearBaseUrl = true;
                    continue;
                }
                if ("--clear-api-key".equalsIgnoreCase(token)) {
                    mutation.apiKey = null;
                    mutation.clearApiKey = true;
                    continue;
                }
                return null;
            }
            return mutation;
        }

        private String requireProviderMutationValue(String[] tokens, int index) {
            if (tokens == null || index < 0 || index >= tokens.length || isBlank(tokens[index])) {
                return null;
            }
            return tokens[index].trim();
        }

        private PlatformType parseProviderType(String raw) {
            if (isBlank(raw)) {
                return null;
            }
            for (PlatformType platformType : PlatformType.values()) {
                if (platformType.getPlatform().equalsIgnoreCase(raw.trim())) {
                    return platformType;
                }
            }
            return null;
        }

        private CliProtocol parseProviderProtocol(String raw) {
            if (isBlank(raw)) {
                return null;
            }
            try {
                return CliProtocol.parse(raw);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        private boolean isSupportedProviderProtocol(PlatformType provider, CliProtocol protocol) {
            if (provider == null || protocol == null || protocol == CliProtocol.CHAT) {
                return true;
            }
            return provider == PlatformType.OPENAI || provider == PlatformType.DOUBAO || provider == PlatformType.DASHSCOPE;
        }

        private String normalizeStoredProtocol(String raw, PlatformType provider, String baseUrl) {
            if (isBlank(raw)) {
                return null;
            }
            return CliProtocol.resolveConfigured(raw, provider, baseUrl).getValue();
        }

        private CliProtocol currentProtocol() {
            return prepared == null ? null : prepared.getProtocol();
        }

        private String maskSecret(String value) {
            if (isBlank(value)) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.length() <= 8) {
                return "****";
            }
            return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
        }

        private Map<String, Object> toHistoryUpdate(SessionEvent event) {
            if (event == null || event.getType() == null) {
                return null;
            }
            if (event.getType() == SessionEventType.USER_MESSAGE) {
                return newMap(
                        "sessionUpdate", "user_message_chunk",
                        "content", textContent(payloadText(event, "input", event.getSummary()))
                );
            }
            if (event.getType() == SessionEventType.ASSISTANT_MESSAGE) {
                String kind = event.getPayload() == null ? null : String.valueOf(event.getPayload().get("kind"));
                return newMap(
                        "sessionUpdate", "reasoning".equals(kind) ? "agent_thought_chunk" : "agent_message_chunk",
                        "content", textContent(payloadText(event, "output", event.getSummary()))
                );
            }
            if (event.getType() == SessionEventType.TOOL_CALL) {
                return newMap(
                        "sessionUpdate", "tool_call",
                        "toolCallId", payloadText(event, "callId", null),
                        "title", payloadText(event, "tool", event.getSummary()),
                        "kind", mapToolKind(payloadText(event, "tool", null)),
                        "status", "pending",
                        "rawInput", newMap(
                                "tool", payloadText(event, "tool", null),
                                "arguments", payloadJsonText(event, "arguments")
                        )
                );
            }
            if (event.getType() == SessionEventType.TOOL_RESULT) {
                return newMap(
                        "sessionUpdate", "tool_call_update",
                        "toolCallId", payloadText(event, "callId", null),
                        "status", "completed",
                        "content", toolCallTextContent(payloadText(event, "output", event.getSummary()))
                );
            }
            if (event.getType() == SessionEventType.TASK_CREATED) {
                return newMap(
                        "sessionUpdate", "tool_call",
                        "toolCallId", payloadText(event, "callId", payloadText(event, "taskId", null)),
                        "title", payloadText(event, "title", event.getSummary()),
                        "kind", "other",
                        "status", mapTaskStatus(payloadText(event, "status", null)),
                        "rawInput", buildTaskRawInput(event)
                );
            }
            if (event.getType() == SessionEventType.TASK_UPDATED) {
                String text = buildTaskUpdateText(event);
                return newMap(
                        "sessionUpdate", "tool_call_update",
                        "toolCallId", payloadText(event, "callId", payloadText(event, "taskId", null)),
                        "status", mapTaskStatus(payloadText(event, "status", null)),
                        "content", toolCallTextContent(text),
                        "rawOutput", buildTaskRawOutput(event)
                );
            }
            if (event.getType() == SessionEventType.TEAM_MESSAGE) {
                return toTeamMessageAcpUpdate(event);
            }
            if (event.getType() == SessionEventType.ERROR) {
                return newMap(
                        "sessionUpdate", "agent_message_chunk",
                        "content", textContent(payloadText(event, "error", event.getSummary()))
                );
            }
            if (event.getType() == SessionEventType.AUTO_CONTINUE
                    || event.getType() == SessionEventType.AUTO_STOP
                    || event.getType() == SessionEventType.BLOCKED) {
                return newMap(
                        "sessionUpdate", "agent_message_chunk",
                        "content", textContent(firstNonBlank(event.getSummary(), event.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')))
                );
            }
            return null;
        }

        private String payloadText(SessionEvent event, String key, String defaultValue) {
            if (event == null || event.getPayload() == null || key == null) {
                return defaultValue;
            }
            Object value = event.getPayload().get(key);
            return value == null ? defaultValue : String.valueOf(value);
        }

        private Object payloadJsonText(SessionEvent event, String key) {
            return parseJsonOrText(payloadText(event, key, null));
        }

        private CodingTaskSessionEventBridge registerTaskEventBridge() {
            if (codingRuntime == null || sessionManager == null) {
                return null;
            }
            CodingTaskSessionEventBridge bridge = new CodingTaskSessionEventBridge(sessionManager, new CodingTaskSessionEventBridge.SessionEventConsumer() {
                @Override
                public void onEvent(SessionEvent event) {
                    if (event == null || !session.getSessionId().equals(event.getSessionId())) {
                        return;
                    }
                    Map<String, Object> update = toHistoryUpdate(event);
                    if (update != null) {
                        sendSessionUpdate(session.getSessionId(), update);
                    }
                }
            });
            codingRuntime.addListener(bridge);
            return bridge;
        }

        private String mapTaskStatus(String status) {
            String normalized = trimToNull(status);
            if (normalized == null) {
                return "pending";
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            if ("running".equals(lower) || "in_progress".equals(lower) || "in-progress".equals(lower) || "started".equals(lower)) {
                return "in_progress";
            }
            if ("completed".equals(lower)) {
                return "completed";
            }
            if ("fallback".equals(lower)) {
                return "completed";
            }
            if ("failed".equals(lower) || "cancelled".equals(lower) || "canceled".equals(lower) || "error".equals(lower)) {
                return "failed";
            }
            return "pending";
        }

        @Override
        public void close() {
            CodingRuntime currentRuntime = codingRuntime;
            CodingTaskSessionEventBridge currentBridge = taskEventBridge;
            CodingCliAgentFactory.PreparedCodingAgent currentPrepared = prepared;
            ManagedCodingSession currentSession = session;
            if (currentRuntime != null && currentBridge != null) {
                currentRuntime.removeListener(currentBridge);
            }
            cancel();
            if (currentPrepared != null && currentPrepared.getMcpRuntimeManager() != null) {
                currentPrepared.getMcpRuntimeManager().close();
            }
            if (currentSession != null) {
                currentSession.close();
            }
        }

        private final class ProviderProfileMutation {
            private final String profileName;
            private String provider;
            private String protocol;
            private String model;
            private boolean clearModel;
            private String baseUrl;
            private boolean clearBaseUrl;
            private String apiKey;
            private boolean clearApiKey;

            private ProviderProfileMutation(String profileName) {
                this.profileName = profileName;
            }

            private boolean hasAnyFieldChanges() {
                return provider != null
                        || protocol != null
                        || model != null
                        || clearModel
                        || baseUrl != null
                        || clearBaseUrl
                        || apiKey != null
                        || clearApiKey;
            }
        }
    }

    private final class AcpTurnObserver extends HeadlessTurnObserver.Adapter {

        private final String sessionId;

        private AcpTurnObserver(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public void onTurnStarted(ManagedCodingSession session, String turnId, String input) {
            sendSessionUpdate(sessionId, newMap(
                    "sessionUpdate", "user_message_chunk",
                    "content", textContent(input)
            ));
        }

        @Override
        public void onReasoningDelta(ManagedCodingSession session, String turnId, Integer step, String delta) {
            sendSessionUpdate(sessionId, newMap(
                    "sessionUpdate", "agent_thought_chunk",
                    "content", textContent(delta)
            ));
        }

        @Override
        public void onAssistantDelta(ManagedCodingSession session, String turnId, Integer step, String delta) {
            sendSessionUpdate(sessionId, newMap(
                    "sessionUpdate", "agent_message_chunk",
                    "content", textContent(delta)
            ));
        }

        @Override
        public void onToolCall(ManagedCodingSession session, String turnId, Integer step, AgentToolCall call) {
            sendSessionUpdate(sessionId, newMap(
                    "sessionUpdate", "tool_call",
                    "toolCallId", call == null ? UUID.randomUUID().toString() : firstNonBlank(call.getCallId(), UUID.randomUUID().toString()),
                    "title", call == null ? "tool" : firstNonBlank(call.getName(), "tool"),
                    "kind", mapToolKind(call == null ? null : call.getName()),
                    "status", "pending",
                    "rawInput", newMap(
                            "tool", call == null ? null : call.getName(),
                            "arguments", parseJsonOrText(call == null ? null : call.getArguments())
                    )
            ));
        }

        @Override
        public void onToolResult(ManagedCodingSession session,
                                 String turnId,
                                 Integer step,
                                 AgentToolCall call,
                                 AgentToolResult result,
                                 boolean failed) {
            sendSessionUpdate(sessionId, newMap(
                    "sessionUpdate", "tool_call_update",
                    "toolCallId", result == null ? null : firstNonBlank(result.getCallId(), call == null ? null : call.getCallId()),
                    "status", failed ? "failed" : "completed",
                    "content", toolCallTextContent(result == null ? null : result.getOutput()),
                    "rawOutput", newMap("text", result == null ? null : result.getOutput())
            ));
        }

        @Override
        public void onTurnError(ManagedCodingSession session, String turnId, Integer step, String message) {
            sendSessionUpdate(sessionId, newMap(
                    "sessionUpdate", "agent_message_chunk",
                    "content", textContent(message)
            ));
        }

        @Override
        public void onSessionEvent(ManagedCodingSession session, SessionEvent event) {
            Map<String, Object> update = toStructuredSessionUpdate(event);
            if (update != null) {
                sendSessionUpdate(sessionId, update);
            }
        }
    }
}
