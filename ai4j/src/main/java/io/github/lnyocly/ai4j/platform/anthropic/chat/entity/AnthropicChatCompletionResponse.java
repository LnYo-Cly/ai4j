package io.github.lnyocly.ai4j.platform.anthropic.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Anthropic Messages 非流式响应（{@code type=message}）。
 * <p>
 * {@code content} 为 content block 数组（text / tool_use），由 service 转换为统一的
 * {@code ChatCompletionResponse}。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicChatCompletionResponse {

    private String id;

    private String type;

    private String role;

    private String model;

    private List<AnthropicContentBlock> content;

    @JsonProperty("stop_reason")
    private String stopReason;

    @JsonProperty("stop_sequence")
    private String stopSequence;

    private AnthropicUsage usage;
}
