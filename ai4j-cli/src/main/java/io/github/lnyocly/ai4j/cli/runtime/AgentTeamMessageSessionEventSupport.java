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

public final class AgentTeamMessageSessionEventSupport {

    private AgentTeamMessageSessionEventSupport() {
    }

    public static boolean supports(AgentEvent event) {
        return event != null && event.getType() == AgentEventType.TEAM_MESSAGE;
    }

    public static SessionEvent toSessionEvent(String sessionId, String turnId, AgentEvent event) {
        if (!supports(event) || isBlank(sessionId)) {
            return null;
        }
        return SessionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .type(SessionEventType.TEAM_MESSAGE)
                .timestamp(System.currentTimeMillis())
                .turnId(turnId)
                .step(event.getStep())
                .summary(buildSummary(event))
                .payload(buildPayload(event))
                .build();
    }

    public static String buildSummary(AgentEvent event) {
        Map<String, Object> payload = payload(event);
        String messageType = firstNonBlank(payloadString(payload, "type"), "message");
        String route = route(payloadString(payload, "fromMemberId"), payloadString(payload, "toMemberId"));
        if (isBlank(route)) {
            return "Team message [" + messageType + "]";
        }
        return "Team message " + route + " [" + messageType + "]";
    }

    public static Map<String, Object> buildPayload(AgentEvent event) {
        Map<String, Object> source = payload(event);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        String content = trimToNull(firstNonBlank(payloadString(source, "content"), payloadString(source, "detail")));
        String messageType = firstNonBlank(payloadString(source, "type"), "message");
        String taskId = trimToNull(payloadString(source, "taskId"));
        payload.put("messageId", payloadValue(source, "messageId"));
        payload.put("taskId", taskId);
        payload.put("callId", taskId == null ? null : "team-task:" + taskId);
        payload.put("fromMemberId", payloadValue(source, "fromMemberId"));
        payload.put("toMemberId", payloadValue(source, "toMemberId"));
        payload.put("messageType", messageType);
        payload.put("title", "Team message");
        payload.put("detail", content);
        payload.put("content", content);
        payload.put("createdAt", payloadValue(source, "createdAt"));
        payload.put("previewLines", previewLines(content));
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

    private static String route(String fromMemberId, String toMemberId) {
        String from = trimToNull(fromMemberId);
        String to = trimToNull(toMemberId);
        if (from == null && to == null) {
            return null;
        }
        return firstNonBlank(from, "?") + " -> " + firstNonBlank(to, "?");
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
