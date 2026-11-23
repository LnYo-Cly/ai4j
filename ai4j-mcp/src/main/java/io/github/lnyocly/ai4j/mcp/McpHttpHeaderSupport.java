package io.github.lnyocly.ai4j.mcp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Implements the 2026 Streamable HTTP x-mcp-header subset for schema
 * properties reachable through object properties only.
 */
public final class McpHttpHeaderSupport {

    private static final String HEADER_PREFIX = "Mcp-Param-";
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    private McpHttpHeaderSupport() {
    }

    public static Map<String, String> createToolParameterHeaders(Map<String, Object> inputSchema,
                                                                   Object arguments) {
        List<Binding> bindings = new ArrayList<Binding>();
        collectBindings(inputSchema, new ArrayList<String>(), false, bindings, new HashSet<String>());
        Map<String, String> headers = new HashMap<String, String>();
        Map<String, Object> argumentMap = asMap(arguments);
        for (Binding binding : bindings) {
            Object value = valueAt(argumentMap, binding.path);
            if (value != null) {
                headers.put(HEADER_PREFIX + binding.headerName, encodeHeaderValue(value, binding.type));
            }
        }
        return headers;
    }

    public static Set<String> getToolParameterHeaderNames(Map<String, Object> inputSchema) {
        List<Binding> bindings = new ArrayList<Binding>();
        collectBindings(inputSchema, new ArrayList<String>(), false, bindings, new HashSet<String>());
        Set<String> names = new HashSet<String>();
        for (Binding binding : bindings) {
            names.add(HEADER_PREFIX + binding.headerName);
        }
        return names;
    }

    public static boolean isValidToolSchema(Map<String, Object> inputSchema) {
        try {
            createToolParameterHeaders(inputSchema, new HashMap<String, Object>());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Compares decoded parameter-header values using the type declared by the
     * matching {@code x-mcp-header} binding. Integers use numeric equivalence
     * so that equivalent decimal forms such as {@code 42} and {@code 42.0}
     * are accepted by the server.
     */
    public static boolean areToolParameterHeaderValuesEquivalent(Map<String, Object> inputSchema,
                                                                   String headerName,
                                                                   String expectedValue,
                                                                   String actualValue) {
        if (expectedValue == null) {
            return actualValue == null;
        }
        if (actualValue == null) {
            return false;
        }
        Binding binding = findBinding(inputSchema, headerName);
        if (binding == null || !"integer".equals(binding.type)) {
            return expectedValue.equals(actualValue);
        }
        try {
            return new BigDecimal(expectedValue).compareTo(new BigDecimal(actualValue)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String encodeHeaderValue(String value) {
        return encodeText(value);
    }

    private static void collectBindings(Object schemaValue, List<String> path, boolean reachable,
                                        List<Binding> bindings, Set<String> headerNames) {
        Map<String, Object> schema = asMap(schemaValue);
        if (schema == null) {
            return;
        }
        Object annotation = schema.get("x-mcp-header");
        if (annotation != null) {
            String headerName = annotation instanceof String ? (String) annotation : null;
            String type = stringValue(schema.get("type"));
            if (!reachable || !isHeaderToken(headerName) || !isPrimitiveType(type)) {
                throw new IllegalArgumentException("Invalid x-mcp-header annotation");
            }
            if (!headerNames.add(headerName.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate x-mcp-header annotation");
            }
            bindings.add(new Binding(new ArrayList<String>(path), headerName, type));
        }

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            if ("properties".equals(entry.getKey())) {
                Map<String, Object> properties = asMap(entry.getValue());
                if (properties != null) {
                    for (Map.Entry<String, Object> property : properties.entrySet()) {
                        List<String> nestedPath = new ArrayList<String>(path);
                        nestedPath.add(property.getKey());
                        collectBindings(property.getValue(), nestedPath, true, bindings, headerNames);
                    }
                }
            } else if (!"x-mcp-header".equals(entry.getKey()) && !"type".equals(entry.getKey())) {
                collectBindings(entry.getValue(), path, false, bindings, headerNames);
            }
        }
    }

    private static Object valueAt(Map<String, Object> arguments, List<String> path) {
        Object current = arguments;
        for (String segment : path) {
            Map<String, Object> map = asMap(current);
            if (map == null || !map.containsKey(segment)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static String encodeHeaderValue(Object value, String type) {
        if ("string".equals(type)) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("x-mcp-header value must be a string");
            }
            return encodeText((String) value);
        }
        if ("boolean".equals(type)) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("x-mcp-header value must be a boolean");
            }
            return ((Boolean) value).booleanValue() ? "true" : "false";
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("x-mcp-header value must be an integer");
        }
        long integer = asSafeInteger((Number) value);
        if (integer > MAX_SAFE_INTEGER || integer < -MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException("x-mcp-header integer is outside the JavaScript safe range");
        }
        return String.valueOf(integer);
    }

    private static long asSafeInteger(Number value) {
        BigDecimal decimal;
        if (value instanceof BigDecimal) {
            decimal = (BigDecimal) value;
        } else if (value instanceof BigInteger) {
            decimal = new BigDecimal((BigInteger) value);
        } else if (value instanceof Double || value instanceof Float) {
            double floatingPoint = value.doubleValue();
            if (Double.isNaN(floatingPoint) || Double.isInfinite(floatingPoint)) {
                throw new IllegalArgumentException("x-mcp-header integer must be finite");
            }
            decimal = BigDecimal.valueOf(floatingPoint);
        } else {
            try {
                decimal = new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("x-mcp-header value must be an integer", e);
            }
        }
        try {
            return decimal.longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("x-mcp-header value must be an exact integer", e);
        }
    }

    private static String encodeText(String value) {
        if (value == null) {
            throw new IllegalArgumentException("x-mcp-header string value must not be null");
        }
        boolean safe = !value.isEmpty() && value.equals(value.trim());
        for (int i = 0; safe && i < value.length(); i++) {
            char c = value.charAt(i);
            safe = c >= 0x21 && c <= 0x7e;
        }
        if (safe && !(value.startsWith("=?base64?") && value.endsWith("?="))) {
            return value;
        }
        return "=?base64?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static boolean isPrimitiveType(String type) {
        return "string".equals(type) || "integer".equals(type) || "boolean".equals(type);
    }

    private static boolean isHeaderToken(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean token = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z') || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
            if (!token) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> map = new HashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() != null) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return map;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Binding findBinding(Map<String, Object> inputSchema, String headerName) {
        if (headerName == null) {
            return null;
        }
        List<Binding> bindings = new ArrayList<Binding>();
        collectBindings(inputSchema, new ArrayList<String>(), false, bindings, new HashSet<String>());
        for (Binding binding : bindings) {
            if ((HEADER_PREFIX + binding.headerName).equalsIgnoreCase(headerName)) {
                return binding;
            }
        }
        return null;
    }

    private static final class Binding {
        private final List<String> path;
        private final String headerName;
        private final String type;

        private Binding(List<String> path, String headerName, String type) {
            this.path = path;
            this.headerName = headerName;
            this.type = type;
        }
    }
}
