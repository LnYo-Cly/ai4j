package io.github.lnyocly.ai4j.coding.task;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CodingTaskProgress {

    private String phase;

    private String message;

    private Integer percent;

    private long updatedAtEpochMs;
}
