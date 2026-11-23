package io.github.lnyocly.ai4j.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire-private JSON helpers shared by the protocol codecs.
 *
 * <p>Jackson {@link JsonNode} is confined to this class and the codec internals
 * (design F2): nothing on the public semantic surface exposes it. Values
 * crossing the boundary are {@code Map<String,Object>} with standard JSON
 * scalars, so all consumers stay library-neutral.</p>
 */
public final class JsonWire {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonWire() {
    }

    /** Parse JSON text into the neutral object shape; returns an empty map for blank input. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || node.isNull() || !node.isObject()) {
                return Collections.emptyMap();
            }
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.put(field.getKey(), convert(field.getValue()));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid JSON object payload: " + e.getMessage(), e);
        }
    }

    /** Parse arbitrary JSON text (object, array or scalar) into the neutral shape. */
    public static Object readValue(String json) {
        try {
            return convert(MAPPER.readTree(json));
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid JSON payload: " + e.getMessage(), e);
        }
    }

    /** Serialize the neutral object shape to compact JSON text. */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("unable to serialize wire payload: " + e.getMessage(), e);
        }
    }

    /** Parse a JSON object field; returns an empty map when absent or null. */
    public static Map<String, Object> objectAt(Map<String, Object> source, String key) {
        if (source == null) {
            return Collections.emptyMap();
        }
        Object value = source.get(key);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return map;
        }
        return Collections.emptyMap();
    }

    /** Parse a JSON array field; returns an empty list when absent or null. */
    @SuppressWarnings("unchecked")
    public static java.util.List<Object> arrayAt(Map<String, Object> source, String key) {
        if (source == null) {
            return Collections.emptyList();
        }
        Object value = source.get(key);
        if (value instanceof java.util.List) {
            return (java.util.List<Object>) value;
        }
        return Collections.emptyList();
    }

    public static String textAt(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Integer intAt(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static Long longAt(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object convert(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.put(field.getKey(), convert(field.getValue()));
            }
            return result;
        }
        if (node.isArray()) {
            java.util.List<Object> result = new java.util.ArrayList<Object>(node.size());
            for (JsonNode element : node) {
                result.add(convert(element));
            }
            return result;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        }
        return node.asText();
    }
}
