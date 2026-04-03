package io.github.lnyocly.ai4j.agent.subagent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StaticSubAgentRegistry implements SubAgentRegistry {

    private final Map<String, RuntimeSubAgent> subAgents;

    public StaticSubAgentRegistry(List<SubAgentDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            this.subAgents = Collections.emptyMap();
            return;
        }
        Map<String, RuntimeSubAgent> map = new LinkedHashMap<>();
        for (SubAgentDefinition definition : definitions) {
            RuntimeSubAgent runtimeSubAgent = RuntimeSubAgent.from(definition);
            if (map.containsKey(runtimeSubAgent.toolName)) {
                throw new IllegalArgumentException("duplicate subagent tool name: " + runtimeSubAgent.toolName);
            }
            map.put(runtimeSubAgent.toolName, runtimeSubAgent);
        }
        this.subAgents = map;
    }

    @Override
    public List<Object> getTools() {
        if (subAgents.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> tools = new ArrayList<>();
        for (RuntimeSubAgent subAgent : subAgents.values()) {
            tools.add(subAgent.tool);
        }
        return tools;
    }

    @Override
    public boolean supports(String toolName) {
        return toolName != null && subAgents.containsKey(toolName);
    }

    @Override
    public SubAgentDefinition getDefinition(String toolName) {
        RuntimeSubAgent subAgent = toolName == null ? null : subAgents.get(toolName);
        return subAgent == null ? null : subAgent.toDefinition();
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        if (call == null || call.getName() == null) {
            throw new IllegalArgumentException("subagent tool call is invalid");
        }
        RuntimeSubAgent subAgent = subAgents.get(call.getName());
        if (subAgent == null) {
            throw new IllegalArgumentException("unknown subagent tool: " + call.getName());
        }
        String input = resolveInput(call.getArguments());
        AgentResult result = subAgent.invoke(input);
        JSONObject payload = new JSONObject();
        payload.put("subagent", subAgent.name);
        payload.put("toolName", subAgent.toolName);
        payload.put("output", result == null ? null : result.getOutputText());
        payload.put("steps", result == null ? 0 : result.getSteps());
        return payload.toJSONString();
    }

    private String resolveInput(String arguments) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject obj = JSON.parseObject(arguments);
            String task = trimToNull(obj.getString("task"));
            if (task == null) {
                task = trimToNull(obj.getString("input"));
            }
            String context = trimToNull(obj.getString("context"));
            if (task == null) {
                return arguments;
            }
            if (context == null) {
                return task;
            }
            return task + "\n\nContext:\n" + context;
        } catch (Exception e) {
            return arguments;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class RuntimeSubAgent {

        private final String name;
        private final String toolName;
        private final Agent agent;
        private final SubAgentSessionMode sessionMode;
        private final Tool tool;
        private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

        private RuntimeSubAgent(String name, String toolName, Agent agent, SubAgentSessionMode sessionMode, Tool tool) {
            this.name = name;
            this.toolName = toolName;
            this.agent = agent;
            this.sessionMode = sessionMode;
            this.tool = tool;
        }

        private AgentResult invoke(String input) throws Exception {
            if (sessionMode == SubAgentSessionMode.REUSE_SESSION) {
                AgentSession session = sessions.computeIfAbsent(toolName, key -> agent.newSession());
                synchronized (session) {
                    return session.run(AgentRequest.builder().input(input).build());
                }
            }
            AgentSession session = agent.newSession();
            return session.run(AgentRequest.builder().input(input).build());
        }

        private static RuntimeSubAgent from(SubAgentDefinition definition) {
            if (definition == null) {
                throw new IllegalArgumentException("subagent definition cannot be null");
            }
            if (definition.getAgent() == null) {
                throw new IllegalArgumentException("subagent agent is required");
            }
            String name = trimToNull(definition.getName());
            if (name == null) {
                throw new IllegalArgumentException("subagent name is required");
            }
            String description = trimToNull(definition.getDescription());
            if (description == null) {
                description = "Delegate a specialized task to subagent " + name;
            }
            String toolName = trimToNull(definition.getToolName());
            if (toolName == null) {
                toolName = "subagent_" + normalizeToolName(name);
            }
            Tool tool = createTool(toolName, description);
            SubAgentSessionMode sessionMode = definition.getSessionMode() == null
                    ? SubAgentSessionMode.NEW_SESSION
                    : definition.getSessionMode();
            return new RuntimeSubAgent(name, toolName, definition.getAgent(), sessionMode, tool);
        }

        private SubAgentDefinition toDefinition() {
            return SubAgentDefinition.builder()
                    .name(name)
                    .description(tool == null || tool.getFunction() == null ? null : tool.getFunction().getDescription())
                    .toolName(toolName)
                    .agent(agent)
                    .sessionMode(sessionMode)
                    .build();
        }

        private static Tool createTool(String toolName, String description) {
            Tool.Function.Property taskProperty = new Tool.Function.Property();
            taskProperty.setType("string");
            taskProperty.setDescription("Task to delegate to this subagent");

            Tool.Function.Property contextProperty = new Tool.Function.Property();
            contextProperty.setType("string");
            contextProperty.setDescription("Optional extra context for the task");

            Map<String, Tool.Function.Property> properties = new LinkedHashMap<>();
            properties.put("task", taskProperty);
            properties.put("context", contextProperty);

            Tool.Function.Parameter parameter = new Tool.Function.Parameter("object", properties, Arrays.asList("task"));
            Tool.Function function = new Tool.Function(toolName, description, parameter);

            Tool tool = new Tool();
            tool.setType("function");
            tool.setFunction(function);
            return tool;
        }

        private static String normalizeToolName(String raw) {
            String normalized = raw.toLowerCase().replaceAll("[^a-z0-9_]", "_");
            normalized = normalized.replaceAll("_+", "_");
            normalized = normalized.replaceAll("^_+", "");
            normalized = normalized.replaceAll("_+$", "");
            if (normalized.isEmpty()) {
                normalized = "agent";
            }
            if (Character.isDigit(normalized.charAt(0))) {
                normalized = "agent_" + normalized;
            }
            return normalized;
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
