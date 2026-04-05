package io.github.lnyocly.ai4j.platform.baichuan.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.BaichuanConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionSupport;
import io.github.lnyocly.ai4j.platform.baichuan.chat.entity.BaichuanChatCompletion;
import io.github.lnyocly.ai4j.platform.baichuan.chat.entity.BaichuanChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import io.github.lnyocly.ai4j.network.UrlUtils;
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
import java.util.List;

/**
 * @Author cly
 * @Description 百川chat服务
 * @Date 2024/8/27 17:29
 */
public class BaichuanChatService implements IChatService, ParameterConvert<BaichuanChatCompletion>, ResultConvert<BaichuanChatCompletionResponse> {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final String TOOL_CALLS_FINISH_REASON = "tool_calls";
    private static final String FIRST_FINISH_REASON = "first";

    private final BaichuanConfig baichuanConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;
    private final ObjectMapper objectMapper;

    public BaichuanChatService(Configuration configuration) {
        this.baichuanConfig = configuration.getBaichuanConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
        this.objectMapper = new ObjectMapper();
    }

    public BaichuanChatService(Configuration configuration, BaichuanConfig baichuanConfig) {
        this.baichuanConfig = baichuanConfig;
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        ToolUtil.pushBuiltInToolContext(chatCompletion.getBuiltInToolContext());
        try {
            String resolvedBaseUrl = resolveBaseUrl(baseUrl);
            String resolvedApiKey = resolveApiKey(apiKey);
            boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());
            prepareChatCompletion(chatCompletion, false);

            BaichuanChatCompletion baichuanChatCompletion = convertChatCompletionObject(chatCompletion);
            Usage allUsage = new Usage();
            String finishReason = FIRST_FINISH_REASON;

            while (requiresFollowUp(finishReason)) {
                BaichuanChatCompletionResponse response = executeChatCompletionRequest(
                        resolvedBaseUrl,
                        resolvedApiKey,
                        baichuanChatCompletion
                );
                if (response == null) {
                    break;
                }

                Choice choice = response.getChoices().get(0);
                finishReason = choice.getFinishReason();
                mergeUsage(allUsage, response.getUsage());

                if (TOOL_CALLS_FINISH_REASON.equals(finishReason)) {
                    if (passThroughToolCalls) {
                        response.setUsage(allUsage);
                        response.setObject("chat.completion");
                        restoreOriginalRequest(chatCompletion, baichuanChatCompletion);
                        return this.convertChatCompletionResponse(response);
                    }
                    baichuanChatCompletion.setMessages(appendToolMessages(
                            baichuanChatCompletion.getMessages(),
                            choice.getMessage(),
                            choice.getMessage().getToolCalls()
                    ));
                    continue;
                }

                response.setUsage(allUsage);
                response.setObject("chat.completion");
                restoreOriginalRequest(chatCompletion, baichuanChatCompletion);
                return this.convertChatCompletionResponse(response);
            }

            return null;
        } finally {
            ToolUtil.popBuiltInToolContext();
        }
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) throws Exception {
        return chatCompletion(null, null, chatCompletion);
    }

    @Override
    public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        ToolUtil.pushBuiltInToolContext(chatCompletion.getBuiltInToolContext());
        try {
            String resolvedBaseUrl = resolveBaseUrl(baseUrl);
            String resolvedApiKey = resolveApiKey(apiKey);
            boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());

            prepareChatCompletion(chatCompletion, true);
            BaichuanChatCompletion baichuanChatCompletion = convertChatCompletionObject(chatCompletion);
            String finishReason = FIRST_FINISH_REASON;

            while (requiresFollowUp(finishReason)) {
                Request request = buildChatCompletionRequest(resolvedBaseUrl, resolvedApiKey, baichuanChatCompletion);
                StreamExecutionSupport.execute(
                        eventSourceListener,
                        chatCompletion.getStreamExecution(),
                        () -> factory.newEventSource(request, convertEventSource(eventSourceListener))
                );

                finishReason = eventSourceListener.getFinishReason();
                List<ToolCall> toolCalls = eventSourceListener.getToolCalls();
                if (!TOOL_CALLS_FINISH_REASON.equals(finishReason) || toolCalls.isEmpty()) {
                    continue;
                }
                if (passThroughToolCalls) {
                    return;
                }

                baichuanChatCompletion.setMessages(appendStreamToolMessages(
                        baichuanChatCompletion.getMessages(),
                        toolCalls
                ));
                resetToolCallState(eventSourceListener);
            }

            restoreOriginalRequest(chatCompletion, baichuanChatCompletion);
        } finally {
            ToolUtil.popBuiltInToolContext();
        }
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }

    @Override
    public BaichuanChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        BaichuanChatCompletion baichuanChatCompletion = new BaichuanChatCompletion();
        baichuanChatCompletion.setModel(chatCompletion.getModel());
        baichuanChatCompletion.setMessages(chatCompletion.getMessages());
        baichuanChatCompletion.setStream(chatCompletion.getStream());
        baichuanChatCompletion.setTemperature(chatCompletion.getTemperature() / 2);
        baichuanChatCompletion.setTopP(chatCompletion.getTopP());
        baichuanChatCompletion.setMaxTokens(resolveMaxTokens(chatCompletion));
        baichuanChatCompletion.setStop(chatCompletion.getStop());
        baichuanChatCompletion.setTools(chatCompletion.getTools());
        baichuanChatCompletion.setFunctions(chatCompletion.getFunctions());
        baichuanChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        baichuanChatCompletion.setExtraBody(chatCompletion.getExtraBody());
        return baichuanChatCompletion;
    }

    @Override
    public EventSourceListener convertEventSource(final SseListener eventSourceListener) {
        return new EventSourceListener() {
            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                eventSourceListener.onOpen(eventSource, response);
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                eventSourceListener.onFailure(eventSource, t, response);
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                if ("[DONE]".equalsIgnoreCase(data)) {
                    eventSourceListener.onEvent(eventSource, id, type, data);
                    return;
                }
                eventSourceListener.onEvent(eventSource, id, type, serializeStreamResponse(data));
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                eventSourceListener.onClosed(eventSource);
            }
        };
    }

    @Override
    public ChatCompletionResponse convertChatCompletionResponse(BaichuanChatCompletionResponse baichuanChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(baichuanChatCompletionResponse.getId());
        chatCompletionResponse.setCreated(baichuanChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(baichuanChatCompletionResponse.getModel());
        chatCompletionResponse.setChoices(baichuanChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(baichuanChatCompletionResponse.getUsage());
        return chatCompletionResponse;
    }

    private String serializeStreamResponse(String data) {
        try {
            BaichuanChatCompletionResponse chatCompletionResponse =
                    objectMapper.readValue(data, BaichuanChatCompletionResponse.class);
            chatCompletionResponse.setObject("chat.completion.chunk");
            ChatCompletionResponse response = convertChatCompletionResponse(chatCompletionResponse);
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new CommonException("Baichuan Chat 对象JSON序列化出错");
        }
    }

    private void prepareChatCompletion(ChatCompletion chatCompletion, boolean stream) {
        chatCompletion.setStream(stream);
        if (!stream) {
            chatCompletion.setStreamOptions(null);
        }
        attachTools(chatCompletion);
    }

    private void attachTools(ChatCompletion chatCompletion) {
        if (hasPendingTools(chatCompletion)) {
            List<Tool> tools = ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices());
            chatCompletion.setTools(tools);
            if (tools == null) {
                chatCompletion.setParallelToolCalls(null);
            }
        }
        if (chatCompletion.getTools() == null || chatCompletion.getTools().isEmpty()) {
            chatCompletion.setParallelToolCalls(null);
        }
    }

    private boolean hasPendingTools(ChatCompletion chatCompletion) {
        return (chatCompletion.getFunctions() != null && !chatCompletion.getFunctions().isEmpty())
                || (chatCompletion.getMcpServices() != null && !chatCompletion.getMcpServices().isEmpty());
    }

    private boolean requiresFollowUp(String finishReason) {
        return FIRST_FINISH_REASON.equals(finishReason) || TOOL_CALLS_FINISH_REASON.equals(finishReason);
    }

    private BaichuanChatCompletionResponse executeChatCompletionRequest(
            String baseUrl,
            String apiKey,
            BaichuanChatCompletion baichuanChatCompletion
    ) throws Exception {
        Request request = buildChatCompletionRequest(baseUrl, apiKey, baichuanChatCompletion);
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return objectMapper.readValue(response.body().string(), BaichuanChatCompletionResponse.class);
            }
        }
        return null;
    }

    private Request buildChatCompletionRequest(String baseUrl, String apiKey, BaichuanChatCompletion baichuanChatCompletion)
            throws JsonProcessingException {
        String requestBody = objectMapper.writeValueAsString(baichuanChatCompletion);
        return new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(UrlUtils.concatUrl(baseUrl, baichuanConfig.getChatCompletionUrl()))
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .build();
    }

    private void mergeUsage(Usage target, Usage usage) {
        if (usage == null) {
            return;
        }
        target.setCompletionTokens(target.getCompletionTokens() + usage.getCompletionTokens());
        target.setTotalTokens(target.getTotalTokens() + usage.getTotalTokens());
        target.setPromptTokens(target.getPromptTokens() + usage.getPromptTokens());
    }

    private List<ChatMessage> appendToolMessages(
            List<ChatMessage> messages,
            ChatMessage assistantMessage,
            List<ToolCall> toolCalls
    ) {
        List<ChatMessage> updatedMessages = new ArrayList<ChatMessage>(messages);
        updatedMessages.add(assistantMessage);
        appendToolResponses(updatedMessages, toolCalls);
        return updatedMessages;
    }

    private List<ChatMessage> appendStreamToolMessages(List<ChatMessage> messages, List<ToolCall> toolCalls) {
        List<ChatMessage> updatedMessages = new ArrayList<ChatMessage>(messages);
        updatedMessages.add(ChatMessage.withAssistant(toolCalls));
        appendToolResponses(updatedMessages, toolCalls);
        return updatedMessages;
    }

    private void appendToolResponses(List<ChatMessage> messages, List<ToolCall> toolCalls) {
        for (ToolCall toolCall : toolCalls) {
            String functionName = toolCall.getFunction().getName();
            String arguments = toolCall.getFunction().getArguments();
            String functionResponse = ToolUtil.invoke(functionName, arguments);
            messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
        }
    }

    private void resetToolCallState(SseListener eventSourceListener) {
        eventSourceListener.setToolCalls(new ArrayList<ToolCall>());
        eventSourceListener.setToolCall(null);
    }

    private void restoreOriginalRequest(ChatCompletion chatCompletion, BaichuanChatCompletion baichuanChatCompletion) {
        chatCompletion.setMessages(baichuanChatCompletion.getMessages());
        chatCompletion.setTools(baichuanChatCompletion.getTools());
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || "".equals(baseUrl)) ? baichuanConfig.getApiHost() : baseUrl;
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || "".equals(apiKey)) ? baichuanConfig.getApiKey() : apiKey;
    }

    @SuppressWarnings("deprecation")
    private Integer resolveMaxTokens(ChatCompletion chatCompletion) {
        if (chatCompletion.getMaxCompletionTokens() != null) {
            return chatCompletion.getMaxCompletionTokens();
        }
        return chatCompletion.getMaxTokens();
    }
}

