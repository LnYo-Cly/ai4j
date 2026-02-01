package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response {

    private String id;

    private String object;

    @JsonProperty("created_at")
    private Long createdAt;

    private String model;

    private String status;

    private List<ResponseItem> output;

    private ResponseError error;

    @JsonProperty("incomplete_details")
    private ResponseIncompleteDetails incompleteDetails;

    private String instructions;

    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;

    @JsonProperty("previous_response_id")
    private String previousResponseId;

    private ResponseUsage usage;

    @JsonProperty("service_tier")
    private String serviceTier;

    private Map<String, Object> metadata;

    @JsonProperty("context_management")
    private ResponseContextManagement contextManagement;
}

