package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.team.AgentTeamMemberSnapshot;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TeamBoardRenderSupport {

    private static final int MAX_RECENT_MESSAGES_PER_LANE = 2;

    private TeamBoardRenderSupport() {
    }

    public static List<String> renderBoardLines(List<SessionEvent> events) {
        Aggregation aggregation = aggregate(events);
        if (aggregation.tasksById.isEmpty() && aggregation.messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<LaneState> lanes = buildLanes(aggregation);
        List<String> lines = new ArrayList<String>();
        lines.add("summary tasks=" + aggregation.tasksById.size()
                + " running=" + aggregation.runningCount
                + " completed=" + aggregation.completedCount
                + " failed=" + aggregation.failedCount
                + " blocked=" + aggregation.blockedCount
                + " members=" + lanes.size());

        for (LaneState lane : lanes) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add("lane " + lane.label);
            if (lane.tasks.isEmpty()) {
                lines.add("  (no tasks)");
            } else {
                for (TaskState task : lane.tasks) {
                    lines.add("  [" + taskBadge(task) + "] " + taskLabel(task));
                    if (!isBlank(task.detail)) {
                        lines.add("    " + clip(singleLine(task.detail), 96));
                    }
                    if (!isBlank(task.childSessionId)) {
                        lines.add("    child session: " + clip(task.childSessionId, 84));
                    }
                    if (task.heartbeatCount > 0) {
                        lines.add("    heartbeats: " + task.heartbeatCount);
                    }
                }
            }
            if (!lane.messages.isEmpty()) {
                lines.add("  messages:");
                for (MessageState message : lane.messages) {
                    lines.add("    - " + messageLabel(message));
                }
            }
        }
        return lines;
    }

    public static List<String> renderBoardLines(AgentTeamState state) {
        Aggregation aggregation = aggregate(state);
        if (aggregation.tasksById.isEmpty() && aggregation.messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<LaneState> lanes = buildLanes(aggregation);
        List<String> lines = new ArrayList<String>();
        lines.add("summary tasks=" + aggregation.tasksById.size()
                + " running=" + aggregation.runningCount
                + " completed=" + aggregation.completedCount
                + " failed=" + aggregation.failedCount
                + " blocked=" + aggregation.blockedCount
                + " members=" + lanes.size());

        for (LaneState lane : lanes) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add("lane " + lane.label);
            if (lane.tasks.isEmpty()) {
                lines.add("  (no tasks)");
            } else {
                for (TaskState task : lane.tasks) {
                    lines.add("  [" + taskBadge(task) + "] " + taskLabel(task));
                    if (!isBlank(task.detail)) {
                        lines.add("    " + clip(singleLine(task.detail), 96));
                    }
                    if (!isBlank(task.childSessionId)) {
                        lines.add("    child session: " + clip(task.childSessionId, 84));
                    }
                    if (task.heartbeatCount > 0) {
                        lines.add("    heartbeats: " + task.heartbeatCount);
                    }
                }
            }
            if (!lane.messages.isEmpty()) {
                lines.add("  messages:");
                for (MessageState message : lane.messages) {
                    lines.add("    - " + messageLabel(message));
                }
            }
        }
        return lines;
    }

    public static String renderBoardOutput(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "team: (none)";
        }
        StringBuilder builder = new StringBuilder("team board:\n");
        for (String line : lines) {
            builder.append(line == null ? "" : line).append('\n');
        }
        return builder.toString().trim();
    }

    private static Aggregation aggregate(List<SessionEvent> events) {
        Aggregation aggregation = new Aggregation();
        if (events == null || events.isEmpty()) {
            return aggregation;
        }
        for (SessionEvent event : events) {
            if (event == null || event.getType() == null) {
                continue;
            }
            if (isTeamTaskEvent(event)) {
                TaskState next = aggregation.tasksById.get(resolveTaskId(event));
                if (next == null) {
                    next = new TaskState();
                    next.order = aggregation.nextTaskOrder++;
                    aggregation.tasksById.put(resolveTaskId(event), next);
                }
                applyTaskEvent(next, event);
                continue;
            }
            if (event.getType() == SessionEventType.TEAM_MESSAGE) {
                MessageState message = toMessageState(event);
                if (message != null) {
                    aggregation.messages.add(message);
                }
            }
        }
        for (TaskState task : aggregation.tasksById.values()) {
            String normalized = normalizeStatus(task.status);
            if ("running".equals(normalized) || "in_progress".equals(normalized)) {
                aggregation.runningCount++;
            } else if ("completed".equals(normalized)) {
                aggregation.completedCount++;
            } else if ("failed".equals(normalized)) {
                aggregation.failedCount++;
            } else if ("blocked".equals(normalized)) {
                aggregation.blockedCount++;
            }
        }
        return aggregation;
    }

    private static Aggregation aggregate(AgentTeamState state) {
        Aggregation aggregation = new Aggregation();
        if (state == null) {
            return aggregation;
        }
        Map<String, String> memberLabels = memberLabelMap(state.getMembers());
        if (state.getTaskStates() != null) {
            for (AgentTeamTaskState persistedTask : state.getTaskStates()) {
                if (persistedTask == null) {
                    continue;
                }
                TaskState next = new TaskState();
                next.order = aggregation.nextTaskOrder++;
                next.taskId = firstNonBlank(trimToNull(persistedTask.getTaskId()),
                        trimToNull(persistedTask.getTask() == null ? null : persistedTask.getTask().getId()),
                        "team-task-" + next.order);
                next.title = trimToNull(persistedTask.getTask() == null ? null : persistedTask.getTask().getTask());
                next.task = trimToNull(persistedTask.getTask() == null ? null : persistedTask.getTask().getTask());
                next.status = persistedTask.getStatus() == null ? null : persistedTask.getStatus().name().toLowerCase(Locale.ROOT);
                next.phase = trimToNull(persistedTask.getPhase());
                next.detail = firstNonBlank(trimToNull(persistedTask.getDetail()),
                        trimToNull(persistedTask.getError()),
                        trimToNull(persistedTask.getOutput()));
                next.percent = persistedTask.getPercent() == null ? null : String.valueOf(persistedTask.getPercent());
                String memberId = firstNonBlank(trimToNull(persistedTask.getClaimedBy()),
                        trimToNull(taskMemberId(persistedTask.getTask())));
                next.memberKey = memberId;
                next.memberLabel = firstNonBlank(memberLabels.get(memberId), memberId, "unassigned");
                next.heartbeatCount = persistedTask.getHeartbeatCount();
                next.updatedAtEpochMs = firstPositive(
                        persistedTask.getUpdatedAtEpochMs(),
                        persistedTask.getLastHeartbeatTime(),
                        persistedTask.getEndTime(),
                        persistedTask.getStartTime()
                );
                aggregation.tasksById.put(next.taskId, next);
            }
        }
        if (state.getMessages() != null) {
            for (AgentTeamMessage message : state.getMessages()) {
                if (message == null) {
                    continue;
                }
                MessageState next = new MessageState();
                next.messageId = trimToNull(message.getId());
                next.fromMemberId = trimToNull(message.getFromMemberId());
                next.toMemberId = trimToNull(message.getToMemberId());
                next.messageType = trimToNull(message.getType());
                next.taskId = trimToNull(message.getTaskId());
                next.text = trimToNull(message.getContent());
                next.createdAtEpochMs = message.getCreatedAt();
                next.memberKey = firstNonBlank(next.fromMemberId, next.toMemberId, "team");
                next.memberLabel = firstNonBlank(memberLabels.get(next.memberKey), next.memberKey, "team");
                aggregation.messages.add(next);
            }
        }
        for (TaskState task : aggregation.tasksById.values()) {
            String normalized = normalizeStatus(task.status);
            if ("running".equals(normalized) || "in_progress".equals(normalized)) {
                aggregation.runningCount++;
            } else if ("completed".equals(normalized)) {
                aggregation.completedCount++;
            } else if ("failed".equals(normalized)) {
                aggregation.failedCount++;
            } else if ("blocked".equals(normalized)) {
                aggregation.blockedCount++;
            }
        }
        return aggregation;
    }

    private static List<LaneState> buildLanes(Aggregation aggregation) {
        LinkedHashMap<String, LaneState> lanes = new LinkedHashMap<String, LaneState>();
        for (TaskState task : aggregation.tasksById.values()) {
            lane(lanes, task.memberKey, task.memberLabel).tasks.add(task);
        }
        for (MessageState message : aggregation.messages) {
            lane(lanes, message.memberKey, message.memberLabel).messages.add(message);
        }
        List<LaneState> ordered = new ArrayList<LaneState>(lanes.values());
        for (LaneState lane : ordered) {
            Collections.sort(lane.tasks, new Comparator<TaskState>() {
                @Override
                public int compare(TaskState left, TaskState right) {
                    int priorityCompare = taskPriority(left) - taskPriority(right);
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    long updatedCompare = right.updatedAtEpochMs - left.updatedAtEpochMs;
                    if (updatedCompare != 0L) {
                        return updatedCompare > 0L ? 1 : -1;
                    }
                    return safeText(left.taskId).compareToIgnoreCase(safeText(right.taskId));
                }
            });
            Collections.sort(lane.messages, new Comparator<MessageState>() {
                @Override
                public int compare(MessageState left, MessageState right) {
                    long createdCompare = right.createdAtEpochMs - left.createdAtEpochMs;
                    if (createdCompare != 0L) {
                        return createdCompare > 0L ? 1 : -1;
                    }
                    return safeText(left.messageId).compareToIgnoreCase(safeText(right.messageId));
                }
            });
            if (lane.messages.size() > MAX_RECENT_MESSAGES_PER_LANE) {
                lane.messages = new ArrayList<MessageState>(lane.messages.subList(0, MAX_RECENT_MESSAGES_PER_LANE));
            }
        }
        Collections.sort(ordered, new Comparator<LaneState>() {
            @Override
            public int compare(LaneState left, LaneState right) {
                int leftPriority = lanePriority(left);
                int rightPriority = lanePriority(right);
                if (leftPriority != rightPriority) {
                    return leftPriority - rightPriority;
                }
                return safeText(left.label).compareToIgnoreCase(safeText(right.label));
            }
        });
        return ordered;
    }

    private static LaneState lane(Map<String, LaneState> lanes, String key, String label) {
        String laneKey = isBlank(key) ? "__unassigned__" : key;
        LaneState lane = lanes.get(laneKey);
        if (lane != null) {
            if (isBlank(lane.label) && !isBlank(label)) {
                lane.label = label;
            }
            return lane;
        }
        LaneState created = new LaneState();
        created.key = laneKey;
        created.label = firstNonBlank(label, "unassigned");
        lanes.put(laneKey, created);
        return created;
    }

    private static void applyTaskEvent(TaskState state, SessionEvent event) {
        Map<String, Object> payload = event.getPayload();
        state.taskId = firstNonBlank(trimToNull(payloadString(payload, "taskId")), state.taskId);
        state.title = firstNonBlank(trimToNull(payloadString(payload, "title")), state.title);
        state.task = firstNonBlank(trimToNull(payloadString(payload, "task")), state.task);
        state.status = firstNonBlank(trimToNull(payloadString(payload, "status")), state.status);
        state.phase = firstNonBlank(trimToNull(payloadString(payload, "phase")), state.phase);
        state.detail = firstNonBlank(trimToNull(payloadString(payload, "detail")),
                trimToNull(payloadString(payload, "error")),
                trimToNull(payloadString(payload, "output")),
                state.detail);
        state.percent = firstNonBlank(trimToNull(payloadString(payload, "percent")), state.percent);
        state.memberKey = firstNonBlank(trimToNull(payloadString(payload, "memberId")),
                trimToNull(payloadString(payload, "memberName")),
                state.memberKey);
        state.memberLabel = firstNonBlank(trimToNull(payloadString(payload, "memberName")),
                trimToNull(payloadString(payload, "memberId")),
                state.memberLabel,
                "unassigned");
        state.childSessionId = firstNonBlank(trimToNull(payloadString(payload, "childSessionId")), state.childSessionId);
        state.heartbeatCount = intValue(payload, "heartbeatCount", state.heartbeatCount);
        state.updatedAtEpochMs = longValue(payload, "updatedAtEpochMs", event.getTimestamp());
        if (state.updatedAtEpochMs <= 0L) {
            state.updatedAtEpochMs = event.getTimestamp();
        }
    }

    private static MessageState toMessageState(SessionEvent event) {
        Map<String, Object> payload = event.getPayload();
        String text = firstNonBlank(trimToNull(payloadString(payload, "content")), trimToNull(payloadString(payload, "detail")));
        String from = trimToNull(payloadString(payload, "fromMemberId"));
        String to = trimToNull(payloadString(payload, "toMemberId"));
        String memberLabel = firstNonBlank(from, to, "team");
        MessageState state = new MessageState();
        state.messageId = trimToNull(payloadString(payload, "messageId"));
        state.memberKey = memberLabel;
        state.memberLabel = memberLabel;
        state.text = text;
        state.messageType = trimToNull(payloadString(payload, "messageType"));
        state.taskId = trimToNull(payloadString(payload, "taskId"));
        state.fromMemberId = from;
        state.toMemberId = to;
        state.createdAtEpochMs = longValue(payload, "createdAt", event.getTimestamp());
        if (state.createdAtEpochMs <= 0L) {
            state.createdAtEpochMs = event.getTimestamp();
        }
        return state;
    }

    private static boolean isTeamTaskEvent(SessionEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getType() != SessionEventType.TASK_CREATED && event.getType() != SessionEventType.TASK_UPDATED) {
            return false;
        }
        Map<String, Object> payload = event.getPayload();
        String callId = trimToNull(payloadString(payload, "callId"));
        String title = firstNonBlank(trimToNull(payloadString(payload, "title")), trimToNull(event.getSummary()));
        return (callId != null && callId.startsWith("team-task:"))
                || !isBlank(payloadString(payload, "memberId"))
                || !isBlank(payloadString(payload, "memberName"))
                || !isBlank(payloadString(payload, "heartbeatCount"))
                || (title != null && title.toLowerCase(Locale.ROOT).startsWith("team task"));
    }

    private static String resolveTaskId(SessionEvent event) {
        Map<String, Object> payload = event == null ? null : event.getPayload();
        String taskId = trimToNull(payloadString(payload, "taskId"));
        if (!isBlank(taskId)) {
            return taskId;
        }
        String callId = trimToNull(payloadString(payload, "callId"));
        if (!isBlank(callId)) {
            return callId;
        }
        return firstNonBlank(trimToNull(event == null ? null : event.getSummary()), "team-task");
    }

    private static String taskBadge(TaskState task) {
        StringBuilder badge = new StringBuilder();
        if (!isBlank(task.status)) {
            badge.append(normalizeStatus(task.status));
        }
        if (!isBlank(task.phase)) {
            if (badge.length() > 0) {
                badge.append('/');
            }
            badge.append(task.phase.toLowerCase(Locale.ROOT));
        }
        if (!isBlank(task.percent)) {
            if (badge.length() > 0) {
                badge.append(' ');
            }
            badge.append(task.percent).append('%');
        }
        return badge.length() == 0 ? "unknown" : badge.toString();
    }

    private static String taskLabel(TaskState task) {
        String label = firstNonBlank(trimToNull(task.task), trimToNull(task.title), trimToNull(task.taskId), "task");
        if (!isBlank(task.taskId)
                && !safeText(label).toLowerCase(Locale.ROOT).contains(task.taskId.toLowerCase(Locale.ROOT))) {
            return label + " (" + task.taskId + ")";
        }
        return label;
    }

    private static String messageLabel(MessageState message) {
        StringBuilder builder = new StringBuilder();
        if (!isBlank(message.messageType)) {
            builder.append('[').append(message.messageType).append(']').append(' ');
        }
        if (!isBlank(message.fromMemberId) || !isBlank(message.toMemberId)) {
            builder.append(firstNonBlank(message.fromMemberId, "?"))
                    .append(" -> ")
                    .append(firstNonBlank(message.toMemberId, "?"));
        } else {
            builder.append(firstNonBlank(message.memberLabel, "team"));
        }
        if (!isBlank(message.taskId)) {
            builder.append(" | task=").append(message.taskId);
        }
        if (!isBlank(message.text)) {
            builder.append(" | ").append(clip(singleLine(message.text), 76));
        }
        return builder.toString();
    }

    private static int lanePriority(LaneState lane) {
        if (lane == null || lane.tasks.isEmpty()) {
            return 3;
        }
        int best = Integer.MAX_VALUE;
        for (TaskState task : lane.tasks) {
            best = Math.min(best, taskPriority(task));
        }
        return best;
    }

    private static int taskPriority(TaskState task) {
        String normalized = normalizeStatus(task == null ? null : task.status);
        if ("running".equals(normalized) || "in_progress".equals(normalized)) {
            return 0;
        }
        if ("ready".equals(normalized) || "pending".equals(normalized)) {
            return 1;
        }
        if ("failed".equals(normalized) || "blocked".equals(normalized)) {
            return 2;
        }
        if ("completed".equals(normalized)) {
            return 3;
        }
        return 4;
    }

    private static int intValue(Map<String, Object> payload, String key, int fallback) {
        String value = trimToNull(payloadString(payload, key));
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long longValue(Map<String, Object> payload, String key, long fallback) {
        String value = trimToNull(payloadString(payload, key));
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String payloadString(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String taskMemberId(AgentTeamTask task) {
        return task == null ? null : task.getMemberId();
    }

    private static long firstPositive(long... values) {
        if (values == null) {
            return 0L;
        }
        for (long value : values) {
            if (value > 0L) {
                return value;
            }
        }
        return 0L;
    }

    private static Map<String, String> memberLabelMap(List<AgentTeamMemberSnapshot> members) {
        if (members == null || members.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> labels = new LinkedHashMap<String, String>();
        for (AgentTeamMemberSnapshot member : members) {
            if (member == null || isBlank(member.getId())) {
                continue;
            }
            labels.put(member.getId(), firstNonBlank(trimToNull(member.getName()), trimToNull(member.getId())));
        }
        return labels;
    }

    private static String singleLine(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String clip(String value, int maxChars) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)) + "...";
    }

    private static String normalizeStatus(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
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

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static final class Aggregation {
        private final LinkedHashMap<String, TaskState> tasksById = new LinkedHashMap<String, TaskState>();
        private final List<MessageState> messages = new ArrayList<MessageState>();
        private int nextTaskOrder;
        private int runningCount;
        private int completedCount;
        private int failedCount;
        private int blockedCount;
    }

    private static final class LaneState {
        private String key;
        private String label;
        private List<TaskState> tasks = new ArrayList<TaskState>();
        private List<MessageState> messages = new ArrayList<MessageState>();
    }

    private static final class TaskState {
        private int order;
        private String taskId;
        private String title;
        private String task;
        private String status;
        private String phase;
        private String detail;
        private String percent;
        private String memberKey;
        private String memberLabel;
        private String childSessionId;
        private int heartbeatCount;
        private long updatedAtEpochMs;
    }

    private static final class MessageState {
        private String messageId;
        private String memberKey;
        private String memberLabel;
        private String fromMemberId;
        private String toMemberId;
        private String messageType;
        private String taskId;
        private String text;
        private long createdAtEpochMs;
    }
}
