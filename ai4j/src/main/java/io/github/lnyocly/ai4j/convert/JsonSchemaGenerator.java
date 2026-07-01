package io.github.lnyocly.ai4j.convert;

import com.alibaba.fastjson2.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a JSON Schema from a Java class using reflection — no external dependency.
 * <p>
 * Supports: String, Integer/int, Long/long, Double/double, Float/float, Boolean/boolean,
 * BigDecimal, BigInteger, enum (→ string + enum values), nested objects (recursive),
 * List&lt;T&gt;/Collection&lt;T&gt; (→ array + items), Map (→ object with string values),
 * arrays. Generates OpenAI Structured Outputs-compatible output (all fields required,
 * additionalProperties: false).
 * <p>
 * Usage:
 * <pre>
 * String schema = JsonSchemaGenerator.generate(MyClass.class);
 * // → {"type":"object","properties":{...},"required":[...],"additionalProperties":false}
 *
 * // For OpenAI response_format:
 * JSONObject responseFormat = JsonSchemaGenerator.responseFormat(MyClass.class, "my_class");
 * </pre>
 */
public final class JsonSchemaGenerator {

    private JsonSchemaGenerator() {
    }

    /**
     * Generate a JSON Schema string from a Java class.
     * The schema has all fields as required and additionalProperties: false
     * (OpenAI Structured Outputs compatible).
     */
    public static String generate(Class<?> clazz) {
        return generateObject(clazz).toString();
    }

    /**
     * Generate a JSONObject schema from a Java class.
     */
    public static JSONObject generateObject(Class<?> clazz) {
        return buildSchema(clazz);
    }

    /**
     * Generate the full response_format object for OpenAI Structured Outputs.
     * <pre>
     * {"type":"json_schema","json_schema":{"name":"...","strict":true,"schema":{...}}}
     * </pre>
     */
    public static JSONObject responseFormat(Class<?> clazz, String schemaName) {
        JSONObject jsonSchema = new JSONObject();
        jsonSchema.put("name", schemaName == null ? clazz.getSimpleName().toLowerCase() : schemaName);
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", buildSchema(clazz));

        JSONObject result = new JSONObject();
        result.put("type", "json_schema");
        result.put("json_schema", jsonSchema);
        return result;
    }

    private static JSONObject buildSchema(Class<?> clazz) {
        if (clazz == String.class || clazz == CharSequence.class || clazz == Character.class
                || clazz == char.class) {
            return simpleSchema("string");
        }
        if (clazz == Integer.class || clazz == int.class || clazz == Long.class || clazz == long.class
                || clazz == Short.class || clazz == short.class || clazz == Byte.class || clazz == byte.class
                || clazz == java.math.BigInteger.class) {
            return simpleSchema("integer");
        }
        if (clazz == Double.class || clazz == double.class || clazz == Float.class || clazz == float.class
                || clazz == java.math.BigDecimal.class) {
            return simpleSchema("number");
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return simpleSchema("boolean");
        }
        if (clazz.isEnum()) {
            JSONObject schema = simpleSchema("string");
            List<String> values = new ArrayList<String>();
            for (Object constant : clazz.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
            schema.put("enum", values);
            return schema;
        }
        if (clazz == Object.class) {
            return simpleSchema("object");
        }
        // Collection/List/Map as raw types (e.g. nested List<List<T>>) — can't infer element type
        if (Collection.class.isAssignableFrom(clazz)) {
            JSONObject schema = simpleSchema("array");
            schema.put("items", simpleSchema("string")); // best-effort fallback
            return schema;
        }
        if (Map.class.isAssignableFrom(clazz)) {
            return simpleSchema("object");
        }
        // Object type — recurse into fields
        return buildObjectSchema(clazz);
    }

    private static JSONObject buildObjectSchema(Class<?> clazz) {
        JSONObject schema = new JSONObject(new LinkedHashMap<String, Object>()); // ordered
        schema.put("type", "object");

        JSONObject properties = new JSONObject(new LinkedHashMap<String, Object>());
        List<String> required = new ArrayList<String>();

        for (Field field : getAllFields(clazz)) {
            field.setAccessible(true);
            String name = getFieldName(field);
            JSONObject fieldSchema = buildFieldSchema(field);

            properties.put(name, fieldSchema);
            required.add(name);
        }

        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject buildFieldSchema(Field field) {
        Class<?> type = field.getType();
        java.lang.reflect.Type genericType = field.getGenericType();

        // Array/List/Collection
        if (type.isArray()) {
            JSONObject schema = simpleSchema("array");
            schema.put("items", buildSchema(type.getComponentType()));
            return schema;
        }
        if (Collection.class.isAssignableFrom(type) || List.class.isAssignableFrom(type)) {
            return buildSchemaFromType(genericType);
        }
        // Map → object with string values (best effort)
        if (Map.class.isAssignableFrom(type)) {
            return simpleSchema("object");
        }

        return buildSchema(type);
    }

    private static JSONObject buildSchemaFromType(java.lang.reflect.Type genericType) {
        if (genericType instanceof Class) {
            return buildSchema((Class<?>) genericType);
        }
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Class<?> rawType = (Class<?>) pt.getRawType();
            Type[] typeArgs = pt.getActualTypeArguments();
            if (Collection.class.isAssignableFrom(rawType) && typeArgs.length > 0) {
                JSONObject schema = simpleSchema("array");
                schema.put("items", buildSchemaFromType(typeArgs[0]));
                return schema;
            }
            if (Map.class.isAssignableFrom(rawType)) {
                return simpleSchema("object");
            }
            return buildSchema(rawType);
        }
        return simpleSchema("string"); // fallback for wildcard types
    }

    private static JSONObject simpleSchema(String type) {
        JSONObject schema = new JSONObject(new LinkedHashMap<String, Object>());
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
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && !java.lang.reflect.Modifier.isTransient(f.getModifiers())) {
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
        // Check for common JSON property annotations
        try {
            com.alibaba.fastjson2.annotation.JSONField fastjson = field.getAnnotation(com.alibaba.fastjson2.annotation.JSONField.class);
            if (fastjson != null && !fastjson.name().isEmpty()) {
                return fastjson.name();
            }
        } catch (NoClassDefFoundError ignored) {
            // fastjson2 annotation not available
        }
        try {
            com.fasterxml.jackson.annotation.JsonProperty jackson = field.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
            if (jackson != null && !jackson.value().isEmpty()) {
                return jackson.value();
            }
        } catch (NoClassDefFoundError ignored) {
            // Jackson not on classpath
        }
        return field.getName();
    }
}
