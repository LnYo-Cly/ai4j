package io.github.lnyocly.ai4j.tool;

import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BuiltInTools {

    public static final String BASH = "bash";
    public static final String READ_FILE = "read_file";
    public static final String WRITE_FILE = "write_file";
    public static final String APPLY_PATCH = "apply_patch";

    private static final Set<String> CODING_TOOL_NAMES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(BASH, READ_FILE, WRITE_FILE, APPLY_PATCH))
    );

    private static final Set<String> READ_ONLY_CODING_TOOL_NAMES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(BASH, READ_FILE))
    );

    private static final List<Tool> CODING_TOOLS = Collections.unmodifiableList(Arrays.asList(
            bashTool(),
            readFileTool(),
            writeFileTool(),
            applyPatchTool()
    ));

    private BuiltInTools() {
    }

    public static Tool readFileTool() {
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<String, Tool.Function.Property>();
        properties.put("path", property("string", "Relative file path inside the workspace, or an absolute path inside an approved read-only skill root."));
        properties.put("startLine", property("integer", "First line number to read, starting from 1."));
        properties.put("endLine", property("integer", "Last line number to read, inclusive."));
        properties.put("maxChars", property("integer", "Maximum characters to return."));
        return tool(
                READ_FILE,
                "Read a text file from the workspace or from an approved read-only skill directory.",
                properties,
                Collections.singletonList("path")
        );
    }

    public static Tool bashTool() {
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<String, Tool.Function.Property>();
        properties.put("action", property("string", "bash action to perform.", Arrays.asList("exec", "start", "status", "logs", "write", "stop", "list")));
        properties.put("command", property("string", "Command string to execute. Use exec for self-terminating commands; use start for interactive or long-running commands."));
        properties.put("cwd", property("string", "Relative working directory inside the workspace."));
        properties.put("timeoutMs", property("integer", "Execution timeout in milliseconds for exec."));
        properties.put("processId", property("string", "Background process identifier."));
        properties.put("offset", property("integer", "Log cursor offset."));
        properties.put("limit", property("integer", "Maximum log characters to return."));
        properties.put("input", property("string", "Text written to stdin for a background process started with action=start."));
        return tool(
                BASH,
                "Execute non-interactive shell commands or manage interactive/background shell processes inside the workspace.",
                properties,
                Collections.singletonList("action")
        );
    }

    public static Tool writeFileTool() {
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<String, Tool.Function.Property>();
        properties.put("path", property("string", "File path to write. Relative paths resolve from the workspace root; absolute paths are allowed."));
        properties.put("content", property("string", "Full text content to write."));
        properties.put("mode", property("string", "Write mode.", Arrays.asList("create", "overwrite", "append")));
        return tool(
                WRITE_FILE,
                "Create, overwrite, or append a text file.",
                properties,
                Arrays.asList("path", "content")
        );
    }

    public static Tool applyPatchTool() {
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<String, Tool.Function.Property>();
        properties.put("patch", property("string", "Patch text to apply. Must include *** Begin Patch and *** End Patch envelope."));
        return tool(
                APPLY_PATCH,
                "Apply a structured patch to workspace files.",
                properties,
                Collections.singletonList("patch")
        );
    }

    public static List<Tool> codingTools() {
        return new ArrayList<Tool>(CODING_TOOLS);
    }

    public static List<Tool> tools(String... names) {
        List<Tool> tools = new ArrayList<Tool>();
        if (names == null || names.length == 0) {
            return tools;
        }
        for (String name : names) {
            Tool tool = toolByName(name);
            if (tool != null) {
                tools.add(tool);
            }
        }
        return tools;
    }

    public static Set<String> allCodingToolNames() {
        return CODING_TOOL_NAMES;
    }

    public static Set<String> readOnlyCodingToolNames() {
        return READ_ONLY_CODING_TOOL_NAMES;
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

    private static Tool toolByName(String name) {
        if (READ_FILE.equals(name)) {
            return readFileTool();
        }
        if (WRITE_FILE.equals(name)) {
            return writeFileTool();
        }
        if (APPLY_PATCH.equals(name)) {
            return applyPatchTool();
        }
        if (BASH.equals(name)) {
            return bashTool();
        }
        return null;
    }
}
