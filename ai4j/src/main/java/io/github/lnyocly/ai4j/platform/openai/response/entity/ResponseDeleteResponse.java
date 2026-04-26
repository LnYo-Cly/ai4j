package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseDeleteResponse {
    private String id;
    private String object;
    private Boolean deleted;
}

