package io.github.lnyocly.ai4j.codec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Shared Jackson-based JSON codec for strict protocol payloads and lenient LLM outputs.
 */
public final class JsonCodec {

    private static final MapperHolder MAPPERS = new MapperHolder();

    private JsonCodec() {
    }

    private static final class MapperHolder {
        private final ObjectMapper protocolMapper = createProtocolMapper();
        private final ObjectMapper lenientLlmMapper = createLenientLlmMapper();
    }

    private static ObjectMapper createProtocolMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    private static ObjectMapper createLenientLlmMapper() {
        JsonFactory factory = JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build();
        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /**
     * Returns the shared thread-safe mapper for strict protocol serialization and deserialization.
     */
    public static ObjectMapper protocolMapper() {
        return MAPPERS.protocolMapper;
    }

    /**
     * Returns the shared thread-safe lenient mapper for LLM outputs (comments, single quotes,
     * unquoted field names, trailing commas).
     */
    public static ObjectMapper lenientMapper() {
        return MAPPERS.lenientLlmMapper;
    }

    public static String toJson(Object value) {
        try {
            return MAPPERS.protocolMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to serialize value to JSON", e);
        }
    }

    public static byte[] toJsonBytes(Object value) {
        try {
            return MAPPERS.protocolMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to serialize value to JSON bytes", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }
        String input = requireNonBlank(json);
        try {
            return MAPPERS.protocolMapper.readValue(input, type);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to deserialize JSON to " + type.getName(), e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (typeReference == null) {
            throw new IllegalArgumentException("TypeReference must not be null");
        }
        String input = requireNonBlank(json);
        try {
            return MAPPERS.protocolMapper.readValue(input, typeReference);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to deserialize JSON to TypeReference", e);
        }
    }

    public static <T> T fromJson(String json, JavaType javaType) {
        if (javaType == null) {
            throw new IllegalArgumentException("JavaType must not be null");
        }
        String input = requireNonBlank(json);
        try {
            return MAPPERS.protocolMapper.readValue(input, javaType);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to deserialize JSON to " + javaType, e);
        }
    }

    public static <T> T fromJsonLenient(String json, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }
        String payload = requireNonBlank(extractJsonPayload(json));
        try {
            return MAPPERS.lenientLlmMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to leniently deserialize JSON to " + type.getName(), e);
        }
    }

    public static <T> T fromJsonLenient(String json, TypeReference<T> typeReference) {
        if (typeReference == null) {
            throw new IllegalArgumentException("TypeReference must not be null");
        }
        String payload = requireNonBlank(extractJsonPayload(json));
        try {
            return MAPPERS.lenientLlmMapper.readValue(payload, typeReference);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to leniently deserialize JSON to TypeReference", e);
        }
    }

    public static JsonNode readTree(String json) {
        String input = requireNonBlank(json);
        try {
            return MAPPERS.protocolMapper.readTree(input);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to parse JSON tree", e);
        }
    }

    public static JsonNode readTreeLenient(String json) {
        String payload = requireNonBlank(extractJsonPayload(json));
        try {
            return MAPPERS.lenientLlmMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to leniently parse JSON tree", e);
        }
    }

    public static ObjectNode createObjectNode() {
        return MAPPERS.protocolMapper.createObjectNode();
    }

    public static ArrayNode createArrayNode() {
        return MAPPERS.protocolMapper.createArrayNode();
    }

    public static JsonNode valueToTree(Object value) {
        try {
            return MAPPERS.protocolMapper.valueToTree(value);
        } catch (IllegalArgumentException e) {
            throw new JsonCodecException("Failed to convert value to JsonNode", e);
        }
    }

    public static <T> T treeToValue(TreeNode node, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }
        if (node == null) {
            return null;
        }
        try {
            return MAPPERS.protocolMapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to convert TreeNode to " + type.getName(), e);
        }
    }

    /**
     * Extracts raw JSON from markdown code fences or surrounding prose while keeping plain JSON intact.
     */
    public static String extractJsonPayload(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            String leading = extractFromLeadingContainer(trimmed);
            if (leading != null) {
                return leading;
            }
        }

        String fenced = extractFromMarkdownFences(trimmed);
        if (fenced != null) {
            return fenced;
        }

        if (startsWithCommentAndJson(trimmed)) {
            return extractFromCommentPrefixed(trimmed);
        }

