package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coarse-grained campaign/initiative container aggregating Tasks in the
 * {@code Todo -> Task -> Work} hierarchy.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WorkRecord {

    private String workId;
    private String scopeKey;
    private String title;
    private String goal;
    private WorkStatus status;
    private HarnessActor createdBy;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;
    @Builder.Default
    private long version = 1L;

    @Builder.Default
    private List<String> taskIds = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public WorkRecord copy() {
        return HarnessJson.copy(this, WorkRecord.class);
    }
}
