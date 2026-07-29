package io.github.lnyocly.ai4j.platform.anthropic.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Anthropic server-side tool request counters. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicServerToolUsage {

    @JsonProperty("web_fetch_requests")
    private Long webFetchRequests;

    @JsonProperty("web_search_requests")
    private Long webSearchRequests;
}
