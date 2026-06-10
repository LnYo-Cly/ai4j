package io.github.lnyocly.ai4j.extension.validation;

import io.github.lnyocly.ai4j.extension.ExtensionManifest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ExtensionToolSchemaValidator {

    private static final int MAX_DEPTH = 24;

    private ExtensionToolSchemaValidator() {
    }

    static String validate(String schema) {
        String normalized = ExtensionManifest.emptyToNull(schema);
        if (normalized == null) {
            return "tool input schema must not be blank";
        }
        Object root;
        try {
            root = new JsonParser(normalized).parse();
        } catch (IllegalArgumentException ex) {
            return "tool input schema must be valid JSON: " + ex.getMessage();
        }
        if (!(root instanceof Map)) {
            return "tool input schema must be a JSON object";
        }
        Map<?, ?> object = (Map<?, ?>) root;
        Object type = object.get("type");
        if (!(type instanceof String) || ExtensionManifest.emptyToNull((String) type) == null) {
            return "tool input schema must contain a non-blank string type field";
        }
        if (!"object".equals(type)) {
            return "tool input schema root type must be object";
        }
        String propertiesError = validateProperties(object.get("properties"), "$.properties", 0);
        if (propertiesError != null) {
            return propertiesError;
        }
        return validateStringArray(object.get("required"), "$.required");
    }

    private static String validateProperties(Object value, String path, int depth) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map)) {
            return path + " must be a JSON object when present";
        }
        Map<?, ?> properties = (Map<?, ?>) value;
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            if (!(entry.getKey() instanceof String) || ExtensionManifest.emptyToNull((String) entry.getKey()) == null) {
                return path + " contains a blank or non-string property name";
            }
            if (!(entry.getValue() instanceof Map)) {
                return path + "." + entry.getKey() + " must be a JSON object";
            }
            String propertyError = validateProperty((Map<?, ?>) entry.getValue(), path + "." + entry.getKey(), depth + 1);
            if (propertyError != null) {
                return propertyError;
            }
        }
        return null;
    }

    private static String validateProperty(Map<?, ?> property, String path, int depth) {
        if (depth > MAX_DEPTH) {
            return path + " is nested too deeply";
        }
        Object type = property.get("type");
        if (type != null && (!(type instanceof String) || ExtensionManifest.emptyToNull((String) type) == null)) {
            return path + ".type must be a non-blank string when present";
        }
        Object description = property.get("description");
        if (description != null && !(description instanceof String)) {
            return path + ".description must be a string when present";
        }
        String enumError = validateStringArray(property.get("enum"), path + ".enum");
        if (enumError != null) {
            return enumError;
        }
        Object items = property.get("items");
        if (items != null) {
            if (!(items instanceof Map)) {
                return path + ".items must be a JSON object when present";
            }
            String itemsError = validateProperty((Map<?, ?>) items, path + ".items", depth + 1);
            if (itemsError != null) {
                return itemsError;
            }
        }
        return validateProperties(property.get("properties"), path + ".properties", depth + 1);
    }

    private static String validateStringArray(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List)) {
            return path + " must be a JSON array when present";
        }
        List<?> values = (List<?>) value;
        for (Object item : values) {
            if (!(item instanceof String) || ExtensionManifest.emptyToNull((String) item) == null) {
                return path + " must contain only non-blank strings";
            }
        }
        return null;
    }

    private static final class JsonParser {
        private final String source;
        private int index;

        private JsonParser(String source) {
            this.source = source;
        }

        private Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (index != source.length()) {
                throw error("unexpected trailing content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= source.length()) {
                throw error("unexpected end of input");
            }
            char ch = source.charAt(index);
            if (ch == '{') {
                return parseObject();
            }
            if (ch == '[') {
                return parseArray();
            }
            if (ch == '"') {
                return parseString();
            }
            if (startsWith("true")) {
                index += 4;
                return Boolean.TRUE;
            }
            if (startsWith("false")) {
                index += 5;
                return Boolean.FALSE;
            }
            if (startsWith("null")) {
                index += 4;
                return null;
            }
            if (ch == '-' || (ch >= '0' && ch <= '9')) {
                return parseNumber();
            }
            throw error("unexpected character '" + ch + "'");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                if (index >= source.length() || source.charAt(index) != '"') {
                    throw error("expected object key string");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<Object>();
            skipWhitespace();
            if (consume(']')) {
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < source.length()) {
                char ch = source.charAt(index++);
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch == '\\') {
                    if (index >= source.length()) {
                        throw error("unfinished escape sequence");
                    }
                    char escape = source.charAt(index++);
                    if (escape == '"' || escape == '\\' || escape == '/') {
                        builder.append(escape);
                    } else if (escape == 'b') {
                        builder.append('\b');
                    } else if (escape == 'f') {
                        builder.append('\f');
                    } else if (escape == 'n') {
                        builder.append('\n');
                    } else if (escape == 'r') {
                        builder.append('\r');
                    } else if (escape == 't') {
                        builder.append('\t');
                    } else if (escape == 'u') {
                        builder.append(parseUnicode());
                    } else {
                        throw error("unsupported escape sequence");
                    }
                    continue;
                }
                if (ch < 0x20) {
                    throw error("control character in string");
                }
                builder.append(ch);
            }
            throw error("unterminated string");
        }

        private String parseNumber() {
            int start = index;
            consume('-');
            if (consume('0')) {
                // leading zero is only valid as the entire integer part
            } else {
                readDigits("expected digit");
            }
            if (consume('.')) {
                readDigits("expected fractional digit");
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) {
                    consume('-');
                }
                readDigits("expected exponent digit");
            }
            return source.substring(start, index);
        }

        private char parseUnicode() {
            if (index + 4 > source.length()) {
                throw error("unfinished unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                char ch = source.charAt(index++);
                int digit = Character.digit(ch, 16);
                if (digit < 0) {
                    throw error("invalid unicode escape");
                }
                value = (value << 4) + digit;
            }
            return (char) value;
        }

        private void readDigits(String message) {
            int start = index;
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (ch < '0' || ch > '9') {
                    break;
                }
                index++;
            }
            if (index == start) {
                throw error(message);
            }
        }

        private void skipWhitespace() {
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (ch != ' ' && ch != '\n' && ch != '\r' && ch != '\t') {
                    return;
                }
                index++;
            }
        }

        private boolean consume(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("expected '" + expected + "'");
            }
        }

        private boolean startsWith(String token) {
            return source.regionMatches(index, token, 0, token.length());
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }
}
