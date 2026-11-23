package io.github.lnyocly.ai4j.flowgram.springboot.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.LinkedHashMap;

/**
 * Shared Jackson codec helpers compatible with Jackson 2.11+ across Spring Boot 2/3/4.
 */
public final class FlowGramJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> LINKED_HASH_MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    private FlowGramJsonSupport() {
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
        return mapper;
    }

    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize value to JSON", ex);
        }
    }

    public static LinkedHashMap<String, Object> readMapOrNull(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, LINKED_HASH_MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse JSON object", ex);
        }
    }

    public static Object tryParse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }
        try {
            return OBJECT_MAPPER.readValue(raw, Object.class);
        } catch (JsonProcessingException ex) {
            return raw;
        } catch (RuntimeException ex) {
            return raw;
        }
    }

    public static Object tryRoundTrip(Object source) {
        if (source == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(source), Object.class);
        } catch (JsonProcessingException ex) {
            return source;
        } catch (RuntimeException ex) {
            return source;
        }
    }
}
