package io.github.lnyocly.ai4j.coding.tool;

import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CodingToolRegistryFactory {

    private CodingToolRegistryFactory() {
    }

    public static AgentToolRegistry createBuiltInRegistry() {
        return new StaticToolRegistry(Arrays.<Object>asList(
                buildBashTool(),
                buildReadFileTool(),
                buildWriteFileTool(),
                buildApplyPatchTool()
        ));
    }

    private static Tool buildReadFileTool() {
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<>();
        properties.put("path", property("string", "Relative file path inside the workspace, or an absolute path inside an approved read-only skill root."));
        properties.put("startLine", property("integer", "First line number to read, starting from 1."));
        properties.put("endLine", property("integer", "Last line number to read, inclusive."));
        properties.put("maxChars", property("integer", "Maximum characters to return."));
        return tool(
                CodingToolNames.READ_FILE,
                "Read a text file from the workspace or from an approved read-only skill directory.",
                properties,
                Arrays.asList("path")
        );
    }

    private static Tool buildBashTool() {
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<>();
        properties.put("action", property("string", "bash action to perform.", Arrays.asList("exec", "start", "status", "logs", "write", "stop", "list")));
        properties.put("command", property("string", "Command string to execute. Use exec for self-terminating commands; use start for interactive or long-running commands."));
        properties.put("cwd", property("string", "Relative working directory inside the workspace."));
        properties.put("timeoutMs", property("integer", "Execution timeout in milliseconds for exec."));
        properties.put("processId", property("string", "Background process identifier."));
        properties.put("offset", property("integer", "Log cursor offset."));
        properties.put("limit", property("integer", "Maximum log characters to return."));
        properties.put("input", property("string", "Text written to stdin for a background process started with action=start."));
        return tool(
                CodingToolNames.BASH,
                "Execute non-interactive shell commands or manage interactive/background shell processes inside the workspace.",
                properties,
                Arrays.asList("action")
        );
    }

    private static Tool buildWriteFileTool() {
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<String, Tool.Function.Property>();
        properties.put("path", property("string", "File path to write. Relative paths resolve from the workspace root; absolute paths are allowed."));
        properties.put("content", property("string", "Full text content to write."));
        properties.put("mode", property("string", "Write mode.", Arrays.asList("create", "overwrite", "append")));
        return tool(
                CodingToolNames.WRITE_FILE,
                "Create, overwrite, or append a text file.",
                properties,
                Arrays.asList("path", "content")
        );
    }

    private static Tool buildApplyPatchTool() {
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<String, Tool.Function.Property>();
        properties.put("patch", property("string", "Patch text to apply. Must include *** Begin Patch and *** End Patch envelope."));
        return tool(
                CodingToolNames.APPLY_PATCH,
                "Apply a structured patch to workspace files.",
                properties,
                Arrays.asList("patch")
        );
    }

    private static Tool tool(String name,
                             String description,
                             Map<String, Tool.Function.Property> properties,
                             List<String> required) {
        Tool.Function.Parameter parameter = new Tool.Function.Parameter("object", properties, required);
        Tool.Function function = new Tool.Function(name, description, parameter);
        return new Tool("function", function);
    }

    private static Tool.Function.Property property(String type, String description) {
        return property(type, description, null);
    }

    private static Tool.Function.Property property(String type, String description, List<String> enumValues) {
        return new Tool.Function.Property(type, description, enumValues, null);
    }
}
