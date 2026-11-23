package io.github.lnyocly.ai4j.plugin.colab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REST and WebSocket Jupyter Kernel Wire-Protocol client for Google Colab / Jupyter runtimes.
 */
public class ColabKernelClient implements AutoCloseable {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<List<KernelInfo>> KERNEL_LIST_TYPE = new TypeReference<List<KernelInfo>>() {
    };

    private final ColabSandboxConfig config;
    private final OkHttpClient httpClient;
    private final boolean ownsHttpClient;

    public ColabKernelClient(ColabSandboxConfig config) {
        this(config, null);
    }

    public ColabKernelClient(ColabSandboxConfig config, OkHttpClient httpClient) {
        if (config == null) {
            throw new IllegalArgumentException("ColabSandboxConfig must not be null");
        }
        this.config = config;
        if (httpClient != null) {
            this.httpClient = httpClient;
            this.ownsHttpClient = false;
        } else {
            this.httpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .readTimeout(config.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .build();
            this.ownsHttpClient = true;
        }
    }

    public ColabSandboxConfig getConfig() {
        return config;
    }

    public KernelInfo createKernel(String kernelName) throws SandboxException {
        String baseUrl = requireBaseUrl();
        String resolvedKernel = isBlank(kernelName) ? config.getKernelName() : kernelName.trim();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", resolvedKernel);
        return postJson(baseUrl + "/api/kernels", body, KernelInfo.class);
    }

    public SessionInfo createSession(String sessionName, String notebookPath, String kernelName) throws SandboxException {
        String baseUrl = requireBaseUrl();
        String resolvedName = isBlank(sessionName) ? config.getSessionName() : sessionName.trim();
        String resolvedPath = isBlank(notebookPath) ? config.getNotebookPath() : notebookPath.trim();
        String resolvedKernel = isBlank(kernelName) ? config.getKernelName() : kernelName.trim();

        Map<String, Object> kernelBody = new LinkedHashMap<String, Object>();
        kernelBody.put("name", resolvedKernel);

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", resolvedName);
        body.put("path", resolvedPath);
        body.put("type", "notebook");
        body.put("kernel", kernelBody);
        return postJson(baseUrl + "/api/sessions", body, SessionInfo.class);
    }

    public List<KernelInfo> listKernels() throws SandboxException {
        String baseUrl = requireBaseUrl();
        Request request = requestBuilder(baseUrl + "/api/kernels").get().build();
        String responseBody = executeText(request);
        try {
            List<KernelInfo> kernels = MAPPER.readValue(responseBody, KERNEL_LIST_TYPE);
            return kernels == null ? Collections.<KernelInfo>emptyList() : kernels;
        } catch (IOException ex) {
            throw new SandboxException("Failed to parse Colab kernel list response", ex);
        }
    }

    public KernelInfo getKernel(String kernelId) throws SandboxException {
        String baseUrl = requireBaseUrl();
        String id = requireId(kernelId, "kernelId");
        Request request = requestBuilder(baseUrl + "/api/kernels/" + encodeSegment(id)).get().build();
        String responseBody = executeText(request);
        try {
            return MAPPER.readValue(responseBody, KernelInfo.class);
        } catch (IOException ex) {
            throw new SandboxException("Failed to parse Colab kernel info for " + id, ex);
        }
    }

    public void interruptKernel(String kernelId) throws SandboxException {
        String baseUrl = requireBaseUrl();
        String id = requireId(kernelId, "kernelId");
        Request request = requestBuilder(baseUrl + "/api/kernels/" + encodeSegment(id) + "/interrupt")
                .post(RequestBody.create("", JSON_MEDIA_TYPE))
                .build();
        executeEmpty(request);
    }

    public void restartKernel(String kernelId) throws SandboxException {
        String baseUrl = requireBaseUrl();
        String id = requireId(kernelId, "kernelId");
        Request request = requestBuilder(baseUrl + "/api/kernels/" + encodeSegment(id) + "/restart")
                .post(RequestBody.create("", JSON_MEDIA_TYPE))
                .build();
        executeEmpty(request);
    }

    public void deleteKernel(String kernelId) throws SandboxException {
        String baseUrl = requireBaseUrl();
        String id = requireId(kernelId, "kernelId");
        Request request = requestBuilder(baseUrl + "/api/kernels/" + encodeSegment(id))
                .delete()
                .build();
        executeEmpty(request);
    }

    public void deleteSession(String sessionId) throws SandboxException {
        String baseUrl = requireBaseUrl();
        String id = requireId(sessionId, "sessionId");
        Request request = requestBuilder(baseUrl + "/api/sessions/" + encodeSegment(id))
                .delete()
                .build();
        executeEmpty(request);
    }

    public ExecutionResult executePython(String kernelId, String code) throws SandboxException {
        return executePython(kernelId, code, config.getExecutionTimeoutMillis(), null, null);
    }

    public ExecutionResult executePython(String kernelId,
                                         String code,
                                         long timeoutMillis,
                                         Map<String, String> environment,
                                         String workingDirectory) throws SandboxException {
        String id = requireId(kernelId, "kernelId");
        if (code == null) {
            throw new IllegalArgumentException("Python code must not be null");
        }
        String effectiveCode = wrapExecutionCode(code, environment, workingDirectory);
        long resolvedTimeout = timeoutMillis > 0L ? timeoutMillis : config.getExecutionTimeoutMillis();

        String wsSessionId = UUID.randomUUID().toString();
        String msgId = UUID.randomUUID().toString();
        String wsUrl = buildChannelsWebSocketUrl(id, wsSessionId);

        long startMs = System.currentTimeMillis();
        final CountDownLatch completionLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> failureRef = new AtomicReference<Throwable>();
        final ExecutionCollector collector = new ExecutionCollector(id, msgId);
        final String executeFrame = buildExecuteRequestJson(msgId, wsSessionId, effectiveCode);

        Request request = requestBuilder(wsUrl).build();
        WebSocket webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket socket, @NotNull Response response) {
                if (!socket.send(executeFrame)) {
                    failureRef.compareAndSet(null, new SandboxException("Failed to send execute_request over WebSocket"));
                    completionLatch.countDown();
                }
            }

            @Override
            public void onMessage(@NotNull WebSocket socket, @NotNull String text) {
                try {
                    if (collector.consumeMessage(text)) {
                        completionLatch.countDown();
                    }
                } catch (Exception ex) {
                    failureRef.compareAndSet(null, ex);
                    completionLatch.countDown();
                }
            }

            @Override
            public void onClosing(@NotNull WebSocket socket, int codeValue, @NotNull String reason) {
                socket.close(codeValue, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket socket, int codeValue, @NotNull String reason) {
                completionLatch.countDown();
            }

            @Override
            public void onFailure(@NotNull WebSocket socket, @NotNull Throwable t, @Nullable Response response) {
                if (!collector.isComplete()) {
                    String statusDetail = response == null ? "" : " (HTTP " + response.code() + ")";
                    failureRef.compareAndSet(null, new SandboxException(
                            "Colab kernel WebSocket failure" + statusDetail + ": " + t.getMessage(), t));
                }
                completionLatch.countDown();
            }
        });

