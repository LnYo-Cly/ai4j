package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseStreamEvent {

    private String type;

    @JsonProperty("sequence_number")
    private Integer sequenceNumber;

    private Response response;

    @JsonProperty("output_index")
    private Integer outputIndex;

    @JsonProperty("content_index")
    private Integer contentIndex;

    @JsonProperty("item_id")
    private String itemId;

    private String delta;

    private String text;

    private String arguments;

    @JsonProperty("call_id")
    private String callId;

    private ResponseError error;

    @JsonIgnore
    private JsonNode raw;
}

