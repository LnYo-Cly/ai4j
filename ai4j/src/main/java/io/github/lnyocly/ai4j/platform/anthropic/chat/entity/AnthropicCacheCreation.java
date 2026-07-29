package io.github.lnyocly.ai4j.platform.anthropic.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Anthropic cache-creation usage split by cache TTL. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicCacheCreation {

    @JsonProperty("ephemeral_5m_input_tokens")
    private Long ephemeral5mInputTokens;

    @JsonProperty("ephemeral_1h_input_tokens")
    private Long ephemeral1hInputTokens;
}
