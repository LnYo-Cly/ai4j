package io.github.lnyocly.ai4j.protocol.chat;

import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.DroppedField;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.ImageBlock;
import io.github.lnyocly.ai4j.model.MediaKind;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.MessageRole;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.OpaqueBlock;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningBlock;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.model.ToolCallBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolCallValue;
import io.github.lnyocly.ai4j.model.ToolChoice;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.model.ToolKind;
import io.github.lnyocly.ai4j.model.ToolResultBlock;
import io.github.lnyocly.ai4j.model.ToolResultItem;
import io.github.lnyocly.ai4j.model.ToolVisibility;
import io.github.lnyocly.ai4j.protocol.CodecSupport;
import io.github.lnyocly.ai4j.protocol.EndpointPolicy;
import io.github.lnyocly.ai4j.protocol.JsonWire;
import io.github.lnyocly.ai4j.protocol.ProtocolCodec;
import io.github.lnyocly.ai4j.protocol.RawSseEvent;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.protocol.WireResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CHAT_COMPLETIONS codec: OpenAI-compatible {@code /chat/completions} wire shape.
 *
 * <p>Anchored on the legacy behaviour in {@code OpenAiChatService} and the
 * {@code ChatMessage}/{@code Content}/{@code Tool} DTO shapes, but with the new
 * contract's read-only encode: input requests are never mutated (C11).</p>
 */
public final class ChatCompletionsCodec implements ProtocolCodec {

    /**
     * Wire whitelist for {@code providerOptions}. A key outside this set is
     * reported as an explicit loss, never silently dropped (design 5.1).
     */
    private static final java.util.Set<String> ALLOWED_PROVIDER_OPTIONS =
            Collections.unmodifiableSet(new java.util.HashSet<String>(java.util.Arrays.asList(
                    "frequency_penalty", "logit_bias", "logprobs", "top_logprobs",
                    "presence_penalty", "seed", "stop", "response_format", "user",
                    "reasoning_effort", "stream_options", "n", "service_tier")));

    @Override
    public Protocol getProtocol() {
        return Protocol.CHAT_COMPLETIONS;
    }

    @Override
    public WireRequest encode(ModelRequest request, EndpointPolicy policy) {
        List<DroppedField> warnings = new ArrayList<DroppedField>();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", request.getModel());

        List<Object> wireMessages = new ArrayList<Object>();
        for (ModelItem item : request.getItems()) {
            if (item instanceof Message) {
                wireMessages.add(messageToWire((Message) item, request, warnings));
            } else if (item instanceof ToolResultItem) {
                wireMessages.add(toolResultToWire((ToolResultItem) item));
            } else {
                warnings.add(DroppedField.of("items[" + item.getType() + "]",
                        "CHAT_COMPLETIONS cannot express this top-level item kind"));
            }
        }
        body.put("messages", wireMessages);

        if (!request.getTools().isEmpty()) {
            List<Object> tools = new ArrayList<Object>();
            for (ToolDeclaration declaration : request.getTools()) {
                Map<String, Object> tool = toolToWire(declaration, warnings);
                if (tool != null) {
                    tools.add(tool);
                }
            }
            if (!tools.isEmpty()) {
                body.put("tools", tools);
            }
        }

        if (request.getToolChoice() != null) {
            Object choice = toolChoiceToWire(request.getToolChoice(), warnings);
            if (choice != null) {
                body.put("tool_choice", choice);
            }
        }
        if (request.getParallelToolCalls() != null) {
            body.put("parallel_tool_calls", request.getParallelToolCalls());
        }
        if (request.getMaxOutputTokens() != null && policy.getProfile().getMaxTokensParameter() != null) {
            body.put(policy.getProfile().getMaxTokensParameter(), request.getMaxOutputTokens());
        }
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.put("top_p", request.getTopP());
        }
        body.put("stream", request.getStream() != null && request.getStream());
        if (Boolean.TRUE.equals(request.getStream()) && policy.getProfile().isIncludeStreamOptions()) {
            Map<String, Object> streamOptions = new LinkedHashMap<String, Object>();
            streamOptions.put("include_usage", Boolean.TRUE);
            body.put("stream_options", streamOptions);
        }
        applyProviderOptions(request, policy, body, warnings);

