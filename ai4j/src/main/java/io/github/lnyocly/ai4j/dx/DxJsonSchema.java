package io.github.lnyocly.ai4j.dx;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflection-based JSON Schema generator for {@code io.github.lnyocly.ai4j.dx}
 * using Jackson annotations only (zero fastjson2 dependency).
 */
final class DxJsonSchema {

    private DxJsonSchema() {
    }

    static Map<String, Object> schemaFor(TypeReference<?> typeReference) {
        return schemaFor(typeReference.getType());
    }

    static Map<String, Object> schemaFor(Type type) {
        return buildTypeSchema(type, new HashSet<Type>());
    }

    static Map<String, Object> buildTypeSchema(Type type, Set<Type> visited) {
        if (type == null || type == Object.class) {
            return simpleSchema("object");
        }
        if (type instanceof JavaType) {
            return buildJavaTypeSchema((JavaType) type, visited);
        }
        if (type instanceof Class<?>) {
            return buildClassSchema((Class<?>) type, visited);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type raw = pt.getRawType();
            if (raw instanceof Class<?>) {
                Class<?> rawClass = (Class<?>) raw;
                Type[] args = pt.getActualTypeArguments();
                if (Collection.class.isAssignableFrom(rawClass)) {
                    Map<String, Object> schema = simpleSchema("array");
                    Type elementType = args.length > 0 ? args[0] : Object.class;
                    schema.put("items", buildTypeSchema(elementType, visited));
                    return schema;
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    Map<String, Object> schema = simpleSchema("object");
                    if (args.length > 1) {
                        schema.put("additionalProperties", buildTypeSchema(args[1], visited));
                    }
                    return schema;
                }
                return buildClassSchema(rawClass, visited);
            }
        }
        return simpleSchema("string");
    }

    private static Map<String, Object> buildJavaTypeSchema(JavaType javaType, Set<Type> visited) {
        if (javaType.isCollectionLikeType() || javaType.isArrayType()) {
            Map<String, Object> schema = simpleSchema("array");
            JavaType elem = javaType.getContentType();
            schema.put("items", elem == null ? simpleSchema("object") : buildJavaTypeSchema(elem, visited));
            return schema;
        }
        if (javaType.isMapLikeType()) {
            Map<String, Object> schema = simpleSchema("object");
            JavaType val = javaType.getContentType();
            if (val != null) {
                schema.put("additionalProperties", buildJavaTypeSchema(val, visited));
            }
            return schema;
        }
        return buildClassSchema(javaType.getRawClass(), visited);
    }

    private static Map<String, Object> buildClassSchema(Class<?> clazz, Set<Type> visited) {
        if (clazz == String.class || clazz == CharSequence.class
                || clazz == Character.class || clazz == char.class) {
            return simpleSchema("string");
        }
        if (clazz == Integer.class || clazz == int.class
                || clazz == Long.class || clazz == long.class
                || clazz == Short.class || clazz == short.class
                || clazz == Byte.class || clazz == byte.class
                || clazz == BigInteger.class) {
            return simpleSchema("integer");
        }
        if (clazz == Double.class || clazz == double.class
                || clazz == Float.class || clazz == float.class
                || clazz == BigDecimal.class) {
            return simpleSchema("number");
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return simpleSchema("boolean");
        }
        if (clazz.isEnum()) {
            Map<String, Object> schema = simpleSchema("string");
            List<String> values = new ArrayList<String>();
            for (Object constant : clazz.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
            schema.put("enum", values);
            return schema;
        }
        if (clazz.isArray()) {
            Map<String, Object> schema = simpleSchema("array");
            schema.put("items", buildClassSchema(clazz.getComponentType(), visited));
            return schema;
        }
        if (Collection.class.isAssignableFrom(clazz)) {
            Map<String, Object> schema = simpleSchema("array");
            schema.put("items", simpleSchema("object"));
            return schema;
        }
        if (Map.class.isAssignableFrom(clazz) || clazz == Object.class) {
            return simpleSchema("object");
        }

        if (!visited.add(clazz)) {
            return simpleSchema("object");
        }
        try {
            Map<String, Object> schema = new LinkedHashMap<String, Object>();
            schema.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<String, Object>();
            List<String> required = new ArrayList<String>();
            for (Field field : allFields(clazz)) {
                String name = fieldName(field);
                Map<String, Object> fieldSchema = buildTypeSchema(field.getGenericType(), visited);
                properties.put(name, fieldSchema);
                required.add(name);
            }
            schema.put("properties", properties);
            schema.put("required", required);
            schema.put("additionalProperties", Boolean.FALSE);
            return schema;
        } finally {
            visited.remove(clazz);
        }
    }

    static Map<String, Object> simpleSchema(String type) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", type);
        return schema;
    }

    private static List<Field> allFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())
                        && !f.isSynthetic()) {
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static String fieldName(Field field) {
        JsonProperty prop = field.getAnnotation(JsonProperty.class);
        if (prop != null && !prop.value().isEmpty()) {
            return prop.value();
        }
        return field.getName();
    }
}
