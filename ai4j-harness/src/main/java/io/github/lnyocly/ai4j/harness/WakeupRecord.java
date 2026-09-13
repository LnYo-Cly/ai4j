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
public class WakeupRecord {

    private String wakeupId;
    private String waitId;
    private String executionId;
    private WaitType type;
    private long dueAtEpochMs;
    private long deliveredAtEpochMs;

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<String, Object>();

    public WakeupRecord copy() {
        return HarnessJson.copy(this, WakeupRecord.class);
    }
}
