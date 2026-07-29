package io.github.lnyocly.ai4j.platform.openai.usage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Optional detail buckets reported by OpenAI-compatible usage payloads.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageDetails implements Serializable {

    @JsonProperty("cached_tokens")
    private Long cachedTokens;

    @JsonProperty("cache_write_tokens")
    private Long cacheWriteTokens;

    @JsonProperty("reasoning_tokens")
    private Long reasoningTokens;

    @JsonProperty("audio_tokens")
    private Long audioTokens;

    @JsonProperty("accepted_prediction_tokens")
    private Long acceptedPredictionTokens;

    @JsonProperty("rejected_prediction_tokens")
    private Long rejectedPredictionTokens;
}
