package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolInputException;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.process.BashProcessInfo;
import io.github.lnyocly.ai4j.coding.process.BashProcessLogChunk;
import io.github.lnyocly.ai4j.coding.process.SessionProcessRegistry;
import io.github.lnyocly.ai4j.coding.shell.LocalShellCommandExecutor;
import io.github.lnyocly.ai4j.coding.shell.ShellCommandExecutor;
import io.github.lnyocly.ai4j.coding.shell.ShellCommandRequest;
import io.github.lnyocly.ai4j.coding.shell.ShellCommandResult;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.util.LinkedHashMap;
import java.util.Map;

public class BashToolExecutor implements ToolExecutor {

    /** Default per-stream output cap: 10 MiB. Overridable via system property. */
    static final long DEFAULT_OUTPUT_LIMIT_BYTES = resolveOutputLimitBytes();
    private static final long MIN_OUTPUT_LIMIT_BYTES = 1024L;
    private static final String TRUNCATION_MARKER_FORMAT =
            "\n[... truncated %d bytes — output exceeded %d-byte limit ...]\n";

    private final WorkspaceContext workspaceContext;
    private final CodingAgentOptions options;
    private final SessionProcessRegistry processRegistry;
    private final ShellCommandExecutor shellCommandExecutor;

    public BashToolExecutor(WorkspaceContext workspaceContext,
                            CodingAgentOptions options,
                            SessionProcessRegistry processRegistry) {
        this(workspaceContext, resolveOptions(options), processRegistry,
                new LocalShellCommandExecutor(workspaceContext, resolveOptions(options).getDefaultCommandTimeoutMs()));
    }

    public BashToolExecutor(WorkspaceContext workspaceContext,
                            CodingAgentOptions options,
                            SessionProcessRegistry processRegistry,
                            ShellCommandExecutor shellCommandExecutor) {
        this.workspaceContext = workspaceContext;
        this.options = resolveOptions(options);
        this.processRegistry = processRegistry;
        this.shellCommandExecutor = shellCommandExecutor == null
                ? new LocalShellCommandExecutor(workspaceContext, this.options.getDefaultCommandTimeoutMs())
                : shellCommandExecutor;
    }

    private static CodingAgentOptions resolveOptions(CodingAgentOptions options) {
        return options == null ? CodingAgentOptions.builder().build() : options;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        boolean processTool = call != null && CodingToolNames.BASH_PROCESS.equals(call.getName());
        String action = arguments.getString("action");
        if (action == null || action.trim().isEmpty()) {
            if (processTool) {
                throw invalid("bash_process requires an action", null);
            }
            action = "exec";
        }
        action = action.trim().toLowerCase(java.util.Locale.ROOT);
        validateAction(arguments, action, processTool);
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
                throw invalid("Unsupported " + (processTool ? "bash_process" : "bash") + " action: " + action, null);
        }
    }

    private String exec(JSONObject arguments) throws Exception {
        ShellCommandResult result = shellCommandExecutor.execute(ShellCommandRequest.builder()
                .command(arguments.getString("command"))
                .workingDirectory(arguments.getString("cwd"))
                .timeoutMs(arguments.getLong("timeoutMs"))
                .build());
        result.setStdout(capOutput(result.getStdout()));
        result.setStderr(capOutput(result.getStderr()));
        return JSON.toJSONString(result);
    }

    /**
     * Truncate a stdout/stderr string to the configured byte limit, preserving the head and tail
     * and inserting a visible marker in the middle. Returns the original string when it fits.
     */
    static String capOutput(String output) {
        return capOutput(output, DEFAULT_OUTPUT_LIMIT_BYTES);
    }

    static String capOutput(String output, long limitBytes) {
        if (output == null || output.isEmpty()) {
            return output;
        }
        byte[] bytes = output.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= limitBytes) {
            return output;
        }
        long effectiveLimit = Math.max(MIN_OUTPUT_LIMIT_BYTES, limitBytes);
        long half = effectiveLimit / 2;
        String head = new String(bytes, 0, (int) half, java.nio.charset.StandardCharsets.UTF_8);
        String tail = new String(bytes, (int) (bytes.length - half), (int) half, java.nio.charset.StandardCharsets.UTF_8);
        long truncated = bytes.length - effectiveLimit;
        String marker = String.format(java.util.Locale.ROOT, TRUNCATION_MARKER_FORMAT, truncated, effectiveLimit);
        return head + marker + tail;
    }

    private static long resolveOutputLimitBytes() {
        String raw = System.getProperty("ai4j.bash.output-limit-mb");
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                long mb = Long.parseLong(raw.trim());
                if (mb > 0) {
                    return mb * 1024L * 1024L;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 10L * 1024L * 1024L;
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
        try {
            JSONObject arguments = JSON.parseObject(rawArguments);
            if (arguments == null) {
                throw invalid("bash arguments must be a JSON object", null);
            }
            return arguments;
        } catch (AgentToolInputException input) {
            throw input;
        } catch (RuntimeException parseFailure) {
            throw invalid("bash arguments must be valid JSON: " + message(parseFailure), parseFailure);
        }
    }

    private void validateAction(JSONObject arguments, String action, boolean processTool) {
        if (("exec".equals(action) || "start".equals(action))
                && isBlank(arguments.getString("command"))) {
            throw invalid("bash " + action + " requires a non-empty command", null);
        }
        if (("status".equals(action) || "logs".equals(action)
                || "stop".equals(action) || "write".equals(action))
                && isBlank(arguments.getString("processId"))) {
            throw invalid("bash " + action + " requires a processId", null);
        }
        if (!"exec".equals(action) && !"start".equals(action)
                && !"status".equals(action) && !"logs".equals(action)
                && !"stop".equals(action) && !"write".equals(action)
                && !"list".equals(action)) {
            throw invalid("Unsupported " + (processTool ? "bash_process" : "bash") + " action: " + action, null);
        }
    }

    private AgentToolInputException invalid(String message, Throwable cause) {
        return cause == null ? new AgentToolInputException(message) : new AgentToolInputException(message, cause);
    }

    private String message(Throwable failure) {
        return failure == null || failure.getMessage() == null ? "invalid input" : failure.getMessage();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
