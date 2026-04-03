package io.github.lnyocly.ai4j.coding.delegate;

import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CodingDelegateToolRegistry implements AgentToolRegistry {

    private final List<Object> tools;

    public CodingDelegateToolRegistry(CodingAgentDefinitionRegistry definitionRegistry) {
        List<CodingAgentDefinition> definitions = definitionRegistry == null
                ? Collections.<CodingAgentDefinition>emptyList()
                : definitionRegistry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            this.tools = Collections.emptyList();
            return;
        }
        List<Object> resolved = new ArrayList<Object>();
        for (CodingAgentDefinition definition : definitions) {
            Tool tool = createTool(definition);
            if (tool != null) {
                resolved.add(tool);
            }
        }
        this.tools = Collections.unmodifiableList(resolved);
    }

    @Override
    public List<Object> getTools() {
        return new ArrayList<Object>(tools);
    }

    private Tool createTool(CodingAgentDefinition definition) {
        if (definition == null || isBlank(definition.getToolName())) {
            return null;
        }
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<String, Tool.Function.Property>();
        properties.put("task", property("string", "Task to delegate to this coding worker."));
        properties.put("context", property("string", "Optional extra context for the delegated task."));
        properties.put("background", property("boolean", "Whether to run the delegated task in the background."));
        properties.put("sessionMode", property("string", "Optional session mode override: new or fork."));
        properties.put("childSessionId", property("string", "Optional child session id override."));

        Tool.Function.Parameter parameter = new Tool.Function.Parameter("object", properties, Arrays.asList("task"));
        Tool.Function function = new Tool.Function(
                definition.getToolName(),
                resolveDescription(definition),
                parameter
        );
        Tool tool = new Tool();
        tool.setType("function");
        tool.setFunction(function);
        return tool;
    }

    private String resolveDescription(CodingAgentDefinition definition) {
        String description = definition.getDescription();
        if (!isBlank(description)) {
            return description;
        }
        return "Delegate a coding task to worker " + definition.getName();
    }

    private Tool.Function.Property property(String type, String description) {
        Tool.Function.Property property = new Tool.Function.Property();
        property.setType(type);
        property.setDescription(description);
        return property;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
