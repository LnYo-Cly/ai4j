package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Model card returned by {@code GET /v1/models}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemOneModelCard {

    private String name;

    private String description;

    @JsonProperty("release_date")
    private String releaseDate;
}
