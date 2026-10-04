package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicUsage;

/**
 * Typed Anthropic Messages stream event.
 *
 * <p>Keeps the native SSE {@code event:} vocabulary (message_start,
 * content_block_*, message_delta, message_stop, ping, error) and reuses the
 * native Anthropic entity DTOs. {@code message_stop} is the success terminal;
 * an {@code error} event carries the provider error in-band. An unmodelled
 * event keeps its raw payload with {@link #UNKNOWN_TYPE}.</p>
 */
public final class NativeMessagesStreamEvent implements NativeStreamEvent {

    public static final String TYPE_MESSAGE_START = "message_start";
    public static final String TYPE_CONTENT_BLOCK_START = "content_block_start";
    public static final String TYPE_CONTENT_BLOCK_DELTA = "content_block_delta";
    public static final String TYPE_CONTENT_BLOCK_STOP = "content_block_stop";
    public static final String TYPE_MESSAGE_DELTA = "message_delta";
    public static final String TYPE_MESSAGE_STOP = "message_stop";
    public static final String TYPE_PING = "ping";
    public static final String TYPE_ERROR = "error";

    private final String id;
    private final String type;
    private final AnthropicChatCompletionResponse message;
    private final AnthropicContentBlock contentBlock;
    private final AnthropicContentBlock delta;
    private final String partialJson;
    private final String signature;
    private final Integer index;
    private final String stopReason;
    private final AnthropicUsage usage;
    private final NativeStreamError error;
    private final boolean unknown;
    private final String rawData;

    private NativeMessagesStreamEvent(String id, String type, AnthropicChatCompletionResponse message,
                                      AnthropicContentBlock contentBlock, AnthropicContentBlock delta,
                                      String partialJson, String signature, Integer index,
                                      String stopReason, AnthropicUsage usage, NativeStreamError error,
                                      boolean unknown, String rawData) {
        this.id = id;
        this.type = type;
        this.message = message;
        this.contentBlock = contentBlock;
        this.delta = delta;
        this.partialJson = partialJson;
        this.signature = signature;
        this.index = index;
        this.stopReason = stopReason;
        this.usage = usage;
        this.error = error;
        this.unknown = unknown;
        this.rawData = rawData;
    }

    static NativeMessagesStreamEvent decode(String id, String sseType, String data) {
        String trimmed = data == null ? "" : data.trim();
        try {
            JsonNode root = NativeProtocolSupport.mapper().readTree(trimmed);
            if (root != null && root.isObject()) {
                String type = text(root, "type");
                if (type == null) {
                    type = sseType;
                }
                if (type == null) {
                    return unknown(id, data);
                }
                if (TYPE_MESSAGE_START.equals(type)) {
                    AnthropicChatCompletionResponse message = tree(root.path("message"),
                            AnthropicChatCompletionResponse.class);
                    AnthropicUsage usage = message == null ? null : message.getUsage();
                    return new NativeMessagesStreamEvent(id, type, message, null, null, null, null,
                            null, null, usage, null, false, data);
                }
                if (TYPE_CONTENT_BLOCK_START.equals(type)) {
                    return new NativeMessagesStreamEvent(id, type, null,
                            tree(root.path("content_block"), AnthropicContentBlock.class), null,
                            null, null, intOrNull(root, "index"), null, null, null, false, data);
                }
                if (TYPE_CONTENT_BLOCK_DELTA.equals(type)) {
                    JsonNode deltaNode = root.path("delta");
                    return new NativeMessagesStreamEvent(id, type, null, null,
                            tree(deltaNode, AnthropicContentBlock.class),
                            text(deltaNode, "partial_json"), text(deltaNode, "signature"),
                            intOrNull(root, "index"), null, null, null, false, data);
                }
                if (TYPE_CONTENT_BLOCK_STOP.equals(type)) {
                    return new NativeMessagesStreamEvent(id, type, null, null, null, null, null,
                            intOrNull(root, "index"), null, null, null, false, data);
                }
                if (TYPE_MESSAGE_DELTA.equals(type)) {
                    JsonNode deltaNode = root.path("delta");
                    return new NativeMessagesStreamEvent(id, type, null, null, null, null, null,
                            null, text(deltaNode, "stop_reason"),
                            tree(root.path("usage"), AnthropicUsage.class), null, false, data);
                }
                if (TYPE_MESSAGE_STOP.equals(type) || TYPE_PING.equals(type)) {
                    return new NativeMessagesStreamEvent(id, type, null, null, null, null, null,
                            null, null, null, null, false, data);
                }
                if (TYPE_ERROR.equals(type)) {
                    return new NativeMessagesStreamEvent(id, type, null, null, null, null, null,
                            null, null, null, NativeStreamError.fromAnthropicNode(root.path("error"), data),
                            false, data);
                }
            }
        } catch (java.io.IOException ignored) {
            // Unparsable payload: keep it as an explicit unknown event below.
        }
        return unknown(id, data);
    }

    private static NativeMessagesStreamEvent unknown(String id, String data) {
        return new NativeMessagesStreamEvent(id, UNKNOWN_TYPE, null, null, null, null, null, null,
                null, null, null, true, data);
    }

    private static <T> T tree(JsonNode node, Class<T> type) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return NativeProtocolSupport.mapper().treeToValue(node, type);
        } catch (java.io.IOException ignored) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || !value.isNumber() ? null : value.asInt();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public boolean isUnknown() {
        return unknown;
    }

    @Override
    public String getRawData() {
        return rawData;
    }

    @Override
    public boolean isTerminal() {
        return TYPE_MESSAGE_STOP.equals(type);
    }

    @Override
    public NativeStreamError getError() {
        return error;
    }

    public AnthropicChatCompletionResponse getMessage() {
        return message;
    }

    public AnthropicContentBlock getContentBlock() {
        return contentBlock;
    }

    public AnthropicContentBlock getDelta() {
        return delta;
    }

    /** {@code input_json_delta} tool-argument fragment. */
    public String getPartialJson() {
        return partialJson;
    }

    /** {@code signature_delta} thinking signature fragment. */
    public String getSignature() {
        return signature;
    }

    public Integer getIndex() {
        return index;
    }

    public String getStopReason() {
        return stopReason;
    }

    public AnthropicUsage getUsage() {
        return usage;
    }
}

