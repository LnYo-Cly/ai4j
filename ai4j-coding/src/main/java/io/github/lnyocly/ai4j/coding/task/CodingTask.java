package io.github.lnyocly.ai4j.coding.task;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CodingTask {

    private String taskId;

    private String definitionName;

    private String parentSessionId;

    private String childSessionId;

    private String input;

    private boolean background;

    private CodingTaskStatus status;

    private CodingTaskProgress progress;

    private long createdAtEpochMs;

    private long startedAtEpochMs;

    private long endedAtEpochMs;

    private String outputText;

    private String error;
}
