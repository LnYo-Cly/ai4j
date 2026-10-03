package io.github.lnyocly.ai4j.protocol.responses;

import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.DroppedField;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.ImageBlock;
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
 * RESPONSES codec: OpenAI {@code /responses} wire shape.
 *
 * <p>Anchored on {@code OpenAiResponsesService} (flat function-tool shape,
 * {@code OPENAI_ALLOWED_FIELDS} whitelist, {@code response.*} SSE events) and
 * {@code ResponseEventParser} raw retention.</p>
 */
public final class ResponsesCodec implements ProtocolCodec {

    private static final java.util.Set<String> ALLOWED_PROVIDER_OPTIONS =
            Collections.unmodifiableSet(new java.util.HashSet<String>(java.util.Arrays.asList(
                    "text", "truncation", "user", "store", "include",
                    "max_tool_calls", "prompt_cache_key", "safety_identifier", "service_tier")));

    @Override
    public Protocol getProtocol() {
        return Protocol.RESPONSES;
    }

    @Override
    public WireRequest encode(ModelRequest request, EndpointPolicy policy) {
        List<DroppedField> warnings = new ArrayList<DroppedField>();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", request.getModel());

        List<Object> input = new ArrayList<Object>();
        List<Message> systemMessages = new ArrayList<Message>();
        for (ModelItem item : request.getItems()) {
            if (item instanceof Message && ((Message) item).getRole() == MessageRole.SYSTEM) {
                systemMessages.add((Message) item);
                continue;
            }
            if (item instanceof Message) {
                input.add(messageToWire((Message) item, warnings));
            } else if (item instanceof ToolCallItem) {
                input.add(toolCallToWire((ToolCallItem) item));
            } else if (item instanceof ToolResultItem) {
                input.add(toolResultToWire((ToolResultItem) item));
            } else if (item instanceof ReasoningItem) {
                input.add(reasoningToWire((ReasoningItem) item, warnings));
            } else if (item instanceof NativeItem) {
                NativeItem nativeItem = (NativeItem) item;
                if (nativeItem.getProtocol() == getProtocol()) {
                    input.add(new LinkedHashMap<String, Object>(nativeItem.getPayload()));
                } else {
                    warnings.add(DroppedField.of("items[native]",
                            "native item belongs to " + nativeItem.getProtocol()
                                    + " and is not replay-valid for RESPONSES"));
                }
            } else {
                warnings.add(DroppedField.of("items[" + item.getType() + "]",
                        "RESPONSES cannot express this top-level item kind"));
            }
        }
        body.put("input", input);

        if (!systemMessages.isEmpty()) {
            Message first = systemMessages.get(0);
            body.put("instructions", joinText(first.getBlocks()));
            for (int i = 1; i < systemMessages.size(); i++) {
                warnings.add(DroppedField.of("messages[" + i + "].system",
                        "only the first system message maps to Responses instructions;"
                                + " additional system messages are not flattened silently"));
            }
        }

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
        if (request.getMaxOutputTokens() != null) {
            body.put("max_output_tokens", request.getMaxOutputTokens());
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

    private Map<String, Object> messageToWire(Message message, List<DroppedField> warnings) {
        Map<String, Object> wire = new LinkedHashMap<String, Object>();
        wire.put("type", "message");
        wire.put("role", wireRole(message.getRole()));

        List<Block> blocks = message.getBlocks();
        List<Object> content = new ArrayList<Object>();
        for (Block block : blocks) {
            if (block instanceof TextBlock) {
                Map<String, Object> part = new LinkedHashMap<String, Object>();
                part.put("type", "input_text");
                part.put("text", ((TextBlock) block).getText());
                content.add(part);
            } else if (block instanceof ImageBlock) {
                Map<String, Object> part = CodecSupport.openAiImagePart((ImageBlock) block);
                if (part != null) {
                    part.put("type", "input_image");
                    content.add(part);
                } else {
                    warnings.add(DroppedField.of("message.content.image",
                            "image media identity cannot be expressed on RESPONSES"));
                }
            } else if (block instanceof ReasoningBlock) {
                warnings.add(DroppedField.of("message.block.reasoning",
                        "reasoning is encoded as a top-level reasoning item, not a message part"));
            } else if (block instanceof OpaqueBlock) {
                Map<String, Object> payload = new LinkedHashMap<String, Object>(
                        ((OpaqueBlock) block).getPayload());
                Object nativeProtocol = payload.get("_protocol");
                if ("RESPONSES".equals(nativeProtocol)) {
                    payload.remove("_protocol");
                    content.add(payload);
                } else {
                    warnings.add(DroppedField.of("message.block.opaque",
                            "opaque block is not replay-valid for RESPONSES"));
                }
            } else if (block instanceof ToolCallBlock || block instanceof ToolResultBlock) {
                warnings.add(DroppedField.of("message.block.tool",
                        "tool calls/results are encoded as top-level Responses items"));
            }
        }
        wire.put("content", content);
        return wire;
    }

    private Map<String, Object> toolCallToWire(ToolCallItem item) {
        Map<String, Object> wire = new LinkedHashMap<String, Object>();
        wire.put("type", "function_call");
        wire.put("call_id", item.getCallId());
        wire.put("name", item.getName());
        wire.put("arguments", item.getArgumentsJson());
        if (item.getItemId() != null) {
            wire.put("id", item.getItemId());
        }
        return wire;
    }

    private Map<String, Object> toolResultToWire(ToolResultItem item) {
        Map<String, Object> wire = new LinkedHashMap<String, Object>();
        wire.put("type", "function_call_output");
        wire.put("call_id", item.getCallId());
        wire.put("output", joinText(item.getValue().getContent()));
        return wire;
    }

    private Map<String, Object> reasoningToWire(ReasoningItem item, List<DroppedField> warnings) {
        Map<String, Object> wire = new LinkedHashMap<String, Object>();
        Map<String, Object> protocolData = item.getProtocolData();
        String encryptedContent = JsonWire.textAt(protocolData, "encrypted_content");
        if (encryptedContent == null && item.getItemId() != null) {
            encryptedContent = JsonWire.textAt(item.getProtocolData(), "response_id");
        }
        if (encryptedContent == null) {
            warnings.add(DroppedField.of("items[reasoning]",
                    "reasoning without provider replay handle is not replay-valid for RESPONSES"));
            return wire;
        }
        wire.put("type", "reasoning");
        wire.put("encrypted_content", encryptedContent);
        if (item.getItemId() != null) {
            wire.put("id", item.getItemId());
        }
        return wire;
    }

    private Map<String, Object> toolToWire(ToolDeclaration declaration, List<DroppedField> warnings) {
        if (declaration.getKind() == ToolKind.FUNCTION) {
            Map<String, Object> tool = new LinkedHashMap<String, Object>();
            tool.put("type", "function");
            tool.put("name", declaration.getName());
            if (declaration.getDescription() != null) {
                tool.put("description", declaration.getDescription());
            }
            tool.put("parameters", declaration.getParameters());
            if (declaration.getStrict() != null) {
                tool.put("strict", declaration.getStrict());
            }
            return tool;
        }
        if (declaration.getKind() == ToolKind.HOSTED) {
            Map<String, Object> spec = new LinkedHashMap<String, Object>(declaration.getNativeSpec());
            if (spec.get("type") == null) {
                warnings.add(DroppedField.of("tools[" + declaration.getName() + "]",
                        "hosted tool declaration requires a native type"));
                return null;
            }
            return spec;
        }
        if (declaration.getKind() == ToolKind.NATIVE) {
            Map<String, Object> spec = new LinkedHashMap<String, Object>(declaration.getNativeSpec());
            if (spec.get("type") != null) {
                return spec;
            }
            warnings.add(DroppedField.of("tools[" + declaration.getName() + "]",
                    "native tool declaration is not replay-valid for RESPONSES"));
            return null;
        }
        warnings.add(DroppedField.of("tools[" + declaration.getName() + "]",
                declaration.getKind() + " tools have no RESPONSES shape"));
        return null;
    }

    private Object toolChoiceToWire(ToolChoice choice, List<DroppedField> warnings) {
        if (choice.isFunctionChoice()) {
            Map<String, Object> wire = new LinkedHashMap<String, Object>();
            wire.put("type", "function");
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
                        "not in the RESPONSES wire whitelist"));
            }
        }
    }

    @Override
    public ModelResult decode(WireResponse response) {
        Map<String, Object> root = JsonWire.readObject(response.getBodyText());
        ModelResult.Builder builder = ModelResult.builder();
        builder.raw(root);
        builder.responseId(JsonWire.textAt(root, "id"));
        builder.stopReason(JsonWire.textAt(root, "status"));
        builder.usage(decodeUsage(JsonWire.objectAt(root, "usage")));
        for (Object output : JsonWire.arrayAt(root, "output")) {
            if (output instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>) output;
                ModelItem item = decodeOutputItem(itemMap);
                if (item != null) {
                    builder.item(item);
                }
            }
        }
        return builder.build();
    }

    private ModelItem decodeOutputItem(Map<String, Object> itemMap) {
        String type = JsonWire.textAt(itemMap, "type");
        if (type == null) {
            return null;
        }
        if ("message".equals(type)) {
            Message.Builder message = Message.builder(roleOf(JsonWire.textAt(itemMap, "role")));
            message.itemId(JsonWire.textAt(itemMap, "id"));
            for (Object rawPart : JsonWire.arrayAt(itemMap, "content")) {
                if (rawPart instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> part = (Map<String, Object>) rawPart;
                    String text = JsonWire.textAt(part, "text");
                    if (text != null) {
                        message.block(TextBlock.of(text));
                    }
                }
            }
            return message.build();
        }
        if ("function_call".equals(type)) {
            ToolCallValue value = ToolCallValue.of(
                    JsonWire.textAt(itemMap, "call_id"),
                    JsonWire.textAt(itemMap, "name"),
                    JsonWire.textAt(itemMap, "arguments"));
            return ToolCallItem.of(value, JsonWire.textAt(itemMap, "id"), null,
                    Collections.singletonMap("responses", itemMap));
        }
        if ("function_call_output".equals(type)) {
            return ToolResultItem.of(io.github.lnyocly.ai4j.model.ToolResultValue.text(
                    JsonWire.textAt(itemMap, "call_id"), JsonWire.textAt(itemMap, "output")));
        }
        if ("reasoning".equals(type)) {
            StringBuilder summaryText = new StringBuilder();
            for (Object rawSummary : JsonWire.arrayAt(itemMap, "summary")) {
                if (rawSummary instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> summary = (Map<String, Object>) rawSummary;
                    String text = JsonWire.textAt(summary, "text");
                    if (text != null) {
                        summaryText.append(text);
                    }
                }
            }
            return ReasoningItem.of(null, summaryText.toString(),
                    JsonWire.textAt(itemMap, "encrypted_content"),
                    JsonWire.textAt(itemMap, "id"),
                    null, Collections.singletonMap("responses", itemMap));
        }
        return NativeItem.of(getProtocol(), itemMap, JsonWire.textAt(itemMap, "id"), null, null);
    }

    private MessageRole roleOf(String role) {
        if ("system".equals(role)) {
            return MessageRole.SYSTEM;
        }
        if ("assistant".equals(role)) {
            return MessageRole.ASSISTANT;
        }
        return MessageRole.USER;
    }

    private ModelUsage decodeUsage(Map<String, Object> usage) {
        if (usage.isEmpty()) {
            return null;
        }
        ModelUsage.Builder builder = ModelUsage.builder();
        Long input = JsonWire.longAt(usage, "input_tokens");
        Long output = JsonWire.longAt(usage, "output_tokens");
        Long total = JsonWire.longAt(usage, "total_tokens");
        if (input != null) {
            builder.inputTokens(input);
        }
        if (output != null) {
            builder.outputTokens(output);
        }
        if (total != null) {
            builder.totalTokens(total);
        }
        Long cached = JsonWire.longAt(JsonWire.objectAt(usage, "input_tokens_details"), "cached_tokens");
        if (cached != null) {
            builder.cachedInputTokens(cached);
        }
        Long reasoning = JsonWire.longAt(JsonWire.objectAt(usage, "output_tokens_details"), "reasoning_tokens");
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
        String type = JsonWire.textAt(root, "type");
        if (type == null) {
            return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                    .itemType(event.getType())
                    .build();
        }
        ModelEvent.Builder builder = ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DELTA);
        builder.sequenceNumber(JsonWire.longAt(root, "sequence_number"))
                .outputIndex(JsonWire.intAt(root, "output_index"))
                .contentIndex(JsonWire.intAt(root, "content_index"))
                .itemId(JsonWire.textAt(root, "item_id"));

        if ("response.created".equals(type)) {
            return ModelEvent.builder(getProtocol(), ModelEventType.RUN_START)
                    .sequenceNumber(JsonWire.longAt(root, "sequence_number"))
                    .build();
        }
        if ("response.output_item.added".equals(type)) {
            Map<String, Object> item = JsonWire.objectAt(root, "item");
            String itemId = JsonWire.textAt(root, "item_id");
            if (itemId == null) {
                itemId = JsonWire.textAt(item, "id");
            }
            return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_START)
                    .itemId(itemId)
                    .outputIndex(JsonWire.intAt(root, "output_index"))
                    .itemType(JsonWire.textAt(item, "type"))
                    .toolName(JsonWire.textAt(item, "name"))
                    .build();
        }
        if ("response.output_text.delta".equals(type)) {
            return builder.delta(EventDeltaKind.TEXT, JsonWire.textAt(root, "delta")).build();
        }
        if ("response.reasoning_summary_text.delta".equals(type)
                || "response.reasoning_text.delta".equals(type)) {
            return builder.delta(EventDeltaKind.REASONING, JsonWire.textAt(root, "delta")).build();
        }
        if ("response.function_call_arguments.delta".equals(type)) {
            return builder.delta(EventDeltaKind.TOOL_ARGS, JsonWire.textAt(root, "delta"))
                    .itemId(JsonWire.textAt(root, "item_id"))
                    .build();
        }
        if ("response.output_text.done".equals(type)) {
            return ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DONE)
                    .itemId(JsonWire.textAt(root, "item_id"))
                    .outputIndex(JsonWire.intAt(root, "output_index"))
                    .contentIndex(JsonWire.intAt(root, "content_index"))
                    .build();
        }
        if ("response.output_item.done".equals(type)) {
            Map<String, Object> item = JsonWire.objectAt(root, "item");
            ModelEvent.Builder done = ModelEvent.builder(getProtocol(), ModelEventType.ITEM_DONE)
                    .itemId(JsonWire.textAt(root, "item_id"))
                    .outputIndex(JsonWire.intAt(root, "output_index"))
                    .contentIndex(JsonWire.intAt(root, "content_index"));
            ModelItem decoded = decodeOutputItem(item);
            if (decoded != null) {
                done.item(decoded);
            }
            return done.build();
        }
        if ("response.completed".equals(type)) {
            Map<String, Object> response = JsonWire.objectAt(root, "response");
            ModelUsage usage = decodeUsage(JsonWire.objectAt(response, "usage"));
            return ModelEvent.builder(getProtocol(), ModelEventType.DONE)
                    .usage(usage)
                    .build();
        }
        if ("response.failed".equals(type) || "response.incomplete".equals(type)) {
            Map<String, Object> response = JsonWire.objectAt(root, "response");
            Map<String, Object> error = JsonWire.objectAt(response, "error");
            String message = JsonWire.textAt(error, "message");
            return ModelEvent.builder(getProtocol(), ModelEventType.ERROR)
                    .failure(io.github.lnyocly.ai4j.model.ModelCallExceptionMarker.builder(getProtocol())
                            .message(message == null ? type : message)
                            .providerErrorType(JsonWire.textAt(error, "type"))
                            .status(io.github.lnyocly.ai4j.model.ModelCallStatus.FAILED)
                            .build())
                    .build();
        }
        if (type.startsWith("response.")) {
            return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                    .itemType(type)
                    .sequenceNumber(JsonWire.longAt(root, "sequence_number"))
                    .build();
        }
        return ModelEvent.builder(getProtocol(), ModelEventType.OPAQUE)
                .itemType(type)
                .build();
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
            default:
                return "user";
        }
    }
}
