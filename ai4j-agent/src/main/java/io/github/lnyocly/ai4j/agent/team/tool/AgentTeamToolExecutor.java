package io.github.lnyocly.ai4j.agent.team.tool;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.team.AgentTeamControl;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutors;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.util.AgentJson;

import java.util.ArrayList;
import java.util.List;

public class AgentTeamToolExecutor implements AsyncToolExecutor {

    private final AgentTeamControl control;
    private final String memberId;
    private final String defaultTaskId;
    private final ToolExecutor delegate;

    public AgentTeamToolExecutor(AgentTeamControl control,
                                 String memberId,
                                 String defaultTaskId,
                                 ToolExecutor delegate) {
        if (control == null) {
            throw new IllegalArgumentException("control is required");
        }
        if (memberId == null || memberId.trim().isEmpty()) {
            throw new IllegalArgumentException("memberId is required");
        }
        this.control = control;
        this.memberId = memberId.trim();
        this.defaultTaskId = defaultTaskId;
        this.delegate = delegate;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        if (call == null) {
            return null;
        }
        String toolName = call.getName();
        if (!AgentTeamToolRegistry.supports(toolName)) {
            if (delegate == null) {
                throw new IllegalStateException("toolExecutor is required for non-team tool: " + toolName);
            }
            return delegate.execute(call);
        }

        ObjectNode args = parseArguments(call.getArguments());
        if (AgentTeamToolRegistry.TOOL_SEND_MESSAGE.equals(toolName)) {
            return handleSendMessage(args);
        }
        if (AgentTeamToolRegistry.TOOL_BROADCAST.equals(toolName)) {
            return handleBroadcast(args);
        }
        if (AgentTeamToolRegistry.TOOL_READ_MESSAGES.equals(toolName)) {
            return handleReadMessages();
        }
        if (AgentTeamToolRegistry.TOOL_LIST_TASKS.equals(toolName)) {
            return handleListTasks();
        }
        if (AgentTeamToolRegistry.TOOL_CLAIM_TASK.equals(toolName)) {
            return handleClaimTask(args);
        }
        if (AgentTeamToolRegistry.TOOL_RELEASE_TASK.equals(toolName)) {
            return handleReleaseTask(args);
        }
        if (AgentTeamToolRegistry.TOOL_REASSIGN_TASK.equals(toolName)) {
            return handleReassignTask(args);
        }
        if (AgentTeamToolRegistry.TOOL_HEARTBEAT_TASK.equals(toolName)) {
            return handleHeartbeatTask(args);
        }
        throw new IllegalStateException("unsupported team tool: " + toolName);
    }

    /** Team-control operations are local and synchronous; preserve pending
     * results from the application delegate for all other tools. */
    @Override
    public AgentToolExecution start(AgentToolCall call) throws Exception {
        if (call == null) {
            return AgentToolExecution.completed(AgentToolResult.builder()
                    .status(AgentToolExecutionStatus.COMPLETED)
                    .build());
        }
        if (AgentTeamToolRegistry.supports(call.getName())) {
            return AgentToolExecution.completed(AgentToolResult.builder()
                    .name(call.getName())
                    .callId(call.getCallId())
                    .output(execute(call))
                    .status(AgentToolExecutionStatus.COMPLETED)
                    .build());
        }
        if (delegate == null) {
            throw new IllegalStateException("toolExecutor is required for non-team tool: " + call.getName());
        }
        return AsyncToolExecutors.start(delegate, call);
    }

    private String handleSendMessage(ObjectNode args) {
        String toMemberId = firstString(args, "toMemberId", "to", "memberId");
        String content = firstString(args, "content", "message", "text");
        String type = firstString(args, "type");
        String taskId = resolveTaskId(args, false);

        if (toMemberId == null) {
            throw new IllegalArgumentException("toMemberId is required");
        }
        if (type == null) {
            type = "peer.message";
        }
        if (content == null) {
            content = "";
        }

        control.sendMessage(memberId, toMemberId, type, taskId, content);
        ObjectNode result = baseResult(AgentTeamToolRegistry.TOOL_SEND_MESSAGE, true);
        result.put("toMemberId", toMemberId);
        if (taskId != null) {
            result.put("taskId", taskId);
        }
        return AgentJson.toJson(result);
    }

    private String handleBroadcast(ObjectNode args) {
        String content = firstString(args, "content", "message", "text");
        String type = firstString(args, "type");
        String taskId = resolveTaskId(args, false);

        if (type == null) {
            type = "peer.broadcast";
        }
        if (content == null) {
            content = "";
        }

        control.broadcastMessage(memberId, type, taskId, content);
        ObjectNode result = baseResult(AgentTeamToolRegistry.TOOL_BROADCAST, true);
        if (taskId != null) {
            result.put("taskId", taskId);
        }
        return AgentJson.toJson(result);
    }

    private String handleListTasks() {
        List<AgentTeamTaskState> tasks = control.listTaskStates();
        ObjectNode result = baseResult(AgentTeamToolRegistry.TOOL_LIST_TASKS, true);
        result.set("tasks", AgentJson.valueToTree(tasks == null ? new ArrayList<AgentTeamTaskState>() : tasks));
        return AgentJson.toJson(result);
    }