        String embedded = extractEmbeddedContainer(trimmed);
        return embedded != null ? embedded : trimmed;
    }

    private static String requireNonBlank(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new JsonCodecException("JSON input must not be null or blank");
        }
        return json;
    }

    private static String extractFromLeadingContainer(String trimmed) {
        int end = findBalancedJsonEnd(trimmed, 0);
        if (end < 0) {
            return null;
        }
        if (end == trimmed.length() - 1 || isOnlyWhitespaceOrComments(trimmed, end + 1)) {
            return trimmed;
        }
        String candidate = trimmed.substring(0, end + 1);
        return canParseLenient(candidate) ? candidate : null;
    }

    private static String extractFromCommentPrefixed(String trimmed) {
        int containerStart = skipWhitespaceAndComments(trimmed, 0);
        if (containerStart >= trimmed.length()) {
            return trimmed;
        }
        char ch = trimmed.charAt(containerStart);
        if (ch != '{' && ch != '[') {
            return trimmed;
        }
        int end = findBalancedJsonEnd(trimmed, containerStart);
        if (end < 0) {
            return trimmed;
        }
        if (isOnlyWhitespaceOrComments(trimmed, end + 1)) {
            return trimmed;
        }
        return trimmed.substring(0, end + 1);
    }

    private static String extractFromMarkdownFences(String text) {
        List<String> jsonTagged = new ArrayList<String>();
        List<String> otherFences = new ArrayList<String>();
        int searchFrom = 0;
        int length = text.length();

        while (searchFrom < length) {
            int openStart = text.indexOf("```", searchFrom);
            if (openStart < 0) {
                break;
            }
            int afterTicks = openStart + 3;
            int lineEnd = findLineEnd(text, afterTicks);
            String headerSegment = text.substring(afterTicks, lineEnd).trim();
            String tag = "";
            int contentStart;
            boolean inlineSameLine = false;

            int headerOffset = skipHorizontalWhitespace(text, afterTicks);
            if (headerOffset < lineEnd && text.regionMatches(true, headerOffset, "json", 0, 4)) {
                int afterTag = headerOffset + 4;
                if (afterTag == lineEnd) {
                    tag = "json";
                    contentStart = skipLineBreak(text, lineEnd);
                } else if (Character.isWhitespace(text.charAt(afterTag))) {
                    tag = "json";
                    contentStart = skipHorizontalWhitespace(text, afterTag);
                    inlineSameLine = contentStart < lineEnd;
                    if (!inlineSameLine) {
                        contentStart = skipLineBreak(text, lineEnd);
                    }
                } else {
                    tag = headerSegment;
                    contentStart = skipLineBreak(text, lineEnd);
                }
            } else if (headerSegment.startsWith("{") || headerSegment.startsWith("[")) {
                contentStart = headerOffset;
                inlineSameLine = true;
            } else {
                tag = headerSegment;
                contentStart = skipLineBreak(text, lineEnd);
            }

            int closeStart = findClosingFence(text, contentStart, inlineSameLine ? lineEnd : -1);
            String body;
            if (closeStart >= 0) {
                body = text.substring(contentStart, closeStart).trim();
                searchFrom = closeStart + 3;
            } else {
                body = text.substring(contentStart).trim();
                searchFrom = length;
            }

            if (!body.isEmpty()) {
                if ("json".equalsIgnoreCase(tag) || "json5".equalsIgnoreCase(tag) || "jsonc".equalsIgnoreCase(tag)) {
                    jsonTagged.add(body);
                } else {
                    otherFences.add(body);
                }
            }
        }

        if (!jsonTagged.isEmpty()) {
            return extractFromCandidateOrKeep(jsonTagged.get(0));
        }
        for (String candidate : otherFences) {
            String trimmedCandidate = candidate.trim();
            if (trimmedCandidate.startsWith("{")
                    || trimmedCandidate.startsWith("[")
                    || startsWithCommentAndJson(trimmedCandidate)) {
                return extractFromCandidateOrKeep(trimmedCandidate);
            }
        }
        if (!otherFences.isEmpty()) {
            return extractFromCandidateOrKeep(otherFences.get(0));
        }
        return null;
    }

    private static String extractFromCandidateOrKeep(String candidate) {
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            String leading = extractFromLeadingContainer(trimmed);
            if (leading != null) {
                return leading;
            }
        }
        if (startsWithCommentAndJson(trimmed)) {
            return extractFromCommentPrefixed(trimmed);
        }
        String embedded = extractEmbeddedContainer(trimmed);
        return embedded != null ? embedded : trimmed;
    }

    private static String extractEmbeddedContainer(String text) {
        String firstBalanced = null;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);
            if (ch != '{' && ch != '[') {
                continue;
            }
            int end = findBalancedJsonEnd(text, i);
            if (end > i) {
                String candidate = text.substring(i, end + 1);
                if (firstBalanced == null) {
                    firstBalanced = candidate;
                }
                if (canParseLenient(candidate)) {
                    return candidate;
                }
            }
        }
        return firstBalanced;
    }

    private static boolean canParseLenient(String candidate) {
        try {
            MAPPERS.lenientLlmMapper.readTree(candidate);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean startsWithCommentAndJson(String text) {
        if (!text.startsWith("//") && !text.startsWith("/*")) {
            return false;
        }
        int next = skipWhitespaceAndComments(text, 0);
        if (next >= text.length()) {
            return false;
        }
        char ch = text.charAt(next);
        return ch == '{' || ch == '[';
    }

    private static int findBalancedJsonEnd(String text, int start) {
        if (start < 0 || start >= text.length()) {
            return -1;
        }
        char first = text.charAt(start);
        if (first != '{' && first != '[') {
            return -1;
        }

        Deque<Character> stack = new ArrayDeque<Character>();
        boolean inDoubleQuote = false;
        boolean inSingleQuote = false;
        boolean escaped = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int length = text.length();

        for (int i = start; i < length; i++) {
            char c = text.charAt(i);

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < length && text.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (inSingleQuote) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }

            if (c == '"') {
                inDoubleQuote = true;
            } else if (c == '\'') {
                inSingleQuote = true;
            } else if (c == '/' && i + 1 < length) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    inLineComment = true;
                    i++;
                } else if (next == '*') {
                    inBlockComment = true;
                    i++;
                }
            } else if (c == '{' || c == '[') {
                stack.push(c == '{' ? '}' : ']');
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty() || stack.peek().charValue() != c) {
                    return -1;
                }
                stack.pop();
                if (stack.isEmpty()) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipWhitespaceAndComments(String text, int index) {
        int i = index;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < length) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < length && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                        i++;
                    }
                    continue;
                }
                if (next == '*') {
                    int close = text.indexOf("*/", i + 2);
                    if (close < 0) {
                        return length;
                    }
                    i = close + 2;
                    continue;
                }
            }
            break;
        }
        return i;
    }

    private static boolean isOnlyWhitespaceOrComments(String text, int index) {
        return skipWhitespaceAndComments(text, index) >= text.length();
    }

    private static int findLineEnd(String text, int start) {
        int length = text.length();
        for (int i = start; i < length; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return length;
    }

    private static int skipHorizontalWhitespace(String text, int start) {
        int i = start;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static int skipLineBreak(String text, int lineEnd) {
        int length = text.length();
        if (lineEnd >= length) {
            return length;
        }
        if (text.charAt(lineEnd) == '\r' && lineEnd + 1 < length && text.charAt(lineEnd + 1) == '\n') {
            return lineEnd + 2;
        }
        if (text.charAt(lineEnd) == '\n' || text.charAt(lineEnd) == '\r') {
            return lineEnd + 1;
        }
        return lineEnd;
    }

    private static int findClosingFence(String text, int contentStart, int sameLineEnd) {
        if (sameLineEnd >= 0) {
            int inlineClose = text.indexOf("```", contentStart);
            if (inlineClose >= 0 && inlineClose < sameLineEnd) {
                return inlineClose;
            }
        }
        int pos = contentStart;
        int length = text.length();
        while (pos < length) {
            int candidate = text.indexOf("```", pos);
            if (candidate < 0) {
                return -1;
            }
            if (isLineStartBefore(text, contentStart, candidate) && isLineEndAfter(text, candidate + 3)) {
                return candidate;
            }
            pos = candidate + 3;
        }
        return text.indexOf("```", contentStart);
    }

    private static boolean isLineStartBefore(String text, int minIndex, int fenceStart) {
        int i = fenceStart - 1;
        while (i >= minIndex) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return true;
            }
            if (c != ' ' && c != '\t') {
                return false;
            }
            i--;
        }
        return fenceStart == minIndex;
    }

    private static boolean isLineEndAfter(String text, int afterFence) {
        int pos = skipHorizontalWhitespace(text, afterFence);
        return pos >= text.length() || text.charAt(pos) == '\n' || text.charAt(pos) == '\r';
    }
}
