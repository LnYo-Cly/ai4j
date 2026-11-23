package io.github.lnyocly.ai4j.harness.fleet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EdgeSyncConflict {

    private String entityType;
    private String entityId;
    private String fieldPath;
    private Object baseValue;
    private Object localValue;
    private Object centerValue;
    private long baseVersion;
    private long localVersion;
    private long centerVersion;
    private String reason;
    private String resolution;
}
