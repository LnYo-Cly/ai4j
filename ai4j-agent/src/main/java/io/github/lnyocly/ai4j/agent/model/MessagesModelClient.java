package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicTool;
import io.github.lnyocly.ai4j.platform.anthropic.stream.AnthropicStreamHandler;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.service.IMessagesService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 协议的 {@link AgentModelClient} 实现，与 {@link ChatModelClient}（OpenAI Chat）、
 * {@link ResponsesModelClient}（OpenAI Responses）并列。
 * <p>
 * agent 规范模型（{@link AgentPrompt}/{@link AgentModelResult}）本就协议中立，这里把它直接映射到
 * Anthropic Messages 原生类型，委托 {@link IMessagesService} 走 Anthropic 线协议——零 OpenAI 转换。
 * <p>
 * 原生 thinking block/delta 映射到 {@link AgentModelResult#getReasoningText()} /
 * {@link AgentModelStreamListener#onReasoningDelta(String)}；tool_use 映射到 {@link AgentToolCall}。
 */
public class MessagesModelClient implements AgentModelClient {

    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final IMessagesService messagesService;
    private final String baseUrl;
    private final String apiKey;

    public MessagesModelClient(IMessagesService messagesService) {
        this(messagesService, null, null);
    }

    public MessagesModelClient(IMessagesService messagesService, String baseUrl, String apiKey) {
        this.messagesService = messagesService;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public AgentModelResult create(AgentPrompt prompt) throws Exception {
        AnthropicChatCompletion request = toAnthropicRequest(prompt, false);
        AnthropicChatCompletionResponse response = messagesService.messages(baseUrl, apiKey, request);
        return toModelResult(response);
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
        AnthropicChatCompletion request = toAnthropicRequest(prompt, true);
        StreamBridge bridge = new StreamBridge(listener);
        messagesService.messagesStream(baseUrl, apiKey, request, bridge);
        AgentModelResult result = bridge.toResult();
        if (listener != null) {
            listener.onComplete(result);
        }
        return result;
    }

    // ---- AgentPrompt -> AnthropicChatCompletion ----

    private AnthropicChatCompletion toAnthropicRequest(AgentPrompt prompt, boolean stream) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt is required");
        }
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel(prompt.getModel());
        request.setStream(stream);

        String system = mergeText(prompt.getSystemPrompt(), prompt.getInstructions());
        if (system != null && !system.isEmpty()) {
            request.setSystem(system);
        }

        List<AnthropicMessage> messages = new ArrayList<AnthropicMessage>();
        if (prompt.getItems() != null) {
            for (Object item : prompt.getItems()) {
                AnthropicMessage message = convertItem(item);
                if (message != null) {
                    messages.add(message);
                }
            }
        }
        request.setMessages(messages);

        request.setMaxTokens(prompt.getMaxOutputTokens() != null ? prompt.getMaxOutputTokens() : DEFAULT_MAX_TOKENS);
        if (prompt.getTemperature() != null) {
            request.setTemperature(prompt.getTemperature());
        }
        if (prompt.getTopP() != null) {
            request.setTopP(prompt.getTopP());
        }

        List<AnthropicTool> tools = convertTools(prompt.getTools());
        if (tools != null && !tools.isEmpty()) {
            request.setTools(tools);
        }
        if (prompt.getToolChoice() != null) {
            request.setToolChoice(prompt.getToolChoice());
        }

        Map<String, Object> extra = prompt.getExtraBody();
        if (prompt.getReasoning() != null) {
            if (extra == null) {
                extra = new LinkedHashMap<String, Object>();
            }
            if (!extra.containsKey("thinking")) {
                extra.put("thinking", prompt.getReasoning());
            }
        }
        if (extra != null) {
            request.setExtraBody(extra);
        }
        return request;
    }

    @SuppressWarnings("unchecked")
    private AnthropicMessage convertItem(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof ChatMessage) {
            return fromChatMessage((ChatMessage) item);
        }
        if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            Object type = map.get("type");
            if ("message".equals(type)) {
                String role = valueAsString(map.get("role"));
                List<AgentToolCall> toolCalls = convertMessageToolCalls(map.get("tool_calls"));
                AnthropicMessage message = new AnthropicMessage();
                message.setRole(role == null ? "user" : role);
                if ("assistant".equals(role) && !toolCalls.isEmpty()) {
                    List<AnthropicContentBlock> blocks = new ArrayList<AnthropicContentBlock>();
                    String text = valueAsString(map.get("content"));
                    if (text != null && !text.isEmpty()) {
                        AnthropicContentBlock tb = new AnthropicContentBlock();
                        tb.setType("text");
                        tb.setText(text);
                        blocks.add(tb);
                    }
                    for (AgentToolCall call : toolCalls) {
                        blocks.add(toolUseBlock(call.getCallId(), call.getName(), call.getArguments()));
                    }
                    message.setContent(blocks);
                } else {
                    message.setContent(valueAsString(map.get("content")));
                }
                return message;
            }
            if ("function_call_output".equals(type)) {
                String callId = valueAsString(map.get("call_id"));
                String output = valueAsString(map.get("output"));
                AnthropicMessage message = new AnthropicMessage();
                message.setRole("user");
                AnthropicContentBlock result = new AnthropicContentBlock();
                result.setType("tool_result");
                result.setToolUseId(callId);
                result.setContent(output);
                message.setContent(Collections.singletonList(result));
                return message;
            }
        }
        return null;
    }

    private AnthropicMessage fromChatMessage(ChatMessage message) {
        AnthropicMessage anthropicMessage = new AnthropicMessage();
        anthropicMessage.setRole(message.getRole());
        List<ToolCall> toolCalls = message.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<AnthropicContentBlock> blocks = new ArrayList<AnthropicContentBlock>();
            String text = message.getContent() == null ? null : message.getContent().getText();
            if (text != null && !text.isEmpty()) {
                AnthropicContentBlock tb = new AnthropicContentBlock();
                tb.setType("text");
                tb.setText(text);
                blocks.add(tb);
            }
            for (ToolCall call : toolCalls) {
                String name = call.getFunction() == null ? null : call.getFunction().getName();
                String args = call.getFunction() == null ? null : call.getFunction().getArguments();
                blocks.add(toolUseBlock(call.getId(), name, args));
            }
            anthropicMessage.setContent(blocks);
        } else {
            anthropicMessage.setContent(message.getContent() == null ? null : message.getContent().getText());
        }
        return anthropicMessage;
    }

    private AnthropicContentBlock toolUseBlock(String id, String name, String argumentsJson) {
        AnthropicContentBlock use = new AnthropicContentBlock();
        use.setType("tool_use");
        use.setId(id);
        use.setName(name);
        use.setInput(parseJsonObject(argumentsJson));
        return use;
    }

    @SuppressWarnings("unchecked")
    private List<AgentToolCall> convertMessageToolCalls(Object value) {
        List<AgentToolCall> calls = new ArrayList<AgentToolCall>();
        if (!(value instanceof List)) {
            return calls;
        }
        for (Object raw : (List<Object>) value) {
            if (!(raw instanceof Map)) {
                continue;
            }
            Map<String, Object> rawMap = (Map<String, Object>) raw;
            Object functionValue = rawMap.get("function");
            if (!(functionValue instanceof Map)) {
                continue;
            }
            Map<String, Object> functionMap = (Map<String, Object>) functionValue;
            calls.add(AgentToolCall.builder()
                    .callId(valueAsString(rawMap.get("id")))
                    .name(valueAsString(functionMap.get("name")))
                    .arguments(valueAsString(functionMap.get("arguments")))
                    .type(valueAsString(rawMap.get("type")))
                    .build());
        }
        return calls;
    }

    private List<AnthropicTool> convertTools(List<Object> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<AnthropicTool> result = new ArrayList<AnthropicTool>();
        for (Object tool : tools) {
            if (!(tool instanceof Tool)) {
                continue;
            }
            Tool.Function function = ((Tool) tool).getFunction();
            if (function == null) {
                continue;
            }
            AnthropicTool anthropicTool = new AnthropicTool();
            anthropicTool.setName(function.getName());
            anthropicTool.setDescription(function.getDescription());
            anthropicTool.setInputSchema(function.getParameters());
            result.add(anthropicTool);
        }
        return result.isEmpty() ? null : result;
    }

    // ---- AnthropicChatCompletionResponse -> AgentModelResult ----

    private AgentModelResult toModelResult(AnthropicChatCompletionResponse response) {
        if (response == null) {
            return AgentModelResult.builder().build();
        }
        Accumulator acc = new Accumulator();
        acc.consume(response.getContent());
        return acc.toResult(response);
    }

    private static String mergeText(String a, String b) {
        boolean hasA = a != null && !a.trim().isEmpty();
        boolean hasB = b != null && !b.trim().isEmpty();
        if (hasA && hasB) {
            return a + "\n\n" + b;
        }
        if (hasA) {
            return a;
        }
        if (hasB) {
            return b;
        }
        return null;
    }

    private static String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Object parseJsonObject(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return com.alibaba.fastjson2.JSON.parse(json);
        } catch (Exception e) {
            return json;
        }
    }

    // ---- streaming bridge: AnthropicStreamHandler -> AgentModelStreamListener ----

    private class StreamBridge implements AnthropicStreamHandler {
        private final AgentModelStreamListener listener;
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final List<AgentToolCall> toolCalls = new ArrayList<AgentToolCall>();
        private String modelName;

        StreamBridge(AgentModelStreamListener listener) {
            this.listener = listener;
        }

        @Override
        public void onStart(String messageId, String model) {
            this.modelName = model;
        }

        AgentModelResult toResult() {
            return toResult(modelName);
        }

        @Override
        public void onDeltaText(String delta) {
            text.append(delta);
            if (listener != null) {
                listener.onDeltaText(delta);
            }
        }

        @Override
        public void onThinkingDelta(String delta) {
            thinking.append(delta);
            if (listener != null) {
                listener.onReasoningDelta(delta);
            }
        }

        @Override
        public void onToolUseComplete(int index, String toolUseId, String name, String inputJson) {
            AgentToolCall call = AgentToolCall.builder()
                    .callId(toolUseId)
                    .name(name)
                    .arguments(inputJson)
                    .type("function")
                    .build();
            toolCalls.add(call);
            if (listener != null) {
                listener.onToolCall(call);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (listener != null) {
                listener.onError(t);
            }
        }

        AgentModelResult toResult(AnthropicChatCompletionResponse response) {
            return toResult(response == null ? null : response.getModel());
        }

        AgentModelResult toResult(String modelName) {
            String outputText = text.toString();
            String reasoningText = thinking.toString();
            List<Object> memoryItems = buildAssistantMemoryItems(outputText, toolCalls);
            Map<String, Object> raw = new LinkedHashMap<String, Object>();
            raw.put("mode", "messages.stream");
            raw.put("model", modelName);
            raw.put("outputText", outputText);
            raw.put("reasoningText", reasoningText);
            raw.put("toolCalls", toolCalls);
            return AgentModelResult.builder()
                    .reasoningText(reasoningText)
                    .outputText(outputText)
                    .toolCalls(toolCalls)
                    .memoryItems(memoryItems)
                    .rawResponse(raw)
                    .build();
        }
    }

    /** 累积原生响应 content blocks，复用于非流式与（潜在）聚合。 */
    private static class Accumulator {
        final StringBuilder text = new StringBuilder();
        final StringBuilder thinking = new StringBuilder();
        final List<AgentToolCall> toolCalls = new ArrayList<AgentToolCall>();

        void consume(List<AnthropicContentBlock> blocks) {
            if (blocks == null) {
                return;
            }
            for (AnthropicContentBlock block : blocks) {
                if (block == null) {
                    continue;
                }
                if ("text".equals(block.getType()) && block.getText() != null) {
                    text.append(block.getText());
                } else if ("thinking".equals(block.getType()) && block.getThinking() != null) {
                    thinking.append(block.getThinking());
                } else if ("tool_use".equals(block.getType())) {
                    toolCalls.add(AgentToolCall.builder()
                            .callId(block.getId())
                            .name(block.getName())
                            .arguments(writeJson(block.getInput()))
                            .type("function")
                            .build());
                }
            }
        }

        AgentModelResult toResult(AnthropicChatCompletionResponse response) {
            String outputText = text.toString();
            String reasoningText = thinking.toString();
            List<Object> memoryItems = buildAssistantMemoryItems(outputText, toolCalls);
            return AgentModelResult.builder()
                    .reasoningText(reasoningText)
                    .outputText(outputText)
                    .toolCalls(toolCalls)
                    .memoryItems(memoryItems)
                    .rawResponse(response)
                    .build();
        }
    }

    private static List<Object> buildAssistantMemoryItems(String outputText, List<AgentToolCall> toolCalls) {
        List<Object> memoryItems = new ArrayList<Object>();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            memoryItems.add(AgentInputItem.assistantToolCallsMessage(outputText == null ? "" : outputText, toolCalls));
            return memoryItems;
        }
        if (outputText != null && !outputText.isEmpty()) {
            memoryItems.add(AgentInputItem.message("assistant", outputText));
        }
        return memoryItems;
    }

    private static String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return com.alibaba.fastjson2.JSON.toJSONString(value);
    }
}
