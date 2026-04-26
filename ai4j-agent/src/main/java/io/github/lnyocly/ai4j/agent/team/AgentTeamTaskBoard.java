package io.github.lnyocly.ai4j.agent.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentTeamTaskBoard {

    private final LinkedHashMap<String, AgentTeamTaskState> states = new LinkedHashMap<>();
    private static final int PERCENT_PLANNED = 0;
    private static final int PERCENT_READY = 5;
    private static final int PERCENT_IN_PROGRESS = 15;
    private static final int PERCENT_TERMINAL = 100;

    public AgentTeamTaskBoard(List<AgentTeamTask> tasks) {
        initialize(tasks);
    }

    private void initialize(List<AgentTeamTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Set<String> usedIds = new HashSet<>();
        int seq = 1;
        for (AgentTeamTask task : tasks) {
            AgentTeamTask safeTask = task == null ? AgentTeamTask.builder().build() : task;
            String id = normalizeId(safeTask.getId());
            if (id == null) {
                id = "task_" + seq;
                seq++;
            }
            while (usedIds.contains(id)) {
                id = id + "_" + seq;
                seq++;
            }
            usedIds.add(id);

            List<String> dependencies = normalizeDependencies(safeTask.getDependsOn());
            AgentTeamTask normalized = safeTask.toBuilder()
                    .id(id)
                    .dependsOn(dependencies)
                    .build();

            states.put(id, AgentTeamTaskState.builder()
                    .taskId(id)
                    .task(normalized)
                    .status(AgentTeamTaskStatus.PENDING)
                    .phase("planned")
                    .percent(Integer.valueOf(PERCENT_PLANNED))
                    .updatedAtEpochMs(System.currentTimeMillis())
                    .build());
        }
        refreshStatuses();
    }

    public synchronized List<AgentTeamTask> normalizedTasks() {
        if (states.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamTask> tasks = new ArrayList<>();
        for (AgentTeamTaskState state : states.values()) {
            tasks.add(state.getTask());
        }
        return tasks;
    }

    public synchronized List<AgentTeamTaskState> nextReadyTasks(int maxCount) {
        refreshStatuses();
        if (states.isEmpty()) {
            return Collections.emptyList();
        }
        int safeCount = maxCount <= 0 ? 1 : maxCount;
        List<AgentTeamTaskState> ready = new ArrayList<>();
        for (AgentTeamTaskState state : states.values()) {
            if (state.getStatus() == AgentTeamTaskStatus.READY) {
                ready.add(copy(state));
                if (ready.size() >= safeCount) {
                    break;
                }
            }
        }
        return ready;
    }

    public synchronized AgentTeamTaskState getTaskState(String taskId) {
        String key = resolveTaskKey(taskId);
        if (key == null) {
            return null;
        }
        return copy(states.get(key));
    }

    public synchronized boolean claimTask(String taskId, String memberId) {
        refreshStatuses();
        String key = resolveTaskKey(taskId);
        if (key == null) {
            return false;
        }
        AgentTeamTaskState state = states.get(key);
        if (state == null || state.getStatus() != AgentTeamTaskStatus.READY) {
            return false;
        }
        long now = System.currentTimeMillis();
        states.put(key, state.toBuilder()
                .status(AgentTeamTaskStatus.IN_PROGRESS)
                .claimedBy(normalizeMemberId(memberId))
                .startTime(now)
                .lastHeartbeatTime(now)
                .endTime(0L)
                .durationMillis(0L)
                .phase("running")
                .detail("Claimed by " + firstNonBlank(normalizeMemberId(memberId), "member") + ".")
                .percent(Integer.valueOf(Math.max(percentOf(state), PERCENT_IN_PROGRESS)))
                .updatedAtEpochMs(now)
                .heartbeatCount(0)
                .output(null)
                .error(null)
                .build());
        return true;
    }

    public synchronized boolean releaseTask(String taskId, String memberId, String reason) {
        String key = resolveTaskKey(taskId);
        if (key == null) {
            return false;
        }
        AgentTeamTaskState state = states.get(key);
        if (state == null || state.getStatus() != AgentTeamTaskStatus.IN_PROGRESS) {
            return false;
        }
        if (!isSameMember(state.getClaimedBy(), memberId)) {
            return false;
        }
        states.put(key, state.toBuilder()
                .status(AgentTeamTaskStatus.PENDING)
                .claimedBy(null)
                .startTime(0L)
                .lastHeartbeatTime(0L)
                .endTime(0L)
                .durationMillis(0L)
                .phase("released")
                .detail(firstNonBlank(trimToNull(reason), "Released back to queue."))
                .updatedAtEpochMs(System.currentTimeMillis())
                .output(null)
                .error(reason)
                .build());
        refreshStatuses();
        return true;
    }

    public synchronized boolean reassignTask(String taskId, String fromMemberId, String toMemberId) {
        String key = resolveTaskKey(taskId);
        if (key == null) {
            return false;
        }
        AgentTeamTaskState state = states.get(key);
        if (state == null || state.getStatus() != AgentTeamTaskStatus.IN_PROGRESS) {
            return false;
        }
        if (!isSameMember(state.getClaimedBy(), fromMemberId)) {
            return false;
        }
        long now = System.currentTimeMillis();
        states.put(key, state.toBuilder()
                .claimedBy(normalizeMemberId(toMemberId))
                .lastHeartbeatTime(now)
                .phase("reassigned")
                .detail("Reassigned from "
                        + firstNonBlank(normalizeMemberId(fromMemberId), "member")
                        + " to "
                        + firstNonBlank(normalizeMemberId(toMemberId), "member")
                        + ".")
                .updatedAtEpochMs(now)
                .build());
        return true;
    }

    public synchronized boolean heartbeatTask(String taskId, String memberId) {
        String key = resolveTaskKey(taskId);
        if (key == null) {
            return false;
        }
        AgentTeamTaskState state = states.get(key);
        if (state == null || state.getStatus() != AgentTeamTaskStatus.IN_PROGRESS) {
            return false;
        }
        if (!isSameMember(state.getClaimedBy(), memberId)) {
            return false;
        }
        long now = System.currentTimeMillis();
        states.put(key, state.toBuilder()
                .lastHeartbeatTime(now)
                .phase("heartbeat")
                .detail("Heartbeat from " + firstNonBlank(normalizeMemberId(memberId), "member") + ".")
                .percent(Integer.valueOf(Math.max(percentOf(state), PERCENT_IN_PROGRESS)))
                .updatedAtEpochMs(now)
                .heartbeatCount(state.getHeartbeatCount() + 1)
                .build());
        return true;
    }

    public synchronized int recoverTimedOutClaims(long timeoutMillis, String reason) {
        if (timeoutMillis <= 0 || states.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int recovered = 0;
        for (Map.Entry<String, AgentTeamTaskState> entry : states.entrySet()) {
            AgentTeamTaskState state = entry.getValue();
            if (state.getStatus() != AgentTeamTaskStatus.IN_PROGRESS) {
                continue;
            }
            long lastBeat = state.getLastHeartbeatTime() > 0 ? state.getLastHeartbeatTime() : state.getStartTime();
            if (lastBeat <= 0 || now - lastBeat < timeoutMillis) {
                continue;
            }
            entry.setValue(state.toBuilder()
                    .status(AgentTeamTaskStatus.PENDING)
                    .claimedBy(null)
                    .startTime(0L)
                    .lastHeartbeatTime(0L)
                    .endTime(0L)
                    .durationMillis(0L)
                    .phase("timeout_recovered")
                    .detail(reason == null ? "Claim timed out." : reason)
                    .updatedAtEpochMs(now)
                    .output(null)
                    .error(reason == null ? "claim timeout" : reason)
                    .build());
            recovered++;
        }
        if (recovered > 0) {
            refreshStatuses();
        }
        return recovered;
    }

    public synchronized void markInProgress(String taskId, String claimedBy) {
        claimTask(taskId, claimedBy);
    }

    public synchronized void markCompleted(String taskId, String output, long durationMillis) {
        String key = resolveTaskKey(taskId);
        if (key == null) {
            return;
        }
        AgentTeamTaskState state = states.get(key);
        if (state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        states.put(key, state.toBuilder()
                .status(AgentTeamTaskStatus.COMPLETED)
                .phase("completed")
                .detail(firstNonBlank(trimToNull(output), "Task completed."))
                .percent(Integer.valueOf(PERCENT_TERMINAL))
                .updatedAtEpochMs(now)
                .output(output)
                .durationMillis(durationMillis)
                .endTime(now)
                .lastHeartbeatTime(now)
                .error(null)
                .build());
        refreshStatuses();
    }

    public synchronized void markFailed(String taskId, String error, long durationMillis) {
        String key = resolveTaskKey(taskId);
        if (key == null) {
            return;
        }
        AgentTeamTaskState state = states.get(key);
        if (state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        states.put(key, state.toBuilder()
                .status(AgentTeamTaskStatus.FAILED)
                .phase("failed")
                .detail(firstNonBlank(trimToNull(error), "Task failed."))
                .percent(Integer.valueOf(PERCENT_TERMINAL))
                .updatedAtEpochMs(now)
                .error(error)
                .durationMillis(durationMillis)
                .endTime(now)
                .lastHeartbeatTime(now)
                .build());
        refreshStatuses();
    }

    public synchronized void markStalledAsBlocked(String reason) {
        for (Map.Entry<String, AgentTeamTaskState> entry : states.entrySet()) {
            AgentTeamTaskState state = entry.getValue();
            if (state.getStatus() == AgentTeamTaskStatus.PENDING || state.getStatus() == AgentTeamTaskStatus.READY) {
                entry.setValue(state.toBuilder()
                        .status(AgentTeamTaskStatus.BLOCKED)
                        .phase("blocked")
                        .detail(reason)
                        .percent(Integer.valueOf(PERCENT_TERMINAL))
                        .updatedAtEpochMs(System.currentTimeMillis())
                        .error(reason)
                        .endTime(System.currentTimeMillis())
                        .build());
            }
        }
    }

    public synchronized boolean hasWorkRemaining() {
        for (AgentTeamTaskState state : states.values()) {
            if (state.getStatus() == AgentTeamTaskStatus.PENDING
                    || state.getStatus() == AgentTeamTaskStatus.READY
                    || state.getStatus() == AgentTeamTaskStatus.IN_PROGRESS) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean hasFailed() {
        for (AgentTeamTaskState state : states.values()) {
            if (state.getStatus() == AgentTeamTaskStatus.FAILED) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<AgentTeamTaskState> snapshot() {
        if (states.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamTaskState> all = new ArrayList<>();
        for (AgentTeamTaskState state : states.values()) {
            all.add(copy(state));
        }
        return all;
    }

    public synchronized int size() {
        return states.size();
    }

    private void refreshStatuses() {
        if (states.isEmpty()) {
            return;
        }
        for (Map.Entry<String, AgentTeamTaskState> entry : states.entrySet()) {
            AgentTeamTaskState state = entry.getValue();
            if (state.getStatus() == AgentTeamTaskStatus.IN_PROGRESS || state.isTerminal()) {
                continue;
            }

            AgentTeamTask task = state.getTask();
            List<String> dependencies = task == null ? null : task.getDependsOn();
            if (dependencies == null || dependencies.isEmpty()) {
                entry.setValue(state.toBuilder()
                        .status(AgentTeamTaskStatus.READY)
                        .phase("ready")
                        .detail("Ready for dispatch.")
                        .percent(Integer.valueOf(Math.max(percentOf(state), PERCENT_READY)))
                        .updatedAtEpochMs(selectUpdatedAt(state))
                        .error(null)
                        .build());
                continue;
            }

            boolean missing = false;
            boolean blocked = false;
            boolean allCompleted = true;
            for (String dependencyId : dependencies) {
                AgentTeamTaskState dependency = states.get(dependencyId);
                if (dependency == null) {
                    missing = true;
                    allCompleted = false;
                    break;
                }
                if (dependency.getStatus() == AgentTeamTaskStatus.FAILED
                        || dependency.getStatus() == AgentTeamTaskStatus.BLOCKED) {
                    blocked = true;
                    allCompleted = false;
                    break;
                }
                if (dependency.getStatus() != AgentTeamTaskStatus.COMPLETED) {
                    allCompleted = false;
                }
            }

            if (missing) {
                entry.setValue(state.toBuilder()
                        .status(AgentTeamTaskStatus.BLOCKED)
                        .phase("blocked")
                        .detail("Blocked: missing dependency.")
                        .percent(Integer.valueOf(PERCENT_TERMINAL))
                        .updatedAtEpochMs(selectUpdatedAt(state))
                        .error("missing dependency")
                        .build());
            } else if (blocked) {
                entry.setValue(state.toBuilder()
                        .status(AgentTeamTaskStatus.BLOCKED)
                        .phase("blocked")
                        .detail("Blocked: dependency failed.")
                        .percent(Integer.valueOf(PERCENT_TERMINAL))
                        .updatedAtEpochMs(selectUpdatedAt(state))
                        .error("dependency failed")
                        .build());
            } else if (allCompleted) {
                entry.setValue(state.toBuilder()
                        .status(AgentTeamTaskStatus.READY)
                        .phase("ready")
                        .detail("Dependencies satisfied.")
                        .percent(Integer.valueOf(Math.max(percentOf(state), PERCENT_READY)))
                        .updatedAtEpochMs(selectUpdatedAt(state))
                        .error(null)
                        .build());
            } else {
                entry.setValue(state.toBuilder()
                        .status(AgentTeamTaskStatus.PENDING)
                        .phase("pending")
                        .detail("Waiting for dependencies.")
                        .percent(Integer.valueOf(Math.max(percentOf(state), PERCENT_PLANNED)))
                        .updatedAtEpochMs(selectUpdatedAt(state))
                        .error(null)
                        .build());
            }
        }
    }

    private String resolveTaskKey(String taskId) {
        if (taskId == null) {
            return null;
        }
        String trimmed = taskId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (states.containsKey(trimmed)) {
            return trimmed;
        }
        String normalized = normalizeId(trimmed);
        if (normalized != null && states.containsKey(normalized)) {
            return normalized;
        }
        return null;
    }

    private List<String> normalizeDependencies(List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>();
        for (String dependency : dependencies) {
            String id = normalizeId(dependency);
            if (id != null && !normalized.contains(id)) {
                normalized.add(id);
            }
        }
        return normalized;
    }

    private String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.replaceAll("[^a-z0-9_\\-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeMemberId(String memberId) {
        if (memberId == null) {
            return null;
        }
        String normalized = memberId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isSameMember(String currentClaimedBy, String expectedMemberId) {
        String current = normalizeMemberId(currentClaimedBy);
        String expected = normalizeMemberId(expectedMemberId);
        if (expected == null) {
            return true;
        }
        return expected.equals(current);
    }

    private AgentTeamTaskState copy(AgentTeamTaskState state) {
        if (state == null) {
            return null;
        }
        return state.toBuilder().build();
    }

    private int percentOf(AgentTeamTaskState state) {
        Integer percent = state == null ? null : state.getPercent();
        return percent == null ? 0 : Math.max(0, Math.min(100, percent.intValue()));
    }

    private long selectUpdatedAt(AgentTeamTaskState state) {
        if (state == null) {
            return System.currentTimeMillis();
        }
        return state.getUpdatedAtEpochMs() > 0L ? state.getUpdatedAtEpochMs() : System.currentTimeMillis();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (trimToNull(value) != null) {
                return value.trim();
            }
        }
        return null;
    }
}
