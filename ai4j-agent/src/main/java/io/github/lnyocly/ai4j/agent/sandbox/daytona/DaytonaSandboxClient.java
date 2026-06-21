package io.github.lnyocly.ai4j.agent.sandbox.daytona;

import com.alibaba.fastjson2.JSON;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small Java 8 Daytona HTTP client used by {@link DaytonaSandboxProvider}.
 */
public class DaytonaSandboxClient {

    private static final String APPLICATION_JSON = "application/json";

    private final DaytonaSandboxConfig config;

    public DaytonaSandboxClient(DaytonaSandboxConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("daytona sandbox config must not be null");
        }
        this.config = config;
    }

    public DaytonaSandbox getSandbox(String idOrName) throws IOException {
        String path = "/sandbox/" + encodePath(requireText(idOrName, "sandbox id or name must not be blank"));
        return request("GET", config.getApiUrl() + path, null, DaytonaSandbox.class, true);
    }

    public DaytonaSandbox createSandbox(DaytonaCreateSandboxRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("create sandbox request must not be null");
        }
        return request("POST", config.getApiUrl() + "/sandbox", request.toPayload(), DaytonaSandbox.class, true);
    }

    public DaytonaSandbox startSandbox(String idOrName) throws IOException {
        String path = "/sandbox/" + encodePath(requireText(idOrName, "sandbox id or name must not be blank")) + "/start";
        return request("POST", config.getApiUrl() + path, null, DaytonaSandbox.class, true);
    }

    public DaytonaSandbox deleteSandbox(String idOrName) throws IOException {
        String path = "/sandbox/" + encodePath(requireText(idOrName, "sandbox id or name must not be blank"));
        return request("DELETE", config.getApiUrl() + path, null, DaytonaSandbox.class, true);
    }

    public String getToolboxProxyUrl(String sandboxId) throws IOException {
        String id = requireText(sandboxId, "sandbox id must not be blank");
        if (config.getToolboxProxyUrl() != null) {
            return config.getToolboxProxyUrl();
        }
        DaytonaToolboxProxyUrl response = request(
                "GET",
                config.getApiUrl() + "/sandbox/" + encodePath(id) + "/toolbox-proxy-url",
                null,
                DaytonaToolboxProxyUrl.class,
                true);
        return response == null ? null : response.getUrl();
    }

    public DaytonaExecuteResponse execute(String sandboxId, String toolboxProxyUrl, DaytonaExecuteRequest request) throws IOException {
        String id = requireText(sandboxId, "sandbox id must not be blank");
        String base = DaytonaSandboxConfig.trimTrailingSlash(toolboxProxyUrl);
        if (base == null) {
            base = getToolboxProxyUrl(id);
        }
        if (base == null) {
            throw new IOException("Daytona toolbox proxy URL is not configured and was not returned by API");
        }
        String modernUrl = base + "/" + encodePath(id) + "/process/execute";
        return request("POST", modernUrl, executePayload(request), DaytonaExecuteResponse.class, true);
    }

    DaytonaSandboxConfig getConfig() {
        return config;
    }

    protected <T> T request(String method,
                            String url,
                            Object payload,
                            Class<T> responseType,
                            boolean authenticated) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(safeTimeout(config.getConnectTimeoutMillis()));
            connection.setReadTimeout(safeTimeout(config.getReadTimeoutMillis()));
            connection.setRequestProperty("Accept", APPLICATION_JSON);
            connection.setRequestProperty("User-Agent", "ai4j-agent-daytona-sandbox/1");
            if (authenticated) {
                setAuthHeaders(connection);
            }
            byte[] requestBytes = null;
            if (payload != null) {
                requestBytes = JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", APPLICATION_JSON);
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
                throw new DaytonaApiException(status, "Daytona API request failed: " + method + " " + url + " -> " + status, body);
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
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        if (config.getOrganizationId() != null) {
            connection.setRequestProperty("X-Daytona-Organization-ID", config.getOrganizationId());
        }
    }

    private static Map<String, Object> executePayload(DaytonaExecuteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("execute request must not be null");
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("command", request.getCommand());
        if (request.getCwd() != null) {
            payload.put("cwd", request.getCwd());
        }
        if (request.getStdin() != null) {
            payload.put("stdin", request.getStdin());
        }
        if (request.getTimeout() != null) {
            payload.put("timeout", request.getTimeout());
        }
        Map<String, String> envs = request.getEnvs();
        if (envs != null && !envs.isEmpty()) {
            payload.put("envs", envs);
        }
        return payload;
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
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    static String requireText(String value, String message) {
        String text = DaytonaSandboxConfig.trimToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private static final class DaytonaToolboxProxyUrl {
        private String url;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = DaytonaSandboxConfig.trimTrailingSlash(url);
        }
    }
}
