package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.harness.fleet.FleetNodeRecord;
import io.github.lnyocly.ai4j.harness.fleet.OrphanHandoffExport;
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
    private Map<String, AcceptanceRecord> acceptances = new LinkedHashMap<String, AcceptanceRecord>();
    @Builder.Default
    private Map<String, SubmissionRecord> submissions = new LinkedHashMap<String, SubmissionRecord>();
    @Builder.Default
    private Map<String, ReviewRecord> reviews = new LinkedHashMap<String, ReviewRecord>();
    @Builder.Default
    private Map<String, ExecutionReviewRecord> executionReviews = new LinkedHashMap<String, ExecutionReviewRecord>();
    @Builder.Default
    private Map<String, ContentPinnedConsentRecord> consents = new LinkedHashMap<String, ContentPinnedConsentRecord>();
    @Builder.Default
    private Map<String, EscalationRecord> escalations = new LinkedHashMap<String, EscalationRecord>();
    @Builder.Default
    private Map<String, ReadSetEntry> readSets = new LinkedHashMap<String, ReadSetEntry>();
    @Builder.Default
    private Map<String, GateRunRecord> gateRuns = new LinkedHashMap<String, GateRunRecord>();
    @Builder.Default
    private Map<String, SteerRecord> steers = new LinkedHashMap<String, SteerRecord>();
    @Builder.Default
    private Map<String, HandoffRecord> handoffs = new LinkedHashMap<String, HandoffRecord>();
    @Builder.Default
    private Map<String, AgentBlueprintRecord> agentBlueprints = new LinkedHashMap<String, AgentBlueprintRecord>();
    @Builder.Default
    private Map<String, SquadRunRecord> squadRuns = new LinkedHashMap<String, SquadRunRecord>();
    @Builder.Default
    private Map<String, TypedArtifactRecord> typedArtifacts = new LinkedHashMap<String, TypedArtifactRecord>();
    @Builder.Default
    private Map<String, TodoRecord> todos = new LinkedHashMap<String, TodoRecord>();
    @Builder.Default
    private Map<String, WorkRecord> works = new LinkedHashMap<String, WorkRecord>();
    @Builder.Default
    private Map<String, ScheduleRecord> schedules = new LinkedHashMap<String, ScheduleRecord>();
    @Builder.Default
    private Map<String, ScheduleRunRecord> scheduleRuns = new LinkedHashMap<String, ScheduleRunRecord>();
    @Builder.Default
    private Map<String, FleetNodeRecord> fleetNodes = new LinkedHashMap<String, FleetNodeRecord>();
    @Builder.Default
    private Map<String, OrphanHandoffExport> orphanExports = new LinkedHashMap<String, OrphanHandoffExport>();
    @Builder.Default
    private Map<String, AgentSessionSnapshot> sessions = new LinkedHashMap<String, AgentSessionSnapshot>();
    @Builder.Default
    private Map<String, SessionLeaseRecord> sessionLeases = new LinkedHashMap<String, SessionLeaseRecord>();
    @Builder.Default
    private Map<String, ToolInvocationRecord> toolInvocations = new LinkedHashMap<String, ToolInvocationRecord>();
    @Builder.Default
    private Map<String, HarnessRule> learnedRules = new LinkedHashMap<String, HarnessRule>();
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
        if (acceptances == null) acceptances = new LinkedHashMap<String, AcceptanceRecord>();
        if (submissions == null) submissions = new LinkedHashMap<String, SubmissionRecord>();
        if (reviews == null) reviews = new LinkedHashMap<String, ReviewRecord>();
        if (executionReviews == null) executionReviews = new LinkedHashMap<String, ExecutionReviewRecord>();
        if (consents == null) consents = new LinkedHashMap<String, ContentPinnedConsentRecord>();
        if (escalations == null) escalations = new LinkedHashMap<String, EscalationRecord>();
        if (readSets == null) readSets = new LinkedHashMap<String, ReadSetEntry>();
        if (gateRuns == null) gateRuns = new LinkedHashMap<String, GateRunRecord>();
        if (steers == null) steers = new LinkedHashMap<String, SteerRecord>();
        if (handoffs == null) handoffs = new LinkedHashMap<String, HandoffRecord>();
        if (agentBlueprints == null) agentBlueprints = new LinkedHashMap<String, AgentBlueprintRecord>();
        if (squadRuns == null) squadRuns = new LinkedHashMap<String, SquadRunRecord>();
        if (typedArtifacts == null) typedArtifacts = new LinkedHashMap<String, TypedArtifactRecord>();
        if (todos == null) todos = new LinkedHashMap<String, TodoRecord>();
        if (works == null) works = new LinkedHashMap<String, WorkRecord>();
        if (schedules == null) schedules = new LinkedHashMap<String, ScheduleRecord>();
        if (scheduleRuns == null) scheduleRuns = new LinkedHashMap<String, ScheduleRunRecord>();
        if (fleetNodes == null) fleetNodes = new LinkedHashMap<String, FleetNodeRecord>();
        if (orphanExports == null) orphanExports = new LinkedHashMap<String, OrphanHandoffExport>();
        if (sessions == null) sessions = new LinkedHashMap<String, AgentSessionSnapshot>();
        if (sessionLeases == null) sessionLeases = new LinkedHashMap<String, SessionLeaseRecord>();
        if (toolInvocations == null) toolInvocations = new LinkedHashMap<String, ToolInvocationRecord>();
        if (learnedRules == null) learnedRules = new LinkedHashMap<String, HarnessRule>();
        if (idempotency == null) idempotency = new LinkedHashMap<String, String>();
        if (events == null) events = new ArrayList<HarnessEventRecord>();
    }
}
