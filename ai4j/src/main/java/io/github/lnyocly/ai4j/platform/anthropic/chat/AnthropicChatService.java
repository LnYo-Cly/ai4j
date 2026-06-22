package io.github.lnyocly.ai4j.platform.anthropic.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.convert.chat.ParameterConvert;
import io.github.lnyocly.ai4j.convert.chat.ResultConvert;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionSupport;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicTool;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicUsage;
import io.github.lnyocly.ai4j.platform.anthropic.stream.AnthropicStreamHandler;
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
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Anthropic Messages 的统一 {@link IChatService} 适配器（OpenAI 格式 ⇄ Anthropic Messages）。
 * <p>
 * 仅负责翻译：{@code ChatCompletion}→{@link AnthropicChatCompletion}、{@link AnthropicChatCompletionResponse}
 * →{@code ChatCompletionResponse}、Anthropic 流式事件→OpenAI chunk。传输、鉴权、SSE 解析全部委托给
 * {@link AnthropicMessagesService}（原生一等公民）。
 * <p>
 * tool_use 经 {@link #mapStopReason} 映射为 OpenAI tool_calls；thinking block/delta 映射为 reasoning_content。
 */
@Slf4j
public class AnthropicChatService implements IChatService,
        ParameterConvert<AnthropicChatCompletion>,
        ResultConvert<AnthropicChatCompletionResponse> {

    private static final String TOOL_CALLS_FINISH_REASON = "tool_calls";
    private static final String FIRST_FINISH_REASON = "first";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final AnthropicConfig anthropicConfig;
    private final AnthropicMessagesService nativeService;
    private final ObjectMapper objectMapper;

    public AnthropicChatService(Configuration configuration) {
        this.anthropicConfig = configuration.getAnthropicConfig();
        this.nativeService = new AnthropicMessagesService(configuration);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
        ToolUtil.pushBuiltInToolContext(chatCompletion.getBuiltInToolContext());
        try {
            boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());
            prepareChatCompletion(chatCompletion, false);
            AnthropicChatCompletion request = convertChatCompletionObject(chatCompletion);
            Usage allUsage = new Usage();
            String finishReason = FIRST_FINISH_REASON;

            while (requiresFollowUp(finishReason)) {
                AnthropicChatCompletionResponse response = nativeService.messages(baseUrl, apiKey, request);
                if (response == null) {
                    break;
                }
                mergeUsage(allUsage, response.getUsage());
                finishReason = mapStopReason(response.getStopReason());
                if (TOOL_CALLS_FINISH_REASON.equals(finishReason)) {
                    if (passThroughToolCalls) {
                        return toChatCompletionResponse(response, allUsage);
                    }
                    request.setMessages(AnthropicMessagesService.appendToolResults(request.safeMessages(), response));
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
            boolean passThroughToolCalls = Boolean.TRUE.equals(chatCompletion.getPassThroughToolCalls());
            prepareChatCompletion(chatCompletion, true);
            AnthropicChatCompletion request = convertChatCompletionObject(chatCompletion);
            String finishReason = FIRST_FINISH_REASON;

            while (requiresFollowUp(finishReason)) {
                request.setStream(Boolean.TRUE);
                Request httpRequest = nativeService.buildRequest(resolveBaseUrl(baseUrl), resolveApiKey(apiKey), request);
                StreamExecutionSupport.execute(
                        eventSourceListener,
                        chatCompletion.getStreamExecution(),
                        () -> nativeService.getFactory().newEventSource(httpRequest, convertEventSource(eventSourceListener))
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

    /**
     * 把原生 SSE 解析（{@link AnthropicMessagesService#toEventListener}）桥接到 OpenAI chunk：
     * 用一个 {@link OpenAiChunkBridge}（{@link AnthropicStreamHandler}）把原生回调翻译成 OpenAI chunk 喂给 {@link SseListener}。
     */
    @Override
    public EventSourceListener convertEventSource(final SseListener eventSourceListener) {
        final OpenAiChunkBridge bridge = new OpenAiChunkBridge(eventSourceListener);
        final EventSourceListener parser = nativeService.toEventListener(bridge, null, null);
        return new EventSourceListener() {
            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                bridge.attach(eventSource);
                eventSourceListener.onOpen(eventSource, response);
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                bridge.attach(eventSource);
                if ("[DONE]".equalsIgnoreCase(data)) {
                    eventSourceListener.onEvent(eventSource, id, type, data);
                    return;
                }
                parser.onEvent(eventSource, id, type, data);
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                eventSourceListener.onClosed(eventSource);
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                eventSourceListener.onFailure(eventSource, t, response);
            }
        };
    }

    // ---- OpenAI chunk bridge (AnthropicStreamHandler → OpenAI chunk → SseListener) ----

    private final class OpenAiChunkBridge implements AnthropicStreamHandler {
        private final SseListener sse;
        private String messageId;
        private String modelName;
        private EventSource eventSource;

        OpenAiChunkBridge(SseListener sse) {
            this.sse = sse;
        }

        void attach(EventSource eventSource) {
            this.eventSource = eventSource;
        }

        @Override
        public void onStart(String messageId, String model) {
            this.messageId = messageId;
            this.modelName = model;
        }

        @Override
        public void onDeltaText(String text) {
            emit(textChunk(text));
        }

        @Override
        public void onThinkingDelta(String thinking) {
            emit(reasoningChunk(thinking));
        }

        @Override
        public void onToolUseComplete(int index, String toolUseId, String name, String inputJson) {
            emit(toolCallChunk(toolUseId, name, inputJson));
        }

        @Override
        public void onStopReason(String stopReason, long inputTokens, long outputTokens) {
            emit(finishChunk(mapStopReason(stopReason), outputTokens));
        }

        @Override
        public void onComplete() {
            sse.onEvent(eventSource, null, null, "[DONE]");
        }

        private void emit(String json) {
            sse.onEvent(eventSource, null, null, json);
        }

        private String textChunk(String text) {
            ChatMessage delta = ChatMessage.builder().role(ChatMessageType.ASSISTANT.getRole()).content(Content.ofText(text)).build();
            return writeChunk(baseChunk(delta, null));
        }

        private String reasoningChunk(String thinking) {
            ChatMessage delta = ChatMessage.builder().role(ChatMessageType.ASSISTANT.getRole()).reasoningContent(thinking).build();
            return writeChunk(baseChunk(delta, null));
        }

        private String toolCallChunk(String toolId, String name, String arguments) {
            ToolCall toolCall = new ToolCall(toolId, "function", new ToolCall.Function(name, arguments));
            ChatMessage delta = ChatMessage.builder()
                    .role(ChatMessageType.ASSISTANT.getRole())
                    .content(Content.ofText(""))
                    .toolCalls(Collections.singletonList(toolCall))
                    .build();
            return writeChunk(baseChunk(delta, null));
        }

        private String finishChunk(String finishReason, long outputTokens) {
            ChatMessage delta = ChatMessage.builder().role(ChatMessageType.ASSISTANT.getRole()).content(Content.ofText("")).build();
            ChatCompletionResponse chunk = baseChunk(delta, finishReason);
            chunk.setUsage(new Usage(0L, outputTokens, outputTokens));
            return writeChunk(chunk);
        }

        private ChatCompletionResponse baseChunk(ChatMessage delta, String finishReason) {
            ChatCompletionResponse chunk = new ChatCompletionResponse();
            chunk.setId(messageId);
            chunk.setObject("chat.completion.chunk");
            chunk.setCreated(System.currentTimeMillis() / 1000L);
            chunk.setModel(modelName);
            Choice choice = new Choice();
            choice.setIndex(0);
            choice.setDelta(delta);
            choice.setFinishReason(finishReason);
            chunk.setChoices(Collections.singletonList(choice));
            return chunk;
        }

        private String writeChunk(ChatCompletionResponse chunk) {
            try {
                return objectMapper.writeValueAsString(chunk);
            } catch (JsonProcessingException e) {
                throw new CommonException("Anthropic stream chunk serialize error");
            }
        }
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
        StringBuilder reasoningBuilder = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<ToolCall>();
        if (response.getContent() != null) {
            for (AnthropicContentBlock block : response.getContent()) {
                if (block == null) {
                    continue;
                }
                if ("text".equals(block.getType()) && block.getText() != null) {
                    textBuilder.append(block.getText());
                } else if ("thinking".equals(block.getType()) && block.getThinking() != null) {
                    reasoningBuilder.append(block.getThinking());
                } else if ("tool_use".equals(block.getType())) {
                    toolCalls.add(new ToolCall(block.getId(), "function",
                            new ToolCall.Function(block.getName(), writeJson(block.getInput()))));
                }
            }
        }
        message.setContent(Content.ofText(textBuilder.toString()));
        if (reasoningBuilder.length() > 0) {
            message.setReasoningContent(reasoningBuilder.toString());
        }
        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls);
        }

        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason(mapStopReason(response.getStopReason()));

        result.setChoices(Collections.singletonList(choice));
        result.setUsage(mergedUsage != null ? mergedUsage : toUsage(response.getUsage()));
        return result;
    }

    private AnthropicMessage toAnthropicMessage(ChatMessage message) {
        AnthropicMessage anthropicMessage = new AnthropicMessage();
        String role = message.getRole();
        if (ChatMessageType.TOOL.getRole().equals(role)) {
            anthropicMessage.setRole(ChatMessageType.USER.getRole());
            AnthropicContentBlock resultBlock = new AnthropicContentBlock();
            resultBlock.setType("tool_result");
            resultBlock.setToolUseId(message.getToolCallId());
            resultBlock.setContent(textOf(message));
            anthropicMessage.setContent(Collections.singletonList(resultBlock));
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

    private List<AnthropicMessage> appendStreamToolMessages(List<AnthropicMessage> messages, List<ToolCall> toolCalls) {
        List<AnthropicMessage> updated = new ArrayList<AnthropicMessage>(messages);
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
            resultMessage.setContent(Collections.singletonList(resultBlock));
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
