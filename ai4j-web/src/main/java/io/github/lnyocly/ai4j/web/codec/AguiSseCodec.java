package io.github.lnyocly.ai4j.web.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.web.sse.AiSseEvent;

/**
 * AG-UI (Agent User Interaction) Server-Sent Events codec serializing protocol
 * payloads via Jackson into {@link AiSseEvent} frames.
 */
public class AguiSseCodec {

    public static final String TYPE_RUN_STARTED = "RUN_STARTED";
    public static final String TYPE_RUN_FINISHED = "RUN_FINISHED";
    public static final String TYPE_RUN_ERROR = "RUN_ERROR";
    public static final String TYPE_STEP_STARTED = "STEP_STARTED";
    public static final String TYPE_STEP_FINISHED = "STEP_FINISHED";
    public static final String TYPE_TEXT_MESSAGE_START = "TEXT_MESSAGE_START";
    public static final String TYPE_TEXT_MESSAGE_CONTENT = "TEXT_MESSAGE_CONTENT";
    public static final String TYPE_TEXT_MESSAGE_END = "TEXT_MESSAGE_END";
    public static final String TYPE_REASONING_MESSAGE_START = "REASONING_MESSAGE_START";
    public static final String TYPE_REASONING_MESSAGE_CONTENT = "REASONING_MESSAGE_CONTENT";
    public static final String TYPE_REASONING_MESSAGE_END = "REASONING_MESSAGE_END";
    public static final String TYPE_TOOL_CALL_START = "TOOL_CALL_START";
    public static final String TYPE_TOOL_CALL_ARGS = "TOOL_CALL_ARGS";
    public static final String TYPE_TOOL_CALL_END = "TOOL_CALL_END";
    public static final String TYPE_TOOL_CALL_RESULT = "TOOL_CALL_RESULT";
    public static final String TYPE_STATE_SNAPSHOT = "STATE_SNAPSHOT";

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

    private final ObjectMapper objectMapper;
    private final boolean includeEventName;

    public AguiSseCodec() {
        this(DEFAULT_OBJECT_MAPPER, false);
    }

    public AguiSseCodec(ObjectMapper objectMapper) {
        this(objectMapper, false);
    }

    public AguiSseCodec(ObjectMapper objectMapper, boolean includeEventName) {
        this.objectMapper = objectMapper != null ? objectMapper : DEFAULT_OBJECT_MAPPER;
        this.includeEventName = includeEventName;
    }

    public AiSseEvent runStarted(String threadId, String runId) {
        return runStarted(threadId, runId, null);
    }

    public AiSseEvent runStarted(String threadId, String runId, Object input) {
        ObjectNode node = baseNode(TYPE_RUN_STARTED);
        node.put("threadId", requireNonBlank(threadId, "threadId"));
        node.put("runId", requireNonBlank(runId, "runId"));
        if (input != null) {
            node.set("input", toTree(input));
        }
        return toFrame(TYPE_RUN_STARTED, node);
    }

    public AiSseEvent stepStarted(String stepName) {
        ObjectNode node = baseNode(TYPE_STEP_STARTED);
        node.put("stepName", requireNonBlank(stepName, "stepName"));
        return toFrame(TYPE_STEP_STARTED, node);
    }

    public AiSseEvent stepFinished(String stepName) {
        ObjectNode node = baseNode(TYPE_STEP_FINISHED);
        node.put("stepName", requireNonBlank(stepName, "stepName"));
        return toFrame(TYPE_STEP_FINISHED, node);
    }

    public AiSseEvent textMessageStart(String messageId) {
        return textMessageStart(messageId, "assistant");
    }

    public AiSseEvent textMessageStart(String messageId, String role) {
        ObjectNode node = baseNode(TYPE_TEXT_MESSAGE_START);
        node.put("messageId", requireNonBlank(messageId, "messageId"));
        node.put("role", isBlank(role) ? "assistant" : role);
        return toFrame(TYPE_TEXT_MESSAGE_START, node);
    }

    public AiSseEvent textMessageContent(String messageId, String delta) {
        ObjectNode node = baseNode(TYPE_TEXT_MESSAGE_CONTENT);
        node.put("messageId", requireNonBlank(messageId, "messageId"));
        node.put("delta", requireNonNull(delta, "delta"));
        return toFrame(TYPE_TEXT_MESSAGE_CONTENT, node);
    }

    public AiSseEvent textMessageEnd(String messageId) {
        ObjectNode node = baseNode(TYPE_TEXT_MESSAGE_END);
        node.put("messageId", requireNonBlank(messageId, "messageId"));
        return toFrame(TYPE_TEXT_MESSAGE_END, node);
    }

    public AiSseEvent reasoningMessageStart(String messageId) {
        ObjectNode node = baseNode(TYPE_REASONING_MESSAGE_START);
        node.put("messageId", requireNonBlank(messageId, "messageId"));
        return toFrame(TYPE_REASONING_MESSAGE_START, node);
    }

