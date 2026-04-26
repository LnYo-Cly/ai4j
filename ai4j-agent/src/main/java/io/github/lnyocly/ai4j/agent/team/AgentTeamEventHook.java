package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.runtime.AgentToolExecutionScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentTeamEventHook implements AgentTeamHook {

    private final AgentListener listener;

    public AgentTeamEventHook() {
        this(null);
    }

    public AgentTeamEventHook(AgentListener listener) {
        this.listener = listener;
    }

    @Override
    public void afterPlan(String objective, AgentTeamPlan plan) {
        if (plan == null || plan.getTasks() == null) {
            return;
        }
        for (AgentTeamTask task : plan.getTasks()) {
            if (task == null || isBlank(task.getId())) {
                continue;
            }
            emit(AgentEventType.TEAM_TASK_CREATED, buildTaskSummary(task, "planned"), buildTaskPayload(
                    task,
                    null,
                    "planned",
                    firstNonBlank(task.getTask(), "Team task planned."),
                    null,
                    null,
                    0L,
                    null
            ));
        }
    }

    @Override
    public void beforeTask(String objective, AgentTeamTask task, AgentTeamMember member) {
        if (task == null || isBlank(task.getId())) {
            return;
        }
        emit(AgentEventType.TEAM_TASK_UPDATED, buildTaskSummary(task, "running"), buildTaskPayload(
                task,
                member,
                "running",
                "Assigned to " + firstNonBlank(member == null ? null : member.getName(), member == null ? null : member.resolveId(), "member") + ".",
                null,
                null,
                0L,
                null
        ));
    }

    @Override
    public void afterTask(String objective, AgentTeamMemberResult result) {
        if (result == null || result.getTask() == null || isBlank(result.getTaskId())) {
            return;
        }
        AgentTeamTask task = result.getTask();
        AgentTeamMember member = AgentTeamMember.builder()
                .id(result.getMemberId())
                .name(result.getMemberName())
                .build();
        String status = result.getTaskStatus() == AgentTeamTaskStatus.FAILED ? "failed" : "completed";
        emit(AgentEventType.TEAM_TASK_UPDATED, buildTaskSummary(task, status), buildTaskPayload(
                task,
                member,
                status,
                firstNonBlank(result.getError(), result.getOutput(), status),
                result.getOutput(),
                result.getError(),
                result.getDurationMillis(),
                null
        ));
    }

    @Override
    public void onTaskStateChanged(String objective,
                                   AgentTeamTaskState state,
                                   AgentTeamMember member,
                                   String detail) {
        if (state == null || state.getTask() == null || isBlank(state.getTaskId())) {
            return;
        }
        AgentTeamTask task = state.getTask();
        String status = normalizeStatus(state.getStatus());
        emit(AgentEventType.TEAM_TASK_UPDATED, buildTaskSummary(task, status), buildTaskPayload(
                task,
                member,
                status,
                firstNonBlank(detail, state.getDetail(), state.getError(), state.getOutput(), status),
                state.getOutput(),
                state.getError(),
                state.getDurationMillis(),
                state
        ));
    }

    @Override
    public void onMessage(AgentTeamMessage message) {
        if (message == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("messageId", message.getId());
        payload.put("fromMemberId", message.getFromMemberId());
        payload.put("toMemberId", message.getToMemberId());
        payload.put("taskId", message.getTaskId());
        payload.put("type", message.getType());
        payload.put("content", message.getContent());
        payload.put("createdAt", Long.valueOf(message.getCreatedAt()));
        payload.put("title", "Team message");
        payload.put("detail", firstNonBlank(message.getContent(), message.getType(), "Team message"));
        emit(AgentEventType.TEAM_MESSAGE, firstNonBlank(message.getContent(), message.getType(), "Team message"), payload);
    }

    private Map<String, Object> buildTaskPayload(AgentTeamTask task,
                                                 AgentTeamMember member,
                                                 String status,
                                                 String detail,
                                                 String output,
                                                 String error,
                                                 long durationMillis,
                                                 AgentTeamTaskState state) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        String taskId = task == null ? null : task.getId();
        payload.put("taskId", taskId);
        payload.put("callId", taskId == null ? null : "team-task:" + taskId);
        payload.put("title", "Team task " + firstNonBlank(task == null ? null : task.getId(), "task"));
        payload.put("status", status);
        payload.put("detail", detail);
        payload.put("phase", firstNonBlank(state == null ? null : state.getPhase(), status));
        payload.put("percent", Integer.valueOf(resolvePercent(status, state == null ? null : state.getPercent())));
        payload.put("updatedAtEpochMs", Long.valueOf(state == null ? System.currentTimeMillis() : state.getUpdatedAtEpochMs()));
        payload.put("heartbeatCount", Integer.valueOf(state == null ? 0 : state.getHeartbeatCount()));
        payload.put("startTime", Long.valueOf(state == null ? 0L : state.getStartTime()));
        payload.put("endTime", Long.valueOf(state == null ? 0L : state.getEndTime()));
        payload.put("lastHeartbeatTime", Long.valueOf(state == null ? 0L : state.getLastHeartbeatTime()));
        payload.put("memberId", firstNonBlank(
                member == null ? null : member.resolveId(),
                state == null ? null : state.getClaimedBy(),
                task == null ? null : task.getMemberId()));
        payload.put("memberName", firstNonBlank(member == null ? null : member.getName(),
                state == null ? null : state.getClaimedBy(),
                member == null ? null : member.resolveId(),
                task == null ? null : task.getMemberId()));
        payload.put("task", task == null ? null : task.getTask());
        payload.put("context", task == null ? null : task.getContext());
        payload.put("dependsOn", task == null ? null : task.getDependsOn());
        payload.put("output", output);
        payload.put("error", error);
        payload.put("durationMillis", Long.valueOf(durationMillis));
        return payload;
    }

    private String buildTaskSummary(AgentTeamTask task, String status) {
        return "Team task " + firstNonBlank(task == null ? null : task.getId(), "task") + " [" + firstNonBlank(status, "unknown") + "]";
    }

    private String normalizeStatus(AgentTeamTaskStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    private int resolvePercent(String status, Integer statePercent) {
        if (statePercent != null) {
            return Math.max(0, Math.min(100, statePercent.intValue()));
        }
        if (isBlank(status)) {
            return 0;
        }
        String normalized = status.trim().toLowerCase();
        if ("completed".equals(normalized) || "failed".equals(normalized) || "blocked".equals(normalized)) {
            return 100;
        }
        if ("running".equals(normalized) || "in_progress".equals(normalized) || "in-progress".equals(normalized)) {
            return 15;
        }
        if ("ready".equals(normalized)) {
            return 5;
        }
        return 0;
    }

    private void emit(AgentEventType type, String message, Map<String, Object> payload) {
        AgentToolExecutionScope.emit(type, message, payload);
        if (listener != null && type != null) {
            listener.onEvent(AgentEvent.builder()
                    .type(type)
                    .message(message)
                    .payload(payload)
                    .build());
        }
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
