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
public class EdgeSyncRequest {

    private String nodeId;
    private String credentialToken;
    private long writerEpoch;
    private long baseSyncSeq;
    @Builder.Default
    private EdgeSyncConflictPolicy conflictPolicy = EdgeSyncConflictPolicy.DETECT_AND_REPORT;

    @Builder.Default
    private List<HarnessEntityRecord> baseEntities = new ArrayList<HarnessEntityRecord>();

    @Builder.Default
    private List<HarnessEntityRecord> localEntities = new ArrayList<HarnessEntityRecord>();

    @Builder.Default
    private List<HarnessEventRecord> localEvents = new ArrayList<HarnessEventRecord>();
}
