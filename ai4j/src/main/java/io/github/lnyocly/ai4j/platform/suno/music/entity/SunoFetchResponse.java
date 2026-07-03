package io.github.lnyocly.ai4j.platform.suno.music.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * ChatFire Suno fetch response.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SunoFetchResponse {
    private String code;
    private String message;
    private SunoTask data;
    private Map<String, Object> raw;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(code);
    }
}
