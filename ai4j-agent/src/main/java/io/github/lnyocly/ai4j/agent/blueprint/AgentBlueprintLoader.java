package io.github.lnyocly.ai4j.agent.blueprint;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads a P1-A single-agent blueprint from YAML into Java 8 DTOs.
 */
public class AgentBlueprintLoader {

    private static final Set<String> TOP_LEVEL_FIELDS = new LinkedHashSet<String>(Arrays.asList(
            "$schema", "version", "id", "name", "model", "instructions", "plugins", "tools", "session", "sandbox", "workflow", "extensions"));

    public AgentBlueprint load(String yaml) {
        if (yaml == null) {
            throw new AgentBlueprintLoadException("blueprint.yaml.required", "Blueprint YAML content is required.");
        }
        return fromLoadedObject(loadYamlObject(yaml));
    }

    public AgentBlueprint load(InputStream inputStream) {
        if (inputStream == null) {
            throw new AgentBlueprintLoadException("blueprint.input.required", "Blueprint input stream is required.");
        }
        try {
            return fromLoadedObject(newYaml().load(inputStream));
        } catch (YAMLException ex) {
            throw parseException(ex);
        }
    }

    public AgentBlueprint load(Path path) {
        if (path == null) {
            throw new AgentBlueprintLoadException("blueprint.path.required", "Blueprint path is required.");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return load(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new AgentBlueprintLoadException("blueprint.io", "Unable to read Agent Blueprint file.", ex);
        }
    }

    public AgentBlueprint load(File file) {
        if (file == null) {
            throw new AgentBlueprintLoadException("blueprint.file.required", "Blueprint file is required.");
        }
        try {
            FileInputStream inputStream = new FileInputStream(file);
            try {
                return load(inputStream);
            } finally {
                inputStream.close();
            }
        } catch (IOException ex) {
            throw new AgentBlueprintLoadException("blueprint.io", "Unable to read Agent Blueprint file.", ex);
        }
    }

    private Object loadYamlObject(String yaml) {
        try {
            return newYaml().load(yaml);
        } catch (YAMLException ex) {
            throw parseException(ex);
        }
    }

    private Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    private AgentBlueprintLoadException parseException(YAMLException ex) {
        return new AgentBlueprintLoadException("blueprint.yaml.invalid", "Invalid Agent Blueprint YAML: " + safeMessage(ex), ex);
    }

    private String safeMessage(Exception ex) {
        String message = ex == null ? null : ex.getMessage();
        if (message == null || message.trim().length() == 0) {
            return "parse failed";
        }
        return message.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private AgentBlueprint fromLoadedObject(Object value) {
        if (value == null) {
            throw new AgentBlueprintLoadException("blueprint.document.required", "Agent Blueprint document is empty.");
        }
        Map<String, Object> map = asMap(value, "$", true);
        AgentBlueprint blueprint = new AgentBlueprint();
        blueprint.setVersion(asString(map.get("version")));
        blueprint.setId(asString(map.get("id")));
        blueprint.setName(asString(map.get("name")));
        blueprint.setModel(readModel(map.get("model")));
        blueprint.setInstructions(readInstructions(map.get("instructions")));
        blueprint.setPlugins(readPlugins(map.get("plugins")));
        blueprint.setTools(readTools(map.get("tools")));
        blueprint.setSession(readSession(map.get("session")));
        blueprint.setSandbox(readSandbox(map.get("sandbox")));
        blueprint.setWorkflow(readWorkflow(map.get("workflow")));
        blueprint.setExtensions(asOptionalMap(map.get("extensions"), "$.extensions"));
        blueprint.setUnknownFields(unknownTopLevelFields(map));
        return blueprint;
    }

    private AgentBlueprintModel readModel(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = asMap(value, "$.model", true);
        AgentBlueprintModel model = new AgentBlueprintModel();
        model.setProvider(asString(map.get("provider")));
        model.setProfile(asString(map.get("profile")));
        model.setModel(asString(map.get("model")));
        model.setOptions(asOptionalMap(map.get("options"), "$.model.options"));
        return model;
    }

    private AgentBlueprintInstructions readInstructions(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = asMap(value, "$.instructions", true);
        AgentBlueprintInstructions instructions = new AgentBlueprintInstructions();
        instructions.setSystem(asString(map.get("system")));
        instructions.setDeveloper(asString(map.get("developer")));
        instructions.setVariables(asOptionalMap(map.get("variables"), "$.instructions.variables"));
        return instructions;
    }

    private List<AgentBlueprintPlugin> readPlugins(Object value) {
        List<AgentBlueprintPlugin> result = new ArrayList<AgentBlueprintPlugin>();
        if (value == null) {
            return result;
        }
        List<Object> list = asList(value, "$.plugins");
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item == null) {
                result.add(null);
                continue;
            }
            Map<String, Object> map = asMap(item, "$.plugins[" + i + "]", true);
            AgentBlueprintPlugin plugin = new AgentBlueprintPlugin();
            plugin.setId(asString(map.get("id")));
            plugin.setEnabled(asBoolean(map.get("enabled"), "$.plugins[" + i + "].enabled"));
            plugin.setConfig(asOptionalMap(map.get("config"), "$.plugins[" + i + "].config"));
            result.add(plugin);
        }
        return result;
    }

