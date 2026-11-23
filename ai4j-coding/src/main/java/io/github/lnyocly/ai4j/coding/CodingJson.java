package io.github.lnyocly.ai4j.coding;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Module-local Jackson JSON helper for {@code ai4j-coding}.
 */
public final class CodingJson {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE_REF =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    private static final ObjectMapper MAPPER = createMapper();

    private CodingJson() {
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    public static ObjectNode newObject() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode newArray() {
        return MAPPER.createArrayNode();
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON", ex);
        }
    }

    public static String toPrettyJson(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON", ex);
        }
    }

    public static ObjectNode parseObject(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || node.isNull() || node.isMissingNode()) {
                return null;
            }
            if (!node.isObject()) {
                throw new IllegalArgumentException("Expected JSON object");
            }
            return (ObjectNode) node;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid JSON object: " + safeMessage(ex), ex);
        }
    }

    public static ObjectNode parseObjectOrNull(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return parseObject(json);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static ArrayNode parseArray(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || node.isNull() || node.isMissingNode()) {
                return null;
            }
            if (!node.isArray()) {
                throw new IllegalArgumentException("Expected JSON array");
            }
            return (ArrayNode) node;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid JSON array: " + safeMessage(ex), ex);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (type == null || isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse JSON into " + type.getSimpleName() + ": " + safeMessage(ex), ex);
        }
    }

    public static <T> T fromJson(byte[] bytes, Class<T> type) {
        if (type == null || bytes == null || bytes.length == 0) {
            return null;
        }
        return fromJson(new String(bytes, StandardCharsets.UTF_8), type);
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (typeReference == null || isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse JSON: " + safeMessage(ex), ex);
        }
    }

    public static Map<String, Object> parseMap(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE_REF);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse JSON map: " + safeMessage(ex), ex);
        }
    }

    public static <T> T convertValue(Object value, Class<T> type) {
        if (value == null || type == null) {
            return null;
        }
        return fromJson(toJson(value), type);
    }

    public static Object copyPlainValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(value);
            return MAPPER.readValue(bytes, Object.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to deep-copy JSON value", ex);
        }
    }

    public static Map<String, Object> toPlainMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<String, Object>();
        }
        Object copied = copyPlainValue(value);
        if (copied instanceof Map<?, ?>) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) copied).entrySet()) {
                if (entry.getKey() != null) {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return map;
        }
        return new LinkedHashMap<String, Object>();
    }

    public static ObjectNode toObjectNode(Object value) {
        if (value == null) {
            return newObject();
        }
        if (value instanceof ObjectNode) {
            return (ObjectNode) value;
        }
        JsonNode tree = MAPPER.valueToTree(value);
        return tree != null && tree.isObject() ? (ObjectNode) tree : newObject();
    }

    public static JsonNode valueToTree(Object value) {
        return MAPPER.valueToTree(value);
    }

    public static String getString(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isValueNode() ? value.asText() : toJson(value);
    }

    public static String getString(ArrayNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode value = array.get(index);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isValueNode() ? value.asText() : toJson(value);
    }

    public static Integer getInteger(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isNumber()) {
            return Integer.valueOf(value.intValue());
        }
        if (value.isTextual()) {
            String trimmed = value.asText().trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            return Integer.valueOf(Integer.parseInt(trimmed));
        }
        if (value.isBoolean()) {
            return Integer.valueOf(value.asBoolean() ? 1 : 0);
        }
        throw new IllegalArgumentException("Expected integer for field '" + key + "'");
    }

    public static int getIntValue(JsonNode node, String key) {
        Integer value = getInteger(node, key);
        return value == null ? 0 : value.intValue();
    }

    public static Long getLong(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isNumber()) {
            return Long.valueOf(value.longValue());
        }
        if (value.isTextual()) {
            String trimmed = value.asText().trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            return Long.valueOf(Long.parseLong(trimmed));
        }
        if (value.isBoolean()) {
            return Long.valueOf(value.asBoolean() ? 1L : 0L);
        }
        throw new IllegalArgumentException("Expected long for field '" + key + "'");
    }

    public static long getLongValue(JsonNode node, String key) {
        Long value = getLong(node, key);
        return value == null ? 0L : value.longValue();
    }

    public static Boolean getBoolean(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isBoolean()) {
            return Boolean.valueOf(value.booleanValue());
        }
        if (value.isNumber()) {
            return Boolean.valueOf(value.intValue() != 0);
        }
        if (value.isTextual()) {
            String trimmed = value.asText().trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if ("true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Expected boolean for field '" + key + "'");
        }
        throw new IllegalArgumentException("Expected boolean for field '" + key + "'");
    }

    public static boolean getBooleanValue(JsonNode node, String key) {
        Boolean value = getBoolean(node, key);
        return value != null && value.booleanValue();
    }

    public static ObjectNode getObject(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isObject() ? (ObjectNode) value : null;
    }

    public static ObjectNode getObject(ArrayNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode value = array.get(index);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isObject() ? (ObjectNode) value : null;
    }

    public static ArrayNode getArray(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isArray() ? (ArrayNode) value : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null ? "invalid JSON" : throwable.getMessage();
    }
}
