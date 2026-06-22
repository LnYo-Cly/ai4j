package io.github.lnyocly.ai4j.platform.anthropic.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anthropic Messages 请求中的工具定义。
 * <p>
 * 对应 OpenAI {@code Tool.Function}，但 schema 字段名为 {@code input_schema}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicTool {

    private String name;

    private String description;

    @JsonProperty("input_schema")
    private Object inputSchema;
}
