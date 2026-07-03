package io.github.lnyocly.ai4j.platform.suno.music.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request for ChatFire Suno music generation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SunoMusicRequest {
    private String prompt;
    private String tags;
    private String mv;
    private String title;

    @JsonProperty("gpt_description_prompt")
    private String gptDescriptionPrompt;

    @JsonProperty("make_instrumental")
    private Boolean makeInstrumental;

    @JsonProperty("generation_type")
    private String generationType;

    @JsonProperty("negative_tags")
    private String negativeTags;

    @JsonProperty("continue_at")
    private Double continueAt;

    @JsonProperty("continue_clip_id")
    private String continueClipId;

    @JsonProperty("continued_aligned_prompt")
    private String continuedAlignedPrompt;

    @JsonProperty("infill_start_s")
    private Double infillStartS;

    @JsonProperty("infill_end_s")
    private Double infillEndS;

    private String task;

    @JsonProperty("cover_clip_id")
    private String coverClipId;

    @JsonIgnore
    @Builder.Default
    private Map<String, Object> extraFields = new LinkedHashMap<String, Object>();

    @JsonAnyGetter
    public Map<String, Object> any() {
        return extraFields == null ? Collections.<String, Object>emptyMap() : extraFields;
    }

    @JsonAnySetter
    public void setExtraField(String name, Object value) {
        if (extraFields == null) {
            extraFields = new LinkedHashMap<String, Object>();
        }
        extraFields.put(name, value);
    }
}