        return WireRequest.of(getProtocol(), "POST", policy.getPath(), body, null,
                Boolean.TRUE.equals(request.getStream()), warnings);
    }

    private Map<String, Object> messageToWire(Message message, ModelRequest request,
                                              List<DroppedField> warnings) {
        Map<String, Object> wire = new LinkedHashMap<String, Object>();
        wire.put("role", wireRole(message.getRole()));
        if (message.getName() != null) {
            wire.put("name", message.getName());
        }

        List<Block> blocks = message.getBlocks();
        boolean onlyText = true;
        boolean hasMedia = false;
        for (Block block : blocks) {
            if (!(block instanceof TextBlock)) {
                onlyText = false;
            }
            if (block instanceof ImageBlock) {
                hasMedia = true;
            }
        }

        if (message.getRole() == MessageRole.TOOL) {
            String toolCallId = null;
            for (ToolResultBlock block : toolResultBlocksOf(blocks)) {
                toolCallId = block.getCallId();
                break;
            }
            if (toolCallId == null) {
                toolCallId = JsonWire.textAt(message.getProtocolData(), "tool_call_id");
            }
            if (toolCallId == null) {
                warnings.add(DroppedField.of("message.tool_call_id",
                        "tool role message without a call id is not expressible on CHAT_COMPLETIONS"));
            } else {
                wire.put("tool_call_id", toolCallId);
            }
            wire.put("content", joinText(blocks));
            return wire;
        }

        if (onlyText) {
            wire.put("content", joinText(blocks));
        } else if (hasMedia) {
            List<Object> parts = new ArrayList<Object>();
            for (Block block : blocks) {
                if (block instanceof TextBlock) {
                    Map<String, Object> part = new LinkedHashMap<String, Object>();
                    part.put("type", "text");
                    part.put("text", ((TextBlock) block).getText());
                    parts.add(part);
                } else if (block instanceof ImageBlock) {
                    Map<String, Object> part = CodecSupport.openAiImagePart((ImageBlock) block);
                    if (part != null) {
                        parts.add(part);
                    } else {
                        warnings.add(DroppedField.of("message.content.image",
                                "image media identity cannot be expressed on CHAT_COMPLETIONS"));
                    }
                }
            }
            wire.put("content", parts);
        } else {
            wire.put("content", joinText(blocks));
        }

        List<ToolCallBlock> toolCalls = toolCallBlocksOf(blocks);
        if (!toolCalls.isEmpty()) {
            List<Object> calls = new ArrayList<Object>();
            for (ToolCallBlock block : toolCalls) {
                Map<String, Object> call = new LinkedHashMap<String, Object>();
                call.put("id", block.getValue().getCallId());
                call.put("type", "function");
                Map<String, Object> function = new LinkedHashMap<String, Object>();
                function.put("name", block.getValue().getName());
                function.put("arguments", block.getValue().getArgumentsJson());
                call.put("function", function);
                calls.add(call);
            }
            wire.put("tool_calls", calls);
        }

        ReasoningBlock reasoning = firstReasoning(blocks);
        if (reasoning != null) {
            wire.put("reasoning_content", reasoning.getText());
        }

        for (OpaqueBlock opaque : opaqueBlocksOf(blocks)) {
            warnings.add(DroppedField.of("message.block.opaque",
                    "opaque block of type " + opaque.getNativeType()
                            + " is not replay-valid for CHAT_COMPLETIONS"));
        }
        if (hasNonTextNonImage(blocks)) {
            warnings.add(DroppedField.of("message.content",
                    "CHAT_COMPLETIONS cannot express one or more block types in this message"));
        }
        return wire;
    }

    private Map<String, Object> toolResultToWire(ToolResultItem item) {
        Map<String, Object> wire = new LinkedHashMap<String, Object>();
        wire.put("role", "tool");
        wire.put("tool_call_id", item.getCallId());
        wire.put("content", joinText(item.getValue().getContent()));
        return wire;
    }

    private Map<String, Object> toolToWire(ToolDeclaration declaration, List<DroppedField> warnings) {
        if (declaration.getKind() == ToolKind.FUNCTION) {
            Map<String, Object> tool = new LinkedHashMap<String, Object>();
            tool.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<String, Object>();
            function.put("name", declaration.getName());
            if (declaration.getDescription() != null) {
                function.put("description", declaration.getDescription());
            }
            function.put("parameters", declaration.getParameters());
            if (declaration.getStrict() != null) {
                function.put("strict", declaration.getStrict());
            }
            tool.put("function", function);
            return tool;
        }
        if (declaration.getKind() == ToolKind.NATIVE) {
            Map<String, Object> nativeSpec = new LinkedHashMap<String, Object>(declaration.getNativeSpec());
            boolean replayValid = "function".equals(nativeSpec.get("type"))
                    && declaration.getName() != null;
            if (replayValid) {
                return nativeSpec;
            }
            warnings.add(DroppedField.of("tools[" + declaration.getName() + "]",
                    "native tool declaration is not replay-valid for CHAT_COMPLETIONS"));
            return null;
        }
        warnings.add(DroppedField.of("tools[" + declaration.getName() + "]",
                declaration.getKind() + " tools have no CHAT_COMPLETIONS shape"));
        return null;
    }

    private Object toolChoiceToWire(ToolChoice choice, List<DroppedField> warnings) {
        if (choice.isFunctionChoice()) {
            Map<String, Object> function = new LinkedHashMap<String, Object>();
            function.put("name", choice.getFunctionName());
            Map<String, Object> wire = new LinkedHashMap<String, Object>();
            wire.put("type", "function");
            wire.put("function", function);
            return wire;
        }
        if (choice.isNativeChoice()) {
            if (choice.getNativeProtocol() == getProtocol()) {
                return choice.getNativeChoice();
            }
            warnings.add(DroppedField.of("tool_choice",
                    "native tool choice belongs to " + choice.getNativeProtocol()));
            return null;
        }
        if (choice.getMode() == null) {
            return null;
        }
        switch (choice.getMode()) {
            case AUTO:
                return "auto";
            case NONE:
                return "none";
            case REQUIRED:
                return "required";
            default:
                return null;
        }
    }

    private void applyProviderOptions(ModelRequest request, EndpointPolicy policy,
                                      Map<String, Object> body, List<DroppedField> warnings) {
        Map<String, Object> merged = new LinkedHashMap<String, Object>();
        merged.putAll(policy.getProfile().getDefaultProviderOptions());
        merged.putAll(request.getProviderOptions());
        for (Map.Entry<String, Object> entry : merged.entrySet()) {
            if (ALLOWED_PROVIDER_OPTIONS.contains(entry.getKey())) {
                body.put(entry.getKey(), entry.getValue());
            } else {
                warnings.add(DroppedField.of("providerOptions." + entry.getKey(),
                        "not in the CHAT_COMPLETIONS wire whitelist"));
            }
        }
    }

    @Override
    public ModelResult decode(WireResponse response) {
        Map<String, Object> root = JsonWire.readObject(response.getBodyText());
        ModelResult.Builder builder = ModelResult.builder();
        builder.raw(root);

        List<Object> choices = JsonWire.arrayAt(root, "choices");
        if (!choices.isEmpty() && choices.get(0) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> choice = (Map<String, Object>) choices.get(0);
            builder.stopReason(JsonWire.textAt(choice, "finish_reason"));
            Map<String, Object> message = JsonWire.objectAt(choice, "message");
            if (!message.isEmpty()) {
                builder.items(decodeMessage(message));
            }
            String reasoning = JsonWire.textAt(message, "reasoning_content");
            if (reasoning != null && !reasoning.isEmpty()) {
                builder.item(io.github.lnyocly.ai4j.model.ReasoningItem.of(reasoning));
            }
        }
        builder.responseId(JsonWire.textAt(root, "id"));
        builder.usage(decodeUsage(JsonWire.objectAt(root, "usage")));
        return builder.build();
    }

    private List<ModelItem> decodeMessage(Map<String, Object> message) {
        List<ModelItem> items = new ArrayList<ModelItem>();
        String role = JsonWire.textAt(message, "role");
        List<Block> blocks = new ArrayList<Block>();
        Object content = message.get("content");
        if (content instanceof String) {
            String text = (String) content;
            if (!text.isEmpty()) {
                blocks.add(TextBlock.of(text));
            }
        } else if (content instanceof List) {
            for (Object part : (List<?>) content) {
                if (part instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> partMap = (Map<String, Object>) part;
                    String text = JsonWire.textAt(partMap, "text");
                    if (text != null) {
                        blocks.add(TextBlock.of(text));
                    }
                }
            }
        }
        if (!blocks.isEmpty() || "assistant".equals(role)) {
            Message.Builder messageBuilder = Message.builder(roleOf(role));
            messageBuilder.blocks(blocks);
            items.add(messageBuilder.build());
        }
        for (Object call : JsonWire.arrayAt(message, "tool_calls")) {
            if (call instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> callMap = (Map<String, Object>) call;
                Map<String, Object> function = JsonWire.objectAt(callMap, "function");
                ToolCallValue value = ToolCallValue.of(
                        JsonWire.textAt(callMap, "id"),
                        JsonWire.textAt(function, "name"),
                        JsonWire.textAt(function, "arguments"));
                items.add(ToolCallItem.of(value, null, null,
                        Collections.singletonMap("chat_completions", callMap)));
            }
        }
        return items;
    }

    private MessageRole roleOf(String role) {
        if ("system".equals(role)) {
            return MessageRole.SYSTEM;
        }
        if ("assistant".equals(role)) {
            return MessageRole.ASSISTANT;
        }
        if ("tool".equals(role)) {
            return MessageRole.TOOL;
        }
        return MessageRole.USER;
    }

    private ModelUsage decodeUsage(Map<String, Object> usage) {
        if (usage.isEmpty()) {
            return null;
        }
        ModelUsage.Builder builder = ModelUsage.builder();
        Long prompt = JsonWire.longAt(usage, "prompt_tokens");
        Long completion = JsonWire.longAt(usage, "completion_tokens");
        Long total = JsonWire.longAt(usage, "total_tokens");
        if (prompt != null) {
            builder.inputTokens(prompt);
        }
        if (completion != null) {
            builder.outputTokens(completion);
        }
        if (total != null) {
            builder.totalTokens(total);
        }
        Map<String, Object> details = JsonWire.objectAt(usage, "completion_tokens_details");
        Long reasoning = JsonWire.longAt(details, "reasoning_tokens");
        if (reasoning != null) {
            builder.reasoningTokens(reasoning);
        }
        return builder.build();
    }

    @Override
    public ModelEvent decodeEvent(RawSseEvent event) {
        String data = event.getData();
        if (data == null || data.trim().isEmpty()) {
            return null;
        }
        Map<String, Object> root = JsonWire.readObject(data);
        if (root.isEmpty()) {
            return null;
        }
        List<Object> choices = JsonWire.arrayAt(root, "choices");
        Map<String, Object> usageMap = JsonWire.objectAt(root, "usage");
        if (choices.isEmpty()) {
            if (!usageMap.isEmpty()) {
                return ModelEvent.builder(getProtocol(), ModelEventType.USAGE)
                        .usage(decodeUsage(usageMap))
                        .build();
            }
            return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                    .itemType("chat_completions.chunk")
                    .build();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> choice = (Map<String, Object>) choices.get(0);
        Map<String, Object> delta = JsonWire.objectAt(choice, "delta");
        if (delta.isEmpty()) {
            Map<String, Object> message = JsonWire.objectAt(choice, "message");
            if (!message.isEmpty()) {
                ModelResult decoded = decode(WireResponse.of(getProtocol(), 200,
                        JsonWire.write(root)));
                return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DONE)
                        .item(decoded.getItems().isEmpty() ? null : decoded.getItems().get(0))
                        .build();
            }
            return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                    .itemType("chat_completions.chunk")
                    .build();
        }
        String text = JsonWire.textAt(delta, "content");
        if (text != null && !text.isEmpty()) {
            return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DELTA)
                    .delta(EventDeltaKind.TEXT, text)
                    .build();
        }
        String reasoning = JsonWire.textAt(delta, "reasoning_content");
        if (reasoning != null && !reasoning.isEmpty()) {
            return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DELTA)
                    .delta(EventDeltaKind.REASONING, reasoning)
                    .build();
        }
        List<Object> toolCalls = JsonWire.arrayAt(delta, "tool_calls");
        if (!toolCalls.isEmpty() && toolCalls.get(0) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> call = (Map<String, Object>) toolCalls.get(0);
            Map<String, Object> function = JsonWire.objectAt(call, "function");
            String arguments = JsonWire.textAt(function, "arguments");
            if (arguments == null) {
                arguments = "";
            }
            return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DELTA)
                    .delta(EventDeltaKind.TOOL_ARGS, arguments)
                    .itemId(JsonWire.textAt(call, "id"))
                    .itemType(JsonWire.textAt(function, "name"))
                    .contentIndex(JsonWire.intAt(call, "index"))
                    .build();
        }
        String finishReason = JsonWire.textAt(choice, "finish_reason");
        if (finishReason != null) {
            ModelEvent.Builder builder = ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DONE);
            if (!usageMap.isEmpty()) {
                builder.usage(decodeUsage(usageMap));
            }
            return builder.build();
        }
        return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                .itemType("chat_completions.chunk")
                .build();
    }

    private static List<ToolCallBlock> toolCallBlocksOf(List<Block> blocks) {
        List<ToolCallBlock> result = new ArrayList<ToolCallBlock>();
        for (Block block : blocks) {
            if (block instanceof ToolCallBlock) {
                result.add((ToolCallBlock) block);
            }
        }
        return result;
    }

    private static List<ToolResultBlock> toolResultBlocksOf(List<Block> blocks) {
        List<ToolResultBlock> result = new ArrayList<ToolResultBlock>();
        for (Block block : blocks) {
            if (block instanceof ToolResultBlock) {
                result.add((ToolResultBlock) block);
            }
        }
        return result;
    }

    private static List<OpaqueBlock> opaqueBlocksOf(List<Block> blocks) {
        List<OpaqueBlock> result = new ArrayList<OpaqueBlock>();
        for (Block block : blocks) {
            if (block instanceof OpaqueBlock) {
                result.add((OpaqueBlock) block);
            }
        }
        return result;
    }

    private static boolean hasNonTextNonImage(List<Block> blocks) {
        for (Block block : blocks) {
            if (!(block instanceof TextBlock) && !(block instanceof ImageBlock)
                    && !(block instanceof ToolCallBlock) && !(block instanceof ReasoningBlock)) {
                return true;
            }
        }
        return false;
    }

    private static ReasoningBlock firstReasoning(List<Block> blocks) {
        for (Block block : blocks) {
            if (block instanceof ReasoningBlock) {
                return (ReasoningBlock) block;
            }
        }
        return null;
    }

    private static String joinText(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (block instanceof TextBlock) {
                sb.append(((TextBlock) block).getText());
            }
        }
        return sb.toString();
    }

    private static String wireRole(MessageRole role) {
        switch (role) {
            case SYSTEM:
                return "system";
            case ASSISTANT:
                return "assistant";
            case TOOL:
                return "tool";
            default:
                return "user";
        }
    }
}
