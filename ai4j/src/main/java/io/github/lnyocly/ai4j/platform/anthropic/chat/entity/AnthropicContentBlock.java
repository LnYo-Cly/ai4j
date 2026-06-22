package io.github.lnyocly.ai4j.platform.anthropic.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anthropic Messages 的 content block，同时用于请求与响应。
 * <p>
 * 常见 type：
 * <ul>
 *   <li>{@code text} —— 文本，使用 {@link #text}</li>
 *   <li>{@code tool_use} —— 模型发起的工具调用，使用 {@link #id}/{@link #name}/{@link #input}</li>
 *   <li>{@code tool_result} —— 工具结果回传，使用 {@link #toolUseId}/{@link #content}</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicContentBlock {
    private String type;

    /** text block 的文本内容 */
    private String text;

    /** tool_use block 的调用 id */
    private String id;

    /** tool_use block 的工具名 */
    private String name;

    /** tool_use block 的入参（JSON 对象） */
    private Object input;

    /** tool_result block 对应的 tool_use id */
    @JsonProperty("tool_use_id")
    private String toolUseId;

    /** tool_result block 的结果内容（字符串或 content block 数组） */
    private Object content;
}
