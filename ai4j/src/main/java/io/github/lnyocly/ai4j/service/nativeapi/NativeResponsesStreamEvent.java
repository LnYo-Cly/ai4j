package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseStreamEvent;

/**
 * Typed OpenAI Responses stream event.
 *
 * <p>Reuses the native {@link ResponseStreamEvent} DTO (type, sequence_number,
 * output_index, content_index, item_id, delta, error, ...). {@code
 * response.completed} is the success terminal; {@code response.failed},
 * {@code response.incomplete} and a bare {@code error} event carry the provider
 * error in-band. Unmodelled events keep their raw payload with {@link
 * #UNKNOWN_TYPE}.</p>
 */
public final class NativeResponsesStreamEvent implements NativeStreamEvent {

    public static final String TYPE_COMPLETED = "response.completed";
    public static final String TYPE_FAILED = "response.failed";
    public static final String TYPE_INCOMPLETE = "response.incomplete";
    public static final String TYPE_ERROR = "error";

    private final String id;
    private final String type;
    private final ResponseStreamEvent event;
    private final NativeStreamError error;
    private final boolean unknown;
    private final String rawData;

    private NativeResponsesStreamEvent(String id, String type, ResponseStreamEvent event,
                                       NativeStreamError error, boolean unknown, String rawData) {
        this.id = id;
        this.type = type;
        this.event = event;
        this.error = error;
        this.unknown = unknown;
        this.rawData = rawData;
    }

    static NativeResponsesStreamEvent decode(String id, String sseType, String data) {
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
                ResponseStreamEvent parsed = null;
                try {
                    parsed = NativeProtocolSupport.mapper().treeToValue(root, ResponseStreamEvent.class);
                } catch (java.io.IOException ignored) {
                    parsed = null;
                }
                NativeStreamError error = null;
                if (TYPE_FAILED.equals(type) || TYPE_INCOMPLETE.equals(type)) {
                    JsonNode errorNode = root.path("response").path("error");
                    if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                        error = NativeStreamError.fromResponsesNode(errorNode, data);
                    } else {
                        error = NativeStreamError.fromResponsesNode(root, data);
                    }
                } else if (TYPE_ERROR.equals(type)) {
                    error = NativeStreamError.fromResponsesNode(root, data);
                }
                boolean known = parsed != null
                        && (type.startsWith("response.") || TYPE_ERROR.equals(type));
                return new NativeResponsesStreamEvent(id, type, parsed, error, !known, data);
            }
        } catch (java.io.IOException ignored) {
            // Unparsable payload: keep it as an explicit unknown event below.
        }
        return unknown(id, data);
    }

    private static NativeResponsesStreamEvent unknown(String id, String data) {
        return new NativeResponsesStreamEvent(id, UNKNOWN_TYPE, null, null, true, data);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
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
        return TYPE_COMPLETED.equals(type);
    }

    @Override
    public NativeStreamError getError() {
        return error;
    }

    /** Parsed native event, or {@code null} for an unknown payload. */
    public ResponseStreamEvent getEvent() {
        return event;
    }
}


