package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolInputException;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public class WriteFileToolExecutor implements ToolExecutor {

    private final WorkspaceContext workspaceContext;

    public WriteFileToolExecutor(WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        String path = safeTrim(arguments.getString("path"));
        if (isBlank(path)) {
            throw invalid("write_file requires a non-empty path", null);
        }
        if (!arguments.containsKey("content") || arguments.get("content") == null) {
            throw invalid("write_file requires content", null);
        }
        if (!(arguments.get("content") instanceof String)) {
            throw invalid("write_file content must be a string", null);
        }
        String content = arguments.getString("content");
        String mode = firstNonBlank(safeTrim(arguments.getString("mode")), "overwrite").toLowerCase(Locale.ROOT);
        if (!"create".equals(mode) && !"overwrite".equals(mode) && !"append".equals(mode)) {
            throw invalid("unsupported write mode: " + mode, null);
        }
        Path file;
        try {
            file = WorkspacePathGuard.resolveForWrite(workspaceContext, path);
        } catch (AgentToolInputException input) {
            throw input;
        } catch (IllegalArgumentException input) {
            throw invalid("write_file path is invalid: " + message(input), input);
        }
        if (Files.exists(file) && Files.isDirectory(file)) {
            throw invalid("Target is a directory: " + path, null);
        }

        boolean existed = Files.exists(file);
        boolean appended = false;
        boolean created = false;
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);

        if ("create".equals(mode) && existed) {
            throw invalid("File already exists: " + path, null);
        }

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if ("create".equals(mode)) {
            Files.write(file, bytes, new OpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE});
            created = true;
        } else if ("overwrite".equals(mode)) {
            Files.write(file, bytes, new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE});
            created = !existed;
        } else if ("append".equals(mode)) {
            Files.write(file, bytes, new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE});
            created = !existed;
            appended = true;
        }

        JSONObject result = new JSONObject();
        result.put("path", path);
        result.put("resolvedPath", file.toString());
        result.put("mode", mode);
        result.put("created", created);
        result.put("appended", appended);
        result.put("bytesWritten", bytes.length);
        return JSON.toJSONString(result);
    }

    private JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            JSONObject arguments = JSON.parseObject(rawArguments);
            if (arguments == null) {
                throw invalid("write_file arguments must be a JSON object", null);
            }
            return arguments;
        } catch (AgentToolInputException input) {
            throw input;
        } catch (RuntimeException parseFailure) {
            throw invalid("write_file arguments must be valid JSON: " + message(parseFailure), parseFailure);
        }
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private AgentToolInputException invalid(String message, Throwable cause) {
        return cause == null ? new AgentToolInputException(message) : new AgentToolInputException(message, cause);
    }

    private String message(Throwable failure) {
        return failure == null || failure.getMessage() == null ? "invalid input" : failure.getMessage();
    }
}
