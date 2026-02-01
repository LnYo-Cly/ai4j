package io.github.lnyocly.ai4j.platform.openai.response.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;


@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseContentPart {

    private String type;

    private String text;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("file_url")
    private String fileUrl;

    @JsonProperty("file_data")
    private String fileData;

    @JsonProperty("video_url")
    private String videoUrl;

    private String detail;

    @JsonProperty("image_pixel_limit")
    private ImagePixelLimit imagePixelLimit;

    @JsonProperty("translation_options")
    private TranslationOptions translationOptions;

    @JsonIgnore
    @Singular("extraBody")
    private Map<String, Object> extraBody;

    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody;
    }
}

