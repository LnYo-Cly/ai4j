package io.github.lnyocly.ai4j.web.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.web.sse.AiSseEvent;

import java.util.UUID;

/**
 * OpenAI-compatible {@code chat.completion.chunk} SSE codec producing
 * {@link AiSseEvent} frames usable with any {@link io.github.lnyocly.ai4j.web.sse.AiSseSink}.
 */
public class OpenAiSseCodec {

    public static final String OBJECT_CHAT_COMPLETION_CHUNK = "chat.completion.chunk";
    public static final String DONE_PAYLOAD = "[DONE]";

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();
    private static final AiSseEvent DONE_FRAME = AiSseEvent.ofData(DONE_PAYLOAD);

    private final ObjectMapper objectMapper;
    private final String id;
    private final String model;
    private final long created;
    private final String systemFingerprint;

    public OpenAiSseCodec() {
        this(DEFAULT_OBJECT_MAPPER, null, null, currentEpochSeconds(), null);
    }

    public OpenAiSseCodec(String id, String model) {
        this(DEFAULT_OBJECT_MAPPER, id, model, currentEpochSeconds(), null);
    }

    public OpenAiSseCodec(String id, String model, long createdEpochSeconds) {
        this(DEFAULT_OBJECT_MAPPER, id, model, createdEpochSeconds, null);
    }

    public OpenAiSseCodec(ObjectMapper objectMapper, String id, String model, long createdEpochSeconds) {
        this(objectMapper, id, model, createdEpochSeconds, null);
    }

    public OpenAiSseCodec(ObjectMapper objectMapper,
                          String id,
                          String model,
                          long createdEpochSeconds,
                          String systemFingerprint) {
        this.objectMapper = objectMapper != null ? objectMapper : DEFAULT_OBJECT_MAPPER;
        this.id = (id == null || id.trim().isEmpty()) ? generateChunkId() : id;
        this.model = model;
        this.created = createdEpochSeconds > 0L ? createdEpochSeconds : currentEpochSeconds();
        this.systemFingerprint = systemFingerprint;
    }

    public String getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public long getCreated() {
        return created;
    }

    public String getSystemFingerprint() {
        return systemFingerprint;
    }

    public AiSseEvent roleChunk() {
        return roleChunk("assistant");
    }

    public AiSseEvent roleChunk(String role) {
        String resolvedRole = (role == null || role.trim().isEmpty()) ? "assistant" : role;
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("role", resolvedRole);
        return chunkEvent(delta, null, null);
    }

    public AiSseEvent contentDelta(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("content", content);
        return chunkEvent(delta, null, null);
    }

    public AiSseEvent reasoningDelta(String reasoningContent) {
        if (reasoningContent == null) {
            throw new IllegalArgumentException("reasoningContent must not be null");
        }
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("reasoning_content", reasoningContent);
        return chunkEvent(delta, null, null);
    }

    public AiSseEvent toolCallDelta(int index, String id, String name, String argumentsDelta) {
        return toolCallDelta(index, id, null, name, argumentsDelta);
    }

    public AiSseEvent toolCallDelta(int index, String id, String type, String name, String argumentsDelta) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        ObjectNode callNode = objectMapper.createObjectNode();
        callNode.put("index", index);
        if (id != null) {
            callNode.put("id", id);
        }
        String resolvedType = type;
        if (resolvedType == null && (id != null || name != null)) {
            resolvedType = "function";
        }
        if (resolvedType != null) {
            callNode.put("type", resolvedType);
        }
        if (name != null || argumentsDelta != null) {
            ObjectNode functionNode = objectMapper.createObjectNode();
            if (name != null) {
                functionNode.put("name", name);
            }
            if (argumentsDelta != null) {
                functionNode.put("arguments", argumentsDelta);
            }
            callNode.set("function", functionNode);
        }

        ArrayNode toolCalls = objectMapper.createArrayNode();
        toolCalls.add(callNode);
        ObjectNode delta = objectMapper.createObjectNode();
        delta.set("tool_calls", toolCalls);
        return chunkEvent(delta, null, null);
    }

    public AiSseEvent finishChunk(String finishReason) {
        return finishChunk(finishReason, null);
    }

    public AiSseEvent finishChunk(String finishReason, Usage usage) {
        String resolvedFinishReason = (finishReason == null || finishReason.trim().isEmpty()) ? "stop" : finishReason;
        ObjectNode emptyDelta = objectMapper.createObjectNode();
        return chunkEvent(emptyDelta, resolvedFinishReason, usage);
    }

    public AiSseEvent usageChunk(Usage usage) {
        if (usage == null) {
            throw new IllegalArgumentException("usage must not be null");
        }
        ObjectNode root = createRootChunkNode();
        root.set("choices", objectMapper.createArrayNode());
        root.set("usage", objectMapper.valueToTree(usage));
        return toSseEvent(root);
    }

    public static AiSseEvent doneFrame() {
        return DONE_FRAME;
    }

    private AiSseEvent chunkEvent(ObjectNode delta, String finishReason, Usage usage) {
        ObjectNode choice = objectMapper.createObjectNode();
        choice.put("index", 0);
        choice.set("delta", delta);
        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        } else {
            choice.putNull("finish_reason");
        }

        ArrayNode choices = objectMapper.createArrayNode();
        choices.add(choice);

        ObjectNode root = createRootChunkNode();
        root.set("choices", choices);
        if (usage != null) {
            root.set("usage", objectMapper.valueToTree(usage));
        }
        return toSseEvent(root);
    }

    private ObjectNode createRootChunkNode() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", id);
        root.put("object", OBJECT_CHAT_COMPLETION_CHUNK);
        root.put("created", created);
        if (model != null) {
            root.put("model", model);
        }
        if (systemFingerprint != null) {
            root.put("system_fingerprint", systemFingerprint);
        }
        return root;
    }

    private AiSseEvent toSseEvent(ObjectNode root) {
        try {
            return AiSseEvent.ofData(objectMapper.writeValueAsString(root));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize OpenAI SSE chunk", ex);
        }
    }

    private static String generateChunkId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static long currentEpochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }
}
