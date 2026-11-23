package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRunRecord {

    private String scheduleRunId;
    private String scheduleId;
    private String scopeKey;
    private String tickKey;
    private ScheduleMode mode;
    private String claimedByNodeId;
    private ScheduleRunStatus status;
    private String summary;
    private String error;
    private String remediationTaskId;
    private String remediationExecutionId;
    private long createdAtEpochMs;
    private long finishedAtEpochMs;

    @Builder.Default
    private List<String> recordedFactIds = new ArrayList<String>();

    @Builder.Default
    private List<String> alerts = new ArrayList<String>();

    public ScheduleRunRecord copy() {
        return HarnessJson.copy(this, ScheduleRunRecord.class);
    }
}
