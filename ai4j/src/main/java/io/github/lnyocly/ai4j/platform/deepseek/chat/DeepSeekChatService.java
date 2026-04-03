package io.github.lnyocly.ai4j.platform.deepseek.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.DeepSeekConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionSupport;
import io.github.lnyocly.ai4j.platform.deepseek.chat.entity.DeepSeekChatCompletion;
import io.github.lnyocly.ai4j.platform.deepseek.chat.entity.DeepSeekChatCompletionResponse;
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
 * @Description DeepSeek Chat服务
 * @Date 2024/8/29 10:26
 */
public class DeepSeekChatService implements IChatService, ParameterConvert<DeepSeekChatCompletion>, ResultConvert<DeepSeekChatCompletionResponse> {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final String TOOL_CALLS_FINISH_REASON = "tool_calls";
    private static final String FIRST_FINISH_REASON = "first";

    private final DeepSeekConfig deepSeekConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;
    private final ObjectMapper objectMapper;

    public DeepSeekChatService(Configuration configuration) {
        this.deepSeekConfig = configuration.getDeepSeekConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
        this.objectMapper = new ObjectMapper();
    }

    public DeepSeekChatService(Configuration configuration, DeepSeekConfig deepSeekConfig) {
        this.deepSeekConfig = deepSeekConfig;
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public DeepSeekChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        DeepSeekChatCompletion deepSeekChatCompletion = new DeepSeekChatCompletion();
        deepSeekChatCompletion.setModel(chatCompletion.getModel());
        deepSeekChatCompletion.setMessages(chatCompletion.getMessages());
        deepSeekChatCompletion.setFrequencyPenalty(chatCompletion.getFrequencyPenalty());
        deepSeekChatCompletion.setMaxTokens(resolveMaxTokens(chatCompletion));
        deepSeekChatCompletion.setPresencePenalty(chatCompletion.getPresencePenalty());
        deepSeekChatCompletion.setResponseFormat(chatCompletion.getResponseFormat());
        deepSeekChatCompletion.setStop(chatCompletion.getStop());
        deepSeekChatCompletion.setStream(chatCompletion.getStream());
        deepSeekChatCompletion.setStreamOptions(chatCompletion.getStreamOptions());
        deepSeekChatCompletion.setTemperature(chatCompletion.getTemperature());
        deepSeekChatCompletion.setTopP(chatCompletion.getTopP());
        deepSeekChatCompletion.setTools(chatCompletion.getTools());
        deepSeekChatCompletion.setFunctions(chatCompletion.getFunctions());
        deepSeekChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        deepSeekChatCompletion.setLogprobs(chatCompletion.getLogprobs());
        deepSeekChatCompletion.setTopLogprobs(chatCompletion.getTopLogprobs());
        deepSeekChatCompletion.setExtraBody(chatCompletion.getExtraBody());
        return deepSeekChatCompletion;
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
    public ChatCompletionResponse convertChatCompletionResponse(DeepSeekChatCompletionResponse deepSeekChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(deepSeekChatCompletionResponse.getId());
        chatCompletionResponse.setObject(deepSeekChatCompletionResponse.getObject());
        chatCompletionResponse.setCreated(deepSeekChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(deepSeekChatCompletionResponse.getModel());
        chatCompletionResponse.setSystemFingerprint(deepSeekChatCompletionResponse.getSystemFingerprint());
        chatCompletionResponse.setChoices(deepSeekChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(deepSeekChatCompletionResponse.getUsage());
        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        String resolvedBaseUrl = resolveBaseUrl(baseUrl);
        String resolvedApiKey = resolveApiKey(apiKey);
        boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());
        prepareChatCompletion(chatCompletion, false);

        DeepSeekChatCompletion deepSeekChatCompletion = convertChatCompletionObject(chatCompletion);
        Usage allUsage = new Usage();
        String finishReason = FIRST_FINISH_REASON;

        while (requiresFollowUp(finishReason)) {
            DeepSeekChatCompletionResponse response = executeChatCompletionRequest(
                    resolvedBaseUrl,
                    resolvedApiKey,
                    deepSeekChatCompletion
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
                    restoreOriginalRequest(chatCompletion, deepSeekChatCompletion);
                    return convertChatCompletionResponse(response);
                }
                deepSeekChatCompletion.setMessages(appendToolMessages(
                        deepSeekChatCompletion.getMessages(),
                        choice.getMessage(),
                        choice.getMessage().getToolCalls()
                ));
                continue;
            }

            response.setUsage(allUsage);
            restoreOriginalRequest(chatCompletion, deepSeekChatCompletion);
            return convertChatCompletionResponse(response);
        }

        return null;
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) throws Exception {
        return this.chatCompletion(null, null, chatCompletion);
    }

    @Override
    public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        String resolvedBaseUrl = resolveBaseUrl(baseUrl);
        String resolvedApiKey = resolveApiKey(apiKey);
        boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());

        prepareChatCompletion(chatCompletion, true);
        DeepSeekChatCompletion deepSeekChatCompletion = convertChatCompletionObject(chatCompletion);
        String finishReason = FIRST_FINISH_REASON;

        while (requiresFollowUp(finishReason)) {
            Request request = buildChatCompletionRequest(resolvedBaseUrl, resolvedApiKey, deepSeekChatCompletion);
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

            deepSeekChatCompletion.setMessages(appendStreamToolMessages(
                    deepSeekChatCompletion.getMessages(),
                    toolCalls
            ));
            resetToolCallState(eventSourceListener);
        }

        restoreOriginalRequest(chatCompletion, deepSeekChatCompletion);
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }

    private String serializeStreamResponse(String data) {
        try {
            DeepSeekChatCompletionResponse chatCompletionResponse =
                    objectMapper.readValue(data, DeepSeekChatCompletionResponse.class);
            ChatCompletionResponse response = convertChatCompletionResponse(chatCompletionResponse);
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new CommonException("读取DeepSeek Chat 对象JSON序列化出错");
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

    private DeepSeekChatCompletionResponse executeChatCompletionRequest(
            String baseUrl,
            String apiKey,
            DeepSeekChatCompletion deepSeekChatCompletion
    ) throws Exception {
        Request request = buildChatCompletionRequest(baseUrl, apiKey, deepSeekChatCompletion);
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return objectMapper.readValue(response.body().string(), DeepSeekChatCompletionResponse.class);
            }
        }
        return null;
    }

    private Request buildChatCompletionRequest(String baseUrl, String apiKey, DeepSeekChatCompletion deepSeekChatCompletion)
            throws JsonProcessingException {
        String requestBody = objectMapper.writeValueAsString(deepSeekChatCompletion);
        return new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(UrlUtils.concatUrl(baseUrl, deepSeekConfig.getChatCompletionUrl()))
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

    private void restoreOriginalRequest(ChatCompletion chatCompletion, DeepSeekChatCompletion deepSeekChatCompletion) {
        chatCompletion.setMessages(deepSeekChatCompletion.getMessages());
        chatCompletion.setTools(deepSeekChatCompletion.getTools());
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || "".equals(baseUrl)) ? deepSeekConfig.getApiHost() : baseUrl;
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || "".equals(apiKey)) ? deepSeekConfig.getApiKey() : apiKey;
    }

    @SuppressWarnings("deprecation")
    private Integer resolveMaxTokens(ChatCompletion chatCompletion) {
        if (chatCompletion.getMaxCompletionTokens() != null) {
            return chatCompletion.getMaxCompletionTokens();
        }
        return chatCompletion.getMaxTokens();
    }
}

