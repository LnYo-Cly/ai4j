package io.github.lnyocly.ai4j.platform.dashscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.config.DashScopeConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionSupport;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.StreamOptions;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.tool.BuiltInToolContext;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DashScope OpenAI-compatible chat completion service over OkHttp and Jackson.
 */
public class DashScopeChatService implements IChatService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final String DEFAULT_API_HOST = "https://dashscope.aliyuncs.com/compatible-mode/v1/";
    private static final String CHAT_COMPLETION_URL = "chat/completions";
    private static final String FIRST_FINISH_REASON = "first";
    private static final String TOOL_CALLS_FINISH_REASON = "tool_calls";

    private final DashScopeConfig dashScopeConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public DashScopeChatService(Configuration configuration) {
        this(configuration, configuration == null ? null : configuration.getDashScopeConfig());
    }

    public DashScopeChatService(Configuration configuration, DashScopeConfig dashScopeConfig) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        this.dashScopeConfig = dashScopeConfig;
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = configuration.createRequestFactory();
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        if (chatCompletion == null) {
            throw new IllegalArgumentException("chatCompletion must not be null");
        }
        BuiltInToolContext builtInToolContext = chatCompletion.getBuiltInToolContext();
        if (builtInToolContext != null) {
            ToolUtil.pushBuiltInToolContext(builtInToolContext);
        }
        try {
            String resolvedBaseUrl = resolveBaseUrl(baseUrl);
            String resolvedApiKey = resolveApiKey(apiKey);
            boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());

            prepareChatCompletion(chatCompletion, false);

            ObjectMapper mapper = JsonCodec.mapper();
            Usage allUsage = new Usage();
            String finishReason = FIRST_FINISH_REASON;

            while (requiresFollowUp(finishReason)) {
                finishReason = null;
                ChatCompletionResponse response = executeChatCompletion(resolvedBaseUrl, resolvedApiKey, chatCompletion, mapper);
                if (response == null) {
                    return null;
                }

                allUsage.merge(response.getUsage());
                List<Choice> choices = response.getChoices();
                if (choices == null || choices.isEmpty()) {
                    response.setUsage(allUsage);
                    return response;
                }

                Choice choice = choices.get(0);
                finishReason = choice.getFinishReason();
                if (!TOOL_CALLS_FINISH_REASON.equals(finishReason)) {
                    response.setUsage(allUsage);
                    return response;
                }

                ChatMessage assistantMessage = choice.getMessage();
                List<ToolCall> toolCalls = assistantMessage == null ? null : assistantMessage.getToolCalls();
                if (passThroughToolCalls || toolCalls == null || toolCalls.isEmpty()) {
                    response.setUsage(allUsage);
                    return response;
                }

                List<ChatMessage> updatedMessages = copyMessages(chatCompletion.getMessages());
                updatedMessages.add(assistantMessage);
                appendToolResponses(updatedMessages, toolCalls);
                chatCompletion.setMessages(updatedMessages);
            }

            return null;
        } finally {
            if (builtInToolContext != null) {
                ToolUtil.popBuiltInToolContext();
            }
        }
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) throws Exception {
        return chatCompletion(null, null, chatCompletion);
    }

    @Override
    public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        if (chatCompletion == null) {
            throw new IllegalArgumentException("chatCompletion must not be null");
        }
        if (eventSourceListener == null) {
            throw new IllegalArgumentException("eventSourceListener must not be null");
        }
        BuiltInToolContext builtInToolContext = chatCompletion.getBuiltInToolContext();
        if (builtInToolContext != null) {
            ToolUtil.pushBuiltInToolContext(builtInToolContext);
        }
        try {
            String resolvedBaseUrl = resolveBaseUrl(baseUrl);
            String resolvedApiKey = resolveApiKey(apiKey);
            boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());

            prepareChatCompletion(chatCompletion, true);

            ObjectMapper mapper = JsonCodec.mapper();
            String finishReason = FIRST_FINISH_REASON;

            while (requiresFollowUp(finishReason)) {
                finishReason = null;
                eventSourceListener.setFinishReason(null);

                Request request = buildChatCompletionRequest(resolvedBaseUrl, resolvedApiKey, chatCompletion, mapper);
                StreamExecutionSupport.execute(
                        eventSourceListener,
                        chatCompletion.getStreamExecution(),
                        () -> factory.newEventSource(request, eventSourceListener)
                );

                finishReason = eventSourceListener.getFinishReason();
                if (!TOOL_CALLS_FINISH_REASON.equals(finishReason)) {
                    break;
                }

                List<ToolCall> toolCalls = eventSourceListener.getToolCalls();
                if (passThroughToolCalls || toolCalls == null || toolCalls.isEmpty()) {
                    return;
                }

                List<ChatMessage> updatedMessages = copyMessages(chatCompletion.getMessages());
                updatedMessages.add(ChatMessage.withAssistant(new ArrayList<ToolCall>(toolCalls)));
                appendToolResponses(updatedMessages, toolCalls);
                eventSourceListener.setToolCalls(new ArrayList<ToolCall>());
                eventSourceListener.setToolCall(null);
                chatCompletion.setMessages(updatedMessages);
            }
        } finally {
            if (builtInToolContext != null) {
                ToolUtil.popBuiltInToolContext();
            }
        }
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }

    private ChatCompletionResponse executeChatCompletion(String baseUrl,
                                                         String apiKey,
                                                         ChatCompletion chatCompletion,
                                                         ObjectMapper mapper) throws Exception {
        Request request = buildChatCompletionRequest(baseUrl, apiKey, chatCompletion, mapper);
        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (response.isSuccessful() && body != null) {
                return mapper.readValue(body.string(), ChatCompletionResponse.class);
            }
            throw HttpErrorDecoder.decode(response);
        }
    }

    private Request buildChatCompletionRequest(String baseUrl,
                                               String apiKey,
                                               ChatCompletion chatCompletion,
                                               ObjectMapper mapper) throws JsonProcessingException {
        String requestBody = serializeRequest(chatCompletion, mapper);
        return new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(UrlUtils.concatUrl(baseUrl, CHAT_COMPLETION_URL))
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .build();
    }

    private String serializeRequest(ChatCompletion chatCompletion, ObjectMapper mapper) throws JsonProcessingException {
        JsonNode tree = mapper.valueToTree(chatCompletion);
        if (!(tree instanceof ObjectNode)) {
            return mapper.writeValueAsString(chatCompletion);
        }
        ObjectNode root = (ObjectNode) tree;
        root.remove("parameters");
        Map<String, Object> parameters = chatCompletion.getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String key = entry.getKey();
                if (!isBlank(key) && !root.has(key) && entry.getValue() != null) {
                    root.set(key, mapper.valueToTree(entry.getValue()));
                }
            }
        }
        if (!root.has("max_tokens") && root.has("max_completion_tokens")) {
            root.set("max_tokens", root.get("max_completion_tokens"));
            root.remove("max_completion_tokens");
        }
        return mapper.writeValueAsString(root);
    }

    private void prepareChatCompletion(ChatCompletion chatCompletion, boolean stream) {
        chatCompletion.setStream(stream);
        if (stream) {
            if (chatCompletion.getStreamOptions() == null) {
                chatCompletion.setStreamOptions(new StreamOptions(true));
            }
        } else {
            chatCompletion.setStreamOptions(null);
        }
        attachTools(chatCompletion);
    }

    private void attachTools(ChatCompletion chatCompletion) {
        if (hasPendingTools(chatCompletion)) {
            List<Tool> tools = ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices());
            chatCompletion.setTools(tools == null || tools.isEmpty() ? null : tools);
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

    private List<ChatMessage> copyMessages(List<ChatMessage> messages) {
        return messages == null ? new ArrayList<ChatMessage>() : new ArrayList<ChatMessage>(messages);
    }

    private void appendToolResponses(List<ChatMessage> messages, List<ToolCall> toolCalls) {
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null || toolCall.getFunction() == null) {
                continue;
            }
            String functionName = toolCall.getFunction().getName();
            String arguments = toolCall.getFunction().getArguments();
            String functionResponse = ToolUtil.invoke(functionName, arguments);
            messages.add(ChatMessage.withTool(functionResponse, toolCall.getId()));
        }
    }

    private String resolveBaseUrl(String baseUrl) {
        if (!isBlank(baseUrl)) {
            return baseUrl;
        }
        if (dashScopeConfig != null && !isBlank(dashScopeConfig.getApiHost())) {
            return dashScopeConfig.getApiHost();
        }
        return DEFAULT_API_HOST;
    }

    private String resolveApiKey(String apiKey) {
        if (!isBlank(apiKey)) {
            return apiKey;
        }
        if (dashScopeConfig != null && dashScopeConfig.getApiKey() != null) {
            return dashScopeConfig.getApiKey();
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
