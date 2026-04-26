package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseContextEdit {

    private String type;

    @JsonProperty("cleared_thinking_turns")
    private Integer clearedThinkingTurns;

    @JsonProperty("cleared_tool_uses")
    private Integer clearedToolUses;
}

