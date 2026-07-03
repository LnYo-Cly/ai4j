package io.github.lnyocly.ai4j.platform.suno.music.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ChatFire Suno async task payload.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SunoTask {
    @JsonProperty("task_id")
    private String taskId;

    private String action;
    private String status;

    @JsonProperty("fail_reason")
    private String failReason;

    @JsonProperty("submit_time")
    private Long submitTime;

    @JsonProperty("start_time")
    private Long startTime;

    @JsonProperty("finish_time")
    private Long finishTime;

    private String progress;

    /**
     * Provider result payload. MUSIC actions usually return an array of song objects.
     */
    private JsonNode data;
}
