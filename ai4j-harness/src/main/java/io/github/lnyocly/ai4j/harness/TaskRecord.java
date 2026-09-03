package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TaskRecord {

    private String taskId;
    private String scopeKey;
    private String title;
    private String goal;
    private String plan;
    private TaskStatus status;
    private String blockedReason;
    private String lastExecutionId;
    private String submissionId;
    private String createdBy;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;
    private long version;

    @Builder.Default
    private List<String> tags = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public TaskRecord copy() {
        return HarnessJson.copy(this, TaskRecord.class);
    }
}
