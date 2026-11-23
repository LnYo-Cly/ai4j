package io.github.lnyocly.ai4j.coding.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolInputException;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingJson;
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
        ObjectNode arguments = parseArguments(call == null ? null : call.getArguments());
        String path = CodingJson.getString(arguments, "path");
        if (path == null || path.trim().isEmpty()) {
            throw invalid("read_file requires a non-empty path", null);
        }
        try {
            Integer startLine = CodingJson.getInteger(arguments, "startLine");
            Integer endLine = CodingJson.getInteger(arguments, "endLine");
            Integer maxChars = CodingJson.getInteger(arguments, "maxChars");
            WorkspaceFileReadResult result = workspaceFileService.readFile(
                    path.trim(),
                    startLine,
                    endLine,
                    maxChars == null ? Integer.valueOf(options.getDefaultReadMaxChars()) : maxChars
            );
            return CodingJson.toJson(result);
        } catch (AgentToolInputException input) {
            throw input;
        } catch (IllegalArgumentException input) {
            throw invalid("read_file arguments are invalid: " + message(input), input);
        }
    }

    private ObjectNode parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return CodingJson.newObject();
        }
        try {
            ObjectNode arguments = CodingJson.parseObject(rawArguments);
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
