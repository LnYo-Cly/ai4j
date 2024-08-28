package io.github.lnyocly.ai4j.platform.openai.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/11 20:01
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Choice {
    private Integer index;

    private ChatMessage delta;
    private ChatMessage message;

    private Object logprobs;

    @JsonProperty("finish_reason")
    private String finishReason;
}
