package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            throw new IllegalArgumentException("path is required");
        }
        String content = arguments.containsKey("content") && arguments.get("content") != null
                ? arguments.getString("content")
                : "";
        String mode = firstNonBlank(safeTrim(arguments.getString("mode")), "overwrite").toLowerCase(Locale.ROOT);
        Path file = resolvePath(path);
        if (Files.exists(file) && Files.isDirectory(file)) {
            throw new IllegalArgumentException("Target is a directory: " + path);
        }

        boolean existed = Files.exists(file);
        boolean appended = false;
        boolean created;
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if ("create".equals(mode)) {
            if (existed) {
                throw new IllegalArgumentException("File already exists: " + path);
            }
            Files.write(file, bytes, new OpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE});
            created = true;
        } else if ("overwrite".equals(mode)) {
            Files.write(file, bytes, new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE});
            created = !existed;
        } else if ("append".equals(mode)) {
            Files.write(file, bytes, new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE});
            created = !existed;
            appended = true;
        } else {
            throw new IllegalArgumentException("Unsupported write mode: " + mode);
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

    private Path resolvePath(String path) {
        Path candidate = Paths.get(path);
        if (candidate.isAbsolute()) {
            return candidate.toAbsolutePath().normalize();
        }
        Path root = workspaceContext == null ? Paths.get(".").toAbsolutePath().normalize() : workspaceContext.getRoot();
        return root.resolve(path).toAbsolutePath().normalize();
    }

    private JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(rawArguments);
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
}
