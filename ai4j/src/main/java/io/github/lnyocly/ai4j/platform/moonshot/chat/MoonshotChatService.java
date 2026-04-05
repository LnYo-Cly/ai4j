package io.github.lnyocly.ai4j.platform.moonshot.chat;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.MoonshotConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionSupport;
import io.github.lnyocly.ai4j.platform.moonshot.chat.entity.MoonshotChatCompletion;
import io.github.lnyocly.ai4j.platform.moonshot.chat.entity.MoonshotChatCompletionResponse;
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
 * @Description 月之暗面请求服务
 * @Date 2024/8/29 23:12
 */
public class MoonshotChatService implements IChatService, ParameterConvert<MoonshotChatCompletion>, ResultConvert<MoonshotChatCompletionResponse> {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final String TOOL_CALLS_FINISH_REASON = "tool_calls";
    private static final String FIRST_FINISH_REASON = "first";

    private final MoonshotConfig moonshotConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;
    private final ObjectMapper objectMapper;

    public MoonshotChatService(Configuration configuration) {
        this.moonshotConfig = configuration.getMoonshotConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
        this.objectMapper = new ObjectMapper();
    }

    public MoonshotChatService(Configuration configuration, MoonshotConfig moonshotConfig) {
        this.moonshotConfig = moonshotConfig;
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public MoonshotChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        MoonshotChatCompletion moonshotChatCompletion = new MoonshotChatCompletion();
        moonshotChatCompletion.setModel(chatCompletion.getModel());
        moonshotChatCompletion.setMessages(chatCompletion.getMessages());
        moonshotChatCompletion.setFrequencyPenalty(chatCompletion.getFrequencyPenalty());
        moonshotChatCompletion.setMaxTokens(resolveMaxTokens(chatCompletion));
        moonshotChatCompletion.setPresencePenalty(chatCompletion.getPresencePenalty());
        moonshotChatCompletion.setResponseFormat(chatCompletion.getResponseFormat());
        moonshotChatCompletion.setStop(chatCompletion.getStop());
        moonshotChatCompletion.setStream(chatCompletion.getStream());
        moonshotChatCompletion.setTemperature(chatCompletion.getTemperature() / 2);
        moonshotChatCompletion.setTopP(chatCompletion.getTopP());
        moonshotChatCompletion.setTools(chatCompletion.getTools());
        moonshotChatCompletion.setFunctions(chatCompletion.getFunctions());
        moonshotChatCompletion.setToolChoice(chatCompletion.getToolChoice());
        moonshotChatCompletion.setExtraBody(chatCompletion.getExtraBody());
        return moonshotChatCompletion;
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
    public ChatCompletionResponse convertChatCompletionResponse(MoonshotChatCompletionResponse moonshotChatCompletionResponse) {
        ChatCompletionResponse chatCompletionResponse = new ChatCompletionResponse();
        chatCompletionResponse.setId(moonshotChatCompletionResponse.getId());
        chatCompletionResponse.setObject(moonshotChatCompletionResponse.getObject());
        chatCompletionResponse.setCreated(moonshotChatCompletionResponse.getCreated());
        chatCompletionResponse.setModel(moonshotChatCompletionResponse.getModel());
        chatCompletionResponse.setChoices(moonshotChatCompletionResponse.getChoices());
        chatCompletionResponse.setUsage(moonshotChatCompletionResponse.getUsage());
        return chatCompletionResponse;
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        String resolvedBaseUrl = resolveBaseUrl(baseUrl);
        String resolvedApiKey = resolveApiKey(apiKey);
        boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());

        prepareChatCompletion(chatCompletion, false);
        MoonshotChatCompletion moonshotChatCompletion = convertChatCompletionObject(chatCompletion);
        Usage allUsage = new Usage();
        String finishReason = FIRST_FINISH_REASON;

        while (requiresFollowUp(finishReason)) {
            MoonshotChatCompletionResponse response = executeChatCompletionRequest(
                    resolvedBaseUrl,
                    resolvedApiKey,
                    moonshotChatCompletion
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
                    restoreOriginalRequest(chatCompletion, moonshotChatCompletion);
                    return convertChatCompletionResponse(response);
                }
                moonshotChatCompletion.setMessages(appendToolMessages(
                        moonshotChatCompletion.getMessages(),
                        choice.getMessage(),
                        choice.getMessage().getToolCalls()
                ));
                continue;
            }

            response.setUsage(allUsage);
            restoreOriginalRequest(chatCompletion, moonshotChatCompletion);
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
        MoonshotChatCompletion moonshotChatCompletion = convertChatCompletionObject(chatCompletion);
        String finishReason = FIRST_FINISH_REASON;

        while (requiresFollowUp(finishReason)) {
            Request request = buildChatCompletionRequest(resolvedBaseUrl, resolvedApiKey, moonshotChatCompletion);
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

            moonshotChatCompletion.setMessages(appendStreamToolMessages(
                    moonshotChatCompletion.getMessages(),
                    toolCalls
            ));
            resetToolCallState(eventSourceListener);
        }

        restoreOriginalRequest(chatCompletion, moonshotChatCompletion);
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }

    private String serializeStreamResponse(String data) {
        try {
            MoonshotChatCompletionResponse chatCompletionResponse =
                    objectMapper.readValue(data, MoonshotChatCompletionResponse.class);
            ChatCompletionResponse response = convertChatCompletionResponse(chatCompletionResponse);
            Usage usage = extractStreamUsage(data);
            if (usage != null) {
                response.setUsage(usage);
            }
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new CommonException("Moonshot Chat 对象JSON序列化出错");
        }
    }

    private Usage extractStreamUsage(String data) {
        JSONObject object = (JSONObject) JSONPath.eval(data, "$.choices[0].usage");
        if (object == null) {
            return null;
        }
        return object.toJavaObject(Usage.class);
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

    private MoonshotChatCompletionResponse executeChatCompletionRequest(
            String baseUrl,
            String apiKey,
            MoonshotChatCompletion moonshotChatCompletion
    ) throws Exception {
        Request request = buildChatCompletionRequest(baseUrl, apiKey, moonshotChatCompletion);
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return objectMapper.readValue(response.body().string(), MoonshotChatCompletionResponse.class);
            }
        }
        return null;
    }

    private Request buildChatCompletionRequest(String baseUrl, String apiKey, MoonshotChatCompletion moonshotChatCompletion)
            throws JsonProcessingException {
        String requestBody = objectMapper.writeValueAsString(moonshotChatCompletion);
        return new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(UrlUtils.concatUrl(baseUrl, moonshotConfig.getChatCompletionUrl()))
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

    private void restoreOriginalRequest(ChatCompletion chatCompletion, MoonshotChatCompletion moonshotChatCompletion) {
        chatCompletion.setMessages(moonshotChatCompletion.getMessages());
        chatCompletion.setTools(moonshotChatCompletion.getTools());
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || "".equals(baseUrl)) ? moonshotConfig.getApiHost() : baseUrl;
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || "".equals(apiKey)) ? moonshotConfig.getApiKey() : apiKey;
    }

    @SuppressWarnings("deprecation")
    private Integer resolveMaxTokens(ChatCompletion chatCompletion) {
        if (chatCompletion.getMaxCompletionTokens() != null) {
            return chatCompletion.getMaxCompletionTokens();
        }
        return chatCompletion.getMaxTokens();
    }
}