    public AiSseEvent reasoningMessageContent(String messageId, String delta) {
        ObjectNode node = baseNode(TYPE_REASONING_MESSAGE_CONTENT);
        node.put("messageId", requireNonBlank(messageId, "messageId"));
        node.put("delta", requireNonNull(delta, "delta"));
        return toFrame(TYPE_REASONING_MESSAGE_CONTENT, node);
    }

    public AiSseEvent reasoningMessageEnd(String messageId) {
        ObjectNode node = baseNode(TYPE_REASONING_MESSAGE_END);
        node.put("messageId", requireNonBlank(messageId, "messageId"));
        return toFrame(TYPE_REASONING_MESSAGE_END, node);
    }

    public AiSseEvent toolCallStart(String toolCallId, String toolCallName) {
        return toolCallStart(toolCallId, toolCallName, null);
    }

    public AiSseEvent toolCallStart(String toolCallId, String toolCallName, String parentMessageId) {
        ObjectNode node = baseNode(TYPE_TOOL_CALL_START);
        node.put("toolCallId", requireNonBlank(toolCallId, "toolCallId"));
        node.put("toolCallName", requireNonBlank(toolCallName, "toolCallName"));
        if (!isBlank(parentMessageId)) {
            node.put("parentMessageId", parentMessageId);
        }
        return toFrame(TYPE_TOOL_CALL_START, node);
    }

    public AiSseEvent toolCallArgs(String toolCallId, String delta) {
        ObjectNode node = baseNode(TYPE_TOOL_CALL_ARGS);
        node.put("toolCallId", requireNonBlank(toolCallId, "toolCallId"));
        node.put("delta", requireNonNull(delta, "delta"));
        return toFrame(TYPE_TOOL_CALL_ARGS, node);
    }

    public AiSseEvent toolCallEnd(String toolCallId) {
        ObjectNode node = baseNode(TYPE_TOOL_CALL_END);
        node.put("toolCallId", requireNonBlank(toolCallId, "toolCallId"));
        return toFrame(TYPE_TOOL_CALL_END, node);
    }

    public AiSseEvent toolCallResult(String toolCallId, String content) {
        return toolCallResult(null, toolCallId, content, "tool");
    }

    public AiSseEvent toolCallResult(String messageId, String toolCallId, String content) {
        return toolCallResult(messageId, toolCallId, content, "tool");
    }

    public AiSseEvent toolCallResult(String messageId, String toolCallId, String content, String role) {
        ObjectNode node = baseNode(TYPE_TOOL_CALL_RESULT);
        if (!isBlank(messageId)) {
            node.put("messageId", messageId);
        }
        node.put("toolCallId", requireNonBlank(toolCallId, "toolCallId"));
        node.put("content", requireNonNull(content, "content"));
        node.put("role", isBlank(role) ? "tool" : role);
        return toFrame(TYPE_TOOL_CALL_RESULT, node);
    }

    public AiSseEvent stateSnapshot(Object snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        ObjectNode node = baseNode(TYPE_STATE_SNAPSHOT);
        node.set("snapshot", toTree(snapshot));
        return toFrame(TYPE_STATE_SNAPSHOT, node);
    }

    public AiSseEvent runFinished(String threadId, String runId) {
        return runFinished(threadId, runId, null);
    }

    public AiSseEvent runFinished(String threadId, String runId, Object result) {
        ObjectNode node = baseNode(TYPE_RUN_FINISHED);
        node.put("threadId", requireNonBlank(threadId, "threadId"));
        node.put("runId", requireNonBlank(runId, "runId"));
        if (result != null) {
            node.set("result", toTree(result));
        }
        return toFrame(TYPE_RUN_FINISHED, node);
    }

    public AiSseEvent runError(String message) {
        return runError(message, null);
    }

    public AiSseEvent runError(String message, String code) {
        ObjectNode node = baseNode(TYPE_RUN_ERROR);
        node.put("message", isBlank(message) ? "Unknown error" : message);
        if (!isBlank(code)) {
            node.put("code", code);
        }
        return toFrame(TYPE_RUN_ERROR, node);
    }

    private ObjectNode baseNode(String type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        return node;
    }

    private JsonNode toTree(Object value) {
        if (value instanceof JsonNode) {
            return (JsonNode) value;
        }
        return objectMapper.valueToTree(value);
    }

    private AiSseEvent toFrame(String eventType, ObjectNode node) {
        String json;
        try {
            json = objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize AG-UI event " + eventType, ex);
        }
        return includeEventName ? AiSseEvent.ofEvent(eventType, json) : AiSseEvent.ofData(json);
    }

    private static String requireNonBlank(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String requireNonNull(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
