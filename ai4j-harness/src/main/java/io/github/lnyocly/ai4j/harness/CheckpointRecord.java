package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointRecord {

    private String checkpointId;
    private String executionId;
    private String taskId;
    private String sessionId;
    private String runId;
    private String summary;
    private long createdAtEpochMs;

    @Builder.Default
    private Map<String, Object> state = new LinkedHashMap<String, Object>();

    public CheckpointRecord copy() {
        return HarnessJson.copy(this, CheckpointRecord.class);
    }
}