    private String handleReadMessages() {
        List<AgentTeamMessage> unread = control.readUnreadMessages(memberId);
        ObjectNode result = baseResult(AgentTeamToolRegistry.TOOL_READ_MESSAGES, true);
        int count = unread == null ? 0 : unread.size();
        result.put("count", count);
        result.put("hasNewMessages", count > 0);
        if (count > 0) {
            ArrayNode summaries = AgentJson.createArrayNode();
            for (AgentTeamMessage message : unread) {
                ObjectNode summary = AgentJson.createObjectNode();
                if (message.getFromMemberId() != null) {
                    summary.put("from", message.getFromMemberId());
                }
                if (message.getToMemberId() != null) {
                    summary.put("to", message.getToMemberId());
                }
                if (message.getType() != null) {
                    summary.put("type", message.getType());
                }
                if (message.getTaskId() != null) {
                    summary.put("taskId", message.getTaskId());
                }
                if (message.getContent() != null) {
                    summary.put("content", message.getContent());
                }
                summaries.add(summary);
            }
            result.set("messages", summaries);
        }
        return AgentJson.toJson(result);
    }

    private String handleClaimTask(ObjectNode args) {
        String taskId = resolveTaskId(args, true);
        boolean ok = control.claimTask(taskId, memberId);
        ObjectNode result = baseResult(AgentTeamToolRegistry.TOOL_CLAIM_TASK, ok);
        result.put("taskId", taskId);
        AgentTeamTaskState taskState = findTaskState(taskId);
        if (taskState != null) {
            result.set("taskState", AgentJson.valueToTree(taskState));
        }
        return AgentJson.toJson(result);
    }

    private String handleReleaseTask(ObjectNode args) {
        String taskId = resolveTaskId(args, true);
        String reason = firstString(args, "reason", "message");
        boolean ok = control.releaseTask(taskId, memberId, reason);
        ObjectNode result = baseResult(AgentTeamToolRegistry.TOOL_RELEASE_TASK, ok);
        result.put("taskId", taskId);
        AgentTeamTaskState taskState = findTaskState(taskId);
        if (taskState != null) {
            result.set("taskState", AgentJson.valueToTree(taskState));
        }
        return AgentJson.toJson(result);
    }

    private String handleReassignTask(ObjectNode args) {
        String taskId = resolveTaskId(args, true);
        String toMemberId = firstString(args, "toMemberId", "to", "memberId");
        if (toMemberId == null) {
            throw new IllegalArgumentException("toMemberId is required");
        }
        boolean ok = control.reassignTask(taskId, memberId, toMemberId);
        ObjectNode result = baseResult(AgentTeamToolRegistry.TOOL_REASSIGN_TASK, ok);
        result.put("taskId", taskId);
        result.put("toMemberId", toMemberId);
        AgentTeamTaskState taskState = findTaskState(taskId);
        if (taskState != null) {
            result.set("taskState", AgentJson.valueToTree(taskState));
        }
        return AgentJson.toJson(result);
    }

    private String handleHeartbeatTask(ObjectNode args) {
        String taskId = resolveTaskId(args, true);
        boolean ok = control.heartbeatTask(taskId, memberId);
        ObjectNode result = baseResult(AgentTeamToolRegistry.TOOL_HEARTBEAT_TASK, ok);
        result.put("taskId", taskId);
        AgentTeamTaskState taskState = findTaskState(taskId);
        if (taskState != null) {
            result.set("taskState", AgentJson.valueToTree(taskState));
        }
        return AgentJson.toJson(result);
    }

    private ObjectNode parseArguments(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return AgentJson.createObjectNode();
        }
        try {
            ObjectNode object = AgentJson.parseObjectStrictOrNull(raw);
            return object == null ? AgentJson.createObjectNode() : object;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid team tool arguments: " + raw);
        }
    }

    private String resolveTaskId(ObjectNode args, boolean required) {
        String taskId = firstString(args, "taskId", "id");
        if (taskId == null || taskId.trim().isEmpty()) {
            taskId = defaultTaskId;
        }
        if (required && (taskId == null || taskId.trim().isEmpty())) {
            throw new IllegalArgumentException("taskId is required");
        }
        return taskId;
    }

    private AgentTeamTaskState findTaskState(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return null;
        }
        List<AgentTeamTaskState> states = control.listTaskStates();
        if (states == null || states.isEmpty()) {
            return null;
        }
        for (AgentTeamTaskState state : states) {
            if (state == null || state.getTaskId() == null) {
                continue;
            }
            if (taskId.equals(state.getTaskId())) {
                return state;
            }
        }
        return null;
    }

    private String firstString(ObjectNode args, String... keys) {
        if (args == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = AgentJson.text(args, key);
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private ObjectNode baseResult(String action, boolean ok) {
        ObjectNode object = AgentJson.createObjectNode();
        object.put("action", action);
        object.put("ok", ok);
        object.put("memberId", memberId);
        return object;
    }
}
