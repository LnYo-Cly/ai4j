package io.github.lnyocly.ai4j.agent;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.structured.StructuredOutputException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Executes an {@link Agent} or {@link AgentSession} against a typed target contract and
 * automatically feeds JSON parsing, required-property, or custom validation errors back into
 * bounded follow-up retry turns until a valid instance is produced or attempts are exhausted.
 */
public class StructuredOutputSelfHealer {

    @FunctionalInterface
    public interface Validator<T> {
        void validate(T value) throws Exception;
    }

    public static final int DEFAULT_MAX_HEAL_ATTEMPTS = AgentOptions.DEFAULT_MAX_HEAL_ATTEMPTS;
    private static final int MAX_ERROR_OUTPUT_SNIPPET = 1200;

    private final Integer configuredMaxHealAttempts;

    public StructuredOutputSelfHealer() {
        this.configuredMaxHealAttempts = null;
    }

    public StructuredOutputSelfHealer(int maxHealAttempts) {
        this.configuredMaxHealAttempts = Integer.valueOf(Math.max(0, maxHealAttempts));
    }

    public int getMaxHealAttempts() {
        return configuredMaxHealAttempts != null
                ? configuredMaxHealAttempts.intValue()
                : DEFAULT_MAX_HEAL_ATTEMPTS;
    }

    public <T> T call(Agent agent, AgentRequest request, Class<T> targetType) throws Exception {
        return call(agent, request, targetType, resolveDefaultAttempts(agent == null ? null : agent.getContext()), null);
    }

    public <T> T call(Agent agent,
                      AgentRequest request,
                      Class<T> targetType,
                      int maxHealAttempts) throws Exception {
        return call(agent, request, targetType, maxHealAttempts, null);
    }

    public <T> T call(Agent agent,
                      AgentRequest request,
                      Class<T> targetType,
                      int maxHealAttempts,
                      Validator<T> validator) throws Exception {
        if (agent == null) {
            throw new IllegalArgumentException("agent cannot be null");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType cannot be null");
        }
        JavaType javaType = JsonCodec.protocolMapper().getTypeFactory().constructType(targetType);
        return executeWithSession(agent.newSession(), request, javaType, targetType, null, maxHealAttempts, validator);
    }

    public <T> T call(Agent agent, AgentRequest request, TypeReference<T> typeReference) throws Exception {
        return call(agent, request, typeReference, resolveDefaultAttempts(agent == null ? null : agent.getContext()), null);
    }

    public <T> T call(Agent agent,
                      AgentRequest request,
                      TypeReference<T> typeReference,
                      int maxHealAttempts) throws Exception {
        return call(agent, request, typeReference, maxHealAttempts, null);
    }

    public <T> T call(Agent agent,
                      AgentRequest request,
                      TypeReference<T> typeReference,
                      int maxHealAttempts,
                      Validator<T> validator) throws Exception {
        if (agent == null) {
            throw new IllegalArgumentException("agent cannot be null");
        }
        if (typeReference == null) {
            throw new IllegalArgumentException("typeReference cannot be null");
        }
        JavaType javaType = JsonCodec.protocolMapper().getTypeFactory().constructType(typeReference);
        return executeWithSession(agent.newSession(), request, javaType, null, typeReference, maxHealAttempts, validator);
    }

    public <T> T call(AgentSession session, AgentRequest request, Class<T> targetType) throws Exception {
        return call(session, request, targetType, resolveDefaultAttempts(session == null ? null : session.getContext()), null);
    }

    public <T> T call(AgentSession session,
                      AgentRequest request,
                      Class<T> targetType,
                      int maxHealAttempts) throws Exception {
        return call(session, request, targetType, maxHealAttempts, null);
    }

