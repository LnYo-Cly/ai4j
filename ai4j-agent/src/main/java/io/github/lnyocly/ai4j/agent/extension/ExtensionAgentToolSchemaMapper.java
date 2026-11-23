package io.github.lnyocly.ai4j.agent.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.util.AgentJson;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ExtensionAgentToolSchemaMapper {

    private ExtensionAgentToolSchemaMapper() {
    }

    static Tool toAgentTool(ExtensionToolSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("extension tool spec must not be null");
        }
        Tool.Function.Parameter parameter = toParameter(spec);
        Tool.Function function = new Tool.Function(spec.getName(), spec.getDescription(), parameter);
        return new Tool("function", function);
    }

    private static Tool.Function.Parameter toParameter(ExtensionToolSpec spec) {
        String schema = trimToNull(spec.getInputSchema());
        if (schema == null) {
            return new Tool.Function.Parameter("object",
                    Collections.<String, Tool.Function.Property>emptyMap(),
                    Collections.<String>emptyList());
        }
        ObjectNode object;
        try {
            object = AgentJson.parseObjectStrictOrNull(schema);
        } catch (Exception ex) {
            throw new ExtensionException("invalid input schema for extension tool " + spec.getName() + ": " + ex.getMessage(), ex);
        }
        if (object == null) {
            return new Tool.Function.Parameter("object",
                    Collections.<String, Tool.Function.Property>emptyMap(),
                    Collections.<String>emptyList());
        }
        String type = firstNonBlank(AgentJson.text(object, "type"), "object");
        Map<String, Tool.Function.Property> properties = parseProperties(AgentJson.objectField(object, "properties"));
        List<String> required = parseStringArray(AgentJson.arrayField(object, "required"));
        return new Tool.Function.Parameter(type, properties, required);
    }

    private static Map<String, Tool.Function.Property> parseProperties(ObjectNode source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<String, Tool.Function.Property>();
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry == null || trimToNull(entry.getKey()) == null) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value == null || !value.isObject()) {
                properties.put(entry.getKey(), new Tool.Function.Property("string", null, null, null));
                continue;
            }
            properties.put(entry.getKey(), parseProperty((ObjectNode) value));
        }
        return properties;
    }

    private static Tool.Function.Property parseProperty(ObjectNode source) {
        String type = firstNonBlank(AgentJson.text(source, "type"), "string");
        String description = trimToNull(AgentJson.text(source, "description"));
        List<String> enumValues = parseStringArray(AgentJson.arrayField(source, "enum"));
        Tool.Function.Property items = null;
        ObjectNode itemObject = AgentJson.objectField(source, "items");
        if (itemObject != null) {
            items = parseProperty(itemObject);
        }
        return new Tool.Function.Property(type, description, enumValues, items);
    }

    private static List<String> parseStringArray(ArrayNode source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>();
        for (JsonNode item : source) {
            String value = item == null || item.isNull() ? null : trimToNull(item.isValueNode() ? item.asText() : item.toString());
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static String firstNonBlank(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
