package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.HarnessJson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Exported orphan execution/lease descriptor produced when a Fleet node is
 * forcefully unregistered, enabling healthy nodes with matching capabilities to take over.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrphanHandoffExport {

    public enum Status {
        PENDING_TAKEOVER,
        TAKEN_OVER,
        CANCELLED
    }

    private String exportId;
    private String scopeKey;
    private String executionId;
    private String taskId;
    private String handoffId;
    private String checkpointId;
    private String previousNodeId;
    private String previousWorkerId;
    private String previousLeaseId;
    private long previousFencingToken;
    private long fencedWriterEpoch;
    private Status status;
    private String takenOverByNodeId;
    private String takenOverByWorkerId;
    private long exportedAtEpochMs;
    private long takenOverAtEpochMs;

    @Builder.Default
    private List<String> requiredTags = new ArrayList<String>();

    public OrphanHandoffExport copy() {
        return HarnessJson.copy(this, OrphanHandoffExport.class);
    }
}
