package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolInputException;
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
        String path = arguments.getString("path");
        if (path == null || path.trim().isEmpty()) {
            throw invalid("read_file requires a non-empty path", null);
        }
        try {
            Integer startLine = arguments.getInteger("startLine");
            Integer endLine = arguments.getInteger("endLine");
            Integer maxChars = arguments.getInteger("maxChars");
            WorkspaceFileReadResult result = workspaceFileService.readFile(
                    path.trim(),
                    startLine,
                    endLine,
                    maxChars == null ? options.getDefaultReadMaxChars() : maxChars
            );
            return JSON.toJSONString(result);
        } catch (AgentToolInputException input) {
            throw input;
        } catch (IllegalArgumentException input) {
            throw invalid("read_file arguments are invalid: " + message(input), input);
        }
    }

    private JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            JSONObject arguments = JSON.parseObject(rawArguments);
            if (arguments == null) {
                throw invalid("read_file arguments must be a JSON object", null);
            }
            return arguments;
        } catch (AgentToolInputException input) {
            throw input;
        } catch (RuntimeException parseFailure) {
            throw invalid("read_file arguments must be valid JSON: " + message(parseFailure), parseFailure);
        }
    }

    private AgentToolInputException invalid(String message, Throwable cause) {
        return cause == null ? new AgentToolInputException(message) : new AgentToolInputException(message, cause);
    }

    private String message(Throwable failure) {
        return failure == null || failure.getMessage() == null ? "invalid input" : failure.getMessage();
    }
}
