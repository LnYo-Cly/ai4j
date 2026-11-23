package io.github.lnyocly.ai4j.agent.util;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal Jackson JSON facade for {@code ai4j-agent} built on top of {@link JsonCodec}.
 */
public final class AgentJson {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    private AgentJson() {
    }

    public static String toJson(Object value) {
        return JsonCodec.toJson(value);
    }

    public static byte[] toJsonBytes(Object value) {
        return JsonCodec.toJsonBytes(value);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JsonCodec.fromJson(json, type);
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JsonCodec.fromJson(json, type);
    }

    public static <T> T fromJsonLenient(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JsonCodec.fromJsonLenient(json, type);
    }

    public static <T> T fromJsonLenient(String json, TypeReference<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JsonCodec.fromJsonLenient(json, type);
    }

    public static Object parsePlain(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JsonCodec.fromJson(json, Object.class);
    }

    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        JsonNode node = JsonCodec.readTree(json);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return JsonCodec.protocolMapper().convertValue(node, MAP_TYPE);
    }

    public static Map<String, Object> parseMapOrEmpty(String json) {
        Map<String, Object> parsed = parseMap(json);
        return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
    }

    public static Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry != null && entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return copy;
        }
        JsonNode node = JsonCodec.valueToTree(value);
        if (node == null || !node.isObject()) {
            return null;
        }
        return JsonCodec.protocolMapper().convertValue(node, MAP_TYPE);
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

    public static ArrayNode parseArrayStrictOrNull(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        JsonNode node = JsonCodec.readTree(json);
        return node != null && node.isArray() ? (ArrayNode) node : null;
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

    public static Object extractValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return JsonCodec.treeToValue(node, Object.class);
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

    public static Long longValue(ObjectNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.isMissingNode()) {
            return null;
        }
        if (child.isNumber()) {
            return Long.valueOf(child.longValue());
        }
        if (child.isTextual()) {
            try {
                return Long.valueOf(child.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static Boolean booleanObject(ObjectNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.isMissingNode()) {
            return null;
        }
        if (child.isBoolean()) {
            return Boolean.valueOf(child.booleanValue());
        }
        if (child.isTextual()) {
            String raw = child.asText().trim();
            if ("true".equalsIgnoreCase(raw)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(raw)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    public static ObjectNode createObjectNode() {
        return JsonCodec.createObjectNode();
    }

    public static ArrayNode createArrayNode() {
        return JsonCodec.createArrayNode();
    }

    public static ArrayNode parseArrayOrNull(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode node = JsonCodec.readTree(json);
            return node != null && node.isArray() ? (ArrayNode) node : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static JsonNode valueToTree(Object value) {
        return JsonCodec.valueToTree(value);
    }

    public static <T> T treeToValue(TreeNode node, Class<T> type) {
        return JsonCodec.treeToValue(node, type);
    }
}
