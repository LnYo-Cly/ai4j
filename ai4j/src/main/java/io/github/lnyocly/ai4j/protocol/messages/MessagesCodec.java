package io.github.lnyocly.ai4j.protocol.messages;

import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.DocumentBlock;
import io.github.lnyocly.ai4j.model.DroppedField;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.ImageBlock;
import io.github.lnyocly.ai4j.model.MediaKind;
import io.github.lnyocly.ai4j.model.MediaRef;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.MessageRole;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.NativeItem;
import io.github.lnyocly.ai4j.model.OpaqueBlock;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningBlock;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.model.ToolCallBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolCallValue;
import io.github.lnyocly.ai4j.model.ToolChoice;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.model.ToolKind;
import io.github.lnyocly.ai4j.model.ToolResultBlock;
import io.github.lnyocly.ai4j.model.ToolResultItem;
import io.github.lnyocly.ai4j.model.ToolResultValue;
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
 * MESSAGES codec: Anthropic {@code /v1/messages} wire shape.
 *
 * <p>Anchored on {@code AnthropicMessagesService} and
 * {@code AnthropicContentBlock}. Closes design 5.3 gap O5 by consuming
 * {@code signature_delta} and preserving the thinking signature in
 * {@link ReasoningBlock#getSignature()} / {@link ReasoningItem#getSignature()},
 * and keeps unknown blocks as {@code OpaqueBlock} rather than dropping them.</p>
 */
public final class MessagesCodec implements ProtocolCodec {

    private static final java.util.Set<String> ALLOWED_PROVIDER_OPTIONS =
            Collections.unmodifiableSet(new java.util.HashSet<String>(java.util.Arrays.asList(
                    "thinking", "top_k", "metadata", "service_tier", "container", "mcp_servers")));

    @Override
    public Protocol getProtocol() {
        return Protocol.MESSAGES;
    }

    @Override
    public WireRequest encode(ModelRequest request, EndpointPolicy policy) {
        List<DroppedField> warnings = new ArrayList<DroppedField>();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", request.getModel());

        List<Object> messages = new ArrayList<Object>();
        List<Object> systemBlocks = new ArrayList<Object>();
        for (ModelItem item : request.getItems()) {
            if (item instanceof Message && ((Message) item).getRole() == MessageRole.SYSTEM) {
                systemBlocks.addAll(messageBlocksToWire((Message) item, warnings));
                continue;
            }
            if (item instanceof Message) {
                Map<String, Object> wire = new LinkedHashMap<String, Object>();
                wire.put("role", wireRole(((Message) item).getRole()));
                wire.put("content", messageBlocksToWire((Message) item, warnings));
                messages.add(wire);
            } else if (item instanceof ToolCallItem) {
                messages.add(assistantToolCallToWire((ToolCallItem) item));
            } else if (item instanceof ToolResultItem) {
                messages.add(userToolResultToWire((ToolResultItem) item));
            } else if (item instanceof ReasoningItem) {
                Map<String, Object> reasoning = assistantReasoningToWire((ReasoningItem) item, warnings);
                if (reasoning != null) {
                    messages.add(reasoning);
                }
            } else if (item instanceof NativeItem) {
                NativeItem nativeItem = (NativeItem) item;
                if (nativeItem.getProtocol() == getProtocol() && !nativeItem.getPayload().isEmpty()) {
                    Map<String, Object> wire = new LinkedHashMap<String, Object>(
                            nativeItem.getPayload());
                    messages.add(wire);
                } else if (nativeItem.getProtocol() == getProtocol()) {
                    warnings.add(DroppedField.of("items[native]",
                            "native item payload is empty and would emit an empty object"));
                } else {
                    warnings.add(DroppedField.of("items[native]",
                            "native item belongs to " + nativeItem.getProtocol()
                                    + " and is not replay-valid for MESSAGES"));
                }
            } else {
                warnings.add(DroppedField.of("items[" + item.getType() + "]",
                        "MESSAGES cannot express this top-level item kind"));
            }
        }
        if (!systemBlocks.isEmpty()) {
            body.put("system", systemBlocks);
        }
        body.put("messages", messages);

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
        if (request.getMaxOutputTokens() != null) {
            body.put("max_tokens", request.getMaxOutputTokens());
        }
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.put("top_p", request.getTopP());
        }
        body.put("stream", request.getStream() != null && request.getStream());
        applyProviderOptions(request, policy, body, warnings);

        return WireRequest.of(getProtocol(), "POST", policy.getPath(), body, null,
                Boolean.TRUE.equals(request.getStream()), warnings);
    }

    private List<Object> messageBlocksToWire(Message message, List<DroppedField> warnings) {
        List<Object> blocks = new ArrayList<Object>();
        for (Block block : message.getBlocks()) {
            Map<String, Object> wire = blockToWire(block, warnings);
            if (wire != null) {
                blocks.add(wire);
            }
        }
        return blocks;
    }

    private Map<String, Object> blockToWire(Block block, List<DroppedField> warnings) {
        if (block instanceof TextBlock) {
            Map<String, Object> wire = new LinkedHashMap<String, Object>();
            wire.put("type", "text");
            wire.put("text", ((TextBlock) block).getText());
            applyProtocolFields(wire, block.getProtocolData(), "cache_control");
            return wire;
        }
        if (block instanceof ImageBlock) {
            return mediaBlockToWire("image", ((ImageBlock) block).getMedia(), warnings);
        }
        if (block instanceof DocumentBlock) {
            DocumentBlock document = (DocumentBlock) block;
            return mediaBlockToWire("document", document.getMedia(), warnings);
        }
        if (block instanceof ReasoningBlock) {
            ReasoningBlock reasoning = (ReasoningBlock) block;
            if (reasoning.getSourceProtocol() != null && reasoning.getSourceProtocol() != Protocol.MESSAGES) {
                warnings.add(DroppedField.of("message.block.reasoning",
                        "foreign reasoning signature from " + reasoning.getSourceProtocol()
                                + " is not replay-valid for MESSAGES"));
                return null;
            }
            Map<String, Object> wire = new LinkedHashMap<String, Object>();
            if (reasoning.getSignature() == null) {
                warnings.add(DroppedField.of("message.block.reasoning",
                        "unsigned reasoning cannot be replayed to MESSAGES (design F4)"));
                return null;
            }
            wire.put("type", "thinking");
            wire.put("thinking", reasoning.getText());
            wire.put("signature", reasoning.getSignature());
            applyProtocolFields(wire, reasoning.getProtocolData(), "cache_control");
            return wire;
        }
        if (block instanceof ToolCallBlock) {
            ToolCallValue value = ((ToolCallBlock) block).getValue();
            Map<String, Object> wire = new LinkedHashMap<String, Object>();
            wire.put("type", "tool_use");
            wire.put("id", value.getCallId());
            wire.put("name", value.getName());
            wire.put("input", parseArguments(value.getArgumentsJson()));
            applyProtocolFields(wire, value.getProtocolData(), "cache_control");
            return wire;
        }
        if (block instanceof ToolResultBlock) {
            ToolResultValue value = ((ToolResultBlock) block).getValue();
            Map<String, Object> wire = new LinkedHashMap<String, Object>();
            wire.put("type", "tool_result");
            wire.put("tool_use_id", value.getCallId());
            wire.put("content", messageBlocksToWire(
                    Message.user().blocks(value.getContent()).build(), warnings));
            if (value.isError()) {
                wire.put("is_error", Boolean.TRUE);
            }
            return wire;
        }
        OpaqueBlock opaque = (OpaqueBlock) block;
        Map<String, Object> payload = new LinkedHashMap<String, Object>(opaque.getPayload());
        if ("MESSAGES".equals(payload.get("_protocol"))) {
            payload.remove("_protocol");
            return payload;
        }
        warnings.add(DroppedField.of("message.block.opaque",
                "opaque block of type " + opaque.getNativeType()
                        + " is not replay-valid for MESSAGES"));
        return null;
    }

    private Map<String, Object> mediaBlockToWire(String type, MediaRef media,
                                                 List<DroppedField> warnings) {
        Map<String, Object> wire = new LinkedHashMap<String, Object>();
        wire.put("type", type);
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        if (media.getKind() == MediaKind.URL) {
            source.put("type", "url");
            source.put("url", media.getValue());
        } else if (media.getKind() == MediaKind.INLINE_BASE64) {
            source.put("type", "base64");
            source.put("media_type", media.getMediaType());
            source.put("data", media.getValue());
        } else {
            source.put("type", "file");
            source.put("file_id", media.getValue());
        }
        wire.put("source", source);
        return wire;
    }

    private Map<String, Object> assistantToolCallToWire(ToolCallItem item) {
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        List<Object> blocks = new ArrayList<Object>();
        Map<String, Object> toolUse = new LinkedHashMap<String, Object>();
        toolUse.put("type", "tool_use");
        toolUse.put("id", item.getCallId());
        toolUse.put("name", item.getName());
        toolUse.put("input", parseArguments(item.getArgumentsJson()));
        blocks.add(toolUse);
        message.put("content", blocks);
        return message;
    }

    private Map<String, Object> userToolResultToWire(ToolResultItem item) {
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", "user");
        List<Object> blocks = new ArrayList<Object>();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("type", "tool_result");
        result.put("tool_use_id", item.getCallId());
        result.put("content", messageBlocksToWire(
                Message.user().blocks(item.getValue().getContent()).build(),
                new ArrayList<DroppedField>()));
        if (item.isError()) {
            result.put("is_error", Boolean.TRUE);
        }
        blocks.add(result);
        message.put("content", blocks);
        return message;
    }

    private Map<String, Object> assistantReasoningToWire(ReasoningItem item,
                                                         List<DroppedField> warnings) {
        if (item.getSourceProtocol() != null && item.getSourceProtocol() != Protocol.MESSAGES) {
            warnings.add(DroppedField.of("items[reasoning]",
                    "foreign reasoning signature from " + item.getSourceProtocol()
                            + " is not replay-valid for MESSAGES"));
            return null;
        }
        if (item.getSignature() == null) {
            warnings.add(DroppedField.of("items[reasoning]",
                    "unsigned reasoning cannot be replayed to MESSAGES (design F4)"));
            return null;
        }
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        List<Object> blocks = new ArrayList<Object>();
        Map<String, Object> thinking = new LinkedHashMap<String, Object>();
        thinking.put("type", "thinking");
        thinking.put("thinking", item.getText() == null ? "" : item.getText());
        thinking.put("signature", item.getSignature());
        blocks.add(thinking);
        message.put("content", blocks);
        return message;
    }

    private Map<String, Object> toolToWire(ToolDeclaration declaration, List<DroppedField> warnings) {
        if (declaration.getKind() == ToolKind.FUNCTION) {
            Map<String, Object> tool = new LinkedHashMap<String, Object>();
            tool.put("name", declaration.getName());
            if (declaration.getDescription() != null) {
                tool.put("description", declaration.getDescription());
            }
            tool.put("input_schema", declaration.getParameters());
            boolean deferred = declaration.getVisibility()
                    == io.github.lnyocly.ai4j.model.ToolVisibility.DEFERRED;
            if (deferred) {
                tool.put("defer_loading", Boolean.TRUE);
            }
            applyProtocolFields(tool, declaration.getNativeSpec(), "cache_control");
            return tool;
        }
        if (declaration.getKind() == ToolKind.HOSTED || declaration.getKind() == ToolKind.NATIVE
                || declaration.getKind() == ToolKind.DEFERRED_REF) {
            Map<String, Object> spec = new LinkedHashMap<String, Object>(declaration.getNativeSpec());
            if (spec.get("type") == null && spec.get("input_schema") == null) {
                warnings.add(DroppedField.of("tools[" + declaration.getName() + "]",
                        "MESSAGES hosted/native tool declaration requires a type prefix"));
                return null;
            }
            return spec;
        }
        warnings.add(DroppedField.of("tools[" + declaration.getName() + "]",
                declaration.getKind() + " tools have no MESSAGES shape"));
        return null;
    }

    private Object toolChoiceToWire(ToolChoice choice, List<DroppedField> warnings) {
        if (choice.isFunctionChoice()) {
            Map<String, Object> wire = new LinkedHashMap<String, Object>();
            wire.put("type", "tool");
            wire.put("name", choice.getFunctionName());
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
                return Collections.singletonMap("type", "auto");
            case NONE:
                return Collections.singletonMap("type", "none");
            case REQUIRED:
                return Collections.singletonMap("type", "any");
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
                        "not in the MESSAGES wire whitelist"));
            }
        }
    }

    @Override
    public ModelResult decode(WireResponse response) {
        Map<String, Object> root = JsonWire.readObject(response.getBodyText());
        ModelResult.Builder builder = ModelResult.builder();
        builder.raw(root);
        builder.responseId(JsonWire.textAt(root, "id"));
        builder.stopReason(JsonWire.textAt(root, "stop_reason"));
        builder.usage(decodeUsage(JsonWire.objectAt(root, "usage")));
        for (Object rawBlock : JsonWire.arrayAt(root, "content")) {
            if (rawBlock instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> block = (Map<String, Object>) rawBlock;
                ModelItem item = decodeContentBlock(block);
                if (item != null) {
                    builder.item(item);
                }
            }
        }
        return builder.build();
    }

    private ModelItem decodeContentBlock(Map<String, Object> block) {
        String type = JsonWire.textAt(block, "type");
        if (type == null) {
            return null;
        }
        if ("text".equals(type)) {
            return Message.assistant()
                    .block(TextBlock.of(JsonWire.textAt(block, "text") == null
                            ? "" : JsonWire.textAt(block, "text")))
                    .build();
        }
        if ("thinking".equals(type)) {
            return ReasoningItem.of(JsonWire.textAt(block, "thinking"),
                    JsonWire.textAt(block, "signature"), Protocol.MESSAGES);
        }
        if ("tool_use".equals(type)) {
            ToolCallValue value = ToolCallValue.of(
                    JsonWire.textAt(block, "id"),
                    JsonWire.textAt(block, "name"),
                    JsonWire.write(JsonWire.objectAt(block, "input")));
            return ToolCallItem.of(value, null, null,
                    Collections.singletonMap("messages", block));
        }
        return NativeItem.of(getProtocol(), block);
    }

    private ModelUsage decodeUsage(Map<String, Object> usage) {
        if (usage.isEmpty()) {
            return null;
        }
        ModelUsage.Builder builder = ModelUsage.builder();
        Long input = JsonWire.longAt(usage, "input_tokens");
        Long output = JsonWire.longAt(usage, "output_tokens");
        if (input != null) {
            builder.inputTokens(input);
        }
        if (output != null) {
            builder.outputTokens(output);
        }
        Long cacheRead = JsonWire.longAt(usage, "cache_read_input_tokens");
        if (cacheRead != null) {
            builder.cachedInputTokens(cacheRead);
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
        String type = JsonWire.textAt(root, "type");
        if (type == null) {
            return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                    .itemType(event.getType())
                    .build();
        }
        if ("message_start".equals(type)) {
            Map<String, Object> message = JsonWire.objectAt(root, "message");
            return ModelEvent.builder(getProtocol(), ModelEventType.MESSAGE_START)
                    .itemId(JsonWire.textAt(message, "id"))
                    .role(JsonWire.textAt(message, "role"))
                    .usage(decodeUsage(JsonWire.objectAt(message, "usage")))
                    .build();
        }
        if ("content_block_start".equals(type)) {
            Map<String, Object> block = JsonWire.objectAt(root, "content_block");
            return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_START)
                    .contentIndex(JsonWire.intAt(root, "index"))
                    .itemType(JsonWire.textAt(block, "type"))
                    .toolName(JsonWire.textAt(block, "name"))
                    .itemId(JsonWire.textAt(block, "id"))
                    .build();
        }
        if ("content_block_delta".equals(type)) {
            Map<String, Object> delta = JsonWire.objectAt(root, "delta");
            String deltaType = JsonWire.textAt(delta, "type");
            ModelEvent.Builder builder = ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DELTA)
                    .contentIndex(JsonWire.intAt(root, "index"));
            if ("text_delta".equals(deltaType)) {
                return builder.delta(EventDeltaKind.TEXT, JsonWire.textAt(delta, "text")).build();
            }
            if ("thinking_delta".equals(deltaType)) {
                return builder.delta(EventDeltaKind.REASONING, JsonWire.textAt(delta, "thinking")).build();
            }
            if ("signature_delta".equals(deltaType)) {
                return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DELTA)
                        .contentIndex(JsonWire.intAt(root, "index"))
                        .delta(EventDeltaKind.OTHER, JsonWire.textAt(delta, "signature"))
                        .itemType("signature_delta")
                        .build();
            }
            if ("input_json_delta".equals(deltaType)) {
                return builder.delta(EventDeltaKind.TOOL_ARGS, JsonWire.textAt(delta, "partial_json")).build();
            }
            return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                    .itemType(deltaType)
                    .contentIndex(JsonWire.intAt(root, "index"))
                    .build();
        }
        if ("content_block_stop".equals(type)) {
            return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DONE)
                    .contentIndex(JsonWire.intAt(root, "index"))
                    .build();
        }
        if ("message_delta".equals(type)) {
            Map<String, Object> delta = JsonWire.objectAt(root, "delta");
            return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DELTA)
                    .itemType("message_delta")
                    .delta(EventDeltaKind.OTHER, JsonWire.textAt(delta, "stop_reason"))
                    .stopReason(JsonWire.textAt(delta, "stop_reason"))
                    .usage(decodeUsage(JsonWire.objectAt(root, "usage")))
                    .build();
        }
        if ("message_stop".equals(type)) {
            return ModelEvent.builder(getProtocol(), ModelEventType.DONE).build();
        }
        if ("error".equals(type)) {
            Map<String, Object> error = JsonWire.objectAt(root, "error");
            return ModelEvent.builder(getProtocol(), ModelEventType.ERROR)
                    .failure(io.github.lnyocly.ai4j.model.ModelCallExceptionMarker.builder(getProtocol())
                            .message(JsonWire.textAt(error, "message"))
                            .providerErrorType(JsonWire.textAt(error, "type"))
                            .build())
                    .build();
        }
        if ("ping".equals(type)) {
            return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                    .itemType("ping")
                    .build();
        }
        return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                .itemType(type)
                .build();
    }

    private static Object parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Object value = JsonWire.readValue(argumentsJson);
            return value == null ? new LinkedHashMap<String, Object>() : value;
        } catch (IllegalArgumentException e) {
            return new LinkedHashMap<String, Object>();
        }
    }

    private static void applyProtocolFields(Map<String, Object> wire, Map<String, Object> protocolData,
                                            String... keys) {
        if (protocolData.isEmpty()) {
            return;
        }
        for (String key : keys) {
            Object value = protocolData.get(key);
            if (value != null) {
                wire.put(key, value);
            }
        }
    }

    private static String wireRole(MessageRole role) {
        return role == MessageRole.ASSISTANT ? "assistant" : "user";
    }
}
