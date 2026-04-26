package io.github.lnyocly.ai4j.platform.doubao.image.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;


@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoubaoImageGenerationRequest {

    @NonNull
    private String model;

    @NonNull
    private String prompt;

    private Integer n;

    private String size;

    @JsonProperty("response_format")
    private String responseFormat;

    private Boolean stream;

    @JsonIgnore
    @Singular("extraBody")
    private Map<String, Object> extraBody;

    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody;
    }
}
