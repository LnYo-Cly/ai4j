package io.github.lnyocly.ai4j.flowgram.springboot.node;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionResult;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FlowGramHttpNodeExecutor implements FlowGramNodeExecutor {

    private final FlowGramNodeValueResolver valueResolver = new FlowGramNodeValueResolver();

    @Override
    public String getType() {
        return "HTTP";
    }

    @Override
    public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) throws Exception {
        Map<String, Object> nodeData = context == null || context.getNode() == null
                ? new LinkedHashMap<String, Object>()
                : safeMap(context.getNode().getData());

        Map<String, Object> api = valueResolver.resolveMap(mapValue(nodeData.get("api")), context);
        Map<String, Object> headers = valueResolver.resolveMap(mapValue(nodeData.get("headersValues")), context);
        Map<String, Object> params = valueResolver.resolveMap(mapValue(nodeData.get("paramsValues")), context);
        Map<String, Object> timeout = valueResolver.resolveMap(mapValue(nodeData.get("timeout")), context);
        Map<String, Object> body = safeMap(mapValue(nodeData.get("body")));

        String method = firstNonBlank(valueAsString(api.get("method")), "GET").toUpperCase(Locale.ROOT);
        String url = valueAsString(api.get("url"));
        if (isBlank(url)) {
            throw new IllegalArgumentException("HTTP node requires api.url");
        }

        String fullUrl = appendQueryParams(url, params);
        int timeoutMs = intValue(timeout.get("timeout"), 10000);
        int retryTimes = Math.max(1, intValue(timeout.get("retryTimes"), 1));

        Exception lastError = null;
        for (int attempt = 0; attempt < retryTimes; attempt++) {
            try {
                return FlowGramNodeExecutionResult.builder()
                        .outputs(executeRequest(fullUrl, method, headers, body, timeoutMs, context))
                        .build();
            } catch (Exception ex) {
                lastError = ex;
            }
        }
        throw lastError == null ? new IllegalStateException("HTTP node execution failed") : lastError;
    }

    private Map<String, Object> executeRequest(String fullUrl,
                                               String method,
                                               Map<String, Object> headers,
                                               Map<String, Object> body,
                                               int timeoutMs,
                                               FlowGramNodeExecutionContext context) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(fullUrl).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setUseCaches(false);
        connection.setDoInput(true);

        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            connection.setRequestProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }

        String requestBody = buildRequestBody(body, context);
        if (requestBody != null && allowsRequestBody(method)) {
            connection.setDoOutput(true);
            if (isBlank(connection.getRequestProperty("Content-Type"))) {
                String bodyType = valueAsString(body.get("bodyType"));
                if ("JSON".equalsIgnoreCase(bodyType)) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                } else if ("raw-text".equalsIgnoreCase(bodyType)) {
                    connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                }
            }
            byte[] payload = requestBody.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
            OutputStream outputStream = connection.getOutputStream();
            try {
                outputStream.write(payload);
            } finally {
                outputStream.close();
            }
        }

        int statusCode = connection.getResponseCode();
        String responseBody = readBody(connection, statusCode);
        Map<String, Object> responseHeaders = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values.size() == 1) {
                responseHeaders.put(entry.getKey(), values.get(0));
            } else {
                responseHeaders.put(entry.getKey(), new ArrayList<String>(values));
            }
        }

        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("statusCode", statusCode);
        outputs.put("body", responseBody);
        outputs.put("headers", responseHeaders);
        outputs.put("contentType", connection.getContentType());
        return outputs;
    }

    private String buildRequestBody(Map<String, Object> body, FlowGramNodeExecutionContext context) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        String bodyType = valueAsString(body.get("bodyType"));
        if (isBlank(bodyType) || "none".equalsIgnoreCase(bodyType)) {
            return null;
        }
        if ("JSON".equalsIgnoreCase(bodyType)) {
            Object jsonValue = valueResolver.resolve(body.get("json"), context);
            if (jsonValue == null) {
                return null;
            }
            return jsonValue instanceof String ? String.valueOf(jsonValue) : JSON.toJSONString(jsonValue);
        }
        if ("raw-text".equalsIgnoreCase(bodyType)) {
            Object rawText = valueResolver.resolve(body.get("rawText"), context);
            return rawText == null ? null : String.valueOf(rawText);
        }
        Object resolved = valueResolver.resolve(body, context);
        return resolved == null ? null : JSON.toJSONString(resolved);
    }

    private String readBody(HttpURLConnection connection, int statusCode) throws Exception {
        InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (inputStream == null) {
            return null;
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        try {
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
        } finally {
            inputStream.close();
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private String appendQueryParams(String rawUrl, Map<String, Object> params) throws Exception {
        if (params == null || params.isEmpty()) {
            return rawUrl;
        }
        StringBuilder builder = new StringBuilder(rawUrl);
        builder.append(rawUrl.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey()))
                    .append('=')
                    .append(encode(String.valueOf(entry.getValue())));
            first = false;
        }
        return builder.toString();
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private boolean allowsRequestBody(String method) {
        return !"GET".equals(method) && !"HEAD".equals(method);
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<String, Object>() : value;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return isBlank(first) ? fallback : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
