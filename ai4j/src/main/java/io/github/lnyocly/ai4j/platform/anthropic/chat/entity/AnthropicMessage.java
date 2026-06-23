package io.github.lnyocly.ai4j.platform.anthropic.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anthropic Messages 请求中的一条消息。
 * <p>
 * {@code content} 既可以是纯字符串（简单文本），也可以是 {@code List<AnthropicContentBlock>}
 * （tool_use / tool_result / 多段文本）。这里用 {@code Object} 承载，由序列化器按实际类型输出。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicMessage {

    private String role;

    private Object content;
}