        boolean timedOut = false;
        boolean canceled = false;
        try {
            boolean finished = completionLatch.await(resolvedTimeout, TimeUnit.MILLISECONDS);
            if (!finished && !collector.isComplete()) {
                timedOut = true;
                bestEffortInterrupt(id);
            }
        } catch (InterruptedException ex) {
            canceled = true;
            bestEffortInterrupt(id);
            Thread.currentThread().interrupt();
        } finally {
            if (timedOut || canceled) {
                webSocket.cancel();
            } else {
                webSocket.close(1000, "done");
            }
        }

        Throwable failure = failureRef.get();
        if (failure != null && !timedOut && !canceled) {
            if (failure instanceof SandboxException) {
                throw (SandboxException) failure;
            }
            throw new SandboxException("Colab Python execution failed: " + failure.getMessage(), failure);
        }

        long durationMillis = Math.max(0L, System.currentTimeMillis() - startMs);
        return collector.buildResult(timedOut, canceled, durationMillis);
    }

    @Override
    public void close() {
        if (ownsHttpClient) {
            httpClient.connectionPool().evictAll();
            httpClient.dispatcher().executorService().shutdown();
        }
    }

    String buildChannelsWebSocketUrl(String kernelId, String wsSessionId) throws SandboxException {
        String baseUrl = requireBaseUrl();
        String wsBase;
        if (baseUrl.startsWith("https://")) {
            wsBase = "wss://" + baseUrl.substring("https://".length());
        } else if (baseUrl.startsWith("http://")) {
            wsBase = "ws://" + baseUrl.substring("http://".length());
        } else if (baseUrl.startsWith("wss://") || baseUrl.startsWith("ws://")) {
            wsBase = baseUrl;
        } else {
            wsBase = "wss://" + baseUrl;
        }
        return wsBase + "/api/kernels/" + encodeSegment(kernelId) + "/channels?session_id=" + encodeSegment(wsSessionId);
    }

    static String wrapExecutionCode(String code, Map<String, String> environment, String workingDirectory) {
        boolean hasWorkingDir = !isBlank(workingDirectory);
        boolean hasEnv = environment != null && !environment.isEmpty();
        if (!hasWorkingDir && !hasEnv) {
            return code;
        }
        StringBuilder preamble = new StringBuilder();
        preamble.append("import os\n");
        if (hasWorkingDir) {
            preamble.append("os.chdir(\"").append(escapePythonString(workingDirectory.trim())).append("\")\n");
        }
        if (hasEnv) {
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                if (!isBlank(entry.getKey()) && entry.getValue() != null) {
                    preamble.append("os.environ[\"")
                            .append(escapePythonString(entry.getKey().trim()))
                            .append("\"] = \"")
                            .append(escapePythonString(entry.getValue()))
                            .append("\"\n");
                }
            }
        }
        preamble.append(code);
        return preamble.toString();
    }

    private String buildExecuteRequestJson(String msgId, String sessionId, String code) throws SandboxException {
        Map<String, Object> header = new LinkedHashMap<String, Object>();
        header.put("msg_id", msgId);
        header.put("username", "ai4j");
        header.put("session", sessionId);
        header.put("msg_type", "execute_request");
        header.put("version", "5.3");

        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("code", code);
        content.put("silent", Boolean.FALSE);
        content.put("store_history", Boolean.TRUE);
        content.put("user_expressions", Collections.emptyMap());
        content.put("allow_stdin", Boolean.FALSE);
        content.put("stop_on_error", Boolean.TRUE);

        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("header", header);
        envelope.put("parent_header", Collections.emptyMap());
        envelope.put("metadata", Collections.emptyMap());
        envelope.put("content", content);
        envelope.put("channel", "shell");
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (IOException ex) {
            throw new SandboxException("Failed to serialize Jupyter execute_request frame", ex);
        }
    }

    private <T> T postJson(String url, Map<String, Object> payload, Class<T> responseType) throws SandboxException {
        try {
            String json = MAPPER.writeValueAsString(payload);
            Request request = requestBuilder(url)
                    .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                    .build();
            String responseBody = executeText(request);
            return MAPPER.readValue(responseBody, responseType);
        } catch (IOException ex) {
            throw new SandboxException("Colab API JSON error at " + url + ": " + ex.getMessage(), ex);
        }
    }

    private Request.Builder requestBuilder(String url) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/json");
        String authHeader = config.resolveAuthorizationHeader();
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
        for (Map.Entry<String, String> entry : config.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return builder;
    }

    private String executeText(Request request) throws SandboxException {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String bodyText = body == null ? "" : new String(body.bytes(), StandardCharsets.UTF_8);
            if (!response.isSuccessful()) {
                throw new SandboxException("Colab API request failed with HTTP " + response.code()
                        + " (" + request.method() + " " + request.url().encodedPath() + ")"
                        + (isBlank(bodyText) ? "" : ": " + bodyText));
            }
            return bodyText;
        } catch (IOException ex) {
            throw new SandboxException("Colab API network failure: " + ex.getMessage(), ex);
        }
    }

    private void executeEmpty(Request request) throws SandboxException {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody body = response.body();
                String bodyText = body == null ? "" : new String(body.bytes(), StandardCharsets.UTF_8);
                throw new SandboxException("Colab API request failed with HTTP " + response.code()
                        + " (" + request.method() + " " + request.url().encodedPath() + ")"
                        + (isBlank(bodyText) ? "" : ": " + bodyText));
            }
        } catch (IOException ex) {
            throw new SandboxException("Colab API network failure: " + ex.getMessage(), ex);
        }
    }

    private void bestEffortInterrupt(String kernelId) {
        try {
            interruptKernel(kernelId);
        } catch (SandboxException | RuntimeException ignored) {
        }
    }

    private String requireBaseUrl() throws SandboxException {
        if (isBlank(config.getBaseUrl())) {
            throw new SandboxException(
                    "Colab API baseUrl is required (configure COLAB_API_URL or SandboxSpec.config.baseUrl)");
        }
        return config.getBaseUrl();
    }

    private static String requireId(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String encodeSegment(String raw) {
        try {
            return URLEncoder.encode(raw, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("UTF-8 encoding unsupported", ex);
        }
    }

    private static String escapePythonString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ExecutionCollector {

        private final String kernelId;
        private final String msgId;
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();
        private final List<StructuredOutput> outputs = new ArrayList<StructuredOutput>();
        private final List<SandboxArtifact> artifacts = new ArrayList<SandboxArtifact>();
        private final AtomicInteger artifactSequence = new AtomicInteger(0);
        private final AtomicBoolean replyReceived = new AtomicBoolean(false);
        private final AtomicBoolean idleReceived = new AtomicBoolean(false);
        private final AtomicBoolean errorSeen = new AtomicBoolean(false);

        ExecutionCollector(String kernelId, String msgId) {
            this.kernelId = kernelId;
            this.msgId = msgId;
        }

        synchronized boolean consumeMessage(String text) throws IOException {
            if (isBlank(text)) {
                return false;
            }
            JsonNode root = MAPPER.readTree(text);
            String parentMsgId = root.path("parent_header").path("msg_id").asText(null);
            if (!isBlank(parentMsgId) && !msgId.equals(parentMsgId)) {
                return false;
            }

            String msgType = root.path("msg_type").asText(null);
            if (isBlank(msgType)) {
                msgType = root.path("header").path("msg_type").asText(null);
            }
            if (isBlank(msgType)) {
                return false;
            }

            JsonNode content = root.path("content");
            if ("stream".equals(msgType)) {
                String streamName = content.path("name").asText("stdout");
                String chunk = content.path("text").asText("");
                if ("stderr".equalsIgnoreCase(streamName)) {
                    stderr.append(chunk);
                } else {
                    stdout.append(chunk);
                }
            } else if ("execute_result".equals(msgType) || "display_data".equals(msgType)) {
                collectRichData(msgType, content.path("data"));
            } else if ("error".equals(msgType)) {
                errorSeen.set(true);
                appendErrorTraceback(content);
            } else if ("execute_reply".equals(msgType)) {
                replyReceived.set(true);
                String status = content.path("status").asText("ok");
                if ("error".equalsIgnoreCase(status)) {
                    errorSeen.set(true);
                    if (stderr.length() == 0 && content.has("ename")) {
                        appendErrorTraceback(content);
                    }
                }
            } else if ("status".equals(msgType)) {
                String state = content.path("execution_state").asText("");
                if ("idle".equalsIgnoreCase(state)) {
                    idleReceived.set(true);
                }
            }
            return isComplete();
        }

        boolean isComplete() {
            return replyReceived.get() && idleReceived.get();
        }

        synchronized ExecutionResult buildResult(boolean timedOut, boolean canceled, long durationMillis) {
            int exitCode = errorSeen.get() || timedOut || canceled ? 1 : 0;
            return new ExecutionResult(
                    kernelId,
                    msgId,
                    exitCode,
                    stdout.toString(),
                    stderr.toString(),
                    timedOut,
                    canceled,
                    durationMillis,
                    outputs,
                    artifacts
            );
        }

        private void appendErrorTraceback(JsonNode content) {
            JsonNode traceback = content.path("traceback");
            if (traceback.isArray() && traceback.size() > 0) {
                for (JsonNode line : traceback) {
                    stderr.append(line.asText("")).append('\n');
                }
                return;
            }
            String ename = content.path("ename").asText("Error");
            String evalue = content.path("evalue").asText("");
            stderr.append(ename);
            if (!evalue.isEmpty()) {
                stderr.append(": ").append(evalue);
            }
            stderr.append('\n');
        }

        private void collectRichData(String outputType, JsonNode dataNode) {
            if (dataNode == null || !dataNode.isObject()) {
                return;
            }
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String mimeType = entry.getKey();
                JsonNode valueNode = entry.getValue();
                if (valueNode == null || valueNode.isNull()) {
                    continue;
                }
                String textValue = valueNode.isTextual() ? valueNode.asText() : valueNode.toString();
                if ("text/plain".equals(mimeType)) {
                    if ("execute_result".equals(outputType) && !textValue.isEmpty()) {
                        if (stdout.length() > 0 && stdout.charAt(stdout.length() - 1) != '\n') {
                            stdout.append('\n');
                        }
                        stdout.append(textValue).append('\n');
                    }
                    outputs.add(new StructuredOutput(outputType, mimeType, textValue, null, null));
                    continue;
                }

                boolean binaryBase64 = isBase64ImageMime(mimeType) && valueNode.isTextual();
                byte[] payloadBytes = decodePayloadBytes(textValue, binaryBase64);
                int index = artifactSequence.incrementAndGet();
                String extension = extensionForMime(mimeType);
                String name = "colab-output-" + index + "." + extension;
                String artifactId = "colab_art_" + msgId.substring(0, Math.min(8, msgId.length())) + "_" + index;

                SandboxArtifact artifact = SandboxArtifact.builder()
                        .artifactId(artifactId)
                        .name(name)
                        .path("/colab/artifacts/" + name)
                        .mimeType(mimeType)
                        .sizeBytes(Long.valueOf(payloadBytes.length))
                        .sha256(sha256Hex(payloadBytes))
                        .build();
                artifacts.add(artifact);

                Object structuredData = valueNode.isContainerNode()
                        ? MAPPER.convertValue(valueNode, Object.class)
                        : null;
                outputs.add(new StructuredOutput(
                        outputType,
                        mimeType,
                        binaryBase64 ? null : textValue,
                        structuredData,
                        binaryBase64 ? textValue : null
                ));
            }
        }

        private static boolean isBase64ImageMime(String mimeType) {
            String lower = mimeType.toLowerCase(Locale.ROOT);
            return lower.equals("image/png")
                    || lower.equals("image/jpeg")
                    || lower.equals("image/jpg")
                    || lower.equals("image/webp")
                    || lower.equals("application/pdf");
        }

        private static byte[] decodePayloadBytes(String raw, boolean base64) {
            if (base64) {
                try {
                    String cleaned = raw.replace("\n", "").replace("\r", "").trim();
                    return Base64.getDecoder().decode(cleaned);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return raw.getBytes(StandardCharsets.UTF_8);
        }

        private static String extensionForMime(String mimeType) {
            String lower = mimeType.toLowerCase(Locale.ROOT);
            if (lower.contains("png")) {
                return "png";
            }
            if (lower.contains("jpeg") || lower.contains("jpg")) {
                return "jpg";
            }
            if (lower.contains("svg")) {
                return "svg";
            }
            if (lower.contains("html")) {
                return "html";
            }
            if (lower.contains("json")) {
                return "json";
            }
            if (lower.contains("markdown")) {
                return "md";
            }
            return "txt";
        }

        private static String sha256Hex(byte[] bytes) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashed = digest.digest(bytes);
                StringBuilder hex = new StringBuilder(hashed.length * 2);
                for (byte b : hashed) {
                    hex.append(String.format(Locale.ROOT, "%02x", Integer.valueOf(b & 0xff)));
                }
                return hex.toString();
            } catch (NoSuchAlgorithmException ex) {
                return null;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class KernelInfo {
        private String id;
        private String name;
        @JsonProperty("execution_state")
        private String executionState;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getExecutionState() {
            return executionState;
        }

        public void setExecutionState(String executionState) {
            this.executionState = executionState;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SessionInfo {
        private String id;
        private String name;
        private String path;
        private String type;
        private KernelInfo kernel;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public KernelInfo getKernel() {
            return kernel;
        }

        public void setKernel(KernelInfo kernel) {
            this.kernel = kernel;
        }
    }

    public static final class StructuredOutput {
        private final String outputType;
        private final String mimeType;
        private final String text;
        private final Object data;
        private final String base64Data;

        public StructuredOutput(String outputType,
                                String mimeType,
                                String text,
                                Object data,
                                String base64Data) {
            this.outputType = outputType;
            this.mimeType = mimeType;
            this.text = text;
            this.data = data;
            this.base64Data = base64Data;
        }

        public String getOutputType() {
            return outputType;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getText() {
            return text;
        }

        public Object getData() {
            return data;
        }

        public String getBase64Data() {
            return base64Data;
        }
    }

    public static final class ExecutionResult {
        private final String kernelId;
        private final String msgId;
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final boolean timedOut;
        private final boolean canceled;
        private final long durationMillis;
        private final List<StructuredOutput> outputs;
        private final List<SandboxArtifact> artifacts;

        public ExecutionResult(String kernelId,
                               String msgId,
                               int exitCode,
                               String stdout,
                               String stderr,
                               boolean timedOut,
                               boolean canceled,
                               long durationMillis,
                               List<StructuredOutput> outputs,
                               List<SandboxArtifact> artifacts) {
            this.kernelId = kernelId;
            this.msgId = msgId;
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.timedOut = timedOut;
            this.canceled = canceled;
            this.durationMillis = durationMillis;
            this.outputs = outputs == null
                    ? Collections.<StructuredOutput>emptyList()
                    : Collections.unmodifiableList(new ArrayList<StructuredOutput>(outputs));
            this.artifacts = artifacts == null
                    ? Collections.<SandboxArtifact>emptyList()
                    : Collections.unmodifiableList(new ArrayList<SandboxArtifact>(artifacts));
        }

        public String getKernelId() {
            return kernelId;
        }

        public String getMsgId() {
            return msgId;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isTimedOut() {
            return timedOut;
        }

        public boolean isCanceled() {
            return canceled;
        }

        public long getDurationMillis() {
            return durationMillis;
        }

        public List<StructuredOutput> getOutputs() {
            return outputs;
        }

        public List<SandboxArtifact> getArtifacts() {
            return artifacts;
        }
    }
}