    public <T> T call(AgentSession session,
                      AgentRequest request,
                      Class<T> targetType,
                      int maxHealAttempts,
                      Validator<T> validator) throws Exception {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType cannot be null");
        }
        JavaType javaType = JsonCodec.protocolMapper().getTypeFactory().constructType(targetType);
        return executeWithSession(session, request, javaType, targetType, null, maxHealAttempts, validator);
    }

    public <T> T call(AgentSession session, AgentRequest request, TypeReference<T> typeReference) throws Exception {
        return call(session, request, typeReference, resolveDefaultAttempts(session == null ? null : session.getContext()), null);
    }

    public <T> T call(AgentSession session,
                      AgentRequest request,
                      TypeReference<T> typeReference,
                      int maxHealAttempts) throws Exception {
        return call(session, request, typeReference, maxHealAttempts, null);
    }

    public <T> T call(AgentSession session,
                      AgentRequest request,
                      TypeReference<T> typeReference,
                      int maxHealAttempts,
                      Validator<T> validator) throws Exception {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        if (typeReference == null) {
            throw new IllegalArgumentException("typeReference cannot be null");
        }
        JavaType javaType = JsonCodec.protocolMapper().getTypeFactory().constructType(typeReference);
        return executeWithSession(session, request, javaType, null, typeReference, maxHealAttempts, validator);
    }

    private int resolveDefaultAttempts(AgentContext context) {
        if (configuredMaxHealAttempts != null) {
            return configuredMaxHealAttempts.intValue();
        }
        if (context != null && context.getOptions() != null) {
            return Math.max(0, context.getOptions().getMaxHealAttempts());
        }
        return DEFAULT_MAX_HEAL_ATTEMPTS;
    }

    private <T> T executeWithSession(AgentSession session,
                                     AgentRequest request,
                                     JavaType javaType,
                                     Class<T> rawClass,
                                     TypeReference<T> typeReference,
                                     int maxHealAttempts,
                                     Validator<T> validator) throws Exception {
        int resolvedMaxAttempts = Math.max(0, maxHealAttempts);
        AgentRequest normalizedRequest = request == null ? AgentRequest.builder().build() : request;
        String schemaJson = buildSchemaJson(javaType);
        AgentContext structuredContext = augmentContextInstructions(session.getContext(), schemaJson);

        String lastRawOutput = null;
        String lastError = null;
        Throwable lastCause = null;

        for (int attempt = 0; attempt <= resolvedMaxAttempts; attempt++) {
            AgentRequest turnRequest = attempt == 0
                    ? normalizedRequest
                    : buildHealRequest(normalizedRequest, lastRawOutput, lastError, schemaJson, attempt);

            AgentResult result = session.runWithContext(structuredContext, turnRequest);
            if (result != null && result.getExecutionStatus() == AgentExecutionStatus.WAITING) {
                throw new StructuredOutputException(
                        "Agent run entered WAITING state before producing structured output",
                        result.getOutputText()
                );
            }

            String outputText = result == null ? null : result.getOutputText();
            lastRawOutput = outputText;
            if (outputText == null || outputText.trim().isEmpty()) {
                lastError = "Agent returned empty output; expected valid JSON matching the schema.";
                lastCause = null;
                continue;
            }

            try {
                JsonNode parsedTree = JsonCodec.readTreeLenient(outputText);
                List<String> schemaErrors = new ArrayList<String>();
                validateJsonTree(parsedTree, javaType, "$", JsonCodec.protocolMapper(), schemaErrors, new HashSet<Class<?>>());
                if (!schemaErrors.isEmpty()) {
                    lastError = joinErrors(schemaErrors);
                    lastCause = null;
                    continue;
                }

                T parsedValue = parseValue(outputText, rawClass, typeReference);
                if (validator != null) {
                    validator.validate(parsedValue);
                }
                return parsedValue;
            } catch (Exception ex) {
                lastError = formatExceptionMessage(ex);
                lastCause = ex;
            }
        }

        String summary = "Failed to produce valid structured output for type "
                + describeType(javaType)
                + " after "
                + (resolvedMaxAttempts + 1)
                + " attempt(s)"
                + (lastError == null ? "" : ": " + lastError);
        throw new StructuredOutputException(summary, lastRawOutput, lastCause);
    }

    private <T> T parseValue(String rawOutput,
                             Class<T> rawClass,
                             TypeReference<T> typeReference) {
        if (rawClass != null) {
            return JsonCodec.fromJsonLenient(rawOutput, rawClass);
        }
        return JsonCodec.fromJsonLenient(rawOutput, typeReference);
    }

    private AgentContext augmentContextInstructions(AgentContext baseContext, String schemaJson) {
        if (baseContext == null) {
            throw new IllegalStateException("agent context is required");
        }
        String existing = baseContext.getInstructions();
        StringBuilder sb = new StringBuilder();
        if (existing != null && !existing.trim().isEmpty()) {
            sb.append(existing.trim()).append("\n\n");
        }
        sb.append("Structured Output Requirement:\n");
        sb.append("Respond ONLY with valid JSON conforming to the following JSON Schema. ");
        sb.append("Do not include prose, markdown headers, or explanations outside the JSON payload.\n");
        sb.append(schemaJson);
        return baseContext.toBuilder()
                .instructions(sb.toString())
                .build();
    }

    private AgentRequest buildHealRequest(AgentRequest originalRequest,
                                          String lastRawOutput,
                                          String lastError,
                                          String schemaJson,
                                          int attempt) {
        StringBuilder prompt = new StringBuilder();
        if (originalRequest.getInput() instanceof CharSequence) {
            String originalText = originalRequest.getInput().toString().trim();
            if (!originalText.isEmpty()) {
                prompt.append("Original task:\n").append(originalText).append("\n\n");
            }
        }
        prompt.append("Self-healing retry #").append(attempt).append(": ");
        prompt.append("Your previous response failed structured output validation.\n");
        if (lastError != null && !lastError.trim().isEmpty()) {
            prompt.append("Validation error: ").append(lastError.trim()).append("\n");
        }
        if (lastRawOutput != null && !lastRawOutput.trim().isEmpty()) {
            prompt.append("Previous invalid output:\n")
                    .append(truncateSnippet(lastRawOutput.trim()))
                    .append("\n");
        }
        prompt.append("\nReturn ONLY corrected valid JSON conforming strictly to this JSON Schema:\n");
        prompt.append(schemaJson);

        AgentRequest.AgentRequestBuilder builder = originalRequest.toBuilder();
        Map<String, Object> metadata = originalRequest.getMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            Map<String, Object> copied = new LinkedHashMap<String, Object>(metadata);
            copied.remove(AgentRequest.METADATA_KEY_TURN_ID);
            copied.remove(AgentRequest.METADATA_KEY_EVENT_ID);
            builder.metadata(copied);
        }
        builder.input(prompt.toString());
        return builder.build();
    }

    static String buildSchemaJson(JavaType javaType) {
        ObjectMapper mapper = JsonCodec.protocolMapper();
        ObjectNode root = buildSchemaNode(javaType, mapper, new HashSet<Class<?>>());
        return JsonCodec.toJson(root);
    }

    private static ObjectNode buildSchemaNode(JavaType javaType,
                                            ObjectMapper mapper,
                                            Set<Class<?>> visiting) {
        ObjectNode node = JsonCodec.createObjectNode();
        if (javaType == null) {
            return node;
        }
        Class<?> raw = javaType.getRawClass();
        if (raw == String.class || CharSequence.class.isAssignableFrom(raw)
                || raw == UUID.class || raw == URI.class || raw == URL.class
                || raw == Date.class || isJavaTimeTemporal(raw)) {
            node.put("type", "string");
            return node;
        }
        if (raw == boolean.class || raw == Boolean.class) {
            node.put("type", "boolean");
            return node;
        }
        if (raw == int.class || raw == Integer.class
                || raw == long.class || raw == Long.class
                || raw == short.class || raw == Short.class
                || raw == byte.class || raw == Byte.class
                || raw == BigInteger.class) {
            node.put("type", "integer");
            return node;
        }
        if (raw == double.class || raw == Double.class
                || raw == float.class || raw == Float.class
                || raw == BigDecimal.class
                || Number.class.isAssignableFrom(raw)) {
            node.put("type", "number");
            return node;
        }
        if (raw.isEnum()) {
            node.put("type", "string");
            ArrayNode enumValues = JsonCodec.createArrayNode();
            Object[] constants = raw.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    enumValues.add(String.valueOf(constant));
                }
            }
            node.set("enum", enumValues);
            return node;
        }
        if (javaType.isArrayType() || javaType.isCollectionLikeType()) {
            node.put("type", "array");
            node.set("items", buildSchemaNode(javaType.getContentType(), mapper, visiting));
            return node;
        }
        if (javaType.isMapLikeType() || Map.class.isAssignableFrom(raw)) {
            node.put("type", "object");
            JavaType valueType = javaType.getContentType();
            if (valueType != null && valueType.getRawClass() != Object.class) {
                node.set("additionalProperties", buildSchemaNode(valueType, mapper, visiting));
            }
            return node;
        }
        if (raw == Object.class || JsonNode.class.isAssignableFrom(raw) || isJdkInternalType(raw)) {
            node.put("type", "object");
            return node;
        }
        if (!visiting.add(raw)) {
            node.put("type", "object");
            return node;
        }

        try {
            node.put("type", "object");
            ObjectNode properties = JsonCodec.createObjectNode();
            ArrayNode required = JsonCodec.createArrayNode();
            DeserializationConfig config = mapper.getDeserializationConfig();
            BeanDescription beanDesc = config.introspect(javaType);
            for (BeanPropertyDefinition prop : beanDesc.findProperties()) {
                if (!isDeserializableProperty(prop)) {
                    continue;
                }
                String propName = prop.getName();
                if (propName == null || propName.isEmpty()) {
                    continue;
                }
                JavaType propType = prop.getPrimaryType();
                properties.set(propName, buildSchemaNode(propType, mapper, visiting));
                if (isRequiredProperty(prop)) {
                    required.add(propName);
                }
            }
            node.set("properties", properties);
            if (!required.isEmpty()) {
                node.set("required", required);
            }
            return node;
        } finally {
            visiting.remove(raw);
        }
    }

    private static void validateJsonTree(JsonNode node,
                                         JavaType javaType,
                                         String path,
                                         ObjectMapper mapper,
                                         List<String> errors,
                                         Set<Class<?>> visiting) {
        if (javaType == null || node == null || node.isNull()) {
            return;
        }
        Class<?> raw = javaType.getRawClass();
        if (javaType.isArrayType() || javaType.isCollectionLikeType()) {
            if (!node.isArray()) {
                errors.add("Expected JSON array at " + path);
                return;
            }
            JavaType elemType = javaType.getContentType();
            for (int i = 0; i < node.size(); i++) {
                validateJsonTree(node.get(i), elemType, path + "[" + i + "]", mapper, errors, visiting);
            }
            return;
        }
        if (javaType.isMapLikeType() || Map.class.isAssignableFrom(raw)) {
            if (!node.isObject()) {
                errors.add("Expected JSON object at " + path);
                return;
            }
            JavaType valType = javaType.getContentType();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                validateJsonTree(entry.getValue(), valType, path + "." + entry.getKey(), mapper, errors, visiting);
            }
            return;
        }
        if (isScalarOrOpaque(raw) || isJdkInternalType(raw)) {
            return;
        }
        if (!node.isObject()) {
            errors.add("Expected JSON object at " + path + " for " + raw.getSimpleName());
            return;
        }
        if (!visiting.add(raw)) {
            return;
        }
        try {
            BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(javaType);
            for (BeanPropertyDefinition prop : beanDesc.findProperties()) {
                if (!isDeserializableProperty(prop)) {
                    continue;
                }
                List<String> acceptedNames = collectAcceptedPropertyNames(prop);
                if (acceptedNames.isEmpty()) {
                    continue;
                }
                JsonNode fieldNode = findMatchingFieldNode(node, acceptedNames);
                String childPath = path + "." + prop.getName();
                if (isRequiredProperty(prop) && (fieldNode == null || fieldNode.isNull())) {
                    errors.add("Missing required property '" + childPath + "'");
                    continue;
                }
                if (fieldNode != null && !fieldNode.isNull()) {
                    validateJsonTree(fieldNode, prop.getPrimaryType(), childPath, mapper, errors, visiting);
                }
            }
        } finally {
            visiting.remove(raw);
        }
    }

    private static boolean isDeserializableProperty(BeanPropertyDefinition prop) {
        return prop != null && (prop.couldDeserialize() || prop.hasField());
    }

    private static boolean isRequiredProperty(BeanPropertyDefinition prop) {
        if (prop.isRequired()) {
            return true;
        }
        return hasRequiredJsonProperty(prop.getField())
                || hasRequiredJsonProperty(prop.getSetter())
                || hasRequiredJsonProperty(prop.getGetter())
                || hasRequiredJsonProperty(prop.getConstructorParameter());
    }

    private static boolean hasRequiredJsonProperty(AnnotatedMember member) {
        if (member == null) {
            return false;
        }
        JsonProperty annotation = member.getAnnotation(JsonProperty.class);
        return annotation != null && annotation.required();
    }

    private static List<String> collectAcceptedPropertyNames(BeanPropertyDefinition prop) {
        Set<String> names = new LinkedHashSet<String>();
        if (prop.getName() != null && !prop.getName().isEmpty()) {
            names.add(prop.getName());
        }
        addJsonAliases(names, prop.getField());
        addJsonAliases(names, prop.getSetter());
        addJsonAliases(names, prop.getGetter());
        addJsonAliases(names, prop.getConstructorParameter());
        return new ArrayList<String>(names);
    }

    private static void addJsonAliases(Set<String> names, AnnotatedMember member) {
        if (member == null) {
            return;
        }
        JsonAlias alias = member.getAnnotation(JsonAlias.class);
        if (alias != null && alias.value() != null) {
            for (String candidate : alias.value()) {
                if (candidate != null && !candidate.isEmpty()) {
                    names.add(candidate);
                }
            }
        }
    }

    private static JsonNode findMatchingFieldNode(JsonNode objectNode, List<String> acceptedNames) {
        for (String name : acceptedNames) {
            if (objectNode.has(name)) {
                return objectNode.get(name);
            }
        }
        return null;
    }

    private static boolean isScalarOrOpaque(Class<?> raw) {
        return raw.isPrimitive()
                || raw.isEnum()
                || raw == Object.class
                || raw == String.class
                || CharSequence.class.isAssignableFrom(raw)
                || Number.class.isAssignableFrom(raw)
                || raw == Boolean.class
                || raw == Character.class
                || JsonNode.class.isAssignableFrom(raw)
                || Date.class.isAssignableFrom(raw)
                || raw == UUID.class
                || raw == URI.class
                || raw == URL.class
                || isJavaTimeTemporal(raw);
    }

    private static boolean isJavaTimeTemporal(Class<?> raw) {
        String name = raw.getName();
        return name.startsWith("java.time.");
    }

    private static boolean isJdkInternalType(Class<?> raw) {
        String name = raw.getName();
        return name.startsWith("java.") || name.startsWith("javax.");
    }

    private static String joinErrors(List<String> errors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(errors.get(i));
        }
        return sb.toString();
    }

    private static String formatExceptionMessage(Throwable ex) {
        if (ex == null) {
            return "Unknown validation error";
        }
        String msg = ex.getMessage();
        if (msg != null && !msg.trim().isEmpty()) {
            return ex.getClass().getSimpleName() + ": " + msg;
        }
        return ex.getClass().getSimpleName();
    }

    private static String truncateSnippet(String raw) {
        if (raw.length() <= MAX_ERROR_OUTPUT_SNIPPET) {
            return raw;
        }
        return raw.substring(0, MAX_ERROR_OUTPUT_SNIPPET) + "...(truncated)";
    }

    private static String describeType(JavaType javaType) {
        if (javaType == null) {
            return "unknown";
        }
        return javaType.toCanonical();
    }
}
