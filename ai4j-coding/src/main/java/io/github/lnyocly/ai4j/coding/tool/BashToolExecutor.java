package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.process.BashProcessInfo;
import io.github.lnyocly.ai4j.coding.process.BashProcessLogChunk;
import io.github.lnyocly.ai4j.coding.process.SessionProcessRegistry;
import io.github.lnyocly.ai4j.coding.shell.LocalShellCommandExecutor;
import io.github.lnyocly.ai4j.coding.shell.ShellCommandRequest;
import io.github.lnyocly.ai4j.coding.shell.ShellCommandResult;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.util.LinkedHashMap;
import java.util.Map;

public class BashToolExecutor implements ToolExecutor {

    private final WorkspaceContext workspaceContext;
    private final CodingAgentOptions options;
    private final SessionProcessRegistry processRegistry;
    private final LocalShellCommandExecutor shellCommandExecutor;

    public BashToolExecutor(WorkspaceContext workspaceContext,
                            CodingAgentOptions options,
                            SessionProcessRegistry processRegistry) {
        this.workspaceContext = workspaceContext;
        this.options = options;
        this.processRegistry = processRegistry;
        this.shellCommandExecutor = new LocalShellCommandExecutor(workspaceContext, options.getDefaultCommandTimeoutMs());
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        String action = arguments.getString("action");
        if (action == null || action.trim().isEmpty()) {
            action = "exec";
        }
        switch (action) {
            case "exec":
                return exec(arguments);
            case "start":
                return start(arguments);
            case "status":
                return status(arguments);
            case "logs":
                return logs(arguments);
            case "write":
                return write(arguments);
            case "stop":
                return stop(arguments);
            case "list":
                return list();
            default:
                throw new IllegalArgumentException("Unsupported bash action: " + action);
        }
    }

    private String exec(JSONObject arguments) throws Exception {
        ShellCommandResult result = shellCommandExecutor.execute(ShellCommandRequest.builder()
                .command(arguments.getString("command"))
                .workingDirectory(arguments.getString("cwd"))
                .timeoutMs(arguments.getLong("timeoutMs"))
                .build());
        return JSON.toJSONString(result);
    }

    private String start(JSONObject arguments) throws Exception {
        BashProcessInfo result = processRegistry.start(arguments.getString("command"), arguments.getString("cwd"));
        return JSON.toJSONString(result);
    }

    private String status(JSONObject arguments) {
        return JSON.toJSONString(processRegistry.status(arguments.getString("processId")));
    }

    private String logs(JSONObject arguments) {
        BashProcessLogChunk result = processRegistry.logs(
                arguments.getString("processId"),
                arguments.getLong("offset"),
                arguments.getInteger("limit")
        );
        return JSON.toJSONString(result);
    }

    private String write(JSONObject arguments) throws Exception {
        String processId = arguments.getString("processId");
        int bytesWritten = processRegistry.write(processId, arguments.getString("input"));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("process", processRegistry.status(processId));
        result.put("bytesWritten", bytesWritten);
        return JSON.toJSONString(result);
    }

    private String stop(JSONObject arguments) {
        return JSON.toJSONString(processRegistry.stop(arguments.getString("processId")));
    }

    private String list() {
        return JSON.toJSONString(processRegistry.list());
    }

    private JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(rawArguments);
    }
}
