package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImagePixelLimit {

    @JsonProperty("min_pixels")
    private Integer minPixels;

    @JsonProperty("max_pixels")
    private Integer maxPixels;
}

