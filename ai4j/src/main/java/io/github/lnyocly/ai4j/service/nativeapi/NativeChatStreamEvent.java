package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;

/**
 * Typed OpenAI Chat Completions stream event.
 *
 * <p>Reuses the native {@link ChatCompletionResponse} chunk DTO, so the delta
 * role/content/tool-calls/reasoning and the per-choice {@code finish_reason}
 * stay exactly as the wire carried them. The {@code [DONE]} sentinel, an
 * in-band {@code {"error":..}} payload and any unmodelled payload each get an
 * explicit type instead of collapsing into a bare string.</p>
 */
public final class NativeChatStreamEvent implements NativeStreamEvent {

    /** A parsed {@code chat.completion.chunk} (or the payload's own {@code object} value). */
    public static final String TYPE_CHUNK = "chat.completion.chunk";
    /** The {@code data: [DONE]} sentinel. */
    public static final String TYPE_DONE = "done";
    /** An in-band {@code {"error":..}} payload. */
    public static final String TYPE_ERROR = "error";

    private final String id;
    private final String type;
    private final ChatCompletionResponse chunk;
    private final NativeStreamError error;
    private final boolean unknown;
    private final String rawData;

    private NativeChatStreamEvent(String id, String type, ChatCompletionResponse chunk,
                                  NativeStreamError error, boolean unknown, String rawData) {
        this.id = id;
        this.type = type;
        this.chunk = chunk;
        this.error = error;
        this.unknown = unknown;
        this.rawData = rawData;
    }

    static NativeChatStreamEvent decode(String id, String sseType, String data) {
        String trimmed = data == null ? "" : data.trim();
        if ("[DONE]".equals(trimmed)) {
            return new NativeChatStreamEvent(id, TYPE_DONE, null, null, false, data);
        }
        try {
            JsonNode root = NativeProtocolSupport.mapper().readTree(trimmed);
            if (root != null && root.isObject()) {
                JsonNode errorNode = root.path("error");
                if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                    return new NativeChatStreamEvent(id, TYPE_ERROR, null,
                            NativeStreamError.fromOpenAiNode(errorNode, root, data), false, data);
                }
                ChatCompletionResponse chunk =
                        NativeProtocolSupport.mapper().treeToValue(root, ChatCompletionResponse.class);
                if (chunk != null && chunk.getChoices() != null) {
                    String type = chunk.getObject() == null ? TYPE_CHUNK : chunk.getObject();
                    return new NativeChatStreamEvent(id, type, chunk, null, false, data);
                }
            }
        } catch (java.io.IOException ignored) {
            // Unparsable payload: keep it as an explicit unknown event below.
        }
        return new NativeChatStreamEvent(id, UNKNOWN_TYPE, null, null, true, data);
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
        return TYPE_DONE.equals(type);
    }

    @Override
    public NativeStreamError getError() {
        return error;
    }

    @Override
    public boolean isCompletionSignal() {
        if (chunk == null || chunk.getChoices() == null) {
            return false;
        }
        for (Choice choice : chunk.getChoices()) {
            if (choice != null && choice.getFinishReason() != null) {
                return true;
            }
        }
        return false;
    }

    /** Parsed native chunk, or {@code null} for {@code done}/{@code error}/{@code unknown}. */
    public ChatCompletionResponse getChunk() {
        return chunk;
    }

    /** Convenience: the payload's native {@code object} when known. */
    public boolean isDone() {
        return TYPE_DONE.equals(type);
    }
}

