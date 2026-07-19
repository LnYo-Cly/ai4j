package io.github.lnyocly.ai4j.platform.suno.music.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * ChatFire Suno task submission response.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SunoSubmitResponse {
    private String code;
    private String message;
    private String data;
    private Map<String, Object> raw;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(code);
    }
}
