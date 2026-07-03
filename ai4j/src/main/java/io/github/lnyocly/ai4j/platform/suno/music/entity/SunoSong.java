package io.github.lnyocly.ai4j.platform.suno.music.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Known fields for a Suno generated song item.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SunoSong {
    private String id;
    private String tags;
    private String state;
    private String title;
    private String handle;
    private String prompt;
    private String status;

    @JsonProperty("clip_id")
    private String clipId;

    private Double duration;
    private Map<String, Object> metadata;

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("video_url")
    private String videoUrl;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("image_large_url")
    private String imageLargeUrl;
}
