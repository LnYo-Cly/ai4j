package io.github.lnyocly.ai4j.platform.openai.video.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request for OpenAI-compatible video creation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoCreateRequest {
    @NonNull
    private String model;

    @NonNull
    private String prompt;

    private Object seconds;
    private String size;

    @JsonIgnore
    @Builder.Default
    private Map<String, Object> extraFields = new LinkedHashMap<String, Object>();

    @JsonIgnore
    @Builder.Default
    private Map<String, File> fileFields = new LinkedHashMap<String, File>();

    @JsonIgnore
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<String, String>();
}
