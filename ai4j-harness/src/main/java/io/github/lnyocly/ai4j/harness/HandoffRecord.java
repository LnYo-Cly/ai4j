package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portable execution handoff envelope supporting export, affinity-checked claim, and revoke.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HandoffRecord {

    private String handoffId;
    private String scopeKey;
    private String executionId;
    private String taskId;
    private String sessionId;
    private String checkpointId;
    private String sourceNodeId;
    private String sourceWorkerId;
    private String targetNodeId;
    private String targetModel;
    private String claimedByNodeId;
    private String claimedByWorkerId;
    private HandoffStatus status;
    private String reason;
    private String revokeReason;
    private String packageDigest;
    private HarnessActor exportedBy;
    private HarnessActor claimedBy;
    private HarnessActor revokedBy;
    private long exportedAtEpochMs;
    private long claimedAtEpochMs;
    private long revokedAtEpochMs;
    private long expiresAtEpochMs;
    private long version;
    private CheckpointRecord checkpoint;
    private AgentSessionSnapshot sessionSnapshot;

    @Builder.Default
    private List<String> requiredTags = new ArrayList<String>();

    @Builder.Default
    private List<ReadSetEntry> readSet = new ArrayList<ReadSetEntry>();

    @Builder.Default
    private List<SteerRecord> pendingSteers = new ArrayList<SteerRecord>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public HandoffRecord copy() {
        return HarnessJson.copy(this, HandoffRecord.class);
    }
}
