package io.github.lnyocly.ai4j.platform.openai.video.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * OpenAI-compatible video task response.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoResponse {
    private String id;
    private String object;
    private String status;
    private String model;
    private String size;
    private String seconds;
    private Integer progress;

    @JsonProperty("video_url")
    private String videoUrl;

    @JsonProperty("created_at")
    private Long createdAt;

    private Map<String, Object> raw;
}
