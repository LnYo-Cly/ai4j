package io.github.lnyocly.ai4j.convert;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates a JSON Schema from a Java class or {@link TypeReference} using reflection and Jackson.
 * <p>
 * Supports: String, Integer/int, Long/long, Double/double, Float/float, Boolean/boolean,
 * BigDecimal, BigInteger, enum (→ string + enum values), nested objects (recursive),
 * List&lt;T&gt;/Collection&lt;T&gt; (→ array + items), Map (→ object),
 * arrays, and parameterized types. Generates OpenAI Structured Outputs-compatible schemas
 * (all fields required, additionalProperties: false).
 */
public final class JsonSchemaGenerator {

    private JsonSchemaGenerator() {
    }

    /**
     * Generate a JSON Schema string from a Java class.
     */
    public static String generate(Class<?> clazz) {
        return generateObject(clazz).toString();
    }

    /**
     * Generate a JSON Schema string from a Jackson {@link TypeReference}.
     */
    public static String generate(TypeReference<?> typeReference) {
        return generateObject(typeReference).toString();
    }

    /**
     * Generate a Jackson {@link ObjectNode} schema from a Java class.
     */
    public static ObjectNode generateObject(Class<?> clazz) {
        return buildSchema(clazz, Collections.<TypeVariable<?>, Type>emptyMap());
    }

    /**
     * Generate a Jackson {@link ObjectNode} schema from a reflection {@link Type}.
     */
    public static ObjectNode generateObject(Type type) {
        return buildSchemaFromType(type, Collections.<TypeVariable<?>, Type>emptyMap());
    }

    /**
     * Generate a Jackson {@link ObjectNode} schema from a Jackson {@link TypeReference}.
     */
    public static ObjectNode generateObject(TypeReference<?> typeReference) {
        if (typeReference == null) {
            throw new IllegalArgumentException("typeReference is required");
        }
        return generateObject(typeReference.getType());
    }

    /**
     * Generate the full response_format object for OpenAI Structured Outputs.
     * <pre>
     * {"type":"json_schema","json_schema":{"name":"...","strict":true,"schema":{...}}}
     * </pre>
     */
    public static ObjectNode responseFormat(Class<?> clazz, String schemaName) {
        return responseFormat((Type) clazz, schemaName);
    }

    /**
     * Generate the full response_format object for OpenAI Structured Outputs from a reflection {@link Type}.
     */
    public static ObjectNode responseFormat(Type type, String schemaName) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        ObjectNode jsonSchema = JsonCodec.createObjectNode();
        jsonSchema.put("name", schemaName == null ? defaultSchemaName(type) : schemaName);
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", buildSchemaFromType(type, Collections.<TypeVariable<?>, Type>emptyMap()));

