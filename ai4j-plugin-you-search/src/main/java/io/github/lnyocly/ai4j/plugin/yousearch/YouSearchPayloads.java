package io.github.lnyocly.ai4j.plugin.yousearch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON helpers for the you-search plugin. Java 8 compatible, no external dependencies.
 */
final class YouSearchPayloads {

    private YouSearchPayloads() {
    }

    /** Wrap raw command arguments into the tool arguments JSON shape. */
    static String queryOnlyArguments(String arguments) {
        String query = arguments == null ? "" : arguments.trim();
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("query", query);
        return writeJson(map);
    }

    /** Extract the "query" field from tool arguments JSON. Returns null when absent or blank. */
    static String extractQuery(String arguments) {
        Map<String, String> map = readFlatObject(arguments);
        String query = map == null ? null : map.get("query");
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Extract the "numResults" field from tool arguments JSON. Returns null when absent/invalid. */
    static Integer extractNumResults(String arguments) {
        Map<String, String> map = readFlatObject(arguments);
        if (map == null) {
            return null;
        }
        String value = map.get("numResults");
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed < 1 || parsed > 20) {
                return null;
            }
            return Integer.valueOf(parsed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Build the tool response envelope with escaped result fields. */
    static String searchResponse(String query, int limit, java.util.List<Map<String, String>> hits) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"type\":\"you.web_search.response\",\"tool\":\"").append(YouSearchExtension.TOOL_NAME).append("\",");
        builder.append("\"query\":\"").append(escapeJson(query)).append("\",");
        builder.append("\"numResults\":").append(limit).append(",");
        if (hits == null || hits.isEmpty()) {
            builder.append("\"results\":[]}");
            return builder.toString();
        }
        builder.append("\"results\":[");
        for (int i = 0; i < hits.size(); i++) {
            Map<String, String> hit = hits.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append("{\"title\":\"").append(escapeJson(hit.get("title"))).append("\",");
            builder.append("\"url\":\"").append(escapeJson(hit.get("url"))).append("\",");
            builder.append("\"snippet\":\"").append(escapeJson(hit.get("snippet"))).append("\"}");
        }
        builder.append("]}");
        return builder.toString();
    }

    /** Build the error envelope returned when the search call cannot be completed. */
    static String errorResponse(String query, String error, String hint) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"type\":\"you.web_search.error\",\"tool\":\"").append(YouSearchExtension.TOOL_NAME).append("\",");
        builder.append("\"query\":\"").append(escapeJson(query)).append("\",");
        builder.append("\"error\":\"").append(escapeJson(error)).append("\"");
        if (hint != null && !hint.trim().isEmpty()) {
            builder.append(",\"hint\":\"").append(escapeJson(hint)).append("\"");
        }
        builder.append('}');
        return builder.toString();
    }

    static String writeJson(Map<String, String> map) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escapeJson(entry.getKey())).append("\":\"");
            builder.append(escapeJson(entry.getValue() == null ? "" : entry.getValue())).append('"');
        }
        builder.append('}');
        return builder.toString();
    }

    /**
     * Parse a flat JSON object of string values: {"query":"...","numResults":"5"}.
     * Only handles string values; non-string values are returned as their raw JSON text.
     * Returns null when the input is not a flat JSON object.
     */
    static Map<String, String> readFlatObject(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // tolerates an optional markdown code fence
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            if (firstBreak >= 0) {
                trimmed = trimmed.substring(firstBreak + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        int i = 1;
        int end = trimmed.length() - 1;
        while (i < end) {
            while (i < end && Character.isWhitespace(trimmed.charAt(i))) {
                i++;
            }
            if (i >= end || trimmed.charAt(i) == ',') {
                i++;
                continue;
            }
            if (trimmed.charAt(i) != '"') {
                return null;
            }
            int[] cursor = new int[1];
            String key = readString(trimmed, i, cursor);
            if (key == null) {
                return null;
            }
            i = cursor[0];
            while (i < end && Character.isWhitespace(trimmed.charAt(i))) {
                i++;
            }
            if (i >= end || trimmed.charAt(i) != ':') {
                return null;
            }
            i++;
            while (i < end && Character.isWhitespace(trimmed.charAt(i))) {
                i++;
            }
            if (i < end && trimmed.charAt(i) == '"') {
                String value = readString(trimmed, i, cursor);
                if (value == null) {
                    return null;
                }
                result.put(key, value);
                i = cursor[0];
            } else if (i < end) {
                int valueStart = i;
                while (i < end && trimmed.charAt(i) != ',' && trimmed.charAt(i) != '}') {
                    i++;
                }
                result.put(key, trimmed.substring(valueStart, i).trim());
            } else {
                return null;
            }
        }
        return result;
    }

    private static String readString(String source, int start, int[] cursorOut) {
        if (start >= source.length() || source.charAt(start) != '"') {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int i = start + 1;
        while (i < source.length()) {
            char ch = source.charAt(i);
            if (ch == '\\') {
                if (i + 1 >= source.length()) {
                    return null;
                }
                char next = source.charAt(i + 1);
                switch (next) {
                    case '"': builder.append('"'); break;
                    case '\\': builder.append('\\'); break;
                    case '/': builder.append('/'); break;
                    case 'b': builder.append('\b'); break;
                    case 'f': builder.append('\f'); break;
                    case 'n': builder.append('\n'); break;
                    case 'r': builder.append('\r'); break;
                    case 't': builder.append('\t'); break;
                    case 'u':
                        if (i + 5 >= source.length()) {
                            return null;
                        }
                        String hex = source.substring(i + 2, i + 6);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException ignored) {
                            return null;
                        }
                        i += 4;
                        break;
                    default:
                        return null;
                }
                i += 2;
            } else if (ch == '"') {
                cursorOut[0] = i + 1;
                return builder.toString();
            } else {
                builder.append(ch);
                i++;
            }
        }
        return null;
    }

    static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", Integer.valueOf(ch)));
                    } else {
                        builder.append(ch);
                    }
            }
        }
        return builder.toString();
    }
}
