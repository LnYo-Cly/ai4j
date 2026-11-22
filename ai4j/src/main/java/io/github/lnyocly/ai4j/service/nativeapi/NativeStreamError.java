package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.platform.anthropic.errors.AnthropicApiException;

/**
 * A protocol error delivered in-band on an already-established SSE stream.
 *
 * <p>The HTTP exchange succeeded (the stream response was 2xx), so this is not
 * an HTTP error: it is the model/protocol reporting a failure as an event. The
 * protocol error fields are kept verbatim so a caller never has to reparse the
 * raw JSON to learn what happened.</p>
 */
public final class NativeStreamError {

    private static final int MAX_RAW_CHARS = 500;

    private final Protocol protocol;
    private final int httpStatus;
    private final String type;
    private final String code;
    private final String message;
    private final String requestId;
    private final String rawData;

    private NativeStreamError(Protocol protocol, int httpStatus, String type, String code,
                              String message, String requestId, String rawData) {
        this.protocol = protocol;
        this.httpStatus = httpStatus;
        this.type = type;
        this.code = code;
        this.message = message;
        this.requestId = requestId;
        this.rawData = rawData;
    }

    /** Anthropic {@code {"type":"error","error":{"type":..,"message":..}}} event. */
    static NativeStreamError fromAnthropicNode(JsonNode errorNode, String rawData) {
        String type = text(errorNode, "type");
        String message = text(errorNode, "message");
        return new NativeStreamError(Protocol.MESSAGES, 200, type, null, message, null, rawData);
    }

    /** OpenAI chat in-band {@code {"error":{..}}} chunk. */
    static NativeStreamError fromOpenAiNode(JsonNode errorNode, JsonNode root, String rawData) {
        String type = text(errorNode, "type");
        String code = text(errorNode, "code");
        String message = text(errorNode, "message");
        if (message == null) {
            message = text(root, "message");
        }
        return new NativeStreamError(Protocol.CHAT_COMPLETIONS, 200, type, code, message, null, rawData);
    }

    /** OpenAI Responses {@code response.failed}/{@code response.incomplete} event. */
    static NativeStreamError fromResponsesNode(JsonNode errorNode, String rawData) {
        String type = text(errorNode, "type");
        String code = text(errorNode, "code");
        String message = text(errorNode, "message");
        return new NativeStreamError(Protocol.RESPONSES, 200, type, code, message, null, rawData);
    }

    public Protocol getProtocol() {
        return protocol;
    }

    /** HTTP status of the stream response that carried the error (2xx). */
    public int getHttpStatus() {
        return httpStatus;
    }

    public String getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRawData() {
        return rawData;
    }

    /** Human-readable message, never {@code null}. */
    public String describe() {
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        if (code != null && !code.trim().isEmpty()) {
            return code;
        }
        if (type != null && !type.trim().isEmpty()) {
            return type;
        }
        return "native stream reported an in-band " + protocol + " error";
    }

    /**
     * Convert to the protocol's typed runtime exception.
     *
     * <p>Anthropic keeps its native {@link AnthropicApiException} hierarchy so the
     * in-band error is catchable exactly like the HTTP error path; the OpenAI
     * protocols raise {@link NativeStreamException} carrying the same fields.</p>
     */
    public RuntimeException toException() {
        if (protocol == Protocol.MESSAGES) {
            return AnthropicApiException.of(httpStatus, type, describe(), requestId);
        }
        return new NativeStreamException(protocol, NativeStreamException.KIND_IN_BAND_ERROR,
                type, code, describe(), requestId, rawData);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return text.length() <= MAX_RAW_CHARS ? text : text.substring(0, MAX_RAW_CHARS) + "... (truncated)";
    }
}
