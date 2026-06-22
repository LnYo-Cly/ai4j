package io.github.lnyocly.ai4j.platform.anthropic.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionSupport;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicTool;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicUsage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Content;
import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages（{@code /v1/messages}）chat 服务。
 * <p>
 * 与现有 OpenAI 兼容 provider 同构：实现 {@link IChatService}，内部用 OkHttp + Jackson，
 * 把统一 OpenAI 格式的 {@link ChatCompletion} 转成 Anthropic Messages 请求、把响应/流式事件转回统一格式。
 * <p>
 * 鉴权使用 {@code x-api-key} + {@code anthropic-version} 头（Anthropic 线协议标准）。
 * 通过覆盖 {@link AnthropicConfig#getApiHost()} 即可对接合作厂家的 Anthropic 兼容入口
 * （如智谱 Coding Plan {@code open.bigmodel.cn/api/anthropic}）。
 */
@Slf4j
public class AnthropicChatService implements IChatService,
        ParameterConvert<AnthropicChatCompletion>,
        ResultConvert<AnthropicChatCompletionResponse> {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final String TOOL_CALLS_FINISH_REASON = "tool_calls";
    private static final String FIRST_FINISH_REASON = "first";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final AnthropicConfig anthropicConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;
    private final ObjectMapper objectMapper;

    public AnthropicChatService(Configuration configuration) {
        this.anthropicConfig = configuration.getAnthropicConfig();
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
            AnthropicChatCompletion request = convertChatCompletionObject(chatCompletion);
            Usage allUsage = new Usage();
            String finishReason = FIRST_FINISH_REASON;

            while (requiresFollowUp(finishReason)) {
                AnthropicChatCompletionResponse response = executeChatCompletionRequest(resolvedBaseUrl, resolvedApiKey, request);
                if (response == null) {
                    break;
                }
                mergeUsage(allUsage, response.getUsage());

                finishReason = mapStopReason(response.getStopReason());
                if (TOOL_CALLS_FINISH_REASON.equals(finishReason)) {
                    if (passThroughToolCalls) {
                        return toChatCompletionResponse(response, allUsage);
                    }
                    request.setMessages(appendToolMessages(request.safeMessages(), response));
                    continue;
                }

                return toChatCompletionResponse(response, allUsage);
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
            AnthropicChatCompletion request = convertChatCompletionObject(chatCompletion);
            String finishReason = FIRST_FINISH_REASON;

            while (requiresFollowUp(finishReason)) {
                Request httpRequest = buildChatCompletionRequest(resolvedBaseUrl, resolvedApiKey, request);
                StreamExecutionSupport.execute(
                        eventSourceListener,
                        chatCompletion.getStreamExecution(),
                        () -> factory.newEventSource(httpRequest, convertEventSource(eventSourceListener))
                );

                finishReason = eventSourceListener.getFinishReason();
                List<ToolCall> toolCalls = eventSourceListener.getToolCalls();
                if (!TOOL_CALLS_FINISH_REASON.equals(finishReason) || toolCalls.isEmpty()) {
                    continue;
                }
                if (passThroughToolCalls) {
                    return;
                }

                request.setMessages(appendStreamToolMessages(request.safeMessages(), toolCalls));
                resetToolCallState(eventSourceListener);
            }
        } finally {
            ToolUtil.popBuiltInToolContext();
        }
    }

    @Override
    public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
        this.chatCompletionStream(null, null, chatCompletion, eventSourceListener);
    }

    @Override
    public AnthropicChatCompletion convertChatCompletionObject(ChatCompletion chatCompletion) {
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel(chatCompletion.getModel());
        request.setStream(chatCompletion.getStream());

        List<AnthropicMessage> anthropicMessages = new ArrayList<AnthropicMessage>();
        StringBuilder systemBuilder = new StringBuilder();
        if (chatCompletion.getMessages() != null) {
            for (ChatMessage message : chatCompletion.getMessages()) {
                String role = message.getRole();
                if (ChatMessageType.SYSTEM.getRole().equals(role)) {
                    if (systemBuilder.length() > 0) {
                        systemBuilder.append("\n\n");
                    }
                    systemBuilder.append(textOf(message));
                    continue;
                }
                anthropicMessages.add(toAnthropicMessage(message));
            }
        }
        if (systemBuilder.length() > 0) {
            request.setSystem(systemBuilder.toString());
        }
        request.setMessages(anthropicMessages);

        request.setMaxTokens(resolveMaxTokens(chatCompletion));
        if (chatCompletion.getTemperature() != null) {
            request.setTemperature(chatCompletion.getTemperature().doubleValue());
        }
        if (chatCompletion.getTopP() != null) {
            request.setTopP(chatCompletion.getTopP().doubleValue());
        }
        request.setStopSequences(chatCompletion.getStop());
        request.setTools(convertTools(chatCompletion.getTools()));
        if (chatCompletion.getToolChoice() != null) {
            request.setToolChoice(chatCompletion.getToolChoice());
        }
        request.setExtraBody(chatCompletion.getExtraBody());
        return request;
    }

    @Override
    public ChatCompletionResponse convertChatCompletionResponse(AnthropicChatCompletionResponse response) {
        return toChatCompletionResponse(response, null);
    }

    @Override
    public EventSourceListener convertEventSource(final SseListener eventSourceListener) {
        return new EventSourceListener() {
            private final Map<Integer, String[]> toolBlocks = new HashMap<Integer, String[]>();
            private final Map<Integer, StringBuilder> toolInputs = new HashMap<Integer, StringBuilder>();
            private String messageId;
            private String modelName;
            private EventSource currentEventSource;

            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                currentEventSource = eventSource;
                eventSourceListener.onOpen(eventSource, response);
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                eventSourceListener.onClosed(eventSource);
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                eventSourceListener.onFailure(eventSource, t, response);
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                currentEventSource = eventSource;
                if ("[DONE]".equalsIgnoreCase(data)) {
                    eventSourceListener.onEvent(eventSource, id, type, data);
                    return;
                }
                JsonNode node;
                try {
                    node = objectMapper.readTree(data);
                } catch (JsonProcessingException e) {
                    throw new CommonException("Anthropic stream JSON parse error");
                }
                String eventType = node.path("type").asText();
                handle(eventType, node, id, type);
            }

            private void handle(String eventType, JsonNode node, String id, String type) {
                if ("message_start".equals(eventType)) {
                    JsonNode message = node.path("message");
                    messageId = message.path("id").asText(null);
                    modelName = message.path("model").asText(null);
                    return;
                }
                if ("content_block_start".equals(eventType)) {
                    int index = node.path("index").asInt();
                    JsonNode block = node.path("content_block");
                    String blockType = block.path("type").asText();
                    if ("tool_use".equals(blockType)) {
                        toolBlocks.put(index, new String[]{block.path("id").asText(null), block.path("name").asText(null)});
                        toolInputs.put(index, new StringBuilder());
                    }
                    return;
                }
                if ("content_block_delta".equals(eventType)) {
                    int index = node.path("index").asInt();
                    JsonNode delta = node.path("delta");
                    String deltaType = delta.path("type").asText();
                    if ("text_delta".equals(deltaType)) {
                        String text = delta.path("text").asText();
                        emit(textChunk(text), id, type);
                    } else if ("input_json_delta".equals(deltaType)) {
                        StringBuilder acc = toolInputs.get(index);
                        if (acc != null) {
                            acc.append(delta.path("partial_json").asText(""));
                        }
                    }
                    return;
                }
                if ("content_block_stop".equals(eventType)) {
                    int index = node.path("index").asInt();
                    String[] meta = toolBlocks.get(index);
                    if (meta != null) {
                        String arguments = toolInputs.containsKey(index) ? toolInputs.get(index).toString() : "";
                        emit(toolCallChunk(meta[0], meta[1], arguments), id, type);
                    }
                    return;
                }
                if ("message_delta".equals(eventType)) {
                    JsonNode delta = node.path("delta");
                    String stopReason = delta.path("stop_reason").asText(null);
                    String finishReason = mapStopReason(stopReason);
                    long outputTokens = node.path("usage").path("output_tokens").asLong(0L);
                    emit(finishChunk(finishReason, outputTokens), id, type);
                    return;
                }
                if ("message_stop".equals(eventType)) {
                    eventSourceListener.onEvent(currentEventSource, id, type, "[DONE]");
                    return;
                }
                // ping / error / 其它：忽略
            }

            private void emit(String json, String id, String type) {
                eventSourceListener.onEvent(currentEventSource, id, type, json);
            }

            private String textChunk(String text) {
                ChatCompletionResponse chunk = baseChunk();
                ChatMessage delta = ChatMessage.builder().role(ChatMessageType.ASSISTANT.getRole()).content(Content.ofText(text)).build();
                chunk.setChoices(singleChoice(delta, null));
                return writeChunk(chunk);
            }

            private String toolCallChunk(String toolId, String name, String arguments) {
                ChatCompletionResponse chunk = baseChunk();
                ToolCall toolCall = new ToolCall(toolId, "function", new ToolCall.Function(name, arguments));
                ChatMessage delta = ChatMessage.builder()
                        .role(ChatMessageType.ASSISTANT.getRole())
                        .content(Content.ofText(""))
                        .toolCalls(java.util.Collections.singletonList(toolCall))
                        .build();
                chunk.setChoices(singleChoice(delta, null));
                return writeChunk(chunk);
            }

            private String finishChunk(String finishReason, long outputTokens) {
                ChatCompletionResponse chunk = baseChunk();
                ChatMessage delta = ChatMessage.builder().role(ChatMessageType.ASSISTANT.getRole()).content(Content.ofText("")).build();
                Usage usage = new Usage(0L, outputTokens, outputTokens);
                chunk.setChoices(singleChoice(delta, finishReason));
                chunk.setUsage(usage);
                return writeChunk(chunk);
            }

            private ChatCompletionResponse baseChunk() {
                ChatCompletionResponse chunk = new ChatCompletionResponse();
                chunk.setId(messageId);
                chunk.setObject("chat.completion.chunk");
                chunk.setCreated(System.currentTimeMillis() / 1000L);
                chunk.setModel(modelName);
                return chunk;
            }

            private List<Choice> singleChoice(ChatMessage delta, String finishReason) {
                Choice choice = new Choice();
                choice.setIndex(0);
                choice.setDelta(delta);
                choice.setFinishReason(finishReason);
                return java.util.Collections.singletonList(choice);
            }

            private String writeChunk(ChatCompletionResponse chunk) {
                try {
                    return objectMapper.writeValueAsString(chunk);
                } catch (JsonProcessingException e) {
                    throw new CommonException("Anthropic stream chunk serialize error");
                }
            }
        };
    }

    // ---- mapping helpers ----

    private ChatCompletionResponse toChatCompletionResponse(AnthropicChatCompletionResponse response, Usage mergedUsage) {
        ChatCompletionResponse result = new ChatCompletionResponse();
        result.setId(response.getId());
        result.setObject("chat.completion");
        result.setCreated(System.currentTimeMillis() / 1000L);
        result.setModel(response.getModel());

        ChatMessage message = ChatMessage.builder().role(ChatMessageType.ASSISTANT.getRole()).build();
        StringBuilder textBuilder = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<ToolCall>();
        if (response.getContent() != null) {
            for (AnthropicContentBlock block : response.getContent()) {
                if (block == null) {
                    continue;
                }
                if ("text".equals(block.getType()) && block.getText() != null) {
                    textBuilder.append(block.getText());
                } else if ("tool_use".equals(block.getType())) {
                    toolCalls.add(new ToolCall(block.getId(), "function",
                            new ToolCall.Function(block.getName(), writeJson(block.getInput()))));
                }
            }
        }
        message.setContent(Content.ofText(textBuilder.toString()));
        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls);
        }

        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason(mapStopReason(response.getStopReason()));

        result.setChoices(java.util.Collections.singletonList(choice));
        result.setUsage(mergedUsage != null ? mergedUsage : toUsage(response.getUsage()));
        return result;
    }

    private AnthropicMessage toAnthropicMessage(ChatMessage message) {
        AnthropicMessage anthropicMessage = new AnthropicMessage();
        String role = message.getRole();
        if (ChatMessageType.TOOL.getRole().equals(role)) {
            // OpenAI tool 结果 → Anthropic user/tool_result
            anthropicMessage.setRole(ChatMessageType.USER.getRole());
            AnthropicContentBlock resultBlock = new AnthropicContentBlock();
            resultBlock.setType("tool_result");
            resultBlock.setToolUseId(message.getToolCallId());
            resultBlock.setContent(textOf(message));
            anthropicMessage.setContent(java.util.Collections.singletonList(resultBlock));
            return anthropicMessage;
        }
        anthropicMessage.setRole(role);
        List<ToolCall> toolCalls = message.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<AnthropicContentBlock> blocks = new ArrayList<AnthropicContentBlock>();
            String text = textOf(message);
            if (text != null && !text.isEmpty()) {
                AnthropicContentBlock textBlock = new AnthropicContentBlock();
                textBlock.setType("text");
                textBlock.setText(text);
                blocks.add(textBlock);
            }
            for (ToolCall toolCall : toolCalls) {
                AnthropicContentBlock useBlock = new AnthropicContentBlock();
                useBlock.setType("tool_use");
                useBlock.setId(toolCall.getId());
                useBlock.setName(toolCall.getFunction() == null ? null : toolCall.getFunction().getName());
                useBlock.setInput(parseJsonObject(toolCall.getFunction() == null ? null : toolCall.getFunction().getArguments()));
                blocks.add(useBlock);
            }
            anthropicMessage.setContent(blocks);
        } else {
            anthropicMessage.setContent(textOf(message));
        }
        return anthropicMessage;
    }

    private List<AnthropicMessage> appendToolMessages(List<AnthropicMessage> messages, AnthropicChatCompletionResponse response) {
        List<AnthropicMessage> updated = new ArrayList<AnthropicMessage>(messages);
        // 先把 assistant 的原始回复（含 tool_use 块）追加进去
        AnthropicMessage assistantMessage = new AnthropicMessage();
        assistantMessage.setRole(ChatMessageType.ASSISTANT.getRole());
        assistantMessage.setContent(response.getContent());
        updated.add(assistantMessage);
        // 再把每个 tool_use 的结果作为 tool_result 回传
        if (response.getContent() != null) {
            for (AnthropicContentBlock block : response.getContent()) {
                if (block != null && "tool_use".equals(block.getType())) {
                    String toolName = block.getName();
                    String arguments = writeJson(block.getInput());
                    String toolResult = ToolUtil.invoke(toolName, arguments);
                    AnthropicMessage resultMessage = new AnthropicMessage();
                    resultMessage.setRole(ChatMessageType.USER.getRole());
                    AnthropicContentBlock resultBlock = new AnthropicContentBlock();
                    resultBlock.setType("tool_result");
                    resultBlock.setToolUseId(block.getId());
                    resultBlock.setContent(toolResult);
                    resultMessage.setContent(java.util.Collections.singletonList(resultBlock));
                    updated.add(resultMessage);
                }
            }
        }
        return updated;
    }

    private List<AnthropicMessage> appendStreamToolMessages(List<AnthropicMessage> messages, List<ToolCall> toolCalls) {
        List<AnthropicMessage> updated = new ArrayList<AnthropicMessage>(messages);
        // assistant 的 tool_use 块
        AnthropicMessage assistantMessage = new AnthropicMessage();
        assistantMessage.setRole(ChatMessageType.ASSISTANT.getRole());
        List<AnthropicContentBlock> blocks = new ArrayList<AnthropicContentBlock>();
        for (ToolCall toolCall : toolCalls) {
            AnthropicContentBlock useBlock = new AnthropicContentBlock();
            useBlock.setType("tool_use");
            useBlock.setId(toolCall.getId());
            useBlock.setName(toolCall.getFunction() == null ? null : toolCall.getFunction().getName());
            useBlock.setInput(parseJsonObject(toolCall.getFunction() == null ? null : toolCall.getFunction().getArguments()));
            blocks.add(useBlock);
        }
        assistantMessage.setContent(blocks);
        updated.add(assistantMessage);
        // tool_result 回传
        for (ToolCall toolCall : toolCalls) {
            String functionName = toolCall.getFunction() == null ? null : toolCall.getFunction().getName();
            String arguments = toolCall.getFunction() == null ? null : toolCall.getFunction().getArguments();
            String toolResult = ToolUtil.invoke(functionName, arguments);
            AnthropicMessage resultMessage = new AnthropicMessage();
            resultMessage.setRole(ChatMessageType.USER.getRole());
            AnthropicContentBlock resultBlock = new AnthropicContentBlock();
            resultBlock.setType("tool_result");
            resultBlock.setToolUseId(toolCall.getId());
            resultBlock.setContent(toolResult);
            resultMessage.setContent(java.util.Collections.singletonList(resultBlock));
            updated.add(resultMessage);
        }
        return updated;
    }

    private List<AnthropicTool> convertTools(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<AnthropicTool> anthropicTools = new ArrayList<AnthropicTool>();
        for (Tool tool : tools) {
            if (tool == null || tool.getFunction() == null) {
                continue;
            }
            Tool.Function function = tool.getFunction();
            AnthropicTool anthropicTool = new AnthropicTool();
            anthropicTool.setName(function.getName());
            anthropicTool.setDescription(function.getDescription());
            anthropicTool.setInputSchema(function.getParameters());
            anthropicTools.add(anthropicTool);
        }
        return anthropicTools.isEmpty() ? null : anthropicTools;
    }

    private String mapStopReason(String stopReason) {
        if (stopReason == null) {
            return null;
        }
        if ("tool_use".equals(stopReason)) {
            return TOOL_CALLS_FINISH_REASON;
        }
        if ("max_tokens".equals(stopReason)) {
            return "length";
        }
        // end_turn / stop_sequence → stop
        return "stop";
    }

    private Usage toUsage(AnthropicUsage usage) {
        Usage result = new Usage();
        if (usage != null) {
            result.setPromptTokens(usage.getInputTokens());
            result.setCompletionTokens(usage.getOutputTokens());
            result.setTotalTokens(usage.getInputTokens() + usage.getOutputTokens());
        }
        return result;
    }

    private void mergeUsage(Usage target, AnthropicUsage usage) {
        if (usage == null) {
            return;
        }
        target.setPromptTokens(target.getPromptTokens() + usage.getInputTokens());
        target.setCompletionTokens(target.getCompletionTokens() + usage.getOutputTokens());
        target.setTotalTokens(target.getTotalTokens() + usage.getInputTokens() + usage.getOutputTokens());
    }

    // ---- request / transport ----

    private AnthropicChatCompletionResponse executeChatCompletionRequest(String baseUrl, String apiKey, AnthropicChatCompletion request) throws Exception {
        Request httpRequest = buildChatCompletionRequest(baseUrl, apiKey, request);
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return objectMapper.readValue(response.body().string(), AnthropicChatCompletionResponse.class);
            }
            return null;
        }
    }

    private Request buildChatCompletionRequest(String baseUrl, String apiKey, AnthropicChatCompletion request) throws JsonProcessingException {
        String requestBody = objectMapper.writeValueAsString(request);
        return new Request.Builder()
                .header("x-api-key", apiKey)
                .header("anthropic-version", anthropicConfig.getApiVersion())
                .header("Content-Type", Constants.APPLICATION_JSON)
                .url(UrlUtils.concatUrl(baseUrl, anthropicConfig.getChatCompletionUrl()))
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .build();
    }

    private void prepareChatCompletion(ChatCompletion chatCompletion, boolean stream) {
        chatCompletion.setStream(stream);
        if (!stream) {
            chatCompletion.setStreamOptions(null);
        }
        if (hasPendingTools(chatCompletion)) {
            List<Tool> tools = ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices());
            chatCompletion.setTools(tools);
        }
    }

    private boolean hasPendingTools(ChatCompletion chatCompletion) {
        return (chatCompletion.getFunctions() != null && !chatCompletion.getFunctions().isEmpty())
                || (chatCompletion.getMcpServices() != null && !chatCompletion.getMcpServices().isEmpty());
    }

    private boolean requiresFollowUp(String finishReason) {
        return FIRST_FINISH_REASON.equals(finishReason) || TOOL_CALLS_FINISH_REASON.equals(finishReason);
    }

    private void resetToolCallState(SseListener eventSourceListener) {
        eventSourceListener.setToolCalls(new ArrayList<ToolCall>());
        eventSourceListener.setToolCall(null);
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || baseUrl.isEmpty()) ? anthropicConfig.getApiHost() : baseUrl;
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || apiKey.isEmpty()) ? anthropicConfig.getApiKey() : apiKey;
    }

    @SuppressWarnings("deprecation")
    private Integer resolveMaxTokens(ChatCompletion chatCompletion) {
        if (chatCompletion.getMaxCompletionTokens() != null) {
            return chatCompletion.getMaxCompletionTokens();
        }
        if (chatCompletion.getMaxTokens() != null) {
            return chatCompletion.getMaxTokens();
        }
        return DEFAULT_MAX_TOKENS;
    }

    private String textOf(ChatMessage message) {
        if (message == null || message.getContent() == null) {
            return null;
        }
        return message.getContent().getText();
    }

    private Object parseJsonObject(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
