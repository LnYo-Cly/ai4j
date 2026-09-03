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
 * Serializable authoritative state for one logical Harness. Query projections
 * can be rebuilt from this state and its journal; application business data is
 * intentionally absent.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessState {

    @Builder.Default
    private int schemaVersion = 1;

    private String harnessId;
    private long version;
    private long updatedAtEpochMs;

    @Builder.Default
    private Map<String, TaskRecord> tasks = new LinkedHashMap<String, TaskRecord>();
    @Builder.Default
    private Map<String, FactRecord> facts = new LinkedHashMap<String, FactRecord>();
    @Builder.Default
    private Map<String, DecisionRecord> decisions = new LinkedHashMap<String, DecisionRecord>();
    @Builder.Default
    private Map<String, EvidenceRecord> evidence = new LinkedHashMap<String, EvidenceRecord>();
    @Builder.Default
    private Map<String, ExecutionRecord> executions = new LinkedHashMap<String, ExecutionRecord>();
    @Builder.Default
    private Map<String, RelationRecord> relations = new LinkedHashMap<String, RelationRecord>();
    @Builder.Default
    private Map<String, CheckpointRecord> checkpoints = new LinkedHashMap<String, CheckpointRecord>();
    @Builder.Default
    private Map<String, WaitRecord> waits = new LinkedHashMap<String, WaitRecord>();
    @Builder.Default
    private Map<String, WakeupRecord> wakeups = new LinkedHashMap<String, WakeupRecord>();
    @Builder.Default
    private Map<String, LeaseRecord> leases = new LinkedHashMap<String, LeaseRecord>();
    @Builder.Default
    private Map<String, GateRecord> gates = new LinkedHashMap<String, GateRecord>();
    @Builder.Default
    private Map<String, SubmissionRecord> submissions = new LinkedHashMap<String, SubmissionRecord>();
    @Builder.Default
    private Map<String, ReviewRecord> reviews = new LinkedHashMap<String, ReviewRecord>();
    @Builder.Default
    private Map<String, AgentSessionSnapshot> sessions = new LinkedHashMap<String, AgentSessionSnapshot>();
    @Builder.Default
    private Map<String, SessionLeaseRecord> sessionLeases = new LinkedHashMap<String, SessionLeaseRecord>();
    @Builder.Default
    private Map<String, ToolInvocationRecord> toolInvocations = new LinkedHashMap<String, ToolInvocationRecord>();
    @Builder.Default
    private Map<String, String> idempotency = new LinkedHashMap<String, String>();
    @Builder.Default
    private List<HarnessEventRecord> events = new ArrayList<HarnessEventRecord>();

    public static HarnessState empty(String harnessId) {
        return HarnessState.builder()
                .harnessId(harnessId)
                .version(0L)
                .updatedAtEpochMs(System.currentTimeMillis())
                .build();
    }

    public HarnessState copy() {
        HarnessState copy = HarnessJson.copy(this, HarnessState.class);
        if (copy == null) {
            copy = HarnessState.empty(harnessId);
        }
        copy.ensureCollections();
        return copy;
    }

    public void ensureCollections() {
        if (tasks == null) tasks = new LinkedHashMap<String, TaskRecord>();
        if (facts == null) facts = new LinkedHashMap<String, FactRecord>();
        if (decisions == null) decisions = new LinkedHashMap<String, DecisionRecord>();
        if (evidence == null) evidence = new LinkedHashMap<String, EvidenceRecord>();
        if (executions == null) executions = new LinkedHashMap<String, ExecutionRecord>();
        if (relations == null) relations = new LinkedHashMap<String, RelationRecord>();
        if (checkpoints == null) checkpoints = new LinkedHashMap<String, CheckpointRecord>();
        if (waits == null) waits = new LinkedHashMap<String, WaitRecord>();
        if (wakeups == null) wakeups = new LinkedHashMap<String, WakeupRecord>();
        if (leases == null) leases = new LinkedHashMap<String, LeaseRecord>();
        if (gates == null) gates = new LinkedHashMap<String, GateRecord>();
        if (submissions == null) submissions = new LinkedHashMap<String, SubmissionRecord>();
        if (reviews == null) reviews = new LinkedHashMap<String, ReviewRecord>();
        if (sessions == null) sessions = new LinkedHashMap<String, AgentSessionSnapshot>();
        if (sessionLeases == null) sessionLeases = new LinkedHashMap<String, SessionLeaseRecord>();
        if (toolInvocations == null) toolInvocations = new LinkedHashMap<String, ToolInvocationRecord>();
        if (idempotency == null) idempotency = new LinkedHashMap<String, String>();
        if (events == null) events = new ArrayList<HarnessEventRecord>();
    }
}
