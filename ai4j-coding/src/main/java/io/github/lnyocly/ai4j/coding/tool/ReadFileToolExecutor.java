package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceFileReadResult;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceFileService;

public class ReadFileToolExecutor implements ToolExecutor {

    private final WorkspaceFileService workspaceFileService;
    private final CodingAgentOptions options;

    public ReadFileToolExecutor(WorkspaceFileService workspaceFileService, CodingAgentOptions options) {
        this.workspaceFileService = workspaceFileService;
        this.options = options;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        WorkspaceFileReadResult result = workspaceFileService.readFile(
                arguments.getString("path"),
                arguments.getInteger("startLine"),
                arguments.getInteger("endLine"),
                arguments.getInteger("maxChars") == null ? options.getDefaultReadMaxChars() : arguments.getInteger("maxChars")
        );
        return JSON.toJSONString(result);
    }

    private JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(rawArguments);
    }
}
