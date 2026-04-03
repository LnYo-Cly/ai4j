package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Content;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.service.IChatService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChatModelClient implements AgentModelClient {

    private static final ConcurrentMap<Thread, SseListener> ACTIVE_STREAMS = new ConcurrentHashMap<Thread, SseListener>();

    private final IChatService chatService;
    private final String baseUrl;
    private final String apiKey;

    public ChatModelClient(IChatService chatService) {
        this(chatService, null, null);
    }

    public ChatModelClient(IChatService chatService, String baseUrl, String apiKey) {
        this.chatService = chatService;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public AgentModelResult create(AgentPrompt prompt) throws Exception {
        ChatCompletion completion = toChatCompletion(prompt, false);
        ChatCompletionResponse response = chatService.chatCompletion(baseUrl, apiKey, completion);
        return toModelResult(response);
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
        ChatCompletion completion = toChatCompletion(prompt, true);
        completion.setPassThroughToolCalls(Boolean.TRUE);
        StreamingSseListener sseListener = new StreamingSseListener(listener);
        Thread currentThread = Thread.currentThread();
        ACTIVE_STREAMS.put(currentThread, sseListener);
        try {
            chatService.chatCompletionStream(baseUrl, apiKey, completion, sseListener);
            throwIfInterrupted(sseListener);
            sseListener.dispatchFailure();
            AgentModelResult result = sseListener.toResult();
            throwIfInterrupted(sseListener);
            if (listener != null) {
                listener.onComplete(result);
            }
            return result;
        } catch (InterruptedException ex) {
            sseListener.cancelStream();
            Thread.currentThread().interrupt();
            throw ex;
        } catch (Exception ex) {
            if (currentThread.isInterrupted()) {
                sseListener.cancelStream();
            }
            throw ex;
        } finally {
            ACTIVE_STREAMS.remove(currentThread, sseListener);
        }
    }

    public static void cancelActiveStream(Thread thread) {
        if (thread == null) {
            return;
        }
        SseListener listener = ACTIVE_STREAMS.get(thread);
        if (listener != null) {
            listener.cancelStream();
        }
    }

    private void throwIfInterrupted(SseListener sseListener) throws InterruptedException {
        if (!Thread.currentThread().isInterrupted()) {
            return;
        }
        sseListener.cancelStream();
        throw new InterruptedException("Model stream interrupted");
    }

    private ChatCompletion toChatCompletion(AgentPrompt prompt, boolean stream) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt is required");
        }
        List<ChatMessage> messages = new ArrayList<>();
        if (prompt.getSystemPrompt() != null && !prompt.getSystemPrompt().trim().isEmpty()) {
            messages.add(ChatMessage.withSystem(prompt.getSystemPrompt()));
        }
        if (prompt.getInstructions() != null && !prompt.getInstructions().trim().isEmpty()) {
            messages.add(ChatMessage.withSystem(prompt.getInstructions()));
        }

        if (prompt.getItems() != null) {
            for (Object item : prompt.getItems()) {
                ChatMessage message = convertToMessage(item);
                if (message != null) {
                    messages.add(message);
                }
            }
        }

        ChatCompletion.ChatCompletionBuilder builder = ChatCompletion.builder()
                .model(prompt.getModel())
                .messages(messages)
                .stream(stream)
                .streamExecution(prompt.getStreamExecution())
                .temperature(prompt.getTemperature() == null ? null : prompt.getTemperature().floatValue())
                .topP(prompt.getTopP() == null ? null : prompt.getTopP().floatValue())
                .maxCompletionTokens(prompt.getMaxOutputTokens())
                .user(prompt.getUser());

        if (prompt.getParallelToolCalls() != null) {
            builder.parallelToolCalls(prompt.getParallelToolCalls());
        }

        if (prompt.getToolChoice() instanceof String) {
            builder.toolChoice((String) prompt.getToolChoice());
        }

        List<Tool> tools = convertTools(prompt.getTools());
        if (!tools.isEmpty()) {
            builder.tools(tools);
            builder.passThroughToolCalls(Boolean.TRUE);
        }

        Map<String, Object> extraBody = prompt.getExtraBody();
        if (extraBody != null) {
            builder.extraBody(extraBody);
        }

        return builder.build();
    }

    private List<Tool> convertTools(List<Object> tools) {
        List<Tool> converted = new ArrayList<>();
        if (tools == null) {
            return converted;
        }
        for (Object tool : tools) {
            if (tool instanceof Tool) {
                converted.add((Tool) tool);
            }
        }
        return converted;
    }

    private ChatMessage convertToMessage(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof ChatMessage) {
            return (ChatMessage) item;
        }
        if (item instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;
            Object type = map.get("type");
            if ("message".equals(type)) {
                String role = valueAsString(map.get("role"));
                List<ToolCall> toolCalls = convertMessageToolCalls(map.get("tool_calls"));
                ChatMessage message = buildMessageFromContent(role, map.get("content"));
                if (!toolCalls.isEmpty() && "assistant".equals(role)) {
                    if (message == null) {
                        return ChatMessage.withAssistant(toolCalls);
                    }
                    return ChatMessage.builder()
                            .role(message.getRole())
                            .content(message.getContent())
                            .name(message.getName())
                            .reasoningContent(message.getReasoningContent())
                            .toolCalls(toolCalls)
                            .build();
                }
                if (message != null) {
                    return message;
                }
            }
            if ("function_call_output".equals(type)) {
                String callId = valueAsString(map.get("call_id"));
                String output = valueAsString(map.get("output"));
                if (callId != null) {
                    return ChatMessage.withTool(output, callId);
                }
            }
        }
        return null;
    }

    private List<ToolCall> convertMessageToolCalls(Object value) {
        List<ToolCall> toolCalls = new ArrayList<ToolCall>();
        if (!(value instanceof List)) {
            return toolCalls;
        }
        @SuppressWarnings("unchecked")
        List<Object> rawCalls = (List<Object>) value;
        for (Object rawCall : rawCalls) {
            if (rawCall instanceof ToolCall) {
                toolCalls.add((ToolCall) rawCall);
                continue;
            }
            if (!(rawCall instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rawMap = (Map<String, Object>) rawCall;
            Object functionValue = rawMap.get("function");
            if (!(functionValue instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> functionMap = (Map<String, Object>) functionValue;
            toolCalls.add(new ToolCall(
                    valueAsString(rawMap.get("id")),
                    valueAsString(rawMap.get("type")),
                    new ToolCall.Function(
                            valueAsString(functionMap.get("name")),
                            valueAsString(functionMap.get("arguments"))
                    )
            ));
        }
        return toolCalls;
    }

    private ChatMessage buildMessageFromContent(String role, Object content) {
        if (role == null || content == null) {
            return null;
        }
        if (content instanceof String) {
            String text = (String) content;
            return text.isEmpty() ? null : new ChatMessage(role, text);
        }
        if (content instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> parts = (List<Object>) content;
            List<Content.MultiModal> multiModals = new ArrayList<>();
            StringBuilder textBuilder = new StringBuilder();
            boolean hasImage = false;

            for (Object part : parts) {
                if (!(part instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) part;
                Object type = map.get("type");
                if ("input_text".equals(type)) {
                    String text = valueAsString(map.get("text"));
                    if (text != null && !text.isEmpty()) {
                        textBuilder.append(text);
                        multiModals.add(new Content.MultiModal(Content.MultiModal.Type.TEXT.getType(), text, null));
                    }
                }
                if ("input_image".equals(type)) {
                    String url = extractImageUrl(map.get("image_url"));
                    if (url != null && !url.isEmpty()) {
                        hasImage = true;
                        multiModals.add(new Content.MultiModal(Content.MultiModal.Type.IMAGE_URL.getType(), null, new Content.MultiModal.ImageUrl(url)));
                    }
                }
            }

            if (hasImage) {
                if (multiModals.isEmpty()) {
                    return null;
                }
                return ChatMessage.builder()
                        .role(role)
                        .content(Content.ofMultiModals(multiModals))
                        .build();
            }

            if (textBuilder.length() > 0) {
                return new ChatMessage(role, textBuilder.toString());
            }
        }
        return null;
    }

    private String extractImageUrl(Object imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        if (imageUrl instanceof String) {
            return (String) imageUrl;
        }
        if (imageUrl instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) imageUrl;
            return valueAsString(map.get("url"));
        }
        return null;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private AgentModelResult toModelResult(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return AgentModelResult.builder().rawResponse(response).build();
        }
        ChatMessage message = response.getChoices().get(0).getMessage();
        String outputText = null;
        String reasoningText = null;
        List<AgentToolCall> toolCalls = new ArrayList<>();
        if (message != null) {
            if (message.getContent() != null) {
                outputText = message.getContent().getText();
            }
            reasoningText = message.getReasoningContent();
            if (message.getToolCalls() != null) {
                for (ToolCall toolCall : message.getToolCalls()) {
                    if (toolCall == null || toolCall.getFunction() == null) {
                        continue;
                    }
                    toolCalls.add(AgentToolCall.builder()
                            .callId(toolCall.getId())
                            .name(toolCall.getFunction().getName())
                            .arguments(toolCall.getFunction().getArguments())
                            .type(toolCall.getType())
                            .build());
                }
            }
        }

        List<Object> memoryItems = buildAssistantMemoryItems(outputText, toolCalls);

        return AgentModelResult.builder()
                .reasoningText(reasoningText == null ? "" : reasoningText)
                .outputText(outputText == null ? "" : outputText)
                .toolCalls(toolCalls)
                .memoryItems(memoryItems)
                .rawResponse(response)
                .build();
    }

    private final class StreamingSseListener extends SseListener {

        private final AgentModelStreamListener listener;

        private StreamingSseListener(AgentModelStreamListener listener) {
            this.listener = listener;
        }

        @Override
        protected void error(Throwable t, okhttp3.Response response) {
            if (listener != null) {
                listener.onError(t);
            }
        }

        @Override
        protected void send() {
            if (listener == null) {
                return;
            }
            String delta = getCurrStr();
            if (delta == null || delta.isEmpty()) {
                return;
            }
            if (isReasoning()) {
                listener.onReasoningDelta(delta);
                return;
            }
            listener.onDeltaText(delta);
        }

        @Override
        protected void retry(Throwable t, int attempt, int maxAttempts) {
            if (listener == null) {
                return;
            }
            String message = t == null || t.getMessage() == null || t.getMessage().trim().isEmpty()
                    ? "Retrying model stream"
                    : t.getMessage().trim();
            listener.onRetry(message, attempt, maxAttempts, t);
        }

        private AgentModelResult toResult() {
            String outputText = getOutput().toString();
            String reasoningText = getReasoningOutput().toString();
            List<AgentToolCall> calls = convertToolCalls(getToolCalls());
            List<Object> memoryItems = buildAssistantMemoryItems(outputText, calls);

            Map<String, Object> rawResponse = new LinkedHashMap<>();
            rawResponse.put("mode", "chat.stream");
            rawResponse.put("outputText", outputText);
            rawResponse.put("reasoningText", reasoningText);
            rawResponse.put("finishReason", getFinishReason());
            rawResponse.put("toolCalls", getToolCalls());
            rawResponse.put("usage", getUsage());

            return AgentModelResult.builder()
                    .reasoningText(reasoningText)
                    .outputText(outputText)
                    .toolCalls(calls)
                    .memoryItems(memoryItems)
                    .rawResponse(rawResponse)
                    .build();
        }
    }

    private List<Object> buildAssistantMemoryItems(String outputText, List<AgentToolCall> toolCalls) {
        List<Object> memoryItems = new ArrayList<Object>();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            memoryItems.add(io.github.lnyocly.ai4j.agent.util.AgentInputItem.assistantToolCallsMessage(
                    outputText == null ? "" : outputText,
                    toolCalls
            ));
            return memoryItems;
        }
        if (outputText != null && !outputText.isEmpty()) {
            memoryItems.add(io.github.lnyocly.ai4j.agent.util.AgentInputItem.message("assistant", outputText));
        }
        return memoryItems;
    }

    private List<AgentToolCall> convertToolCalls(List<ToolCall> toolCalls) {
        List<AgentToolCall> calls = new ArrayList<>();
        if (toolCalls == null) {
            return calls;
        }
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null || toolCall.getFunction() == null) {
                continue;
            }
            calls.add(AgentToolCall.builder()
                    .callId(toolCall.getId())
                    .name(toolCall.getFunction().getName())
                    .arguments(toolCall.getFunction().getArguments())
                    .type(toolCall.getType())
                    .build());
        }
        return calls;
    }
}
