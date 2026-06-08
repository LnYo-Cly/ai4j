package io.github.lnyocly.ai4j.agent.extension;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Collections;
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
        JSONObject object;
        try {
            object = JSON.parseObject(schema);
        } catch (Exception ex) {
            throw new ExtensionException("invalid input schema for extension tool " + spec.getName() + ": " + ex.getMessage(), ex);
        }
        if (object == null) {
            return new Tool.Function.Parameter("object",
                    Collections.<String, Tool.Function.Property>emptyMap(),
                    Collections.<String>emptyList());
        }
        String type = firstNonBlank(object.getString("type"), "object");
        Map<String, Tool.Function.Property> properties = parseProperties(object.getJSONObject("properties"));
        List<String> required = parseStringArray(object.getJSONArray("required"));
        return new Tool.Function.Parameter(type, properties, required);
    }

    private static Map<String, Tool.Function.Property> parseProperties(JSONObject source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Tool.Function.Property> properties = new LinkedHashMap<String, Tool.Function.Property>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry == null || trimToNull(entry.getKey()) == null) {
                continue;
            }
            JSONObject value = asObject(entry.getValue());
            if (value == null) {
                properties.put(entry.getKey(), new Tool.Function.Property("string", null, null, null));
                continue;
            }
            properties.put(entry.getKey(), parseProperty(value));
        }
        return properties;
    }

    private static Tool.Function.Property parseProperty(JSONObject source) {
        String type = firstNonBlank(source.getString("type"), "string");
        String description = trimToNull(source.getString("description"));
        List<String> enumValues = parseStringArray(source.getJSONArray("enum"));
        Tool.Function.Property items = null;
        JSONObject itemObject = source.getJSONObject("items");
        if (itemObject != null) {
            items = parseProperty(itemObject);
        }
        return new Tool.Function.Property(type, description, enumValues, items);
    }

    private static JSONObject asObject(Object value) {
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        if (value instanceof Map) {
            JSONObject object = new JSONObject();
            for (Object item : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
                if (entry != null && entry.getKey() != null) {
                    object.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return object;
        }
        return null;
    }

    private static List<String> parseStringArray(JSONArray source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>();
        for (Object item : source) {
            String value = item == null ? null : trimToNull(String.valueOf(item));
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
