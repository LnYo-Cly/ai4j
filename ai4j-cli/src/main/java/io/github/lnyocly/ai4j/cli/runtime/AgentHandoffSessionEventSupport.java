package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentHandoffSessionEventSupport {

    private AgentHandoffSessionEventSupport() {
    }

    public static boolean supports(AgentEvent event) {
        if (event == null || event.getType() == null) {
            return false;
        }
        return event.getType() == AgentEventType.HANDOFF_START || event.getType() == AgentEventType.HANDOFF_END;
    }

    public static SessionEventType resolveSessionEventType(AgentEvent event) {
        if (!supports(event)) {
            return null;
        }
        return event.getType() == AgentEventType.HANDOFF_START
                ? SessionEventType.TASK_CREATED
                : SessionEventType.TASK_UPDATED;
    }

    public static SessionEvent toSessionEvent(String sessionId, String turnId, AgentEvent event) {
        SessionEventType type = resolveSessionEventType(event);
        if (type == null || isBlank(sessionId)) {
            return null;
        }
        return SessionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .type(type)
                .timestamp(System.currentTimeMillis())
                .turnId(turnId)
                .step(event == null ? null : event.getStep())
                .summary(buildSummary(event))
                .payload(buildPayload(event))
                .build();
    }

    public static String buildSummary(AgentEvent event) {
        Map<String, Object> payload = payload(event);
        String title = firstNonBlank(payloadString(payload, "title"), event == null ? null : event.getMessage(), "Subagent task");
        String status = firstNonBlank(payloadString(payload, "status"), event == null || event.getType() == null ? null : event.getType().name().toLowerCase());
        return title + " [" + status + "]";
    }

    public static Map<String, Object> buildPayload(AgentEvent event) {
        Map<String, Object> source = payload(event);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        String handoffId = firstNonBlank(payloadString(source, "handoffId"), payloadString(source, "callId"));
        String detail = firstNonBlank(payloadString(source, "detail"), payloadString(source, "error"), payloadString(source, "output"));
        String output = trimToNull(payloadString(source, "output"));
        String error = trimToNull(payloadString(source, "error"));
        payload.put("taskId", handoffId);
        payload.put("callId", handoffId);
        payload.put("tool", payloadString(source, "tool"));
        payload.put("subagent", payloadString(source, "subagent"));
        payload.put("title", firstNonBlank(payloadString(source, "title"), "Subagent task"));
        payload.put("detail", detail);
        payload.put("status", payloadString(source, "status"));
        payload.put("sessionMode", payloadString(source, "sessionMode"));
        payload.put("depth", payloadValue(source, "depth"));
        payload.put("attempts", payloadValue(source, "attempts"));
        payload.put("durationMillis", payloadValue(source, "durationMillis"));
        payload.put("output", output);
        payload.put("error", error);
        payload.put("previewLines", previewLines(firstNonBlank(error, output, detail)));
        return payload;
    }

    private static Map<String, Object> payload(AgentEvent event) {
        Object payload = event == null ? null : event.getPayload();
        if (payload instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) payload;
            return map;
        }
        return new LinkedHashMap<String, Object>();
    }

    private static Object payloadValue(Map<String, Object> payload, String key) {
        return payload == null || key == null ? null : payload.get(key);
    }

    private static String payloadString(Map<String, Object> payload, String key) {
        Object value = payloadValue(payload, key);
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> previewLines(String raw) {
        List<String> lines = new ArrayList<String>();
        if (isBlank(raw)) {
            return lines;
        }
        String[] split = raw.replace("\r", "").split("\n");
        int max = Math.min(4, split.length);
        for (int i = 0; i < max; i++) {
            String line = trimToNull(split[i]);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
