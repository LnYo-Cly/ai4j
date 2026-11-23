package io.github.lnyocly.ai4j.agent.sandbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal Jackson JSON helpers for sandbox clients built on top of {@link JsonCodec}.
 */
public final class SandboxJson {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    private SandboxJson() {
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

    public static ObjectNode parseObjectStrict(String json) {
        if (json == null || json.trim().isEmpty()) {
            return JsonCodec.createObjectNode();
        }
        JsonNode node = JsonCodec.readTree(json);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Expected JSON object payload");
        }
        return (ObjectNode) node;
    }

    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> parsed = JsonCodec.fromJson(json, MAP_TYPE);
        return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
    }

    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JsonCodec.fromJson(json, type);
    }

    public static String toJson(Object value) {
        return JsonCodec.toJson(value);
    }

    public static byte[] toJsonBytes(Object value) {
        return JsonCodec.toJsonBytes(value);
    }

    public static ObjectNode objectField(ObjectNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        return child != null && child.isObject() ? (ObjectNode) child : null;
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

    public static Map<String, Object> emptyMap() {
        return Collections.emptyMap();
    }
}
