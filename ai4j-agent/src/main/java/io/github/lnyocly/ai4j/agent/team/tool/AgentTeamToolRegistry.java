package io.github.lnyocly.ai4j.agent.team.tool;

import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentTeamToolRegistry implements AgentToolRegistry {

    public static final String TOOL_SEND_MESSAGE = "team_send_message";
    public static final String TOOL_BROADCAST = "team_broadcast";
    public static final String TOOL_LIST_TASKS = "team_list_tasks";
    public static final String TOOL_CLAIM_TASK = "team_claim_task";
    public static final String TOOL_RELEASE_TASK = "team_release_task";
    public static final String TOOL_REASSIGN_TASK = "team_reassign_task";
    public static final String TOOL_HEARTBEAT_TASK = "team_heartbeat_task";

    private static final Set<String> TOOL_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            TOOL_SEND_MESSAGE,
            TOOL_BROADCAST,
            TOOL_LIST_TASKS,
            TOOL_CLAIM_TASK,
            TOOL_RELEASE_TASK,
            TOOL_REASSIGN_TASK,
            TOOL_HEARTBEAT_TASK
    )));

    private final List<Object> tools;

    public AgentTeamToolRegistry() {
        this.tools = buildTools();
    }

    @Override
    public List<Object> getTools() {
        return new ArrayList<>(tools);
    }

    public static boolean supports(String toolName) {
        return toolName != null && TOOL_NAMES.contains(toolName);
    }

    private List<Object> buildTools() {
        List<Object> list = new ArrayList<>();
        list.add(createSendMessageTool());
        list.add(createBroadcastTool());
        list.add(createListTasksTool());
        list.add(createClaimTaskTool());
        list.add(createReleaseTaskTool());
        list.add(createReassignTaskTool());
        list.add(createHeartbeatTaskTool());
        return list;
    }

    private Tool createSendMessageTool() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<>();
        props.put("toMemberId", stringProperty("Receiver member id"));
        props.put("content", stringProperty("Message content"));
        props.put("type", stringProperty("Optional message type, default peer.message"));
        props.put("taskId", stringProperty("Optional task id for message threading"));
        return createTool(TOOL_SEND_MESSAGE,
                "Send a direct message to a teammate.",
                props,
                Arrays.asList("toMemberId", "content"));
    }

    private Tool createBroadcastTool() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<>();
        props.put("content", stringProperty("Broadcast message content"));
        props.put("type", stringProperty("Optional message type, default peer.broadcast"));
        props.put("taskId", stringProperty("Optional task id for message threading"));
        return createTool(TOOL_BROADCAST,
                "Broadcast a message to the whole team.",
                props,
                Arrays.asList("content"));
    }

    private Tool createListTasksTool() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<>();
        return createTool(TOOL_LIST_TASKS,
                "List current shared task states.",
                props,
                Collections.<String>emptyList());
    }

    private Tool createClaimTaskTool() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<>();
        props.put("taskId", stringProperty("Task id to claim"));
        return createTool(TOOL_CLAIM_TASK,
                "Claim a ready task for execution.",
                props,
                Arrays.asList("taskId"));
    }

    private Tool createReleaseTaskTool() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<>();
        props.put("taskId", stringProperty("Task id to release"));
        props.put("reason", stringProperty("Optional release reason"));
        return createTool(TOOL_RELEASE_TASK,
                "Release a claimed task back to the queue.",
                props,
                Arrays.asList("taskId"));
    }

    private Tool createReassignTaskTool() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<>();
        props.put("taskId", stringProperty("Task id to reassign"));
        props.put("toMemberId", stringProperty("Target teammate id"));
        return createTool(TOOL_REASSIGN_TASK,
                "Reassign your claimed task to another teammate.",
                props,
                Arrays.asList("taskId", "toMemberId"));
    }

    private Tool createHeartbeatTaskTool() {
        Map<String, Tool.Function.Property> props = new LinkedHashMap<>();
        props.put("taskId", stringProperty("Task id to heartbeat"));
        return createTool(TOOL_HEARTBEAT_TASK,
                "Heartbeat for an in-progress task to avoid timeout recovery.",
                props,
                Arrays.asList("taskId"));
    }

    private Tool createTool(String name,
                            String description,
                            Map<String, Tool.Function.Property> properties,
                            List<String> required) {
        Tool.Function.Parameter parameter = new Tool.Function.Parameter();
        parameter.setType("object");
        parameter.setProperties(properties);
        parameter.setRequired(required);

        Tool.Function function = new Tool.Function();
        function.setName(name);
        function.setDescription(description);
        function.setParameters(parameter);

        Tool tool = new Tool();
        tool.setType("function");
        tool.setFunction(function);
        return tool;
    }

    private Tool.Function.Property stringProperty(String description) {
        Tool.Function.Property property = new Tool.Function.Property();
        property.setType("string");
        property.setDescription(description);
        return property;
    }
}