        ObjectNode result = JsonCodec.createObjectNode();
        result.put("type", "json_schema");
        result.set("json_schema", jsonSchema);
        return result;
    }

    /**
     * Generate the full response_format object for OpenAI Structured Outputs from a {@link TypeReference}.
     */
    public static ObjectNode responseFormat(TypeReference<?> typeReference, String schemaName) {
        if (typeReference == null) {
            throw new IllegalArgumentException("typeReference is required");
        }
        return responseFormat(typeReference.getType(), schemaName);
    }

    private static String defaultSchemaName(Type type) {
        if (type instanceof Class<?>) {
            return ((Class<?>) type).getSimpleName().toLowerCase(Locale.ROOT);
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?>) {
                return ((Class<?>) rawType).getSimpleName().toLowerCase(Locale.ROOT);
            }
        }
        return "structured_output";
    }

    private static ObjectNode buildSchema(Class<?> clazz, Map<TypeVariable<?>, Type> typeBindings) {
        if (clazz == String.class || clazz == CharSequence.class || clazz == Character.class
                || clazz == char.class) {
            return simpleSchema("string");
        }
        if (clazz == Integer.class || clazz == int.class || clazz == Long.class || clazz == long.class
                || clazz == Short.class || clazz == short.class || clazz == Byte.class || clazz == byte.class
                || clazz == BigInteger.class) {
            return simpleSchema("integer");
        }
        if (clazz == Double.class || clazz == double.class || clazz == Float.class || clazz == float.class
                || clazz == BigDecimal.class) {
            return simpleSchema("number");
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return simpleSchema("boolean");
        }
        if (clazz.isEnum()) {
            ObjectNode schema = simpleSchema("string");
            ArrayNode values = JsonCodec.createArrayNode();
            Object[] constants = clazz.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    values.add(((Enum<?>) constant).name());
                }
            }
            schema.set("enum", values);
            return schema;
        }
        if (clazz == Object.class) {
            return simpleSchema("object");
        }
        if (clazz.isArray()) {
            ObjectNode schema = simpleSchema("array");
            schema.set("items", buildSchema(clazz.getComponentType(), typeBindings));
            return schema;
        }
        if (Collection.class.isAssignableFrom(clazz)) {
            ObjectNode schema = simpleSchema("array");
            schema.set("items", simpleSchema("string"));
            return schema;
        }
        if (Map.class.isAssignableFrom(clazz)) {
            return simpleSchema("object");
        }
        return buildObjectSchema(clazz, typeBindings);
    }

    private static ObjectNode buildObjectSchema(Class<?> clazz, Map<TypeVariable<?>, Type> typeBindings) {
        ObjectNode schema = JsonCodec.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = JsonCodec.createObjectNode();
        ArrayNode required = JsonCodec.createArrayNode();

        for (Field field : getAllFields(clazz)) {
            if (field.getAnnotation(JsonIgnore.class) != null) {
                continue;
            }
            String name = getFieldName(field);
            ObjectNode fieldSchema = buildFieldSchema(field, typeBindings);

            properties.set(name, fieldSchema);
            required.add(name);
        }

        schema.set("properties", properties);
        schema.set("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ObjectNode buildFieldSchema(Field field, Map<TypeVariable<?>, Type> typeBindings) {
        Class<?> type = field.getType();
        Type genericType = resolveType(field.getGenericType(), typeBindings);

        if (type.isArray()) {
            ObjectNode schema = simpleSchema("array");
            schema.set("items", buildSchema(type.getComponentType(), typeBindings));
            return schema;
        }
        if (genericType instanceof ParameterizedType || genericType instanceof TypeVariable<?>) {
            return buildSchemaFromType(genericType, typeBindings);
        }
        if (Collection.class.isAssignableFrom(type)) {
            return buildSchemaFromType(genericType, typeBindings);
        }
        if (Map.class.isAssignableFrom(type)) {
            return simpleSchema("object");
        }
        if (genericType instanceof Class<?>) {
            return buildSchema((Class<?>) genericType, typeBindings);
        }
        return buildSchema(type, typeBindings);
    }

    private static ObjectNode buildSchemaFromType(Type type, Map<TypeVariable<?>, Type> outerBindings) {
        Type resolved = resolveType(type, outerBindings);
        if (resolved instanceof Class<?>) {
            return buildSchema((Class<?>) resolved, outerBindings);
        }
        if (resolved instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) resolved;
            Type rawTypeObj = pt.getRawType();
            if (!(rawTypeObj instanceof Class<?>)) {
                return simpleSchema("object");
            }
            Class<?> rawType = (Class<?>) rawTypeObj;
            Type[] typeArgs = pt.getActualTypeArguments();
            if (Collection.class.isAssignableFrom(rawType) && typeArgs.length > 0) {
                ObjectNode schema = simpleSchema("array");
                schema.set("items", buildSchemaFromType(typeArgs[0], outerBindings));
                return schema;
            }
            if (Map.class.isAssignableFrom(rawType)) {
                return simpleSchema("object");
            }
            Map<TypeVariable<?>, Type> nestedBindings = new HashMap<TypeVariable<?>, Type>(outerBindings);
            TypeVariable<?>[] typeParams = rawType.getTypeParameters();
            int count = Math.min(typeParams.length, typeArgs.length);
            for (int i = 0; i < count; i++) {
                nestedBindings.put(typeParams[i], resolveType(typeArgs[i], outerBindings));
            }
            return buildSchema(rawType, nestedBindings);
        }
        if (resolved instanceof WildcardType) {
            Type[] upperBounds = ((WildcardType) resolved).getUpperBounds();
            if (upperBounds.length > 0) {
                return buildSchemaFromType(upperBounds[0], outerBindings);
            }
        }
        return simpleSchema("string");
    }

    private static Type resolveType(Type type, Map<TypeVariable<?>, Type> typeBindings) {
        if (type instanceof TypeVariable<?> && typeBindings != null) {
            Type resolved = typeBindings.get(type);
            if (resolved != null && resolved != type) {
                return resolveType(resolved, typeBindings);
            }
        }
        return type;
    }

    private static ObjectNode simpleSchema(String type) {
        ObjectNode schema = JsonCodec.createObjectNode();
        schema.put("type", type);
        return schema;
    }

    /**
     * Get all declared fields from the class and its superclasses (except Object).
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())
                        && !Modifier.isTransient(f.getModifiers())) {
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * Resolve the JSON field name: uses @JsonProperty if present, otherwise the field name as-is.
     */
    private static String getFieldName(Field field) {
        JsonProperty jackson = field.getAnnotation(JsonProperty.class);
        if (jackson != null && !jackson.value().isEmpty()) {
            return jackson.value();
        }
        return field.getName();
    }
}
