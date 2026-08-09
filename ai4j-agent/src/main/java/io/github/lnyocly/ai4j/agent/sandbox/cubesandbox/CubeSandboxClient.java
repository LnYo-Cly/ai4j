package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

class CubeSandboxClient {

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

    JSONObject health() throws IOException, CubeSandboxApiException {
        return requestJson("GET", "/health", null, 200);
    }

    CubeSandboxRemote create(String templateId,
                             int timeoutSeconds,
                             Map<String, String> envVars,
                             Map<String, String> metadata,
                             Boolean allowInternetAccess,
                             Object network) throws IOException, CubeSandboxApiException {
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

    CubeSandboxRemote connect(String sandboxId) throws IOException, CubeSandboxApiException {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("timeout", Integer.valueOf(config.getTimeoutSeconds()));
        return toRemote(requestJson("POST", "/sandboxes/" + urlEncodePath(sandboxId) + "/connect", payload, 200, 201));
    }

    JSONObject getSandbox(String sandboxId) throws IOException, CubeSandboxApiException {
        return requestJson("GET", "/sandboxes/" + urlEncodePath(sandboxId), null, 200);
    }

    void kill(String sandboxId) throws IOException, CubeSandboxApiException {
        requestJson("DELETE", "/sandboxes/" + urlEncodePath(sandboxId), null, 200, 204);
    }

    ProcessRun runProcess(CubeSandboxRemote remote,
                          String command,
                          String cwd,
                          Map<String, String> environment,
                          Long timeoutMillis) throws IOException, CubeSandboxApiException {
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
        HttpURLConnection connection = null;
        try {
            URL url = dataUrl(remote, config.getEnvdPort(), "/process.Process/Start");
            connection = (HttpURLConnection) url.openConnection();
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
                String errorBody = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
                throw new CubeSandboxApiException(status, "CubeSandbox process start failed: POST " + url + " -> " + status, errorBody);
            }
            ProcessRun run = parseProcessStream(connection.getInputStream());
            run.durationMillis = Long.valueOf(System.currentTimeMillis() - start);
            return run;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject requestJson(String method, String path, Object payload, int... okStatuses) throws IOException, CubeSandboxApiException {
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
                String errorBody = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
                throw new CubeSandboxApiException(status, "CubeSandbox control API failed: " + method + " " + path + " -> " + status, errorBody);
            }
            if (status == 204) {
                return new JSONObject();
            }
            String body = readBody(connection.getInputStream());
            if (body == null || body.trim().isEmpty()) {
                return new JSONObject();
            }
            return JSON.parseObject(body);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private URL dataUrl(CubeSandboxRemote remote, int port, String path) throws IOException, CubeSandboxApiException {
        try {
            if (config.getEnvdBaseUrl() != null) {
                return new URL(config.getEnvdBaseUrl() + path);
            }
            return new URL("http://" + remote.host(port, config.getSandboxDomain()) + path);
        } catch (io.github.lnyocly.ai4j.agent.sandbox.SandboxException e) {
            throw new CubeSandboxApiException(400, "Invalid remote host: " + e.getMessage(), null);
        }
    }

    private ProcessRun parseProcessStream(InputStream input) throws IOException, CubeSandboxApiException {
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
                throw new CubeSandboxApiException(400, "CubeSandbox Connect stream message too large: " + size + " bytes", null);
            }
            byte[] payload = new byte[size];
            data.readFully(payload);
            if ((flags & CONNECT_COMPRESSED_FLAG) != 0) {
                throw new CubeSandboxApiException(400, "CubeSandbox Connect stream compressed messages are not supported", null);
            }
            if ((flags & CONNECT_END_STREAM_FLAG) != 0) {
                parseConnectEndStream(payload);
                continue;
            }
            JSONObject response;
            try {
                response = JSON.parseObject(new String(payload, StandardCharsets.UTF_8));
            } catch (RuntimeException parseError) {
                // Skip malformed Connect data frames (lenient parse, mirrors E2B).
                continue;
            }
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
                        throw new CubeSandboxApiException(400, "CubeSandbox process failed: " + error, null);
                    }
                    throw new CubeSandboxApiException(400, "CubeSandbox process stream ended without exit code", null);
                }
                run.exitCode = exitCode;
                sawEnd = true;
            }
        }
        if (!sawEnd) {
            throw new CubeSandboxApiException(400, "CubeSandbox process stream ended without EndEvent", null);
        }
        run.stdout = stdout.toString();
        run.stderr = stderr.toString();
        return run;
    }

    private void parseConnectEndStream(byte[] payload) throws CubeSandboxApiException {
        if (payload == null || payload.length == 0) {
            return;
        }
        JSONObject object;
        try {
            object = JSON.parseObject(new String(payload, StandardCharsets.UTF_8));
        } catch (RuntimeException parseError) {
            return;
        }
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
            throw new CubeSandboxApiException(400, "CubeSandbox Connect stream error " + code + ": " + message, null);
        }
        throw new CubeSandboxApiException(400, "CubeSandbox Connect stream error: " + message, null);
    }

    private static String decodeBase64(String value) {
        try {
            byte[] raw = Base64.getDecoder().decode(value);
            return new String(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            return value;
        }
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

    private static CubeSandboxRemote toRemote(JSONObject object) throws CubeSandboxApiException {
        String sandboxId = object.getString("sandboxID");
        String templateId = object.getString("templateID");
        if (sandboxId == null || sandboxId.trim().isEmpty()) {
            throw new CubeSandboxApiException(400, "CubeSandbox response missing sandboxID", null);
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

    private static String requireText(String value, String message) throws CubeSandboxApiException {
        if (value == null || value.trim().isEmpty()) {
            throw new CubeSandboxApiException(400, message, null);
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

    private static String safeHeaderValue(String value, String headerName) throws CubeSandboxApiException {
        if (value == null) {
            return "";
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new CubeSandboxApiException(400, "CubeSandbox " + headerName + " header contains invalid line break", null);
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

    private static int readTimeout(Long timeoutMillis) {
        if (timeoutMillis == null || timeoutMillis.longValue() <= 0) {
            return DEFAULT_PROCESS_READ_TIMEOUT_MILLIS;
        }
        long withMargin = timeoutMillis.longValue() + 5000L;
        return withMargin > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) withMargin;
    }

    private static String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
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
}
