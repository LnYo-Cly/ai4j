package io.github.lnyocly.ai4j.agent.team.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.team.AgentTeamControl;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.List;

public class AgentTeamToolExecutor implements ToolExecutor {

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

        JSONObject args = parseArguments(call.getArguments());
        if (AgentTeamToolRegistry.TOOL_SEND_MESSAGE.equals(toolName)) {
            return handleSendMessage(args);
        }
        if (AgentTeamToolRegistry.TOOL_BROADCAST.equals(toolName)) {
            return handleBroadcast(args);
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

    private String handleSendMessage(JSONObject args) {
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
        JSONObject result = baseResult(AgentTeamToolRegistry.TOOL_SEND_MESSAGE, true);
        result.put("toMemberId", toMemberId);
        result.put("taskId", taskId);
        return result.toJSONString();
    }

    private String handleBroadcast(JSONObject args) {
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
        JSONObject result = baseResult(AgentTeamToolRegistry.TOOL_BROADCAST, true);
        result.put("taskId", taskId);
        return result.toJSONString();
    }

    private String handleListTasks() {
        List<AgentTeamTaskState> tasks = control.listTaskStates();
        JSONObject result = baseResult(AgentTeamToolRegistry.TOOL_LIST_TASKS, true);
        result.put("tasks", tasks == null ? new ArrayList<AgentTeamTaskState>() : tasks);
        return result.toJSONString();
    }

    private String handleClaimTask(JSONObject args) {
        String taskId = resolveTaskId(args, true);
        boolean ok = control.claimTask(taskId, memberId);
        JSONObject result = baseResult(AgentTeamToolRegistry.TOOL_CLAIM_TASK, ok);
        result.put("taskId", taskId);
        result.put("taskState", findTaskState(taskId));
        return result.toJSONString();
    }

    private String handleReleaseTask(JSONObject args) {
        String taskId = resolveTaskId(args, true);
        String reason = firstString(args, "reason", "message");
        boolean ok = control.releaseTask(taskId, memberId, reason);
        JSONObject result = baseResult(AgentTeamToolRegistry.TOOL_RELEASE_TASK, ok);
        result.put("taskId", taskId);
        result.put("taskState", findTaskState(taskId));
        return result.toJSONString();
    }

    private String handleReassignTask(JSONObject args) {
        String taskId = resolveTaskId(args, true);
        String toMemberId = firstString(args, "toMemberId", "to", "memberId");
        if (toMemberId == null) {
            throw new IllegalArgumentException("toMemberId is required");
        }
        boolean ok = control.reassignTask(taskId, memberId, toMemberId);
        JSONObject result = baseResult(AgentTeamToolRegistry.TOOL_REASSIGN_TASK, ok);
        result.put("taskId", taskId);
        result.put("toMemberId", toMemberId);
        result.put("taskState", findTaskState(taskId));
        return result.toJSONString();
    }

    private String handleHeartbeatTask(JSONObject args) {
        String taskId = resolveTaskId(args, true);
        boolean ok = control.heartbeatTask(taskId, memberId);
        JSONObject result = baseResult(AgentTeamToolRegistry.TOOL_HEARTBEAT_TASK, ok);
        result.put("taskId", taskId);
        result.put("taskState", findTaskState(taskId));
        return result.toJSONString();
    }

    private JSONObject parseArguments(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            JSONObject object = JSON.parseObject(raw);
            return object == null ? new JSONObject() : object;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid team tool arguments: " + raw);
        }
    }

    private String resolveTaskId(JSONObject args, boolean required) {
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

    private String firstString(JSONObject args, String... keys) {
        if (args == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = args.getString(key);
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private JSONObject baseResult(String action, boolean ok) {
        JSONObject object = new JSONObject();
        object.put("action", action);
        object.put("ok", ok);
        object.put("memberId", memberId);
        return object;
    }
}
