package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseUsage {

    @JsonProperty("input_tokens")
    private Integer inputTokens;

    @JsonProperty("output_tokens")
    private Integer outputTokens;

    @JsonProperty("total_tokens")
    private Integer totalTokens;

    @JsonProperty("input_tokens_details")
    private ResponseUsageDetails inputTokensDetails;

    @JsonProperty("output_tokens_details")
    private ResponseUsageDetails outputTokensDetails;

    @JsonProperty("tool_usage")
    private ResponseToolUsage toolUsage;

    @JsonProperty("tool_usage_details")
    private ResponseToolUsageDetails toolUsageDetails;
}

