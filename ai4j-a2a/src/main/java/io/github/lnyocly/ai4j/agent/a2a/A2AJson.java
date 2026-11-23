package io.github.lnyocly.ai4j.agent.a2a;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal Jackson JSON helpers for A2A protocol client, server, and tests built on {@link JsonCodec}.
 */
public final class A2AJson {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    private A2AJson() {
    }

    public static String toJson(Object value) {
        return JsonCodec.toJson(value);
    }

    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JsonCodec.fromJson(json, type);
    }

    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JsonCodec.fromJson(json, MAP_TYPE);
    }

    public static JsonNode readTree(String json) {
        return JsonCodec.readTree(json);
    }

    public static ObjectNode parseObjectOrNull(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode node = JsonCodec.readTree(json);
            return node != null && node.isObject() ? (ObjectNode) node : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static ObjectNode parseObjectStrictOrNull(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        JsonNode node = JsonCodec.readTree(json);
        return node != null && node.isObject() ? (ObjectNode) node : null;
    }

    public static ObjectNode toObjectNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectNode) {
            return (ObjectNode) value;
        }
        JsonNode node = JsonCodec.valueToTree(value);
        return node != null && node.isObject() ? (ObjectNode) node : null;
    }

    public static ObjectNode objectField(ObjectNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        return child != null && child.isObject() ? (ObjectNode) child : null;
    }

    public static ArrayNode arrayField(ObjectNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        return child != null && child.isArray() ? (ArrayNode) child : null;
    }

    public static ObjectNode objectAt(ArrayNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode child = array.get(index);
        return child != null && child.isObject() ? (ObjectNode) child : null;
    }

    public static String textAt(ArrayNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode child = array.get(index);
        if (child == null || child.isNull() || child.isMissingNode()) {
            return null;
        }
        return child.asText();
    }

    public static String text(ObjectNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.isMissingNode()) {
            return null;
        }
        return child.asText();
    }

    public static Integer integer(ObjectNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.isMissingNode()) {
            return null;
        }
        if (child.isNumber()) {
            return Integer.valueOf(child.intValue());
        }
        if (child.isTextual()) {
            try {
                return Integer.valueOf(child.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static int intValue(ObjectNode node, String field, int defaultValue) {
        Integer value = integer(node, field);
        return value == null ? defaultValue : value.intValue();
    }

    public static boolean booleanValue(ObjectNode node, String field) {
        if (node == null || field == null) {
            return false;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.isMissingNode()) {
            return false;
        }
        return child.asBoolean(false);
    }

    public static Object extractValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return JsonCodec.treeToValue(node, Object.class);
    }

    public static void setStringList(ObjectNode node, String field, List<String> values) {
        ArrayNode array = JsonCodec.createArrayNode();
        if (values != null) {
            for (String value : values) {
                array.add(value);
            }
        }
        node.set(field, array);
    }

    public static Iterator<String> fieldNames(ObjectNode node) {
        return node.fieldNames();
    }
}
