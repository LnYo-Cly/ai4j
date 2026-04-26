package io.github.lnyocly.ai4j.coding.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StaticCodingAgentDefinitionRegistry implements CodingAgentDefinitionRegistry {

    private final Map<String, CodingAgentDefinition> definitions;
    private final List<CodingAgentDefinition> orderedDefinitions;

    public StaticCodingAgentDefinitionRegistry(List<CodingAgentDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            this.definitions = Collections.emptyMap();
            this.orderedDefinitions = Collections.emptyList();
            return;
        }
        Map<String, CodingAgentDefinition> map = new LinkedHashMap<String, CodingAgentDefinition>();
        List<CodingAgentDefinition> ordered = new ArrayList<CodingAgentDefinition>();
        for (CodingAgentDefinition definition : definitions) {
            if (definition == null || isBlank(definition.getName())) {
                continue;
            }
            CodingAgentDefinition normalized = definition.toBuilder().build();
            register(map, normalized.getName(), normalized);
            if (!isBlank(normalized.getToolName())) {
                register(map, normalized.getToolName(), normalized);
            }
            ordered.add(normalized);
        }
        this.definitions = Collections.unmodifiableMap(map);
        this.orderedDefinitions = Collections.unmodifiableList(ordered);
    }

    @Override
    public CodingAgentDefinition getDefinition(String nameOrToolName) {
        if (isBlank(nameOrToolName)) {
            return null;
        }
        return definitions.get(normalize(nameOrToolName));
    }

    @Override
    public List<CodingAgentDefinition> listDefinitions() {
        return orderedDefinitions;
    }

    private void register(Map<String, CodingAgentDefinition> map, String key, CodingAgentDefinition definition) {
        String normalized = normalize(key);
        if (map.containsKey(normalized)) {
            throw new IllegalArgumentException("Duplicate coding agent definition: " + key);
        }
        map.put(normalized, definition);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
