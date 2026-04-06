package io.github.lnyocly.ai4j.agentflow.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agentflow.AgentFlowConfig;
import io.github.lnyocly.ai4j.agentflow.AgentFlowException;
import io.github.lnyocly.ai4j.agentflow.AgentFlowUsage;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AgentFlowSupport {

    protected static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);

    protected final Configuration configuration;
    protected final AgentFlowConfig agentFlowConfig;
    protected final OkHttpClient okHttpClient;
    protected final EventSource.Factory eventSourceFactory;

    protected AgentFlowSupport(Configuration configuration, AgentFlowConfig agentFlowConfig) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration is required");
        }
        if (configuration.getOkHttpClient() == null) {
            throw new IllegalArgumentException("OkHttpClient configuration is required");
        }
        if (agentFlowConfig == null) {
            throw new IllegalArgumentException("agentFlowConfig is required");
        }
        this.configuration = configuration;
        this.agentFlowConfig = agentFlowConfig;
        this.okHttpClient = configuration.getOkHttpClient();
        this.eventSourceFactory = configuration.createRequestFactory();
    }

    protected String defaultUserId(String requestUserId) {
        if (!isBlank(requestUserId)) {
            return requestUserId;
        }
        if (!isBlank(agentFlowConfig.getUserId())) {
            return agentFlowConfig.getUserId();
        }
        return "default-user";
    }

    protected String defaultConversationId(String requestConversationId) {
        if (!isBlank(requestConversationId)) {
            return requestConversationId;
        }
        return agentFlowConfig.getConversationId();
    }

    protected String requireBaseUrl() {
        if (isBlank(agentFlowConfig.getBaseUrl())) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        return agentFlowConfig.getBaseUrl();
    }

    protected String requireWebhookUrl() {
        if (isBlank(agentFlowConfig.getWebhookUrl())) {
            throw new IllegalArgumentException("webhookUrl is required");
        }
        return agentFlowConfig.getWebhookUrl();
    }

    protected String requireApiKey() {
        if (isBlank(agentFlowConfig.getApiKey())) {
            throw new IllegalArgumentException("apiKey is required");
        }
        return agentFlowConfig.getApiKey();
    }

    protected String requireBotId() {
        if (isBlank(agentFlowConfig.getBotId())) {
            throw new IllegalArgumentException("botId is required");
        }
        return agentFlowConfig.getBotId();
    }

    protected String requireWorkflowId(String requestWorkflowId) {
        if (!isBlank(requestWorkflowId)) {
            return requestWorkflowId;
        }
        if (!isBlank(agentFlowConfig.getWorkflowId())) {
            return agentFlowConfig.getWorkflowId();
        }
        throw new IllegalArgumentException("workflowId is required");
    }

    protected String joinedUrl(String baseUrl, String path) {
        return UrlUtils.concatUrl(baseUrl, path);
    }

    protected String appendQuery(String url, Map<String, String> queryParameters) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        HttpUrl.Builder builder = parsed.newBuilder();
        if (queryParameters != null) {
            for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
                if (!isBlank(entry.getValue())) {
                    builder.addQueryParameter(entry.getKey(), entry.getValue());
                }
            }
        }
        return builder.build().toString();
    }

    protected RequestBody jsonBody(Object body) {
        return RequestBody.create(JSON.toJSONString(body), JSON_MEDIA_TYPE);
    }

    protected Request.Builder jsonRequestBuilder(String url) {
        Request.Builder builder = new Request.Builder().url(url);
        builder.header("Content-Type", Constants.APPLICATION_JSON);
        if (!isBlank(agentFlowConfig.getApiKey())) {
            builder.header("Authorization", "Bearer " + agentFlowConfig.getApiKey());
        }
        if (agentFlowConfig.getHeaders() != null) {
            for (Map.Entry<String, String> entry : agentFlowConfig.getHeaders().entrySet()) {
                if (!isBlank(entry.getKey()) && entry.getValue() != null) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
        }
        return builder;
    }

    protected String execute(Request request) throws IOException {
        try (Response response = okHttpClient.newCall(request).execute()) {
            return readResponse(request, response);
        }
    }

    protected JSONObject executeObject(Request request) throws IOException {
        String body = execute(request);
        if (isBlank(body)) {
            return new JSONObject();
        }
        Object parsed = JSON.parse(body);
        if (parsed instanceof JSONObject) {
            return (JSONObject) parsed;
        }
        throw new AgentFlowException("Expected JSON object response but got: " + body);
    }

    protected Object parseJsonOrText(String body) {
        if (isBlank(body)) {
            return null;
        }
        try {
            return JSON.parse(body);
        } catch (Exception ex) {
            return body;
        }
    }

    protected String readResponse(Request request, Response response) throws IOException {
        ResponseBody body = response.body();
        String content = body == null ? "" : body.string();
        if (!response.isSuccessful()) {
            throw new AgentFlowException("HTTP " + response.code() + " calling " + request.url() + ": " + abbreviate(content));
        }
        return content;
    }

    protected void assertCozeSuccess(JSONObject response) {
        Integer code = response == null ? null : response.getInteger("code");
        if (code != null && code.intValue() != 0) {
            throw new AgentFlowException("Coze request failed: code=" + code + ", msg=" + response.getString("msg"));
        }
    }

    protected Map<String, Object> mutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        return new LinkedHashMap<String, Object>(source);
    }

    protected Map<String, String> toStringMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    protected String extractText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) value;
            String direct = firstNonBlank(
                    jsonObject.getString("answer"),
                    jsonObject.getString("output"),
                    jsonObject.getString("text"),
                    jsonObject.getString("content"),
                    jsonObject.getString("result"),
                    jsonObject.getString("message")
            );
            if (!isBlank(direct)) {
                return direct;
            }
            if (jsonObject.size() == 1) {
                Map.Entry<String, Object> entry = jsonObject.entrySet().iterator().next();
                return extractText(entry.getValue());
            }
            return JSON.toJSONString(jsonObject);
        }
        return String.valueOf(value);
    }

    protected AgentFlowUsage usageFromDify(JSONObject usage) {
        if (usage == null || usage.isEmpty()) {
            return null;
        }
        return AgentFlowUsage.builder()
                .inputTokens(usage.getInteger("prompt_tokens"))
                .outputTokens(usage.getInteger("completion_tokens"))
                .totalTokens(usage.getInteger("total_tokens"))
                .raw(usage)
                .build();
    }

    protected AgentFlowUsage usageFromCoze(JSONObject usage) {
        if (usage == null || usage.isEmpty()) {
            return null;
        }
        return AgentFlowUsage.builder()
                .inputTokens(firstNonNullInteger(usage.getInteger("input_tokens"), usage.getInteger("input_count")))
                .outputTokens(firstNonNullInteger(usage.getInteger("output_tokens"), usage.getInteger("output_count")))
                .totalTokens(usage.getInteger("token_count"))
                .raw(usage)
                .build();
    }

    protected Integer firstNonNullInteger(Integer first, Integer second) {
        return first != null ? first : second;
    }

    protected String firstNonBlank(String first, String second) {
        return firstNonBlank(first, second, null, null, null, null);
    }

    protected String firstNonBlank(String first,
                                   String second,
                                   String third,
                                   String fourth,
                                   String fifth,
                                   String sixth) {
        String[] values = new String[]{first, second, third, fourth, fifth, sixth};
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    protected long pollIntervalMillis() {
        Long value = agentFlowConfig.getPollIntervalMillis();
        return value == null || value.longValue() <= 0L ? 1_000L : value.longValue();
    }

    protected long pollTimeoutMillis() {
        Long value = agentFlowConfig.getPollTimeoutMillis();
        return value == null || value.longValue() <= 0L ? 60_000L : value.longValue();
    }

    protected void sleep(long millis) throws InterruptedException {
        if (millis > 0L) {
            Thread.sleep(millis);
        }
    }

    protected String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500) + "...";
    }

    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
