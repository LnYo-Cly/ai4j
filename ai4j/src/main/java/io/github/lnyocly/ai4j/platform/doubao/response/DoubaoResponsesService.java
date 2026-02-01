package io.github.lnyocly.ai4j.platform.doubao.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.ResponseSseListener;
import io.github.lnyocly.ai4j.platform.openai.response.ResponseEventParser;
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

/**
 * @Author cly
 * @Description Doubao Responses API service
 * @Date 2026/2/1
 */
public class DoubaoResponsesService implements IResponsesService {

    private final DoubaoConfig doubaoConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public DoubaoResponsesService(Configuration configuration) {
        this.doubaoConfig = configuration.getDoubaoConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = EventSources.createFactory(okHttpClient);
    }

    @Override
    public Response create(String baseUrl, String apiKey, ResponseRequest request) throws Exception {
        String url = resolveUrl(baseUrl, doubaoConfig.getResponsesUrl());
        String key = resolveApiKey(apiKey);
        request.setStream(false);
        request.setStreamOptions(null);

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(request);

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
        throw new CommonException("Doubao Responses request failed");
    }

    @Override
    public Response create(ResponseRequest request) throws Exception {
        return create(null, null, request);
    }

    @Override
    public void createStream(String baseUrl, String apiKey, ResponseRequest request, ResponseSseListener listener) throws Exception {
        String url = resolveUrl(baseUrl, doubaoConfig.getResponsesUrl());
        String key = resolveApiKey(apiKey);
        if (request.getStream() == null || !request.getStream()) {
            request.setStream(true);
        }
        request.setStreamOptions(null);

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(request);

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
        String url = resolveUrl(baseUrl, doubaoConfig.getResponsesUrl() + "/" + responseId);
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
        throw new CommonException("Doubao Responses retrieve failed");
    }

    @Override
    public Response retrieve(String responseId) throws Exception {
        return retrieve(null, null, responseId);
    }

    @Override
    public ResponseDeleteResponse delete(String baseUrl, String apiKey, String responseId) throws Exception {
        String url = resolveUrl(baseUrl, doubaoConfig.getResponsesUrl() + "/" + responseId);
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
        throw new CommonException("Doubao Responses delete failed");
    }

    @Override
    public ResponseDeleteResponse delete(String responseId) throws Exception {
        return delete(null, null, responseId);
    }

    private String resolveUrl(String baseUrl, String path) {
        String host = (baseUrl == null || "".equals(baseUrl)) ? doubaoConfig.getApiHost() : baseUrl;
        return ValidateUtil.concatUrl(host, path);
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || "".equals(apiKey)) ? doubaoConfig.getApiKey() : apiKey;
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
