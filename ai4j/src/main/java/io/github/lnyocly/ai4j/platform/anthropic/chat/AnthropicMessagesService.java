package io.github.lnyocly.ai4j.platform.anthropic.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.errors.AnthropicApiException;
import io.github.lnyocly.ai4j.platform.anthropic.stream.AnthropicStreamHandler;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IMessagesService;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Anthropic Messages 原生服务（{@code /v1/messages}），实现 {@link IMessagesService}。
 * <p>
 * 原生 in {@link AnthropicChatCompletion} → 原生 out {@link AnthropicChatCompletionResponse}，零 OpenAI 转换。
 * {@code messages(...)} 为单轮语义；{@code messagesStream(...)} 以 {@link AnthropicStreamHandler} 回调暴露原生事件。
 * 鉴权 {@code x-api-key} + {@code anthropic-version}；错误抛 {@link AnthropicApiException}。
 * <p>
 * 统一 OpenAI 适配器 {@link AnthropicChatService} 委托本类的传输与 SSE 解析，只额外做 OpenAI↔Anthropic 翻译。
 */
@Slf4j
public class AnthropicMessagesService implements IMessagesService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);

    private final AnthropicConfig anthropicConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;
    private final ObjectMapper objectMapper;

    public AnthropicMessagesService(Configuration configuration) {
        this.anthropicConfig = configuration.getAnthropicConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AnthropicChatCompletionResponse messages(String baseUrl, String apiKey, AnthropicChatCompletion request) throws Exception {
        return executeRequest(resolveBaseUrl(baseUrl), resolveApiKey(apiKey), request);
    }

    @Override
    public AnthropicChatCompletionResponse messages(AnthropicChatCompletion request) throws Exception {
        return messages(null, null, request);
    }

    @Override
    public void messagesStream(String baseUrl, String apiKey, AnthropicChatCompletion request, final AnthropicStreamHandler handler) throws Exception {
        request.setStream(Boolean.TRUE);
        Request httpRequest = buildRequest(resolveBaseUrl(baseUrl), resolveApiKey(apiKey), request);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
        EventSourceListener listener = toEventListener(handler, new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        }, errorRef);
        factory.newEventSource(httpRequest, listener);
        latch.await();
        Throwable err = errorRef.get();
        if (err != null) {
            if (err instanceof Exception) {
                throw (Exception) err;
            }
            throw new RuntimeException(err);
        }
    }

    @Override
    public void messagesStream(AnthropicChatCompletion request, AnthropicStreamHandler handler) throws Exception {
        messagesStream(null, null, request, handler);
    }

    // ---- transport (shared with the unified adapter) ----

    public AnthropicChatCompletionResponse executeRequest(String baseUrl, String apiKey, AnthropicChatCompletion request) throws Exception {
        Request httpRequest = buildRequest(baseUrl, apiKey, request);
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return objectMapper.readValue(response.body().string(), AnthropicChatCompletionResponse.class);
            }
            throw decodeError(response);
        }
    }

    public Request buildRequest(String baseUrl, String apiKey, AnthropicChatCompletion request) throws JsonProcessingException {
        String body = objectMapper.writeValueAsString(request);
        return new Request.Builder()
                .header("x-api-key", apiKey)
                .header("anthropic-version", anthropicConfig.getApiVersion())
                .header("Content-Type", Constants.APPLICATION_JSON)
                .url(UrlUtils.concatUrl(baseUrl, anthropicConfig.getChatCompletionUrl()))
                .post(RequestBody.create(body, JSON_MEDIA_TYPE))
                .build();
    }

    public EventSource.Factory getFactory() {
        return factory;
    }

    /**
     * 把 Anthropic SSE 事件解析为 {@link AnthropicStreamHandler} 回调。供 {@link #messagesStream} 与
     * 统一适配器（桥接到 OpenAI chunk）共用，避免事件解析重复。
     *
     * @param completeSink message_stop 时调用（可为 null）
     * @param errorRef    onFailure 时写入异常（可为 null）
     */
    public EventSourceListener toEventListener(final AnthropicStreamHandler handler,
                                               final Runnable completeSink,
                                               final AtomicReference<Throwable> errorRef) {
        return new EventSourceListener() {
            private final Map<Integer, String[]> toolBlocks = new HashMap<Integer, String[]>();
            private final Map<Integer, StringBuilder> toolInputs = new HashMap<Integer, StringBuilder>();

            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                Throwable resolved = t;
                if (resolved == null && response != null) {
                    resolved = decodeError(response);
                }
                if (resolved == null) {
                    resolved = new AnthropicApiException(0, null, "anthropic stream failed", null);
                }
                if (errorRef != null) {
                    errorRef.set(resolved);
                }
                safeError(handler, resolved);
                if (completeSink != null) {
                    completeSink.run();
                }
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                if ("[DONE]".equalsIgnoreCase(data)) {
                    return;
                }
                JsonNode node;
                try {
                    node = objectMapper.readTree(data);
                } catch (JsonProcessingException e) {
                    safeError(handler, new AnthropicApiException(0, "parse_error", "anthropic stream json parse error", null, e));
                    return;
                }
                String eventType = node.path("type").asText();
                if ("message_start".equals(eventType)) {
                    JsonNode message = node.path("message");
                    safeStart(handler, message.path("id").asText(null), message.path("model").asText(null));
                } else if ("content_block_start".equals(eventType)) {
                    int idx = node.path("index").asInt();
                    JsonNode block = node.path("content_block");
                    String blockType = block.path("type").asText();
                    if ("tool_use".equals(blockType)) {
                        String toolId = block.path("id").asText(null);
                        String name = block.path("name").asText(null);
                        toolBlocks.put(idx, new String[]{toolId, name});
                        toolInputs.put(idx, new StringBuilder());
                        handler.onToolUseStart(idx, toolId, name);
                    }
                } else if ("content_block_delta".equals(eventType)) {
                    int idx = node.path("index").asInt();
                    JsonNode delta = node.path("delta");
                    String deltaType = delta.path("type").asText();
                    if ("text_delta".equals(deltaType)) {
                        handler.onDeltaText(delta.path("text").asText(""));
                    } else if ("thinking_delta".equals(deltaType)) {
                        handler.onThinkingDelta(delta.path("thinking").asText(""));
                    } else if ("input_json_delta".equals(deltaType)) {
                        String partial = delta.path("partial_json").asText("");
                        StringBuilder acc = toolInputs.get(idx);
                        if (acc != null) {
                            acc.append(partial);
                        }
                        handler.onToolUseDelta(idx, partial);
                    }
                } else if ("content_block_stop".equals(eventType)) {
                    int idx = node.path("index").asInt();
                    String[] meta = toolBlocks.get(idx);
                    if (meta != null) {
                        String inputJson = toolInputs.containsKey(idx) ? toolInputs.get(idx).toString() : "";
                        handler.onToolUseComplete(idx, meta[0], meta[1], inputJson);
                    }
                } else if ("message_delta".equals(eventType)) {
                    String stopReason = node.path("delta").path("stop_reason").asText(null);
                    long out = node.path("usage").path("output_tokens").asLong(0L);
                    long in = node.path("usage").path("input_tokens").asLong(0L);
                    handler.onStopReason(stopReason, in, out);
                } else if ("message_stop".equals(eventType)) {
                    handler.onComplete();
                    if (completeSink != null) {
                        completeSink.run();
                    }
                }
            }
        };
    }

    /** 工具循环辅助：把 assistant 原始回复 + 每个 tool_use 的结果（经 ToolUtil.invoke）追加回 messages。供统一适配器循环复用。 */
    public static List<AnthropicMessage> appendToolResults(List<AnthropicMessage> messages, AnthropicChatCompletionResponse response) {
        List<AnthropicMessage> updated = new ArrayList<AnthropicMessage>(messages);
        AnthropicMessage assistant = new AnthropicMessage();
        assistant.setRole("assistant");
        assistant.setContent(response.getContent());
        updated.add(assistant);
        if (response.getContent() != null) {
            for (AnthropicContentBlock block : response.getContent()) {
                if (block != null && "tool_use".equals(block.getType())) {
                    String args = writeJson(block.getInput());
                    String result = ToolUtil.invoke(block.getName(), args);
                    AnthropicMessage toolResult = new AnthropicMessage();
                    toolResult.setRole("user");
                    AnthropicContentBlock rb = new AnthropicContentBlock();
                    rb.setType("tool_result");
                    rb.setToolUseId(block.getId());
                    rb.setContent(result);
                    toolResult.setContent(Collections.singletonList(rb));
                    updated.add(toolResult);
                }
            }
        }
        return updated;
    }

    // ---- helpers ----

    private AnthropicApiException decodeError(Response response) {
        int status = response.code();
        String type = null;
        String message = null;
        try {
            if (response.body() != null) {
                String raw = response.body().string();
                JsonNode node = objectMapper.readTree(raw);
                JsonNode err = node.path("error");
                type = err.path("type").asText(null);
                String msg = err.path("message").asText(null);
                message = msg != null ? msg : node.path("message").asText(null);
            }
        } catch (Exception ignored) {
        }
        String requestId = response.header("request-id");
        return AnthropicApiException.of(status, type, message, requestId);
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || baseUrl.isEmpty()) ? anthropicConfig.getApiHost() : baseUrl;
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || apiKey.isEmpty()) ? anthropicConfig.getApiKey() : apiKey;
    }

    private static String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static void safeStart(AnthropicStreamHandler handler, String messageId, String model) {
        try {
            handler.onStart(messageId, model);
        } catch (Throwable t) {
            safeError(handler, t);
        }
    }

    private static void safeError(AnthropicStreamHandler handler, Throwable t) {
        try {
            handler.onError(t);
        } catch (Throwable ignored) {
        }
    }
}
