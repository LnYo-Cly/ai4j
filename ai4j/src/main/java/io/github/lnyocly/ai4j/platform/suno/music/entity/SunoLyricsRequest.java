package io.github.lnyocly.ai4j.platform.suno.music.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request for ChatFire Suno lyrics generation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SunoLyricsRequest {
    private String prompt;

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
