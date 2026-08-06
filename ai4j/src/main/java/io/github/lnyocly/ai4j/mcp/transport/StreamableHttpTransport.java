package io.github.lnyocly.ai4j.mcp.transport;

import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpRequest;
import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.util.McpMessageCodec;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streamable HTTP传输层实现
 * 支持MCP 2025-03-26规范的Streamable HTTP传输
 */
public class StreamableHttpTransport implements McpTransport {
    
    private static final Logger log = LoggerFactory.getLogger(StreamableHttpTransport.class);
    
    private final String mcpEndpointUrl;
    private final OkHttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private McpMessageHandler messageHandler;
    private EventSource eventSource;
    private String sessionId;
    private String lastEventId;
    private final McpProtocolProfile configuredProtocolProfile;
    private volatile McpProtocolProfile negotiatedProtocolProfile;
    /**
     * 自定义HTTP头（用于认证等）
     */
    private Map<String, String> headers;
    public StreamableHttpTransport(String mcpEndpointUrl) {
        this.mcpEndpointUrl = mcpEndpointUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.configuredProtocolProfile = McpProtocolProfile.AUTO;
    }
    public StreamableHttpTransport(TransportConfig config) {
        this.mcpEndpointUrl = config.getUrl();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.headers = config.getHeaders();
        this.configuredProtocolProfile = config.getProtocolProfile() == null
                ? McpProtocolProfile.AUTO : config.getProtocolProfile();
    }
    
    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (running.compareAndSet(false, true)) {
                    log.info("启动Streamable HTTP传输层，连接到: {}", mcpEndpointUrl);
                    if (messageHandler != null) {
                        messageHandler.onConnected();
                    }
                }
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (running.compareAndSet(true, false)) {
                    log.info("停止Streamable HTTP传输层");
                    if (eventSource != null) {
                        eventSource.cancel();
                        eventSource = null;
                    }
                    sessionId = null;
                    lastEventId = null;
                    if (configuredProtocolProfile.isAutomatic()) {
                        negotiatedProtocolProfile = null;
                    }
                    if (messageHandler != null) {
                        messageHandler.onDisconnected("传输层停止");
                    }
                }
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(McpMessage message) {
        return sendMessage(message, null);
    }

    @Override
    public CompletableFuture<Void> sendMessage(McpMessage message, final Map<String, String> requestHeaders) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (!running.get()) {
                    throw new IllegalStateException("Streamable HTTP传输层未启动");
                }
                McpProtocolProfile activeProfile = requireNegotiatedProfile();
            
