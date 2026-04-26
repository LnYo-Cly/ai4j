package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import io.github.lnyocly.ai4j.agent.team.FileAgentTeamMessageBus;
import io.github.lnyocly.ai4j.agent.team.FileAgentTeamStateStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class CliTeamStateManager {

    private static final int DEFAULT_MESSAGE_LIMIT = 20;

    private final Path workspaceRoot;
    private final Path teamDirectory;
    private final FileAgentTeamStateStore stateStore;

    public CliTeamStateManager(Path workspaceRoot) {
        Path normalizedRoot = workspaceRoot == null
                ? Paths.get(".").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
        this.workspaceRoot = normalizedRoot;
        this.teamDirectory = normalizedRoot.resolve(".ai4j").resolve("teams");
        this.stateStore = new FileAgentTeamStateStore(teamDirectory.resolve("state"));
    }

    public List<String> listKnownTeamIds() {
        List<AgentTeamState> states = listStates();
        if (states.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<String>();
        for (AgentTeamState state : states) {
            if (state == null || isBlank(state.getTeamId())) {
                continue;
            }
            ids.add(state.getTeamId());
        }
        return ids;
    }

    public String renderListOutput() {
        List<AgentTeamState> states = listStates();
        if (states.isEmpty()) {
            return "teams: (none)";
        }
        StringBuilder builder = new StringBuilder("teams:\n");
        for (AgentTeamState rawState : states) {
            AgentTeamState state = hydrate(rawState);
            TaskSummary summary = summarizeTasks(state == null ? null : state.getTaskStates());
            int messageCount = state == null || state.getMessages() == null ? 0 : state.getMessages().size();
            builder.append("- ").append(firstNonBlank(state == null ? null : state.getTeamId(), "(team)"))
                    .append(" | updated=").append(formatTimestamp(state == null ? 0L : state.getUpdatedAt()))
                    .append(" | active=").append(state != null && state.isRunActive() ? "yes" : "no")
                    .append(" | tasks=").append(summary.total)
                    .append(" (running=").append(summary.running)
                    .append(", completed=").append(summary.completed)
                    .append(", failed=").append(summary.failed)
                    .append(", blocked=").append(summary.blocked).append(')')
                    .append(" | messages=").append(messageCount);
            String objective = clip(state == null ? null : state.getObjective(), 64);
            if (!isBlank(objective)) {
                builder.append(" | objective=").append(objective);
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    public String renderStatusOutput(String requestedTeamId) {
        ResolvedTeamState resolved = resolveState(requestedTeamId);
        if (resolved == null || resolved.state == null) {
            return teamMissingOutput(requestedTeamId);
        }
        AgentTeamState state = resolved.state;
        TaskSummary summary = summarizeTasks(state.getTaskStates());
        int messageCount = state.getMessages() == null ? 0 : state.getMessages().size();
        int memberCount = state.getMembers() == null ? 0 : state.getMembers().size();
        StringBuilder builder = new StringBuilder("team status:\n");
        builder.append("- teamId=").append(resolved.teamId).append('\n');
        builder.append("- storage=").append(teamDirectory).append('\n');
        builder.append("- updated=").append(formatTimestamp(state.getUpdatedAt()))
                .append(", active=").append(state.isRunActive() ? "yes" : "no").append('\n');
        builder.append("- objective=").append(firstNonBlank(clip(state.getObjective(), 220), "(none)")).append('\n');
        builder.append("- members=").append(memberCount)
                .append(", rounds=").append(state.getLastRounds())
                .append(", messages=").append(messageCount).append('\n');
        builder.append("- tasks=").append(summary.total)
                .append(" (pending=").append(summary.pending)
                .append(", ready=").append(summary.ready)
                .append(", running=").append(summary.running)
                .append(", completed=").append(summary.completed)
                .append(", failed=").append(summary.failed)
                .append(", blocked=").append(summary.blocked).append(')').append('\n');
        builder.append("- lastRunStartedAt=").append(formatTimestamp(state.getLastRunStartedAt()))
                .append(", lastRunCompletedAt=").append(formatTimestamp(state.getLastRunCompletedAt())).append('\n');
        builder.append("- lastOutput=").append(firstNonBlank(clip(state.getLastOutput(), 220), "(none)"));
        return builder.toString().trim();
    }

    public String renderMessagesOutput(String requestedTeamId, Integer limit) {
        ResolvedTeamState resolved = resolveState(requestedTeamId);
        if (resolved == null || resolved.state == null) {
            return teamMissingOutput(requestedTeamId);
        }
        int safeLimit = limit == null || limit.intValue() <= 0 ? DEFAULT_MESSAGE_LIMIT : limit.intValue();
        List<AgentTeamMessage> messages = loadMessages(resolved.teamId, resolved.state);
        if (messages.isEmpty()) {
            return "team messages:\n- teamId=" + resolved.teamId + "\n- count=0";
        }
        Collections.sort(messages, new Comparator<AgentTeamMessage>() {
            @Override
            public int compare(AgentTeamMessage left, AgentTeamMessage right) {
                long l = left == null ? 0L : left.getCreatedAt();
                long r = right == null ? 0L : right.getCreatedAt();
                return l == r ? 0 : (l < r ? 1 : -1);
            }
        });
        if (messages.size() > safeLimit) {
            messages = new ArrayList<AgentTeamMessage>(messages.subList(0, safeLimit));
        }

        StringBuilder builder = new StringBuilder("team messages:\n");
        builder.append("- teamId=").append(resolved.teamId).append('\n');
        builder.append("- count=").append(messages.size()).append('\n');
        for (AgentTeamMessage message : messages) {
            if (message == null) {
                continue;
            }
            builder.append("- ").append(formatTimestamp(message.getCreatedAt()))
                    .append(" | ").append(firstNonBlank(message.getFromMemberId(), "?"))
                    .append(" -> ").append(firstNonBlank(message.getToMemberId(), "*"));
            if (!isBlank(message.getType())) {
                builder.append(" [").append(message.getType()).append(']');
            }
            if (!isBlank(message.getTaskId())) {
                builder.append(" | task=").append(message.getTaskId());
            }
            if (!isBlank(message.getContent())) {
                builder.append(" | ").append(clip(singleLine(message.getContent()), 120));
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    public ResolvedTeamState resolveState(String requestedTeamId) {
        String normalized = trimToNull(requestedTeamId);
        AgentTeamState state = normalized == null ? latestState() : stateStore.load(normalized);
        if (state == null || isBlank(state.getTeamId())) {
            return null;
        }
        AgentTeamState hydrated = hydrate(state);
        return new ResolvedTeamState(hydrated.getTeamId(), hydrated);
    }

    public List<String> renderBoardLines(String requestedTeamId) {
        ResolvedTeamState resolved = resolveState(requestedTeamId);
        if (resolved == null || resolved.state == null) {
            return Collections.emptyList();
        }
        return TeamBoardRenderSupport.renderBoardLines(resolved.state);
    }

    public String renderResumeOutput(String requestedTeamId) {
        ResolvedTeamState resolved = resolveState(requestedTeamId);
        if (resolved == null || resolved.state == null) {
            return teamMissingOutput(requestedTeamId);
        }
        List<String> boardLines = TeamBoardRenderSupport.renderBoardLines(resolved.state);
        StringBuilder builder = new StringBuilder("team resumed: ").append(resolved.teamId);
        if (!boardLines.isEmpty()) {
            builder.append('\n').append(TeamBoardRenderSupport.renderBoardOutput(boardLines));
        }
        return builder.toString().trim();
    }

    private List<AgentTeamState> listStates() {
        return stateStore.list();
    }

    private AgentTeamState latestState() {
        List<AgentTeamState> states = listStates();
        return states.isEmpty() ? null : states.get(0);
    }

    private AgentTeamState hydrate(AgentTeamState state) {
        if (state == null || isBlank(state.getTeamId())) {
            return state;
        }
        return state.toBuilder()
                .messages(loadMessages(state.getTeamId(), state))
                .build();
    }

    private List<AgentTeamMessage> loadMessages(String teamId, AgentTeamState fallbackState) {
        if (isBlank(teamId)) {
            return fallbackState == null || fallbackState.getMessages() == null
                    ? Collections.<AgentTeamMessage>emptyList()
                    : new ArrayList<AgentTeamMessage>(fallbackState.getMessages());
        }
        Path mailboxFile = teamDirectory.resolve("mailbox").resolve(teamId + ".jsonl");
        if (Files.exists(mailboxFile)) {
            return new FileAgentTeamMessageBus(mailboxFile).snapshot();
        }
        return fallbackState == null || fallbackState.getMessages() == null
                ? Collections.<AgentTeamMessage>emptyList()
                : new ArrayList<AgentTeamMessage>(fallbackState.getMessages());
    }

    private TaskSummary summarizeTasks(List<AgentTeamTaskState> taskStates) {
        TaskSummary summary = new TaskSummary();
        if (taskStates == null || taskStates.isEmpty()) {
            return summary;
        }
        for (AgentTeamTaskState taskState : taskStates) {
            if (taskState == null || taskState.getStatus() == null) {
                continue;
            }
            summary.total++;
            AgentTeamTaskStatus status = taskState.getStatus();
            if (status == AgentTeamTaskStatus.PENDING) {
                summary.pending++;
            } else if (status == AgentTeamTaskStatus.READY) {
                summary.ready++;
            } else if (status == AgentTeamTaskStatus.IN_PROGRESS) {
                summary.running++;
            } else if (status == AgentTeamTaskStatus.COMPLETED) {
                summary.completed++;
            } else if (status == AgentTeamTaskStatus.FAILED) {
                summary.failed++;
            } else if (status == AgentTeamTaskStatus.BLOCKED) {
                summary.blocked++;
            }
        }
        return summary;
    }

    private String teamMissingOutput(String requestedTeamId) {
        String normalized = trimToNull(requestedTeamId);
        return normalized == null ? "team: (none)" : "team not found: " + normalized;
    }

    private String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0L) {
            return "(none)";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(epochMillis));
    }

    private String singleLine(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String clip(String value, int maxChars) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)) + "...";
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

    public static final class ResolvedTeamState {
        private final String teamId;
        private final AgentTeamState state;

        private ResolvedTeamState(String teamId, AgentTeamState state) {
            this.teamId = teamId;
            this.state = state;
        }

        public String getTeamId() {
            return teamId;
        }

        public AgentTeamState getState() {
            return state;
        }
    }

    private static final class TaskSummary {
        private int total;
        private int pending;
        private int ready;
        private int running;
        private int completed;
        private int failed;
        private int blocked;
    }
}
