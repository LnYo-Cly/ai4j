package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseUsageDetails {

    @JsonProperty("cached_tokens")
    private Integer cachedTokens;

    @JsonProperty("text_tokens")
    private Integer textTokens;

    @JsonProperty("audio_tokens")
    private Integer audioTokens;

    @JsonProperty("image_tokens")
    private Integer imageTokens;

    @JsonProperty("reasoning_tokens")
    private Integer reasoningTokens;
}

