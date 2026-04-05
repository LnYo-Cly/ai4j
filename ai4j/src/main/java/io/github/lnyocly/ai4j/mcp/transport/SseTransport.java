package io.github.lnyocly.ai4j.mcp.transport;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.entity.*;
import io.github.lnyocly.ai4j.mcp.util.McpMessageCodec;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class SseTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(SseTransport.class);

    private final String sseEndpointUrl;
    private final OkHttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, String> initialQueryParams = new java.util.LinkedHashMap<>();

    private McpMessageHandler messageHandler;
    private String messageEndpointUrl; // 从endpoint事件中获取
    private volatile boolean endpointReceived = false;
    private volatile HttpURLConnection sseConnection;
    private volatile InputStream sseInputStream;
    private volatile Thread sseReaderThread;
    /**
     * 自定义HTTP头（用于认证等）
     */
    private Map<String, String> headers;

    public SseTransport(String sseEndpointUrl) {
        parseInitialQueryParams(sseEndpointUrl);
        this.sseEndpointUrl = sseEndpointUrl;
        this.httpClient = createDefaultHttpClient();
    }

    public SseTransport(TransportConfig config) {
        parseInitialQueryParams(config.getUrl());
        this.sseEndpointUrl = config.getUrl();
        this.httpClient = createDefaultHttpClient();
        this.headers = config.getHeaders();
    }

    public SseTransport(String sseEndpointUrl, OkHttpClient httpClient) {
        parseInitialQueryParams(sseEndpointUrl);
        this.sseEndpointUrl = sseEndpointUrl;
        this.httpClient = httpClient;
    }

    private void parseInitialQueryParams(String url) {
        try {
            HttpUrl httpUrl = HttpUrl.get(url);
            if (httpUrl != null) {
                // HttpUrl.queryParameterNames() 返回 Set<String>
                for (String name : httpUrl.queryParameterNames()) {
                    // 取第一个值（如有多值，可按需改为 list）
                    String value = httpUrl.queryParameter(name);
                    if (value != null) {
                        initialQueryParams.put(name, value);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析 SSE URL 查询参数失败：{}", e.getMessage());
        }
    }

    private static OkHttpClient createDefaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // SSE需要长连接
                .writeTimeout(60, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // 每30秒发送一个 ping 帧来保持连接
                .build();
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(false, true)) {
                log.debug("启动SSE传输层，连接到: {}", sseEndpointUrl);

                try {
                    // 建立SSE连接
                    startSseConnection();

                    // 等待endpoint事件
                    waitForEndpointEvent();

                    if (messageHandler != null) {
                        messageHandler.onConnected();
                    }
                } catch (Exception e) {
                    running.set(false);
                    closeSseResources();
                    waitForReaderThreadToExit();
                    log.debug("启动SSE传输层失败: {}", McpTransportSupport.safeMessage(e), e);
                    if (messageHandler != null) {
                        messageHandler.onError(e);
                    }
                    throw new RuntimeException(McpTransportSupport.safeMessage(e), e);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(true, false)) {
                log.debug("停止SSE传输层");

                closeSseResources();

                endpointReceived = false;
                messageEndpointUrl = null;

                if (messageHandler != null) {
                    messageHandler.onDisconnected("Transport stopped");
                }

                log.debug("SSE传输层已停止");
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendMessage(McpMessage message) {
        return CompletableFuture.runAsync(() -> {
            if (!running.get()) {
                throw new IllegalStateException("SSE传输层未启动");
            }

            if (!endpointReceived || messageEndpointUrl == null) {
                throw new IllegalStateException("尚未收到服务器endpoint事件，无法发送消息");
            }

            try {
                String jsonMessage = JSON.toJSONString(message);
                log.debug("通过POST发送消息到 {}: {}", messageEndpointUrl, jsonMessage);

                // 从消息端点URL中提取会话ID
                String sessionId = extractSessionId(messageEndpointUrl);
                HttpURLConnection connection = null;
                try {
                    connection = openPostConnection(messageEndpointUrl, sessionId);
                    byte[] requestBytes = jsonMessage.getBytes(StandardCharsets.UTF_8);
                    OutputStream outputStream = connection.getOutputStream();
                    try {
                        outputStream.write(requestBytes);
                        outputStream.flush();
                    } finally {
                        outputStream.close();
                    }

                    int statusCode = connection.getResponseCode();
                    String responseMessage = connection.getResponseMessage();
                    String responseBody = McpTransportSupport.readResponseBody(connection, statusCode);
                    int responseLength = responseBody == null ? 0 : responseBody.length();

                    log.debug("HTTP响应: 状态码={}, 消息={}, 响应体长度={}",
                            statusCode, responseMessage, responseLength);
                    if (responseLength > 0) {
                        log.debug("HTTP响应体: {}", responseBody);
                    }

                    if ((statusCode < 200 || statusCode >= 300) && statusCode != 202 && statusCode != 204) {
                        throw new IOException(McpTransportSupport.buildHttpFailureMessage(statusCode, responseMessage, responseBody));
                    }

                    if (statusCode == 202) {
                        log.debug("消息已提交异步处理，状态码: {}", statusCode);
                    } else if (statusCode == 204) {
                        log.debug("消息已接收，将通过SSE异步响应，状态码: {}", statusCode);
                    } else {
                        log.debug("消息发送成功，状态码: {}", statusCode);
                    }
                } finally {
                    disconnectQuietly(connection);
                }

            } catch (Exception e) {
                log.debug("发送消息失败: {}", McpTransportSupport.safeMessage(e), e);
                if (messageHandler != null) {
                    messageHandler.onError(e);
                }
                throw new RuntimeException(McpTransportSupport.safeMessage(e), e);
            }
        });
    }

    @Override
    public void setMessageHandler(McpMessageHandler handler) {
        this.messageHandler = handler;
    }

    @Override
    public boolean isConnected() {
        return running.get() && sseReaderThread != null && sseReaderThread.isAlive() && endpointReceived;
    }

    @Override
    public boolean needsHeartbeat() {
        return false;
    }

    @Override
    public String getTransportType() {
        return "sse";
    }

    /**
     * 启动SSE连接
     */
    private void startSseConnection() {
        Thread readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                readEventStream();
            }
        }, "mcp-sse-reader-" + Integer.toHexString(System.identityHashCode(this)));
        readerThread.setDaemon(true);
        this.sseReaderThread = readerThread;
        readerThread.start();

        log.debug("SSE连接读取线程已启动: {}", sseEndpointUrl);
    }

    /**
     * 等待endpoint事件
     */
    private void waitForEndpointEvent() {
        int maxWaitSeconds = 10;
        int waitedSeconds = 0;

        while (!endpointReceived && waitedSeconds < maxWaitSeconds && running.get()) {
            try {
                Thread.sleep(1000);
                waitedSeconds++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待endpoint事件被中断", e);
            }
        }

        if (!endpointReceived) {
            throw new RuntimeException("超时等待服务器endpoint事件");
        }

        log.debug("已收到endpoint事件，消息端点: {}", messageEndpointUrl);
    }

    private void readEventStream() {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        BufferedReader reader = null;

        try {
            connection = openSseConnection();
            inputStream = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            this.sseConnection = connection;
            this.sseInputStream = inputStream;

            log.debug("SSE连接已建立，状态码: {}", connection.getResponseCode());
            processEventStream(reader);
            handleSseClosure("SSE connection closed");
        } catch (Exception e) {
            if (!running.get()) {
                log.debug("SSE读取线程已按停止请求退出: {}", e.getMessage());
                return;
            }
            log.debug("SSE连接失败: {}", McpTransportSupport.safeMessage(e), e);
            if (messageHandler != null) {
                messageHandler.onError(e);
            }
            handleSseClosure("SSE connection failed: " + e.getMessage());
        } finally {
            closeQuietly(reader);
            closeQuietly(inputStream);
            disconnectQuietly(connection);
            this.sseInputStream = null;
            this.sseConnection = null;
            if (Thread.currentThread() == this.sseReaderThread) {
                this.sseReaderThread = null;
            }
        }
    }

    private HttpURLConnection openSseConnection() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(sseEndpointUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "ai4j-mcp-client/1.0.0");
        connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(30));
        connection.setReadTimeout(0);
        connection.setDoInput(true);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        int statusCode = connection.getResponseCode();
        if (statusCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("SSE连接失败，状态码: " + statusCode);
        }
        return connection;
    }

    private HttpURLConnection openPostConnection(String endpointUrl, String sessionId) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(30));
        connection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(60));
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ai4j-mcp-client/1.0.0");

        if (sessionId != null) {
            connection.setRequestProperty("mcp-session-id", sessionId);
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        return connection;
    }

    private void processEventStream(BufferedReader reader) throws IOException {
        String eventId = null;
        String eventType = null;
        StringBuilder dataBuilder = new StringBuilder();

        while (running.get()) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }

            if (line.isEmpty()) {
                dispatchSseEvent(eventId, eventType, dataBuilder);
                eventId = null;
                eventType = null;
                dataBuilder.setLength(0);
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

            if ("event".equals(field)) {
                eventType = value;
            } else if ("data".equals(field)) {
                if (dataBuilder.length() > 0) {
                    dataBuilder.append('\n');
                }
                dataBuilder.append(value);
            } else if ("id".equals(field)) {
                eventId = value;
            }
        }

        if (eventId != null || eventType != null || dataBuilder.length() > 0) {
            dispatchSseEvent(eventId, eventType, dataBuilder);
        }
    }

    private void dispatchSseEvent(String eventId, String eventType, StringBuilder dataBuilder) {
        String data = dataBuilder.toString();
        if ((eventType == null || eventType.trim().isEmpty()) && data.trim().isEmpty()) {
            return;
        }

        String resolvedType = (eventType == null || eventType.trim().isEmpty()) ? "message" : eventType.trim();
        log.debug("接收SSE事件: id={}, type={}, data={}", eventId, resolvedType, data);

        try {
            if ("endpoint".equals(resolvedType)) {
                handleEndpointEvent(data);
            } else if ("message".equals(resolvedType)) {
                handleMessageEvent(data);
            } else {
                log.debug("忽略未知事件类型: {}", resolvedType);
            }
        } catch (Exception e) {
            log.error("处理SSE事件失败", e);
            if (messageHandler != null) {
                messageHandler.onError(e);
            }
        }
    }

    private void handleSseClosure(String reason) {
        if (running.compareAndSet(true, false)) {
            endpointReceived = false;
            messageEndpointUrl = null;
            if (messageHandler != null) {
                messageHandler.onDisconnected(reason);
            }
        }
    }

    private void closeSseResources() {
        final HttpURLConnection connection = sseConnection;
        final InputStream inputStream = sseInputStream;
        sseConnection = null;
        sseInputStream = null;

        Thread readerThread = sseReaderThread;
        if (readerThread != null) {
            readerThread.interrupt();
        }

        if (connection != null || inputStream != null) {
            Thread closerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    disconnectQuietly(connection);
                    closeQuietly(inputStream);
                }
            }, "mcp-sse-closer-" + Integer.toHexString(System.identityHashCode(this)));
            closerThread.setDaemon(true);
            closerThread.start();
        }
    }

    private void waitForReaderThreadToExit() {
        Thread readerThread = sseReaderThread;
        if (readerThread == null || readerThread == Thread.currentThread()) {
            return;
        }

        try {
            readerThread.join(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("等待SSE读取线程退出时被中断");
            return;
        }

        if (readerThread.isAlive()) {
            log.warn("SSE读取线程未在预期时间内退出");
        }
    }

    private void closeQuietly(InputStream inputStream) {
        McpTransportSupport.closeQuietly(inputStream);
    }

    private void closeQuietly(BufferedReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (IOException e) {
            log.debug("关闭SSE读取器失败: {}", e.getMessage());
        }
    }

    private void disconnectQuietly(HttpURLConnection connection) {
        if (connection != null) {
            connection.disconnect();
        }
    }

    /**
     * 处理endpoint事件
     */
    private void handleEndpointEvent(String data) {
        if (data != null && !data.trim().isEmpty()) {
            String endpointPath = data.trim();
            // 将相对路径转换为完整URL
            messageEndpointUrl = buildFullUrl(endpointPath);
            endpointReceived = true;
            log.debug("收到endpoint事件，消息端点: {}", messageEndpointUrl);
        } else {
            log.warn("收到空的endpoint事件数据");
        }
    }

    /**
     * 将相对路径转换为完整URL
     */
    private String buildFullUrl(String endpointPath) {
        if (endpointPath == null || endpointPath.trim().isEmpty()) {
            throw new IllegalArgumentException("端点路径不能为空");
        }

        // 如果已经是完整URL，则合并 initialQueryParams（避免重复 key）
        if (endpointPath.startsWith("http://") || endpointPath.startsWith("https://")) {
            return mergeInitialQueriesIntoUrl(endpointPath);
        }

        // 否则将相对路径拼接到 base 上
        String baseUrl = extractBaseUrl(sseEndpointUrl);
        String trimmedPath = endpointPath.trim();
        if (!trimmedPath.startsWith("/")) {
            trimmedPath = "/" + trimmedPath;
        }
        String combined = baseUrl + trimmedPath;
        return mergeInitialQueriesIntoUrl(combined);
    }

    private String mergeInitialQueriesIntoUrl(String urlStr) {
        try {
            HttpUrl url = HttpUrl.get(urlStr);
            if (url == null) return urlStr; // fallback

            HttpUrl.Builder builder = url.newBuilder();

            // 把初始查询参数加上，前提是 endpoint URL 没有同名参数
            for (Map.Entry<String, String> e : initialQueryParams.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();
                if (url.queryParameter(key) == null && val != null) {
                    builder.addQueryParameter(key, val);
                }
            }

            return builder.build().toString();
        } catch (Exception ex) {
            log.debug("合并初始查询参数失败，返回原始 URL: {}, 错误: {}", urlStr, ex.getMessage());
            return urlStr;
        }
    }

    /**
     * 从完整URL中提取基础URL（协议+域名+端口）
     */
    private String extractBaseUrl(String fullUrl) {
        try {
            java.net.URL url = new java.net.URL(fullUrl);
            return url.getProtocol() + "://" + url.getHost() +
                (url.getPort() != -1 ? ":" + url.getPort() : "");
        } catch (Exception e) {
            // 简单的字符串处理作为备选
            int protocolEnd = fullUrl.indexOf("://");
            if (protocolEnd > 0) {
                int pathStart = fullUrl.indexOf("/", protocolEnd + 3);
                if (pathStart > 0) {
                    return fullUrl.substring(0, pathStart);
                }
            }
            return fullUrl;
        }
    }

    /**
     * 从消息端点URL中提取会话ID
     */
    private String extractSessionId(String messageUrl) {
        if (messageUrl == null) {
            return null;
        }

        try {
            // 从URL参数中提取session_id
            // 例如: https://mcp.api-inference.modelscope.net/messages/?session_id=abc123
            int sessionIdIndex = messageUrl.indexOf("session_id=");
            if (sessionIdIndex > 0) {
                int startIndex = sessionIdIndex + "session_id=".length();
                int endIndex = messageUrl.indexOf("&", startIndex);
                if (endIndex == -1) {
                    endIndex = messageUrl.length();
                }
                return messageUrl.substring(startIndex, endIndex);
            }
        } catch (Exception e) {
            log.debug("提取会话ID失败: {}", messageUrl, e);
        }

        return null;
    }
    /**
     * 处理message事件
     */
    private void handleMessageEvent(String data) {
        if (data == null || data.trim().isEmpty()) {
            log.warn("收到空的message事件数据");
            return;
        }

        try {
            McpMessage message = parseMessage(data);
            if (message != null && messageHandler != null) {
                messageHandler.handleMessage(message);
            }
        } catch (Exception e) {
            log.error("解析message事件失败: {}", data, e);
            if (messageHandler != null) {
                messageHandler.onError(e);
            }
        }
    }

    /**
     * 解析JSON消息为McpMessage对象
     */
    private McpMessage parseMessage(String jsonString) {
        try {
            return McpMessageCodec.parseMessage(jsonString);
        } catch (Exception e) {
            log.error("解析消息失败: {}", jsonString, e);
            return null;
        }
    }
}
