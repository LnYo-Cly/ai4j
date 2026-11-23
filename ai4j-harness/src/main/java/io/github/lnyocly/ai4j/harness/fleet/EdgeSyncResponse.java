package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.HarnessEntityRecord;
import io.github.lnyocly.ai4j.harness.HarnessEventRecord;
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
public class EdgeSyncResponse {

    private boolean synced;
    private boolean hasConflicts;
    private long newBaseSeq;
    private long writerEpoch;

    @Builder.Default
    private List<EdgeSyncResolution> resolutions = new ArrayList<EdgeSyncResolution>();

    @Builder.Default
    private List<EdgeSyncConflict> conflicts = new ArrayList<EdgeSyncConflict>();

    @Builder.Default
    private List<HarnessEntityRecord> mergedEntities = new ArrayList<HarnessEntityRecord>();

    @Builder.Default
    private List<HarnessEventRecord> eventsSinceBase = new ArrayList<HarnessEventRecord>();
}
