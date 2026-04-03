package io.github.lnyocly.ai4j.coding.delegate;

import io.github.lnyocly.ai4j.coding.task.CodingTaskStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CodingDelegateResult {

    private String taskId;

    private String definitionName;

    private String parentSessionId;

    private String childSessionId;

    private boolean background;

    private CodingTaskStatus status;

    private String outputText;

    private String error;
}
