package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.AgentsMdStore;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.nio.file.Path;

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
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        String action = arguments.getString("action");
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
        JSONObject result = new JSONObject();
        result.put("action", "read");
        result.put("path", relativized());
        result.put("exists", store.exists());
        result.put("content", content);
        return JSON.toJSONString(result);
    }

    private String write(JSONObject arguments) throws Exception {
        String content = arguments.containsKey("content") && arguments.get("content") != null
                ? arguments.getString("content")
                : "";
        Path file = store.write(content);
        JSONObject result = new JSONObject();
        result.put("action", "write");
        result.put("path", relativized());
        result.put("resolvedPath", file.toString());
        result.put("success", true);
        return JSON.toJSONString(result);
    }

    private String append(JSONObject arguments) throws Exception {
        String text = arguments.containsKey("text") && arguments.get("text") != null
                ? arguments.getString("text")
                : "";
        Path file = store.append(text);
        JSONObject result = new JSONObject();
        result.put("action", "append");
        result.put("path", relativized());
        result.put("resolvedPath", file.toString());
        result.put("success", true);
        return JSON.toJSONString(result);
    }

    private String relativized() {
        Path root = workspaceContext.getRoot();
        Path agentsMd = store.resolveAgentsMdPath();
        if (agentsMd.startsWith(root)) {
            return root.relativize(agentsMd).toString().replace('\\', '/');
        }
        return agentsMd.toString().replace('\\', '/');
    }

    private JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(rawArguments);
    }
}
