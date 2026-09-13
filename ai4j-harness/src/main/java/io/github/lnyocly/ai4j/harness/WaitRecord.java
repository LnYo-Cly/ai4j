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
public class WaitRecord {

    private String waitId;
    private String executionId;
    private String taskId;
    private WaitType type;
    private WaitStatus status;
    private String operationId;
    private String externalKey;
    private long dueAtEpochMs;
    private long createdAtEpochMs;
    private long resolvedAtEpochMs;

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<String, Object>();

    public WaitRecord copy() {
        return HarnessJson.copy(this, WaitRecord.class);
    }
}
