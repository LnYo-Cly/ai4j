package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class CubeSandboxClient implements Closeable {

    private static final String CONNECT_CONTENT_TYPE = "application/connect+json";
    private static final String CONNECT_PROTOCOL_VERSION = "1";
    private static final byte CONNECT_END_STREAM_FLAG = 0x02;
    private static final byte CONNECT_COMPRESSED_FLAG = 0x01;
    private static final int DEFAULT_PROCESS_READ_TIMEOUT_MILLIS = 10 * 60 * 1000;

    private final CubeSandboxConfig config;

    CubeSandboxClient(CubeSandboxConfig config) {
        this.config = config;
    }

    CubeSandboxConfig getConfig() {
        return config;
    }

    JSONObject health() throws SandboxException {
        return requestJson("GET", "/health", null, 200);
    }

    CubeSandboxRemote create(String templateId,
                             int timeoutSeconds,
                             Map<String, String> envVars,
                             Map<String, String> metadata,
                             Boolean allowInternetAccess,
                             Object network) throws SandboxException {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("templateID", requireText(templateId, "CubeSandbox templateID is required. Set CUBE_TEMPLATE_ID or spec.config.templateId."));
        payload.put("timeout", Integer.valueOf(timeoutSeconds));
        if (envVars != null && !envVars.isEmpty()) {
            payload.put("envVars", envVars);
        }
        if (metadata != null && !metadata.isEmpty()) {
            payload.put("metadata", metadata);
        }
        if (allowInternetAccess != null && !allowInternetAccess.booleanValue()) {
            payload.put("allowInternetAccess", Boolean.FALSE);
        }
        if (network != null) {
            payload.put("network", network);
        }
        return toRemote(requestJson("POST", "/sandboxes", payload, 200, 201));
    }

    CubeSandboxRemote connect(String sandboxId) throws SandboxException {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("timeout", Integer.valueOf(config.getTimeoutSeconds()));
        return toRemote(requestJson("POST", "/sandboxes/" + urlEncodePath(sandboxId) + "/connect", payload, 200, 201));
    }

    JSONObject getSandbox(String sandboxId) throws SandboxException {
        return requestJson("GET", "/sandboxes/" + urlEncodePath(sandboxId), null, 200);
    }

    void kill(String sandboxId) throws SandboxException {
        requestJson("DELETE", "/sandboxes/" + urlEncodePath(sandboxId), null, 200, 204);
    }

    ProcessRun runProcess(CubeSandboxRemote remote,
                          String command,
                          String cwd,
                          Map<String, String> environment,
                          Long timeoutMillis) throws SandboxException {
        Map<String, Object> process = new LinkedHashMap<String, Object>();
        process.put("cmd", "/bin/bash");
        process.put("args", new String[]{"-l", "-c", command});
        process.put("envs", environment == null ? new LinkedHashMap<String, String>() : environment);
        if (cwd != null && !cwd.trim().isEmpty()) {
            process.put("cwd", cwd.trim());
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("process", process);
        payload.put("stdin", Boolean.FALSE);

        byte[] envelope = encodeConnectEnvelope(JSON.toJSONBytes(payload), (byte) 0);
        long start = System.currentTimeMillis();
        if (config.getProxyNodeIp() != null && "http".equals(config.getProxyScheme())) {
            return runProcessThroughHttpSocket(remote, envelope, timeoutMillis, start);
        }
        if (config.getProxyNodeIp() != null) {
            throw new SandboxException("CubeSandbox proxyNodeIp is only supported with http proxyScheme in the Java 8 adapter");
        }
        HttpURLConnection connection = null;
        try {
            URL url = dataUrl(remote, config.getEnvdPort(), "/process.Process/Start");
            connection = openDataConnection(url);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(config.getRequestTimeoutMillis());
            connection.setReadTimeout(readTimeout(timeoutMillis));
            connection.setRequestProperty("Content-Type", CONNECT_CONTENT_TYPE);
            connection.setRequestProperty("Connect-Protocol-Version", CONNECT_PROTOCOL_VERSION);
            connection.setRequestProperty("Connect-Content-Encoding", "identity");
            connection.setRequestProperty("Authorization", basicAuth(config.getUser()));
            if (timeoutMillis != null && timeoutMillis.longValue() > 0) {
                connection.setRequestProperty("Connect-Timeout-Ms", String.valueOf(timeoutMillis.longValue()));
            }
            if (remote.getEnvdAccessToken() != null) {
                connection.setRequestProperty("X-Access-Token", safeHeaderValue(remote.getEnvdAccessToken(), "X-Access-Token"));
            }
            write(connection, envelope);

            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new SandboxException("CubeSandbox process start failed: HTTP " + status + errorSuffix(connection));
            }
            ProcessRun run = parseProcessStream(connection.getInputStream());
            run.durationMillis = Long.valueOf(System.currentTimeMillis() - start);
            return run;
        } catch (IOException e) {
            throw new SandboxException("CubeSandbox process start failed: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ProcessRun runProcessThroughHttpSocket(CubeSandboxRemote remote,
                                                   byte[] envelope,
                                                   Long timeoutMillis,
                                                   long start) throws SandboxException {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(config.getProxyNodeIp(), config.getProxyPortHttp()), config.getRequestTimeoutMillis());
            socket.setSoTimeout(readTimeout(timeoutMillis));
            OutputStream output = socket.getOutputStream();
            writeAscii(output, dataRequestHeaders(remote, envelope.length, timeoutMillis));
            output.write(envelope);
            output.flush();

            HttpSocketResponse response = readHttpSocketResponse(socket.getInputStream());
            if (response.status >= 400) {
                String body = read(response.body);
                throw new SandboxException("CubeSandbox process start failed: HTTP " + response.status
                        + (body == null || body.trim().isEmpty() ? "" : ": " + body.trim()));
            }
            ProcessRun run = parseProcessStream(response.body);
            run.durationMillis = Long.valueOf(System.currentTimeMillis() - start);
            return run;
        } catch (IOException e) {
            throw new SandboxException("CubeSandbox process start failed: " + e.getMessage(), e);
        } finally {
            closeQuietly(socket);
        }
    }

    private String dataRequestHeaders(CubeSandboxRemote remote, int contentLength, Long timeoutMillis) throws SandboxException {
        StringBuilder builder = new StringBuilder();
        builder.append("POST /process.Process/Start HTTP/1.1\r\n");
        builder.append("Host: ").append(safeHeaderValue(remote.host(config.getEnvdPort(), config.getSandboxDomain()), "Host")).append("\r\n");
        builder.append("Content-Type: ").append(CONNECT_CONTENT_TYPE).append("\r\n");
        builder.append("Connect-Protocol-Version: ").append(CONNECT_PROTOCOL_VERSION).append("\r\n");
        builder.append("Connect-Content-Encoding: identity\r\n");
        builder.append("Authorization: ").append(safeHeaderValue(basicAuth(config.getUser()), "Authorization")).append("\r\n");
        if (timeoutMillis != null && timeoutMillis.longValue() > 0) {
            builder.append("Connect-Timeout-Ms: ").append(timeoutMillis.longValue()).append("\r\n");
        }
        if (remote.getEnvdAccessToken() != null) {
            builder.append("X-Access-Token: ").append(safeHeaderValue(remote.getEnvdAccessToken(), "X-Access-Token")).append("\r\n");
        }
        builder.append("Content-Length: ").append(contentLength).append("\r\n");
        builder.append("Connection: close\r\n\r\n");
        return builder.toString();
    }

    private JSONObject requestJson(String method, String path, Object payload, int... okStatuses) throws SandboxException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(config.getApiUrl() + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(config.getRequestTimeoutMillis());
            connection.setReadTimeout(config.getRequestTimeoutMillis());
            connection.setRequestProperty("Accept", "application/json");
            if (config.getApiKey() != null) {
                connection.setRequestProperty("Authorization", safeHeaderValue("Bearer " + config.getApiKey(), "Authorization"));
            }
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                write(connection, JSON.toJSONBytes(payload));
            }
            int status = connection.getResponseCode();
            if (!statusOk(status, okStatuses)) {
                throw new SandboxException("CubeSandbox control API failed " + method + " " + path + ": HTTP " + status + errorSuffix(connection));
            }
            if (status == 204) {
                return new JSONObject();
            }
            String body = read(connection.getInputStream());
            if (body == null || body.trim().isEmpty()) {
                return new JSONObject();
            }
            return JSON.parseObject(body);
        } catch (IOException e) {
            throw new SandboxException("CubeSandbox control API failed " + method + " " + path + ": " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openDataConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    private URL dataUrl(CubeSandboxRemote remote, int port, String path) throws IOException, SandboxException {
        return new URL(config.getProxyScheme() + "://" + remote.host(port, config.getSandboxDomain()) + path);
    }

    private ProcessRun parseProcessStream(InputStream input) throws IOException, SandboxException {
        DataInputStream data = new DataInputStream(input);
        ProcessRun run = new ProcessRun();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        boolean sawEnd = false;
        while (true) {
            int flags;
            try {
                flags = data.readUnsignedByte();
            } catch (EOFException eof) {
                break;
            }
            int size = data.readInt();
            if (size < 0 || size > config.getConnectEnvelopeLimitBytes()) {
                throw new SandboxException("CubeSandbox Connect stream message too large: " + size + " bytes");
            }
            byte[] payload = new byte[size];
            data.readFully(payload);
            if ((flags & CONNECT_COMPRESSED_FLAG) != 0) {
                throw new SandboxException("CubeSandbox Connect stream compressed messages are not supported");
            }
            if ((flags & CONNECT_END_STREAM_FLAG) != 0) {
                parseConnectEndStream(payload);
                continue;
            }
            JSONObject response = JSON.parseObject(new String(payload, StandardCharsets.UTF_8));
            JSONObject event = response.getJSONObject("event");
            if (event == null) {
                continue;
            }
            JSONObject start = event.getJSONObject("start");
            if (start != null && start.getInteger("pid") != null) {
                run.pid = start.getInteger("pid");
            }
            JSONObject datum = event.getJSONObject("data");
            if (datum != null) {
                String out = datum.getString("stdout");
                String err = datum.getString("stderr");
                if (out != null && !out.isEmpty()) {
                    stdout.append(decodeBase64(out));
                }
                if (err != null && !err.isEmpty()) {
                    stderr.append(decodeBase64(err));
                }
            }
            JSONObject end = event.getJSONObject("end");
            if (end != null) {
                Integer exitCode = end.getInteger("exitCode");
                if (exitCode == null) {
                    exitCode = end.getInteger("exit_code");
                }
                if (exitCode == null && end.getString("status") != null) {
                    exitCode = parseExitCodeFromStatus(end.getString("status"));
                }
                if (exitCode == null) {
                    String error = end.getString("error");
                    if (error != null && !error.trim().isEmpty()) {
                        throw new SandboxException("CubeSandbox process failed: " + error);
                    }
                    throw new SandboxException("CubeSandbox process stream ended without exit code");
                }
                run.exitCode = exitCode;
                sawEnd = true;
            }
        }
        if (!sawEnd) {
            throw new SandboxException("CubeSandbox process stream ended without EndEvent");
        }
        run.stdout = stdout.toString();
        run.stderr = stderr.toString();
        return run;
    }

    private void parseConnectEndStream(byte[] payload) throws SandboxException {
        if (payload == null || payload.length == 0) {
            return;
        }
        JSONObject object = JSON.parseObject(new String(payload, StandardCharsets.UTF_8));
        JSONObject error = object.getJSONObject("error");
        if (error == null) {
            return;
        }
        String message = error.getString("message");
        String code = error.getString("code");
        if (message == null || message.trim().isEmpty()) {
            message = "Connect stream error";
        }
        if (code != null && !code.trim().isEmpty()) {
            throw new SandboxException("CubeSandbox Connect stream error " + code + ": " + message);
        }
        throw new SandboxException("CubeSandbox Connect stream error: " + message);
    }

    private static String decodeBase64(String value) {
        byte[] raw = Base64.getDecoder().decode(value);
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static Integer parseExitCodeFromStatus(String status) {
        if (status == null) {
            return null;
        }
        String marker = "exit status ";
        int index = status.indexOf(marker);
        if (index >= 0) {
            return parseIntegerPrefix(status.substring(index + marker.length()));
        }
        marker = "exited with code ";
        index = status.indexOf(marker);
        if (index >= 0) {
            return parseIntegerPrefix(status.substring(index + marker.length()));
        }
        if ("exited".equals(status)) {
            return Integer.valueOf(0);
        }
        return null;
    }

    private static Integer parseIntegerPrefix(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= '0' && ch <= '9') || (i == 0 && ch == '-')) {
                builder.append(ch);
            } else {
                break;
            }
        }
        if (builder.length() == 0 || "-".equals(builder.toString())) {
            return null;
        }
        try {
            return Integer.valueOf(builder.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static CubeSandboxRemote toRemote(JSONObject object) throws SandboxException {
        String sandboxId = object.getString("sandboxID");
        String templateId = object.getString("templateID");
        if (sandboxId == null || sandboxId.trim().isEmpty()) {
            throw new SandboxException("CubeSandbox response missing sandboxID");
        }
        return new CubeSandboxRemote(
                templateId,
                sandboxId,
                object.getString("clientID"),
                object.getString("envdVersion"),
                object.getString("envdAccessToken"),
                object.getString("trafficAccessToken"),
                object.getString("domain"));
    }

    private static boolean statusOk(int status, int[] okStatuses) {
        for (int okStatus : okStatuses) {
            if (status == okStatus) {
                return true;
            }
        }
        return false;
    }

    private static String requireText(String value, String message) throws SandboxException {
        if (value == null || value.trim().isEmpty()) {
            throw new SandboxException(message);
        }
        return value.trim();
    }

    private static String urlEncodePath(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String basicAuth(String user) {
        String value = user == null || user.trim().isEmpty() ? "root" : user.trim();
        return "Basic " + Base64.getEncoder().encodeToString((value + ":").getBytes(StandardCharsets.UTF_8));
    }

    private static String safeHeaderValue(String value, String headerName) throws SandboxException {
        if (value == null) {
            return "";
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new SandboxException("CubeSandbox " + headerName + " header contains invalid line break");
        }
        return value;
    }

    private static byte[] encodeConnectEnvelope(byte[] payload, byte flags) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(flags);
        int size = payload == null ? 0 : payload.length;
        out.write((size >>> 24) & 0xff);
        out.write((size >>> 16) & 0xff);
        out.write((size >>> 8) & 0xff);
        out.write(size & 0xff);
        if (payload != null) {
            out.write(payload, 0, payload.length);
        }
        return out.toByteArray();
    }

    private static void write(HttpURLConnection connection, byte[] payload) throws IOException {
        OutputStream output = connection.getOutputStream();
        try {
            output.write(payload);
        } finally {
            output.close();
        }
    }

    private static void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static int readTimeout(Long timeoutMillis) {
        if (timeoutMillis == null || timeoutMillis.longValue() <= 0) {
            return DEFAULT_PROCESS_READ_TIMEOUT_MILLIS;
        }
        long withMargin = timeoutMillis.longValue() + 5000L;
        return withMargin > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) withMargin;
    }

    private static String errorSuffix(HttpURLConnection connection) throws IOException {
        InputStream stream = connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        String body = read(stream);
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject json = JSON.parseObject(body);
            String message = json.getString("message");
            if (message != null && !message.trim().isEmpty()) {
                return ": " + message.trim();
            }
            JSONObject error = json.getJSONObject("error");
            if (error != null && error.getString("message") != null) {
                return ": " + error.getString("message").trim();
            }
        } catch (Exception ignored) {
            // fall through to text
        }
        return ": " + body.trim();
    }

    private static String read(InputStream stream) throws IOException {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            stream.close();
        }
    }

    private static HttpSocketResponse readHttpSocketResponse(InputStream input) throws IOException {
        String statusLine = readAsciiLine(input);
        if (statusLine == null || statusLine.trim().isEmpty()) {
            throw new IOException("empty HTTP response from CubeSandbox data proxy");
        }
        int status = parseHttpStatus(statusLine);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        String line;
        while ((line = readAsciiLine(input)) != null) {
            if (line.length() == 0) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }

        InputStream body = input;
        String transferEncoding = header(headers, "Transfer-Encoding");
        if (transferEncoding != null && transferEncoding.toLowerCase(java.util.Locale.ENGLISH).contains("chunked")) {
            body = new ChunkedInputStream(input);
        } else {
            String contentLength = header(headers, "Content-Length");
            if (contentLength != null) {
                try {
                    body = new LimitedInputStream(input, Long.parseLong(contentLength.trim()));
                } catch (NumberFormatException ignored) {
                    // Keep the raw body stream if the peer returned an invalid Content-Length.
                }
            }
        }
        return new HttpSocketResponse(status, body);
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static int parseHttpStatus(String statusLine) throws IOException {
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) {
            throw new IOException("invalid HTTP status line from CubeSandbox data proxy: " + statusLine);
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("invalid HTTP status code from CubeSandbox data proxy: " + statusLine, e);
        }
    }

    private static String readAsciiLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != -1) {
            if (next == '\n') {
                break;
            }
            line.write(next);
        }
        if (next == -1 && line.size() == 0) {
            return null;
        }
        byte[] bytes = line.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    @Override
    public void close() {
        // HttpURLConnection has no shared close hook.
    }

    static final class ProcessRun {
        private Integer pid;
        private Integer exitCode;
        private String stdout;
        private String stderr;
        private Long durationMillis;

        Integer getPid() {
            return pid;
        }

        Integer getExitCode() {
            return exitCode;
        }

        String getStdout() {
            return stdout;
        }

        String getStderr() {
            return stderr;
        }

        Long getDurationMillis() {
            return durationMillis;
        }
    }

    private static final class HttpSocketResponse {
        private final int status;
        private final InputStream body;

        private HttpSocketResponse(int status, InputStream body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private LimitedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = Math.max(0L, remaining);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0L) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0L) {
                return -1;
            }
            int capped = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, capped);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }

    private static final class ChunkedInputStream extends InputStream {
        private final InputStream delegate;
        private int remaining;
        private boolean done;
        private boolean needChunkTerminator;

        private ChunkedInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            if (!ensureChunk()) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
                if (remaining == 0) {
                    needChunkTerminator = true;
                }
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (!ensureChunk()) {
                return -1;
            }
            int capped = Math.min(len, remaining);
            int read = delegate.read(b, off, capped);
            if (read > 0) {
                remaining -= read;
                if (remaining == 0) {
                    needChunkTerminator = true;
                }
            }
            return read;
        }

        private boolean ensureChunk() throws IOException {
            while (!done && remaining == 0) {
                if (needChunkTerminator) {
                    readAsciiLine(delegate);
                    needChunkTerminator = false;
                }
                String line = readAsciiLine(delegate);
                if (line == null) {
                    done = true;
                    return false;
                }
                int semicolon = line.indexOf(';');
                String sizeText = (semicolon >= 0 ? line.substring(0, semicolon) : line).trim();
                remaining = Integer.parseInt(sizeText, 16);
                if (remaining == 0) {
                    String trailer;
                    while ((trailer = readAsciiLine(delegate)) != null && trailer.length() > 0) {
                        // drain trailers
                    }
                    done = true;
                    return false;
                }
            }
            return !done;
        }
    }
}
