package io.github.lnyocly.ai4j.coding.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolInputException;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.CodingJson;
import io.github.lnyocly.ai4j.coding.workspace.AgentsMdStore;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateAgentsMdToolExecutor implements ToolExecutor {

    private final WorkspaceContext workspaceContext;
    private final AgentsMdStore store;

    public UpdateAgentsMdToolExecutor(WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
        this.store = new AgentsMdStore(workspaceContext);
    }

    public UpdateAgentsMdToolExecutor(WorkspaceContext workspaceContext, AgentsMdStore store) {
        this.workspaceContext = workspaceContext;
        this.store = store;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        ObjectNode arguments = parseArguments(call == null ? null : call.getArguments());
        String action = CodingJson.getString(arguments, "action");
        if (action == null || action.trim().isEmpty()) {
            action = "read";
        }
        switch (action) {
            case "read":
                return read();
            case "write":
                return write(arguments);
            case "append":
                return append(arguments);
            default:
                throw new IllegalArgumentException("Unsupported action: " + action);
        }
    }

    private String read() throws Exception {
        String content = store.read();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("action", "read");
        result.put("path", relativized());
        result.put("exists", store.exists());
        result.put("content", content);
        return CodingJson.toJson(result);
    }

    private String write(ObjectNode arguments) throws Exception {
        String content = arguments.hasNonNull("content")
                ? CodingJson.getString(arguments, "content")
                : "";
        Path file = store.write(content);
        ObjectNode result = CodingJson.newObject();
        result.put("action", "write");
        result.put("path", relativized());
        result.put("resolvedPath", file.toString());
        result.put("success", true);
        return CodingJson.toJson(result);
    }

    private String append(ObjectNode arguments) throws Exception {
        String text = arguments.hasNonNull("text")
                ? CodingJson.getString(arguments, "text")
                : "";
        Path file = store.append(text);
        ObjectNode result = CodingJson.newObject();
        result.put("action", "append");
        result.put("path", relativized());
        result.put("resolvedPath", file.toString());
        result.put("success", true);
        return CodingJson.toJson(result);
    }

    private String relativized() {
        Path root = workspaceContext.getRoot();
        Path agentsMd = store.resolveAgentsMdPath();
        if (agentsMd.startsWith(root)) {
            return root.relativize(agentsMd).toString().replace('\\', '/');
        }
        return agentsMd.toString().replace('\\', '/');
    }

    private ObjectNode parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return CodingJson.newObject();
        }
        try {
            ObjectNode arguments = CodingJson.parseObject(rawArguments);
            if (arguments == null) {
                throw new AgentToolInputException("update_agents_md arguments must be a JSON object");
            }
            return arguments;
        } catch (AgentToolInputException input) {
            throw input;
        } catch (RuntimeException parseFailure) {
            throw new AgentToolInputException("update_agents_md arguments must be valid JSON: " + parseFailure.getMessage(), parseFailure);
        }
    }
}
