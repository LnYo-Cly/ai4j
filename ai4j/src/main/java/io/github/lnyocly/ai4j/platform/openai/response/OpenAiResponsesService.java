package io.github.lnyocly.ai4j.platform.openai.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.ResponseSseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.StreamOptions;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseDeleteResponse;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseStreamEvent;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IResponsesService;
import io.github.lnyocly.ai4j.utils.ValidateUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @Author cly
 * @Description OpenAI Responses API service
 * @Date 2026/2/1
 */
public class OpenAiResponsesService implements IResponsesService {

    private static final Set<String> OPENAI_ALLOWED_FIELDS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "model",
            "input",
            "include",
            "instructions",
            "max_output_tokens",
            "metadata",
            "parallel_tool_calls",
            "previous_response_id",
            "reasoning",
            "store",
            "stream",
            "stream_options",
            "temperature",
            "text",
            "tool_choice",
            "tools",
            "top_p",
            "truncation",
            "user",
            "background"
    ));

    private final OpenAiConfig openAiConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public OpenAiResponsesService(Configuration configuration) {
        this.openAiConfig = configuration.getOpenAiConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = EventSources.createFactory(okHttpClient);
    }

    @Override
    public Response create(String baseUrl, String apiKey, ResponseRequest request) throws Exception {
        String url = resolveUrl(baseUrl, openAiConfig.getResponsesUrl());
        String key = resolveApiKey(apiKey);
        request.setStream(false);
        request.setStreamOptions(null);

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(buildOpenAiPayload(request));

        Request httpRequest = new Request.Builder()
                .header("Authorization", "Bearer " + key)
                .url(url)
                .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), body))
                .build();

        try (okhttp3.Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return mapper.readValue(response.body().string(), Response.class);
            }
        }
        throw new CommonException("OpenAI Responses request failed");
    }

    @Override
    public Response create(ResponseRequest request) throws Exception {
        return create(null, null, request);
    }

    @Override
    public void createStream(String baseUrl, String apiKey, ResponseRequest request, ResponseSseListener listener) throws Exception {
        String url = resolveUrl(baseUrl, openAiConfig.getResponsesUrl());
        String key = resolveApiKey(apiKey);
        if (request.getStream() == null || !request.getStream()) {
            request.setStream(true);
        }
        if (request.getStreamOptions() == null) {
            request.setStreamOptions(new StreamOptions(true));
        }

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(buildOpenAiPayload(request));

        Request httpRequest = new Request.Builder()
                .header("Authorization", "Bearer " + key)
                .url(url)
                .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), body))
                .build();

        factory.newEventSource(httpRequest, convertEventSource(mapper, listener));
        listener.getCountDownLatch().await();
    }

    @Override
    public void createStream(ResponseRequest request, ResponseSseListener listener) throws Exception {
        createStream(null, null, request, listener);
    }

    @Override
    public Response retrieve(String baseUrl, String apiKey, String responseId) throws Exception {
        String url = resolveUrl(baseUrl, openAiConfig.getResponsesUrl() + "/" + responseId);
        String key = resolveApiKey(apiKey);
        ObjectMapper mapper = new ObjectMapper();

        Request httpRequest = new Request.Builder()
                .header("Authorization", "Bearer " + key)
                .url(url)
                .get()
                .build();

        try (okhttp3.Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return mapper.readValue(response.body().string(), Response.class);
            }
        }
        throw new CommonException("OpenAI Responses retrieve failed");
    }

    @Override
    public Response retrieve(String responseId) throws Exception {
        return retrieve(null, null, responseId);
    }

    @Override
    public ResponseDeleteResponse delete(String baseUrl, String apiKey, String responseId) throws Exception {
        String url = resolveUrl(baseUrl, openAiConfig.getResponsesUrl() + "/" + responseId);
        String key = resolveApiKey(apiKey);
        ObjectMapper mapper = new ObjectMapper();

        Request httpRequest = new Request.Builder()
                .header("Authorization", "Bearer " + key)
                .url(url)
                .delete()
                .build();

        try (okhttp3.Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return mapper.readValue(response.body().string(), ResponseDeleteResponse.class);
            }
        }
        throw new CommonException("OpenAI Responses delete failed");
    }

    @Override
    public ResponseDeleteResponse delete(String responseId) throws Exception {
        return delete(null, null, responseId);
    }

    private String resolveUrl(String baseUrl, String path) {
        String host = (baseUrl == null || "".equals(baseUrl)) ? openAiConfig.getApiHost() : baseUrl;
        return ValidateUtil.concatUrl(host, path);
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || "".equals(apiKey)) ? openAiConfig.getApiKey() : apiKey;
    }

    private Map<String, Object> buildOpenAiPayload(ResponseRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.getModel() != null) {
            payload.put("model", request.getModel());
        }
        if (request.getInput() != null) {
            payload.put("input", request.getInput());
        }
        if (request.getInclude() != null) {
            payload.put("include", request.getInclude());
        }
        if (request.getInstructions() != null) {
            payload.put("instructions", request.getInstructions());
        }
        if (request.getMaxOutputTokens() != null) {
            payload.put("max_output_tokens", request.getMaxOutputTokens());
        }
        if (request.getMetadata() != null) {
            payload.put("metadata", request.getMetadata());
        }
        if (request.getParallelToolCalls() != null) {
            payload.put("parallel_tool_calls", request.getParallelToolCalls());
        }
        if (request.getPreviousResponseId() != null) {
            payload.put("previous_response_id", request.getPreviousResponseId());
        }
        if (request.getReasoning() != null) {
            payload.put("reasoning", request.getReasoning());
        }
        if (request.getStore() != null) {
            payload.put("store", request.getStore());
        }
        if (request.getStream() != null) {
            payload.put("stream", request.getStream());
        }
        if (request.getStreamOptions() != null) {
            payload.put("stream_options", request.getStreamOptions());
        }
        if (request.getTemperature() != null) {
            payload.put("temperature", request.getTemperature());
        }
        if (request.getText() != null) {
            payload.put("text", request.getText());
        }
        if (request.getToolChoice() != null) {
            payload.put("tool_choice", request.getToolChoice());
        }
        if (request.getTools() != null) {
            payload.put("tools", request.getTools());
        }
        if (request.getTopP() != null) {
            payload.put("top_p", request.getTopP());
        }
        if (request.getTruncation() != null) {
            payload.put("truncation", request.getTruncation());
        }
        if (request.getUser() != null) {
            payload.put("user", request.getUser());
        }
        if (request.getExtraBody() != null) {
            for (Map.Entry<String, Object> entry : request.getExtraBody().entrySet()) {
                if (OPENAI_ALLOWED_FIELDS.contains(entry.getKey()) && !payload.containsKey(entry.getKey())) {
                    payload.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return payload;
    }

    private EventSourceListener convertEventSource(ObjectMapper mapper, ResponseSseListener listener) {
        return new EventSourceListener() {
            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull okhttp3.Response response) {
                // no-op
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable okhttp3.Response response) {
                listener.onError(t, response);
                listener.complete();
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                if ("[DONE]".equalsIgnoreCase(data)) {
                    listener.complete();
                    return;
                }
                try {
                    ResponseStreamEvent event = ResponseEventParser.parse(mapper, data);
                    listener.accept(event);
                    if (isTerminalEvent(event.getType())) {
                        listener.complete();
                    }
                } catch (Exception e) {
                    listener.onError(e, null);
                    listener.complete();
                }
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                listener.complete();
            }
        };
    }

    private boolean isTerminalEvent(String type) {
        if (type == null) {
            return false;
        }
        return "response.completed".equals(type)
                || "response.failed".equals(type)
                || "response.incomplete".equals(type);
    }
}
