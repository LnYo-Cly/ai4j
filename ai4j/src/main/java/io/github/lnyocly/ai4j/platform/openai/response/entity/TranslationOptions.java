package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TranslationOptions {

    @JsonProperty("source_language")
    private String sourceLanguage;

    @JsonProperty("target_language")
    private String targetLanguage;
}

