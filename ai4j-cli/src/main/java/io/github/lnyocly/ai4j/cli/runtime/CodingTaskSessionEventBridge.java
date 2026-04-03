package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.cli.session.CodingSessionManager;
import io.github.lnyocly.ai4j.coding.runtime.CodingRuntimeListener;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLink;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import io.github.lnyocly.ai4j.coding.task.CodingTask;
import io.github.lnyocly.ai4j.coding.task.CodingTaskProgress;
import io.github.lnyocly.ai4j.coding.task.CodingTaskStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CodingTaskSessionEventBridge implements CodingRuntimeListener {

    public interface SessionEventConsumer {

        void onEvent(SessionEvent event);
    }

    private final CodingSessionManager sessionManager;
    private final SessionEventConsumer consumer;

    public CodingTaskSessionEventBridge(CodingSessionManager sessionManager) {
        this(sessionManager, null);
    }

    public CodingTaskSessionEventBridge(CodingSessionManager sessionManager, SessionEventConsumer consumer) {
        this.sessionManager = sessionManager;
        this.consumer = consumer;
    }

    @Override
    public void onTaskCreated(CodingTask task, CodingSessionLink link) {
        append(toTaskCreatedEvent(task, link));
    }

    @Override
    public void onTaskUpdated(CodingTask task) {
        append(toTaskUpdatedEvent(task));
    }

    public SessionEvent toTaskCreatedEvent(CodingTask task, CodingSessionLink link) {
        if (task == null || isBlank(task.getParentSessionId())) {
            return null;
        }
        return SessionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId(task.getParentSessionId())
                .type(SessionEventType.TASK_CREATED)
                .timestamp(System.currentTimeMillis())
                .summary(buildSummary(task))
                .payload(buildPayload(task, link))
                .build();
    }

    public SessionEvent toTaskUpdatedEvent(CodingTask task) {
        if (task == null || isBlank(task.getParentSessionId())) {
            return null;
        }
        return SessionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId(task.getParentSessionId())
                .type(SessionEventType.TASK_UPDATED)
                .timestamp(System.currentTimeMillis())
                .summary(buildSummary(task))
                .payload(buildPayload(task, null))
                .build();
    }

    private void append(SessionEvent event) {
        if (event == null) {
            return;
        }
        if (sessionManager != null) {
            try {
                sessionManager.appendEvent(event.getSessionId(), event);
            } catch (IOException ignored) {
            }
        }
        if (consumer != null) {
            consumer.onEvent(event);
        }
    }

    private Map<String, Object> buildPayload(CodingTask task, CodingSessionLink link) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        CodingTaskProgress progress = task == null ? null : task.getProgress();
        String detail = progress == null ? null : trimToNull(progress.getMessage());
        String output = trimToNull(task == null ? null : task.getOutputText());
        String error = trimToNull(task == null ? null : task.getError());
        payload.put("taskId", task == null ? null : task.getTaskId());
        payload.put("callId", task == null ? null : task.getTaskId());
        payload.put("tool", task == null ? null : task.getDefinitionName());
        payload.put("title", buildTitle(task));
        payload.put("detail", detail);
        payload.put("status", normalizeStatus(task == null ? null : task.getStatus()));
        payload.put("background", task != null && task.isBackground());
        payload.put("childSessionId", task == null ? null : task.getChildSessionId());
        payload.put("phase", progress == null ? null : progress.getPhase());
        payload.put("percent", progress == null ? null : progress.getPercent());
        payload.put("sessionMode", link == null || link.getSessionMode() == null ? null : link.getSessionMode().name().toLowerCase());
        payload.put("output", output);
        payload.put("error", error);
        payload.put("previewLines", previewLines(firstNonBlank(error, output, detail)));
        return payload;
    }

    private String buildSummary(CodingTask task) {
        String title = buildTitle(task);
        String status = normalizeStatus(task == null ? null : task.getStatus());
        return firstNonBlank(title, "delegate task") + " [" + firstNonBlank(status, "unknown") + "]";
    }

    private String buildTitle(CodingTask task) {
        String definitionName = task == null ? null : trimToNull(task.getDefinitionName());
        if (definitionName == null) {
            return "Delegate task";
        }
        return "Delegate " + definitionName;
    }

    private List<String> previewLines(String raw) {
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

    private String normalizeStatus(CodingTaskStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    private String firstNonBlank(String... values) {
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
