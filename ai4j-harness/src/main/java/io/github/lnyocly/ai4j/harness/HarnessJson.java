package io.github.lnyocly.ai4j.harness;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSandboxBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared thread-safe Jackson JSON facade for ai4j-harness.
 */
final class HarnessJson {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    private static final TypeReference<ArrayList<Object>> LIST_TYPE =
            new TypeReference<ArrayList<Object>>() {
            };

    private static final ObjectMapper MAPPER = createMapper();

    private HarnessJson() {
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);

        SimpleModule module = new SimpleModule("ai4j-harness");
        module.addDeserializer(AgentSessionSandboxBinding.class, new SandboxBindingDeserializer());
        mapper.registerModule(module);
        return mapper;
    }

    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new HarnessStoreException("cannot encode JSON", error);
        }
    }

    static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException error) {
            throw new HarnessStoreException("cannot decode JSON as " + type.getSimpleName(), error);
        }
    }

    static Map<String, Object> parseMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException error) {
            throw new HarnessStoreException("cannot decode JSON object", error);
        }
    }

    static List<Object> parseList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException error) {
            throw new HarnessStoreException("cannot decode JSON array", error);
        }
    }

    static Object parseAny(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException error) {
            throw new HarnessStoreException("cannot decode JSON value", error);
        }
    }

    static Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        return MAPPER.convertValue(value, MAP_TYPE);
    }

    static <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        return MAPPER.convertValue(value, type);
    }

    static <T> T copy(T value, Class<T> type) {
        if (value == null) {
            return null;
        }
        return fromJson(toJson(value), type);
    }

    private static final class SandboxBindingDeserializer extends JsonDeserializer<AgentSessionSandboxBinding> {
        @Override
        public AgentSessionSandboxBinding deserialize(JsonParser parser,
                                                      DeserializationContext context) throws IOException {
            JsonNode node = context.readTree(parser);
            if (node == null || node.isNull()) {
                return null;
            }
            AgentSessionSandboxBinding.Builder builder = AgentSessionSandboxBinding.builder()
                    .providerId(textOrNull(node, "providerId"))
                    .sandboxSessionId(textOrNull(node, "sandboxSessionId"))
                    .profile(textOrNull(node, "profile"))
                    .image(textOrNull(node, "image"))
                    .workspaceId(textOrNull(node, "workspaceId"));
            String status = textOrNull(node, "status");
            if (status != null) {
                builder.status(SandboxStatus.valueOf(status));
            }
            JsonNode labelsNode = node.get("labels");
            if (labelsNode != null && labelsNode.isObject()) {
                Map<String, String> labels = new LinkedHashMap<String, String>();
                java.util.Iterator<Map.Entry<String, JsonNode>> fields = labelsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode val = entry.getValue();
                    labels.put(entry.getKey(), val == null || val.isNull() ? null : val.asText());
                }
                builder.labels(labels);
            }
            JsonNode boundNode = node.get("boundAtEpochMs");
            if (boundNode != null && boundNode.isNumber()) {
                builder.boundAtEpochMs(boundNode.asLong());
            }
            JsonNode updatedNode = node.get("updatedAtEpochMs");
            if (updatedNode != null && updatedNode.isNumber()) {
                builder.updatedAtEpochMs(updatedNode.asLong());
            }
            return builder.build();
        }

        private String textOrNull(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        }
    }
}