    private List<AgentBlueprintTool> readTools(Object value) {
        List<AgentBlueprintTool> result = new ArrayList<AgentBlueprintTool>();
        if (value == null) {
            return result;
        }
        List<Object> list = asList(value, "$.tools");
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item == null) {
                result.add(null);
                continue;
            }
            Map<String, Object> map = asMap(item, "$.tools[" + i + "]", true);
            AgentBlueprintTool tool = new AgentBlueprintTool();
            tool.setRef(asString(map.get("ref")));
            tool.setApproval(asString(map.get("approval")));
            tool.setConfig(asOptionalMap(map.get("config"), "$.tools[" + i + "].config"));
            result.add(tool);
        }
        return result;
    }

    private AgentBlueprintSession readSession(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = asMap(value, "$.session", true);
        AgentBlueprintSession session = new AgentBlueprintSession();
        session.setMemory(readMemory(map.get("memory")));
        session.setCompact(readCompact(map.get("compact")));
        return session;
    }

    private AgentBlueprintMemory readMemory(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = asMap(value, "$.session.memory", true);
        AgentBlueprintMemory memory = new AgentBlueprintMemory();
        memory.setEnabled(asBoolean(map.get("enabled"), "$.session.memory.enabled"));
        memory.setScope(asString(map.get("scope")));
        memory.setStore(asString(map.get("store")));
        memory.setConfig(asOptionalMap(map.get("config"), "$.session.memory.config"));
        return memory;
    }

    private AgentBlueprintCompact readCompact(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = asMap(value, "$.session.compact", true);
        AgentBlueprintCompact compact = new AgentBlueprintCompact();
        compact.setEnabled(asBoolean(map.get("enabled"), "$.session.compact.enabled"));
        compact.setTrigger(readCompactTrigger(map.get("trigger")));
        compact.setStrategy(asString(map.get("strategy")));
        compact.setPreserve(asStringList(map.get("preserve"), "$.session.compact.preserve"));
        return compact;
    }

    private AgentBlueprintCompactTrigger readCompactTrigger(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = asMap(value, "$.session.compact.trigger", true);
        AgentBlueprintCompactTrigger trigger = new AgentBlueprintCompactTrigger();
        trigger.setContextRatio(asDouble(map.get("contextRatio"), "$.session.compact.trigger.contextRatio"));
        trigger.setMaxTurns(asInteger(map.get("maxTurns"), "$.session.compact.trigger.maxTurns"));
        return trigger;
    }

    private AgentBlueprintSandbox readSandbox(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = asMap(value, "$.sandbox", true);
        AgentBlueprintSandbox sandbox = new AgentBlueprintSandbox();
        sandbox.setEnabled(asBoolean(map.get("enabled"), "$.sandbox.enabled"));
        sandbox.setProvider(asString(map.get("provider")));
        sandbox.setProfile(asString(map.get("profile")));
        sandbox.setConfig(asOptionalMap(map.get("config"), "$.sandbox.config"));
        return sandbox;
    }

    private AgentBlueprintWorkflow readWorkflow(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = asMap(value, "$.workflow", true);
        AgentBlueprintWorkflow workflow = new AgentBlueprintWorkflow();
        workflow.setMode(asString(map.get("mode")));
        workflow.setMaxTurns(asInteger(map.get("maxTurns"), "$.workflow.maxTurns"));
        return workflow;
    }

    private List<String> unknownTopLevelFields(Map<String, Object> map) {
        List<String> fields = new ArrayList<String>();
        for (String key : map.keySet()) {
            if (!TOP_LEVEL_FIELDS.contains(key)) {
                fields.add(key);
            }
        }
        return fields;
    }

    private Map<String, Object> asOptionalMap(Object value, String path) {
        if (value == null) {
            return new LinkedHashMap<String, Object>();
        }
        return asMap(value, path, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value, String path, boolean required) {
        if (value == null) {
            if (required) {
                throw new AgentBlueprintLoadException("blueprint.type.map", path + " must be a map.");
            }
            return new LinkedHashMap<String, Object>();
        }
        if (!(value instanceof Map)) {
            throw new AgentBlueprintLoadException("blueprint.type.map", path + " must be a map.");
        }
        Map<Object, Object> raw = (Map<Object, Object>) value;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new AgentBlueprintLoadException("blueprint.type.key", path + " contains a non-string key.");
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value, String path) {
        if (value == null) {
            return new ArrayList<Object>();
        }
        if (!(value instanceof List)) {
            throw new AgentBlueprintLoadException("blueprint.type.list", path + " must be a list.");
        }
        return (List<Object>) value;
    }

    private List<String> asStringList(Object value, String path) {
        List<String> result = new ArrayList<String>();
        if (value == null) {
            return result;
        }
        List<Object> list = asList(value, path);
        for (int i = 0; i < list.size(); i++) {
            result.add(asString(list.get(i)));
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean asBoolean(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        throw new AgentBlueprintLoadException("blueprint.type.boolean", path + " must be a boolean.");
    }

    private Double asDouble(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new AgentBlueprintLoadException("blueprint.type.number", path + " must be a number.");
    }

    private Integer asInteger(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            double numeric = ((Number) value).doubleValue();
            if (Math.floor(numeric) == numeric) {
                return Integer.valueOf(((Number) value).intValue());
            }
        }
        if (value instanceof String) {
            try {
                return Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new AgentBlueprintLoadException("blueprint.type.integer", path + " must be an integer.");
    }
}
