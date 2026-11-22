package io.github.lnyocly.ai4j.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/8 17:29
 */
@AllArgsConstructor
@Getter
public enum PlatformType {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    ZHIPU("zhipu"),
    DEEPSEEK("deepseek"),
    MOONSHOT("moonshot"),
    HUNYUAN("hunyuan"),
    LINGYI("lingyi"),
    OLLAMA("ollama"),
    MINIMAX("minimax"),
    BAICHUAN("baichuan"),
    DASHSCOPE("dashscope"),
    DOUBAO("doubao"),
    JINA("jina"),
    SUNO("suno"),
    GROK("grok"),
    AGNES("agnes"),
    TYPESAFE("typesafe"),
    GEMINI("gemini"),
    ;
    private final String platform;

    /**
     * Strictly resolves a platform identifier after trimming and lowercasing.
     */
    public static PlatformType fromPlatform(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Platform must not be null");
        }
        String target = value.trim().toLowerCase(Locale.ROOT);
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Platform must not be blank");
        }
        for (PlatformType platformType : PlatformType.values()) {
            if (platformType.getPlatform().equals(target)) {
                return platformType;
            }
        }
        throw new IllegalArgumentException("Unsupported platform: " + value);
    }

    public static PlatformType getPlatform(String value) {
        return fromPlatform(value);
    }
}
