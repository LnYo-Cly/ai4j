package io.github.lnyocly.ai4j.platform.anthropic.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anthropic Messages 响应的用量信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicUsage {

    @JsonProperty("input_tokens")
    private long inputTokens;

    @JsonProperty("output_tokens")
    private long outputTokens;

    @JsonProperty("cache_read_input_tokens")
    private Long cacheReadInputTokens;

    @JsonProperty("cache_creation_input_tokens")
    private Long cacheCreationInputTokens;

    /**
     * Compatibility constructor retained for callers using the original two buckets.
     */
    public AnthropicUsage(long inputTokens, long outputTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }
}
