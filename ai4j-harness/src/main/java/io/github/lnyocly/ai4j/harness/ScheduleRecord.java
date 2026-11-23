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
public class ScheduleRecord {

    private String scheduleId;
    private String scopeKey;
    private String name;
    private ScheduleMode mode;
    private String cronExpression;
    private String description;
    private String targetNodeId;
    private boolean enabled;
    private String lastClaimTickKey;
    private String lastRunId;
    private String lastRunSummary;
    private long lastTriggeredAtEpochMs;
    private HarnessActor createdBy;
    private long createdAtEpochMs;
    @Builder.Default
    private long version = 1L;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public ScheduleRecord copy() {
        return HarnessJson.copy(this, ScheduleRecord.class);
    }
}
