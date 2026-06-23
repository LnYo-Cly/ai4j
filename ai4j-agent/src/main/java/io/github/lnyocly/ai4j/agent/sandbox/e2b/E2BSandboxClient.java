package io.github.lnyocly.ai4j.agent.sandbox.e2b;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small Java 8 E2B HTTP client used by {@link E2BSandboxProvider}.
 *
 * <p>Speaks two surfaces: the control API (JSON REST, {@code X-API-Key} auth) for
 * create/delete, and the per-sandbox execution host (Connect server-streaming
 * {@code process.Process/Start}, Bearer auth) for running commands.</p>
 */
public class E2BSandboxClient {

    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private static final long MAX_FRAME_BYTES = 64L * 1024 * 1024;

    private final E2BSandboxConfig config;

    public E2BSandboxClient(E2BSandboxConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("e2b sandbox config must not be null");
        }
        this.config = config;
    }

    E2BSandboxConfig getConfig() {
        return config;
    }

    public E2BCreateSandboxResponse createSandbox() throws IOException {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("templateID", config.getTemplateId());
        body.put("timeout", config.getTimeoutSeconds());
        Map<String, Object> options = config.getCreateOptions();
        if (options != null && !options.isEmpty()) {
            body.putAll(options);
        }
        return request("POST", config.getApiUrl() + "/sandboxes", body, E2BCreateSandboxResponse.class);
    }

    public void deleteSandbox(String sandboxId) throws IOException {
        String id = requireText(sandboxId, "sandbox id must not be blank");
        request("DELETE", config.getApiUrl() + "/sandboxes/" + encodePath(id), null, null);
    }

    /**
     * Runs one command against a sandbox execution host using the Connect server-streaming
     * {@code process.Process/Start} RPC and aggregates the streamed response into a result.
     *
     * @param sandboxHost     execution host, e.g. {@code https://49983-<sid>.e2b.app}
     * @param executionToken  bearer token for the execution host (API key or envd access token)
     * @param accessToken     optional {@code X-Access-Token} (envd access token); may be null
     * @param cmd             executable
     * @param args            argument list
     * @param envs            environment variables
     * @param cwd             working directory; may be null
     * @param readTimeoutMillis socket read timeout for the streaming response
     */
    public E2BProcessResult execute(String sandboxHost,
                                    String executionToken,
                                    String accessToken,
                                    String cmd,
                                    List<String> args,
                                    Map<String, String> envs,
                                    String cwd,
                                    long readTimeoutMillis) throws IOException {
        byte[] frame = buildProcessFrame(cmd, args, envs, cwd);
        String base = E2BSandboxConfig.trimTrailingSlash(sandboxHost);
        if (base == null) {
            throw new IOException("E2B sandbox host is not configured");
        }
        String url = base + "/process.Process/Start";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(safeTimeout(config.getConnectTimeoutMillis()));
            connection.setReadTimeout(safeTimeout(readTimeoutMillis));
            connection.setRequestProperty("Content-Type", "application/connect+json");
            connection.setRequestProperty("Connect-Protocol-Version", "1");
            connection.setRequestProperty("Accept", "application/connect+json");
            connection.setRequestProperty("User-Agent", "ai4j-agent-e2b-sandbox/1");
            if (executionToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + executionToken);
            }
            if (accessToken != null) {
                connection.setRequestProperty("X-Access-Token", accessToken);
            }
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Length", String.valueOf(frame.length));
            OutputStream output = connection.getOutputStream();
            try {
                output.write(frame);
            } finally {
                output.close();
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (status >= 400) {
                String errorBody = readBody(stream);
                throw new E2BApiException(status, "E2B execute failed: POST " + url + " -> " + status, errorBody);
            }
            E2BProcessResult result = parseConnectStream(stream);
            // A Connect trailer carrying a top-level "error" is a protocol failure (not a command
            // non-zero exit), so surface it as an exception.
            if (result.getError() != null && !result.isExited()) {
                throw new E2BApiException(status, "E2B execute protocol error: " + result.getError(),
                        result.getStderr());
            }
            return result;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Builds the Connect unary request frame for {@code process.Process/Start}:
     * one envelope {@code 0x00 + big-endian uint32 length + JSON payload}.
     */
    static byte[] buildProcessFrame(String cmd, List<String> args, Map<String, String> envs, String cwd) {
        Map<String, Object> process = new LinkedHashMap<String, Object>();
        process.put("cmd", cmd);
        process.put("args", args != null ? args : Collections.<String>emptyList());
        process.put("envs", envs != null ? envs : Collections.<String, String>emptyMap());
        if (cwd != null) {
            process.put("cwd", cwd);
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("process", process);
        root.put("stdin", false);
        byte[] json = JSON.toJSONString(root).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(json.length + 5);
        out.write(0x00);
        writeUnsignedInt(out, json.length);
        out.write(json, 0, json.length);
        return out.toByteArray();
    }

    /**
     * Parses a Connect streaming response (sequence of envelopes) into an aggregated result.
     * Each envelope is {@code 1 byte flags + big-endian uint32 length + JSON payload}; the
     * end-of-stream envelope has flag bit {@code 0x02} set and may carry a trailer object.
     */
    static E2BProcessResult parseConnectStream(InputStream input) throws IOException {
        java.io.DataInputStream din = new java.io.DataInputStream(new BufferedInputStream(input));
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Long pid = null;
        Integer exitCode = null;
        boolean exited = false;
        String error = null;
        while (true) {
            int flagsByte = din.read();
            if (flagsByte == -1) {
                break;
            }
            long length = readUnsignedInt(din);
            if (length < 0 || length > MAX_FRAME_BYTES) {
                throw new IOException("invalid Connect frame length: " + length);
            }
            byte[] payload = new byte[(int) length];
            if (length > 0) {
                din.readFully(payload);
            }
            if (length == 0) {
                if ((flagsByte & 0x02) != 0) {
                    break;
                }
                continue;
            }
            String json = new String(payload, StandardCharsets.UTF_8);
            JSONObject root = parseLenient(json);
            if (root != null) {
                JSONObject errorObj = root.getJSONObject("error");
                if (errorObj != null) {
                    String message = errorObj.getString("message");
                    error = message != null ? message : json;
                }
                JSONObject event = root.getJSONObject("event");
                if (event != null) {
                    JSONObject start = event.getJSONObject("start");
                    if (start != null && pid == null) {
                        Integer p = start.getInteger("pid");
                        pid = p == null ? null : p.longValue();
                    }
                    JSONObject data = event.getJSONObject("data");
                    if (data != null) {
                        appendBase64(stdout, data.getString("stdout"));
                        appendBase64(stderr, data.getString("stderr"));
                    }
                    JSONObject end = event.getJSONObject("end");
                    if (end != null) {
                        exited = true;
                        Integer code = end.getInteger("exitCode");
                        if (code == null) {
                            // E2B omits the numeric exitCode on a clean (0) exit and only sends
                            // "status":"exit status <N>"; fall back to parsing it.
                            code = parseExitCodeFromStatus(end.getString("status"));
                        }
                        if (code != null) {
                            exitCode = code;
                        }
                    }
                }
            }
            if ((flagsByte & 0x02) != 0) {
                break;
            }
        }
        return new E2BProcessResult(pid, exitCode, stdout.toString(), stderr.toString(), error, exited);
    }

    private static JSONObject parseLenient(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Extracts the trailing integer from an E2B process end status such as
     * {@code "exit status 0"} or {@code "exit status 7"}.
     */
    private static Integer parseExitCodeFromStatus(String status) {
        if (status == null) {
            return null;
        }
        int end = status.length();
        while (end > 0 && !Character.isDigit(status.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && Character.isDigit(status.charAt(start - 1))) {
            start--;
        }
        if (start == end) {
            return null;
        }
        try {
            return Integer.valueOf(status.substring(start, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void appendBase64(StringBuilder target, String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException notBase64) {
            // Fall back to the raw token rather than dropping the output.
            target.append(encoded);
            return;
        }
        target.append(new String(decoded, StandardCharsets.UTF_8));
    }

    private static void writeUnsignedInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static long readUnsignedInt(java.io.DataInputStream din) throws IOException {
        int ch1 = din.read();
        int ch2 = din.read();
        int ch3 = din.read();
        int ch4 = din.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new EOFException("unexpected end of Connect frame header");
        }
        return ((long) ch1 << 24) | ((long) ch2 << 16) | ((long) ch3 << 8) | (long) ch4;
    }

    protected <T> T request(String method, String url, Object payload, Class<T> responseType) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(safeTimeout(config.getConnectTimeoutMillis()));
            connection.setReadTimeout(safeTimeout(config.getReadTimeoutMillis()));
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ai4j-agent-e2b-sandbox/1");
            setAuthHeaders(connection);
            byte[] requestBytes = null;
            if (payload != null) {
                requestBytes = JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Content-Length", String.valueOf(requestBytes.length));
            }
            if (requestBytes != null) {
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(requestBytes);
                } finally {
                    output.close();
                }
            }
            int status = connection.getResponseCode();
            String body = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status >= 300) {
                throw new E2BApiException(status, "E2B API request failed: " + method + " " + url + " -> " + status, body);
            }
            if (responseType == null || responseType == Void.class) {
                return null;
            }
            if (body == null || body.trim().isEmpty()) {
                return null;
            }
            return JSON.parseObject(body, responseType);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void setAuthHeaders(HttpURLConnection connection) {
        String apiKey = config.getApiKey();
        if (apiKey != null) {
            connection.setRequestProperty("X-API-Key", apiKey);
        }
    }

    private static String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        try {
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int safeTimeout(long timeoutMillis) {
        if (timeoutMillis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) timeoutMillis;
    }

    static String encodePath(String value) throws IOException {
        return URLEncoder.encode(value, UTF_8).replace("+", "%20");
    }

    static String requireText(String value, String message) {
        String text = E2BSandboxConfig.trimToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }
}
