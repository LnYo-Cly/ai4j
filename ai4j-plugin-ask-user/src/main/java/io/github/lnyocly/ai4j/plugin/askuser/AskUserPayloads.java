package io.github.lnyocly.ai4j.plugin.askuser;

final class AskUserPayloads {

    private static final int MAX_ARGUMENTS_RAW_CHARS = 16 * 1024;

    private AskUserPayloads() {
    }

    static String toolRequest(String arguments) {
        JsonValue raw = cap(emptyToDefault(arguments, "{}"));
        return "{"
                + "\"type\":\"ai4j.ask_user.request\","
                + "\"source\":\"tool\","
                + "\"tool\":\"" + AskUserExtension.TOOL_NAME + "\","
                + "\"status\":\"pending_user_input\","
                + "\"hostAction\":\"render_question_to_user\","
                + "\"blocking\":\"host_decides\","
                + "\"argumentsRaw\":\"" + escapeJson(raw.value) + "\""
                + truncatedField(raw)
                + "}";
    }

    static String commandRequest(String arguments) {
        JsonValue question = cap(emptyToDefault(arguments, "Ask the user for clarification."));
        return "{"
                + "\"type\":\"ai4j.ask_user.request\","
                + "\"source\":\"command\","
                + "\"command\":\"" + AskUserExtension.COMMAND_NAME + "\","
                + "\"status\":\"pending_user_input\","
                + "\"hostAction\":\"render_question_to_user\","
                + "\"blocking\":\"host_decides\","
                + "\"arguments\":{\"question\":\"" + escapeJson(question.value) + "\"},"
                + "\"argumentsRaw\":\"" + escapeJson(question.value) + "\""
                + truncatedField(question)
                + "}";
    }

    private static String emptyToDefault(String value, String fallback) {
        String trimmed = emptyToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static JsonValue cap(String value) {
        if (value == null || value.length() <= MAX_ARGUMENTS_RAW_CHARS) {
            return new JsonValue(value, false);
        }
        return new JsonValue(value.substring(0, MAX_ARGUMENTS_RAW_CHARS), true);
    }

    private static String truncatedField(JsonValue value) {
        return value.truncated ? ",\"argumentsTruncated\":true" : "";
    }

    private static String escapeJson(String value) {
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
                        String hex = Integer.toHexString(ch);
                        builder.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) {
                            builder.append('0');
                        }
                        builder.append(hex);
                    } else {
                        builder.append(ch);
                    }
                    break;
            }
        }
        return builder.toString();
    }

    private static final class JsonValue {
        private final String value;
        private final boolean truncated;

        private JsonValue(String value, boolean truncated) {
            this.value = value == null ? "" : value;
            this.truncated = truncated;
        }
    }
}
