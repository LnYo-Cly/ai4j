package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.service.PlatformType;

import java.util.Locale;

public enum CliProtocol {
    CHAT("chat"),
    RESPONSES("responses"),
    MESSAGES("messages"),
    GEMINI("gemini");

    private final String value;

    CliProtocol(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CliProtocol parse(String value) {
        String normalized = normalize(value);
        if ("anthropic".equals(normalized)) {
            return MESSAGES;
        }
        for (CliProtocol protocol : values()) {
            if (protocol.value.equals(normalized)) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("Unsupported protocol: " + value + ". Expected: chat, responses, messages, gemini");
    }

    public static CliProtocol resolveConfigured(String value, PlatformType provider, String baseUrl) {
        String normalized = normalize(value);
        if (normalized == null || "auto".equals(normalized)) {
            return defaultProtocol(provider, baseUrl);
        }
        return parse(normalized);
    }

    public static CliProtocol defaultProtocol(PlatformType provider, String baseUrl) {
        if (provider == PlatformType.ANTHROPIC) {
            return MESSAGES;
        }
        if (provider == PlatformType.GEMINI) {
            return GEMINI;
        }
        if (provider == PlatformType.OPENAI) {
            String normalizedBaseUrl = normalize(baseUrl);
            if (normalizedBaseUrl == null || normalizedBaseUrl.contains("api.openai.com")) {
                return RESPONSES;
            }
            return CHAT;
        }
        if (provider == PlatformType.DOUBAO || provider == PlatformType.DASHSCOPE) {
            return RESPONSES;
        }
        return CHAT;
    }

    public boolean isSupportedBy(PlatformType provider) {
        if (provider == null) {
            return false;
        }
        switch (this) {
            case CHAT:
                return true;
            case RESPONSES:
                return provider == PlatformType.OPENAI
                        || provider == PlatformType.DOUBAO
                        || provider == PlatformType.DASHSCOPE;
            case MESSAGES:
                return provider == PlatformType.ANTHROPIC
                        || provider == PlatformType.OPENAI
                        || provider == PlatformType.ZHIPU
                        || provider == PlatformType.MINIMAX
                        || provider == PlatformType.DASHSCOPE
                        || provider == PlatformType.DEEPSEEK
                        || provider == PlatformType.MOONSHOT;
            case GEMINI:
                return provider == PlatformType.GEMINI
                        || provider == PlatformType.OPENAI;
            default:
                return false;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