            try {
                String jsonMessage = JSON.toJSONString(message);
                log.debug("发送消息到MCP端点: {}", jsonMessage);
                
                RequestBody body = RequestBody.create(
                    MediaType.get("application/json"), 
                    jsonMessage
                );

                Request.Builder requestBuilder = new Request.Builder()
                        .url(mcpEndpointUrl)
                        .post(body);
                
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        requestBuilder.header(entry.getKey(), entry.getValue());
                    }
                }

                if (activeProfile.isModern()) {
                    applyModernRequestHeaders(message, requestBuilder, activeProfile);
                    if (requestHeaders != null) {
                        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                            if (entry.getKey() != null && entry.getKey().regionMatches(
                                    true, 0, "Mcp-Param-", 0, "Mcp-Param-".length())
                                    && entry.getValue() != null) {
                                requestBuilder.header(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                } else {
                    if (sessionId != null) {
                        requestBuilder.header("mcp-session-id", sessionId);
                    }
                    if (lastEventId != null) {
                        requestBuilder.header("last-event-id", lastEventId);
                    }
                    if (activeProfile.requiresLegacyProtocolVersionHeader()
                            && !"initialize".equals(message.getMethod())) {
                        requestBuilder.header("MCP-Protocol-Version", activeProfile.getProtocolVersion());
                    }
                }

                // Mandatory Streamable HTTP headers cannot be weakened by custom auth headers.
                requestBuilder.header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream");

                Request request = requestBuilder.build();
                
                    Response response = httpClient.newCall(request).execute();
                    try {
                        if (!response.isSuccessful()) {
                        String responseBody = response.body() == null ? "" : response.body().string();
                        if (dispatchHttpErrorResponse(responseBody)) {
                            return;
                        }
                        throw new IOException(McpTransportSupport.buildHttpFailureMessage(
                                response.code(), response.message(), responseBody));
                        }
                    
                    // 检查会话ID
                    if (!activeProfile.isModern()) {
                        String newSessionId = response.header("mcp-session-id");
                        if (newSessionId != null) {
                            StreamableHttpTransport.this.sessionId = newSessionId;
                            log.debug("收到会话ID: {}", StreamableHttpTransport.this.sessionId);
                        }
                    }
                    
                    String contentType = response.header("Content-Type", "");
                    
                    if (contentType.startsWith("text/event-stream")) {
                        // 服务器选择SSE流响应
                        handleSseResponse(response);
                    } else if (contentType.startsWith("application/json")) {
                        // 服务器选择单一JSON响应
                        handleJsonResponse(response);
                    } else {
                        log.warn("未知的响应内容类型: {}", contentType);
                    }
                } finally {
                    response.close();
                }
                
            } catch (Exception e) {
                log.debug("发送Streamable HTTP消息失败: {}", McpTransportSupport.safeMessage(e), e);
                if (messageHandler != null) {
                    messageHandler.onError(e);
                }
                throw new RuntimeException(McpTransportSupport.safeMessage(e), e);
                }
            }
        });
    }
    
    /**
     * 处理JSON响应
     */
    private void handleJsonResponse(Response response) throws IOException {
        if (response.body() == null) return;

        String responseBody = response.body().string();
        log.debug("收到JSON响应: {}", responseBody);

        // 如果响应体是空或只含空白，直接跳过
        // initialized 会返回202 Accepted，一个空体，不需要解析
        if (responseBody == null || responseBody.trim().isEmpty()) {
            log.debug("空的 JSON 响应，忽略");
            return;
        }

        try {
            McpMessage message = parseMcpMessage(responseBody);
            if (messageHandler != null) {
                messageHandler.handleMessage(message);
            }
        } catch (Exception e) {
            log.debug("解析JSON响应失败: {}", McpTransportSupport.safeMessage(e), e);
            if (messageHandler != null) {
                messageHandler.onError(e);
            }
        }
    }
    
    /**
     * 处理SSE流响应
     */
    private void handleSseResponse(Response response) {
        log.debug("服务器升级到SSE流");

        try {
            // 直接处理当前响应的SSE流
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            StringBuilder dataBuilder = new StringBuilder();
            boolean hasData = false;
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (hasData) {
                        dispatchSseEvent(dataBuilder.toString());
                    }
                    dataBuilder.setLength(0);
                    hasData = false;
                    continue;
                }

                if (line.startsWith(":")) {
                    continue;
                }

                int separatorIndex = line.indexOf(':');
                String field = separatorIndex >= 0 ? line.substring(0, separatorIndex) : line;
                String value = separatorIndex >= 0 ? line.substring(separatorIndex + 1) : "";
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }

                if ("data".equals(field)) {
                    if (hasData) {
                        dataBuilder.append('\n');
                    }
                    dataBuilder.append(value);
                    hasData = true;
                } else if (!getProtocolProfile().isModern() && "id".equals(field)) {
                    lastEventId = value.isEmpty() ? null : value;
                }
            }

            if (hasData) {
                dispatchSseEvent(dataBuilder.toString());
            }
        } catch (Exception e) {
            log.debug("处理SSE响应失败: {}", McpTransportSupport.safeMessage(e), e);
            if (messageHandler != null) {
                messageHandler.onError(e);
            }
        }
    }

    private void dispatchSseEvent(String data) {
        if (data == null || data.trim().isEmpty()) {
            return;
        }

        try {
            McpMessage message = parseMcpMessage(data);
            if (messageHandler != null) {
                messageHandler.handleMessage(message);
            }
        } catch (Exception e) {
            log.debug("解析SSE数据失败: {} -> {}", McpTransportSupport.clip(data, 120), McpTransportSupport.safeMessage(e), e);
        }
    }
    

    
    @Override
    public void setMessageHandler(McpMessageHandler handler) {
        this.messageHandler = handler;
    }
    
    @Override
    public boolean isConnected() {
        return running.get();
    }

    @Override
    public boolean needsHeartbeat() {
        return !getProtocolProfile().isModern();
    }

    @Override
    public String getTransportType() {
        return "streamable_http";
    }

    @Override
    public McpProtocolProfile getProtocolProfile() {
        McpProtocolProfile resolved = negotiatedProtocolProfile;
        return resolved == null ? configuredProtocolProfile : resolved;
    }

    /**
     * Performs the only safe automatic probe: {@code server/discover}. A
     * recognized modern error is evidence of the modern era and must never be
     * retried as {@code initialize}. A method-not-found response to this
     * mandatory modern method, or an otherwise unrecognized HTTP 400
     * compatibility response, selects the initialization-era profile.
     */
    @Override
    public CompletableFuture<McpProtocolProfile> negotiateProtocol(final String clientName,
                                                                     final String clientVersion) {
        if (!configuredProtocolProfile.isAutomatic()) {
            return CompletableFuture.completedFuture(configuredProtocolProfile);
        }
        return CompletableFuture.supplyAsync(() -> {
            synchronized (StreamableHttpTransport.this) {
                if (!running.get()) {
                    throw new IllegalStateException("Streamable HTTP transport is not started");
                }
                if (negotiatedProtocolProfile != null) {
                    return negotiatedProtocolProfile;
                }
                negotiatedProtocolProfile = probeProtocol(clientName, clientVersion);
                log.debug("Streamable HTTP protocol profile selected: {}", negotiatedProtocolProfile);
                return negotiatedProtocolProfile;
            }
        });
    }

    private McpProtocolProfile requireNegotiatedProfile() {
        McpProtocolProfile profile = getProtocolProfile();
        if (!profile.isAutomatic()) {
            return profile;
        }
        try {
            return negotiateProtocol("ai4j-mcp-transport", "1.0.0")
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to negotiate Streamable HTTP protocol", e);
        }
    }

    private McpProtocolProfile probeProtocol(String clientName, String clientVersion) {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("io.modelcontextprotocol/protocolVersion",
                McpProtocolProfile.MODERN_2026_07_28.getProtocolVersion());
        Map<String, Object> clientInfo = new HashMap<String, Object>();
        clientInfo.put("name", clientName == null ? "ai4j" : clientName);
        clientInfo.put("version", clientVersion == null ? "unknown" : clientVersion);
        metadata.put("io.modelcontextprotocol/clientInfo", clientInfo);
        metadata.put("io.modelcontextprotocol/clientCapabilities", new HashMap<String, Object>());

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("_meta", metadata);
        McpRequest requestMessage = new McpRequest("server/discover", Long.valueOf(0L), params);
        RequestBody body = RequestBody.create(MediaType.get("application/json"), JSON.toJSONString(requestMessage));
        Request.Builder requestBuilder = new Request.Builder().url(mcpEndpointUrl).post(body);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }
        requestBuilder.header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", McpProtocolProfile.MODERN_2026_07_28.getProtocolVersion())
                .header("Mcp-Method", "server/discover");

        try {
            Response response = httpClient.newCall(requestBuilder.build()).execute();
            try {
                String responseBody = readProbeResponse(response);
                if (response.isSuccessful()) {
                    return classifySuccessfulProbe(responseBody);
                }
                if (isLegacyDiscoveryError(responseBody)) {
                    return McpProtocolProfile.LEGACY_2025_11_25;
                }
                throw new IllegalStateException(McpTransportSupport.buildHttpFailureMessage(
                        response.code(), response.message(), responseBody));
            } finally {
                response.close();
            }
        } catch (IOException e) {
            throw new IllegalStateException("MCP protocol discovery failed: "
                    + McpTransportSupport.safeMessage(e), e);
        }
    }

    private static String readProbeResponse(Response response) throws IOException {
        if (response.body() == null) {
            return "";
        }
        String contentType = response.header("Content-Type", "");
        if (contentType.startsWith("text/event-stream")) {
            return readFirstSseData(response);
        }
        return response.body().string();
    }

    private static String readFirstSseData(Response response) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
        StringBuilder data = new StringBuilder();
        boolean hasData = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (hasData) {
                    return data.toString();
                }
                continue;
            }
            if (line.startsWith("data:")) {
                String value = line.substring("data:".length());
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if (hasData) {
                    data.append('\n');
                }
                data.append(value);
                hasData = true;
            }
        }
        return hasData ? data.toString() : "";
    }

    private McpProtocolProfile classifySuccessfulProbe(String responseBody) {
        McpMessage response;
        try {
            response = parseMcpMessage(responseBody);
        } catch (Exception ignored) {
            // A successful response that is not an MCP discovery result is not
            // evidence of a legacy endpoint. Do not turn proxy corruption into
            // an initialize retry.
            throw new IllegalStateException("MCP server did not return a usable modern discovery result");
        }
        if (response.isSuccessResponse() && supportsModernVersion(response.getResult())) {
            return McpProtocolProfile.MODERN_2026_07_28;
        }
        if (response.isErrorResponse() && isLegacyDiscoveryError(responseBody)) {
            // server/discover is mandatory in the 2026 era. A JSON-RPC
            // method-not-found reply is explicit legacy evidence.
            return McpProtocolProfile.LEGACY_2025_11_25;
        }
        if (response.isErrorResponse()) {
            throw new IllegalStateException(
                    "MCP server returned a JSON-RPC error during protocol discovery");
        }
        throw new IllegalStateException("MCP server did not return a usable modern discovery result");
    }

    private static boolean supportsModernVersion(Object result) {
        Map<String, Object> values = asMap(result);
        if (values == null || !(values.get("supportedVersions") instanceof Iterable<?>)) {
            return false;
        }
        for (Object version : (Iterable<?>) values.get("supportedVersions")) {
            if (McpProtocolProfile.MODERN_2026_07_28.getProtocolVersion().equals(String.valueOf(version))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLegacyDiscoveryError(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return false;
        }
        try {
            McpMessage response = parseMcpMessage(responseBody);
            if (!response.isErrorResponse() || response.getError() == null
                    || response.getError().getCode() == null) {
                return false;
            }
            return response.getError().getCode().intValue() == -32601;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 获取会话ID
     */
    public String getSessionId() {
        return getProtocolProfile().isModern() ? null : sessionId;
    }

    /** Records the concrete legacy version chosen by a successful initialize response. */
    public synchronized void acceptLegacyProtocolVersion(String protocolVersion) {
        McpProtocolProfile selectedProfile = McpProtocolProfile.fromProtocolVersion(protocolVersion);
        if (!configuredProtocolProfile.acceptsLegacyVersion(selectedProfile)) {
            throw new IllegalStateException("MCP server selected unsupported legacy protocol version: "
                    + protocolVersion);
        }
        if (sessionId == null) {
            throw new IllegalStateException("Legacy MCP initialize response did not provide mcp-session-id");
        }
        negotiatedProtocolProfile = selectedProfile;
    }
    
    /**
     * 终止会话
     */
    public CompletableFuture<Void> terminateSession() {
        if (getProtocolProfile().isModern()) {
            CompletableFuture<Void> future = new CompletableFuture<Void>();
            future.completeExceptionally(new UnsupportedOperationException(
                    "Modern Streamable HTTP does not define protocol sessions"));
            return future;
        }
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (sessionId != null) {
                    try {
                        Request.Builder builder = new Request.Builder();
                        if (headers != null) {
                            for (Map.Entry<String, String> entry : headers.entrySet()) {
                                builder.header(entry.getKey(), entry.getValue());
                            }
                        }
                        Request.Builder requestBuilder = builder
                                .url(mcpEndpointUrl)
                                .delete()
                                .header("mcp-session-id", sessionId);
                        McpProtocolProfile profile = getProtocolProfile();
                        if (profile.requiresLegacyProtocolVersionHeader()) {
                            requestBuilder.header("MCP-Protocol-Version", profile.getProtocolVersion());
                        }
                        Request request = requestBuilder.build();

                        Response response = httpClient.newCall(request).execute();
                        try {
                            log.info("会话已终止: {}", sessionId);
                        } finally {
                            response.close();
                        }
                    } catch (Exception e) {
                        log.warn("终止会话失败", e);
                    } finally {
                        sessionId = null;
                    }
                }
            }
        });
    }

    public static McpMessage parseMcpMessage(String jsonString) {
        return McpMessageCodec.parseMessage(jsonString);
    }

    private void applyModernRequestHeaders(McpMessage message, Request.Builder requestBuilder,
                                           McpProtocolProfile activeProfile) {
        if (message == null || (!message.isRequest() && !message.isNotification())) {
            throw new IllegalArgumentException("Modern Streamable HTTP accepts only JSON-RPC requests or notifications");
        }
        Map<String, Object> params = asMap(message.getParams());
        Map<String, Object> metadata = params == null ? null : asMap(params.get("_meta"));
        String version = metadata == null ? null
                : stringValue(metadata.get("io.modelcontextprotocol/protocolVersion"));
        if (!activeProfile.getProtocolVersion().equals(version)) {
            throw new IllegalArgumentException("Modern MCP request metadata must declare "
                    + activeProfile.getProtocolVersion());
        }

        requestBuilder.header("MCP-Protocol-Version", version);
        requestBuilder.header("Mcp-Method", message.getMethod());
        if (requiresNameHeader(message.getMethod())) {
            String name = "resources/read".equals(message.getMethod())
                    ? stringValue(params.get("uri")) : stringValue(params.get("name"));
            if (name == null) {
                throw new IllegalArgumentException("Modern MCP request requires a name header");
            }
            requestBuilder.header("Mcp-Name", encodeHeaderValue(name));
        }
    }

    private boolean dispatchHttpErrorResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return false;
        }
        try {
            McpMessage message = parseMcpMessage(responseBody);
            if (!message.isResponse()) {
                return false;
            }
            if (messageHandler != null) {
                messageHandler.handleMessage(message);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean requiresNameHeader(String method) {
        return "tools/call".equals(method) || "resources/read".equals(method)
                || "prompts/get".equals(method);
    }

    private static String encodeHeaderValue(String value) {
        boolean safe = value.length() > 0 && value.equals(value.trim());
        for (int i = 0; i < value.length() && safe; i++) {
            char c = value.charAt(i);
            safe = c >= 0x21 && c <= 0x7e;
        }
        if (safe && !(value.startsWith("=?base64?") && value.endsWith("?="))) {
            return value;
        }
        return "=?base64?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> map = new HashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() != null) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return map;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}
