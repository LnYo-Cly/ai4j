package io.github.lnyocly.ai4j.platform.openai.chat.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/6 23:54
 */
@Getter
@AllArgsConstructor
public enum ChatMessageType {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
    ;

    private final String role;

}
