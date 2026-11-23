package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collaborator coordinating safe-point {@code steer}, portable {@code handoff}
 * ({@code export}/{@code claim}/{@code revoke}), {@code SquadRun} budget enforcement,
 * {@code Todo -> Task -> Work} lifecycle, and idempotent {@code Schedule(detect | remediate)}.
 */
public final class ExecutionCoordinationService {

    public static final String ERROR_SQUAD_BUDGET_EXHAUSTED = "SQUAD_LEADER_BUDGET_EXHAUSTED";
    public static final String ERROR_HANDOFF_EXPIRED = "HANDOFF_EXPIRED";
    public static final String ERROR_HANDOFF_AFFINITY_MISMATCH = "HANDOFF_AFFINITY_MISMATCH";

    private final HarnessStore store;
    private final HarnessEventJournal eventJournal;
    private final HarnessLeaseCoordinator leaseCoordinator;
    private final HarnessActor defaultActor;

    public ExecutionCoordinationService(HarnessStore store,
                                        HarnessEventJournal eventJournal,
                                        HarnessLeaseCoordinator leaseCoordinator,
                                        HarnessActor defaultActor) {
        this.store = store;
        this.eventJournal = eventJournal;
        this.leaseCoordinator = leaseCoordinator;
        this.defaultActor = HarnessSupport.normalizeActor(defaultActor, HarnessActor.agent("harness-agent"));
    }

    // ---- Safe-Point Steer ----

    public SteerRecord steer(String executionId,
                             String expectedTaskId,
                             long expectedVersion,
                             String instructions,
                             HarnessActor actor) {
        final String execKey = HarnessSupport.requireText(executionId, "execution id");
        final String directive = HarnessSupport.requireText(instructions, "steer instructions");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent()) {
            throw new HarnessValidationException(
                    "an Agent cannot inject safe-point steer instructions; human/system principal required");
        }
        final String normalizedExpectedTask = HarnessSupport.trimToNull(expectedTaskId);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<SteerRecord>() {
            @Override
            public SteerRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, execKey);
                if (execution.getStatus() != ExecutionStatus.READY
                        && execution.getStatus() != ExecutionStatus.RUNNING
                        && execution.getStatus() != ExecutionStatus.WAITING) {
                    throw new HarnessConflictException("cannot steer terminal/unknown execution in status "
                            + execution.getStatus() + ": " + execKey);
                }
                if (normalizedExpectedTask != null
                        && !normalizedExpectedTask.equals(HarnessSupport.trimToNull(execution.getTaskId()))) {
                    throw new HarnessConflictException("steer expectedTaskId mismatch: expected "
                            + normalizedExpectedTask + " but execution taskId is " + execution.getTaskId());
                }
                if (execution.getVersion() != expectedVersion) {
                    throw new HarnessConflictException("steer expectedVersion mismatch for execution "
                            + execKey + ": expected " + expectedVersion + " but was " + execution.getVersion());
                }
                long now = HarnessSupport.now();
                String steerId = HarnessSupport.valueOrGenerated(null, "steer_");
                SteerRecord record = SteerRecord.builder()
                        .steerId(steerId)
                        .scopeKey(execution.getScopeKey())
                        .executionId(execKey)
                        .taskId(execution.getTaskId())
                        .expectedTaskId(normalizedExpectedTask)
                        .expectedVersion(expectedVersion)
                        .instructions(directive)
                        .status(SteerStatus.PENDING)
                        .actor(HarnessSupport.copyActor(effectiveActor))
                        .createdAtEpochMs(now)
                        .build();
                state.getSteers().put(steerId, record);
                execution.setVersion(execution.getVersion() + 1L);
                execution.setUpdatedAtEpochMs(now);
                eventJournal.addEvent(state, "steer.injected", steerId, effectiveActor,
                        HarnessSupport.mapOf("executionId", execKey, "taskId", execution.getTaskId(),
                                "expectedVersion", expectedVersion, "instructions", directive));
                return record.copy();
            }
        });
    }

    public List<SteerRecord> listSteers(String executionId) {
        String execKey = HarnessSupport.requireText(executionId, "execution id");
        HarnessState state = store.load();
        if (state == null || state.getSteers() == null) {
            return Collections.emptyList();
        }
        List<SteerRecord> result = new ArrayList<SteerRecord>();
        for (SteerRecord steer : state.getSteers().values()) {
            if (steer != null && execKey.equals(steer.getExecutionId())) {
                result.add(steer.copy());
            }
        }
        Collections.sort(result, new Comparator<SteerRecord>() {
            @Override
            public int compare(SteerRecord left, SteerRecord right) {
                return Long.compare(left.getCreatedAtEpochMs(), right.getCreatedAtEpochMs());
            }
        });
        return result;
    }

    public List<SteerRecord> consumePendingSteers(String executionId, final int step) {
        final String execKey = HarnessSupport.requireText(executionId, "execution id");
        HarnessState snapshot = store.load();
        if (snapshot == null || snapshot.getSteers() == null || snapshot.getSteers().isEmpty()) {
            return Collections.emptyList();
        }
        boolean hasPending = false;
        for (SteerRecord steer : snapshot.getSteers().values()) {
            if (steer != null && execKey.equals(steer.getExecutionId())
                    && steer.getStatus() == SteerStatus.PENDING) {
                hasPending = true;
                break;
            }
        }
        if (!hasPending) {
            return Collections.emptyList();
        }
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<List<SteerRecord>>() {
            @Override
            public List<SteerRecord> apply(HarnessState state) {
                List<SteerRecord> consumed = new ArrayList<SteerRecord>();
                long now = HarnessSupport.now();
                for (SteerRecord steer : state.getSteers().values()) {
                    if (steer != null && execKey.equals(steer.getExecutionId())
                            && steer.getStatus() == SteerStatus.PENDING) {
                        steer.setStatus(SteerStatus.APPLIED);
                        steer.setAppliedAtEpochMs(now);
                        steer.setAppliedStep(step);
                        consumed.add(steer.copy());
                        eventJournal.addEvent(state, "steer.applied", steer.getSteerId(), defaultActor,
                                HarnessSupport.mapOf("executionId", execKey, "step", step));
                    }
                }
                Collections.sort(consumed, new Comparator<SteerRecord>() {
                    @Override
                    public int compare(SteerRecord left, SteerRecord right) {
                        return Long.compare(left.getCreatedAtEpochMs(), right.getCreatedAtEpochMs());
                    }
                });
                return consumed;
            }
        });
    }

    // ---- Portable Handoff (export / claim / revoke) ----

    public HandoffRecord exportHandoff(String executionId,
                                       String targetNodeId,
                                       String targetModel,
                                       String reason,
                                       long ttlMillis,
                                       HarnessActor actor) {
        final String execKey = HarnessSupport.requireText(executionId, "execution id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<HandoffRecord>() {
            @Override
            public HandoffRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, execKey);
                if (ExecutionStatus.SUCCEEDED.equals(execution.getStatus())
                        || ExecutionStatus.CANCELLED.equals(execution.getStatus())) {
                    throw new HarnessConflictException("cannot export handoff for terminal execution: " + execKey);
                }
                long now = HarnessSupport.now();
                String sourceNodeId = execution.getAssignedNodeId();
                String sourceWorkerId = execution.getWorkerId();
                if (execution.getLeaseId() != null) {
                    LeaseRecord activeLease = state.getLeases().get(execution.getLeaseId());
                    if (activeLease != null && activeLease.getReleasedAtEpochMs() <= 0L) {
                        activeLease.setReleasedAtEpochMs(now);
                    }
                    leaseCoordinator.releaseSessionLease(state, execution, execution.getLeaseId(),
                            execution.getFencingToken(), execution.getWorkerId(), now);
                    execution.setLeaseId(null);
                    execution.setWorkerId(null);
                    execution.setFencingToken(0L);
                }
                if (execution.getStatus() == ExecutionStatus.RUNNING
                        || execution.getStatus() == ExecutionStatus.UNKNOWN) {
                    execution.setStatus(ExecutionStatus.READY);
                }

                CheckpointRecord checkpoint = execution.getCheckpointId() == null
                        ? null : state.getCheckpoints().get(execution.getCheckpointId());
                AgentSessionSnapshot sessionSnapshot = execution.getSessionId() == null
                        ? null : state.getSessions().get(execution.getSessionId());
                List<ReadSetEntry> readSet = new ArrayList<ReadSetEntry>();
                for (ReadSetEntry rs : state.getReadSets().values()) {
                    if (rs != null && execKey.equals(rs.getExecutionId())) {
                        readSet.add(rs.copy());
                    }
                }
                List<SteerRecord> pendingSteers = new ArrayList<SteerRecord>();
                for (SteerRecord steer : state.getSteers().values()) {
                    if (steer != null && execKey.equals(steer.getExecutionId())
                            && steer.getStatus() == SteerStatus.PENDING) {
                        pendingSteers.add(steer.copy());
                    }
                }

                String handoffId = HarnessSupport.valueOrGenerated(null, "handoff_");
                String normalizedTargetNode = HarnessSupport.trimToNull(targetNodeId);
                String normalizedTargetModel = HarnessSupport.trimToNull(targetModel);
                long expiresAt = ttlMillis > 0L ? now + ttlMillis : 0L;

                Map<String, Object> digestMap = new LinkedHashMap<String, Object>();
                digestMap.put("handoffId", handoffId);
                digestMap.put("executionId", execKey);
                digestMap.put("checkpointId", execution.getCheckpointId());
                digestMap.put("targetNodeId", normalizedTargetNode);
                digestMap.put("targetModel", normalizedTargetModel);
                digestMap.put("readSetSize", readSet.size());
                String digest = HarnessDigests.sha256Hex(HarnessJson.toJson(digestMap));

                HandoffRecord record = HandoffRecord.builder()
                        .handoffId(handoffId)
                        .scopeKey(execution.getScopeKey())
                        .executionId(execKey)
                        .taskId(execution.getTaskId())
                        .sessionId(execution.getSessionId())
                        .checkpointId(execution.getCheckpointId())
                        .sourceNodeId(sourceNodeId)
                        .sourceWorkerId(sourceWorkerId)
                        .targetNodeId(normalizedTargetNode)
                        .targetModel(normalizedTargetModel)
                        .status(HandoffStatus.EXPORTED)
                        .reason(reason)
                        .packageDigest(digest)
                        .exportedBy(HarnessSupport.copyActor(effectiveActor))
                        .exportedAtEpochMs(now)
                        .expiresAtEpochMs(expiresAt)
                        .version(1L)
                        .checkpoint(checkpoint == null ? null : checkpoint.copy())
                        .sessionSnapshot(sessionSnapshot == null
                                ? null : HarnessJson.copy(sessionSnapshot, AgentSessionSnapshot.class))
                        .requiredTags(HarnessSupport.copyList(execution.getRequiredTags()))
                        .readSet(readSet)
                        .pendingSteers(pendingSteers)
                        .build();

                state.getHandoffs().put(handoffId, record);
                execution.setHandoffId(handoffId);
                execution.setTargetNodeId(normalizedTargetNode);
                execution.setTargetModel(normalizedTargetModel);
                execution.setAssignedNodeId(null);
                execution.setUpdatedAtEpochMs(now);
                execution.setVersion(execution.getVersion() + 1L);

                eventJournal.addEvent(state, "handoff.exported", handoffId, effectiveActor,
                        HarnessSupport.mapOf("executionId", execKey, "targetNodeId", normalizedTargetNode,
                                "targetModel", normalizedTargetModel, "packageDigest", digest));
                return record.copy();
            }
        });
    }

    public HandoffRecord claimHandoff(String handoffId,
                                      String claimingNodeId,
                                      String claimingWorkerId,
                                      long leaseDurationMillis,
                                      HarnessActor actor) {
        final String id = HarnessSupport.requireText(handoffId, "handoff id");
        final String nodeId = HarnessSupport.requireText(claimingNodeId, "claiming nodeId");
        final String workerId = HarnessSupport.trimToNull(claimingWorkerId) == null
                ? nodeId : claimingWorkerId.trim();
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);

        HandoffRecord claimedRecord = HarnessSupport.write(store, new HarnessSupport.StateCommand<HandoffRecord>() {
            @Override
            public HandoffRecord apply(HarnessState state) {
                HandoffRecord handoff = state.getHandoffs().get(id);
                if (handoff == null) {
                    throw new HarnessValidationException("handoff not found: " + id);
                }
                if (handoff.getStatus() != HandoffStatus.EXPORTED) {
                    throw new HarnessConflictException("handoff is not EXPORTED: " + id + " (" + handoff.getStatus() + ")");
                }
                long now = HarnessSupport.now();
                if (handoff.getExpiresAtEpochMs() > 0L && now > handoff.getExpiresAtEpochMs()) {
                    handoff.setStatus(HandoffStatus.EXPIRED);
                    handoff.setVersion(Math.max(1L, handoff.getVersion()) + 1L);
                    throw new HarnessConflictException(ERROR_HANDOFF_EXPIRED, "handoff expired: " + id);
                }
                if (handoff.getTargetNodeId() != null && !handoff.getTargetNodeId().equals(nodeId)) {
                    throw new HarnessConflictException(ERROR_HANDOFF_AFFINITY_MISMATCH,
                            "handoff targets node " + handoff.getTargetNodeId() + " but claiming node is " + nodeId);
                }
                ExecutionRecord execution = HarnessSupport.requireExecution(state, handoff.getExecutionId());
                if (execution.getStatus() != ExecutionStatus.READY
                        && execution.getStatus() != ExecutionStatus.WAITING) {
                    throw new HarnessConflictException("execution cannot be claimed via handoff in status "
                            + execution.getStatus());
                }

                handoff.setStatus(HandoffStatus.CLAIMED);
                handoff.setClaimedByNodeId(nodeId);
                handoff.setClaimedByWorkerId(workerId);
                handoff.setClaimedBy(HarnessSupport.copyActor(effectiveActor));
                handoff.setClaimedAtEpochMs(now);
                handoff.setVersion(Math.max(1L, handoff.getVersion()) + 1L);

                execution.setAssignedNodeId(nodeId);
                execution.setHandoffId(null);
                execution.setUpdatedAtEpochMs(now);
                execution.setVersion(execution.getVersion() + 1L);

                eventJournal.addEvent(state, "handoff.claimed", id, effectiveActor,
                        HarnessSupport.mapOf("executionId", handoff.getExecutionId(),
                                "claimingNodeId", nodeId, "claimingWorkerId", workerId));
                return handoff.copy();
            }
        });

        ExecutionRecord exe = store.load().getExecutions().get(claimedRecord.getExecutionId());
        if (exe != null && exe.getStatus() == ExecutionStatus.READY && leaseDurationMillis > 0L) {
            leaseCoordinator.claimExecution(exe.getExecutionId(), workerId, leaseDurationMillis);
        }
        return getHandoff(id);
    }

    public HandoffRecord revokeHandoff(String handoffId, String reason, HarnessActor actor) {
        final String id = HarnessSupport.requireText(handoffId, "handoff id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<HandoffRecord>() {
            @Override
            public HandoffRecord apply(HarnessState state) {
                HandoffRecord handoff = state.getHandoffs().get(id);
                if (handoff == null) {
                    throw new HarnessValidationException("handoff not found: " + id);
                }
                if (handoff.getStatus() == HandoffStatus.REVOKED) {
                    return handoff.copy();
                }
                long now = HarnessSupport.now();
                handoff.setStatus(HandoffStatus.REVOKED);
                handoff.setRevokedBy(HarnessSupport.copyActor(effectiveActor));
                handoff.setRevokedAtEpochMs(now);
                handoff.setRevokeReason(reason);
                handoff.setVersion(Math.max(1L, handoff.getVersion()) + 1L);

                ExecutionRecord execution = handoff.getExecutionId() == null
                        ? null : state.getExecutions().get(handoff.getExecutionId());
                if (execution != null && id.equals(execution.getHandoffId())) {
                    execution.setHandoffId(null);
                    execution.setUpdatedAtEpochMs(now);
                }
                eventJournal.addEvent(state, "handoff.revoked", id, effectiveActor,
                        HarnessSupport.mapOf("executionId", handoff.getExecutionId(), "reason", reason));
                return handoff.copy();
            }
        });
    }

    public HandoffRecord getHandoff(String handoffId) {
        String id = HarnessSupport.requireText(handoffId, "handoff id");
        HarnessState state = store.load();
        HandoffRecord record = state == null ? null : state.getHandoffs().get(id);
        return record == null ? null : record.copy();
    }

    public List<HandoffRecord> listHandoffs(String scopeKey) {
        HarnessState state = store.load();
        if (state == null || state.getHandoffs() == null) {
            return Collections.emptyList();
        }
        List<HandoffRecord> result = new ArrayList<HandoffRecord>();
        for (HandoffRecord handoff : state.getHandoffs().values()) {
            if (handoff != null && HarnessSupport.visibleScope(handoff.getScopeKey(), scopeKey)) {
                result.add(handoff.copy());
            }
        }
        return result;
    }

    // ---- SquadRun (leaderTurnBudget enforcement & synthesis convergence) ----

    public SquadRunRecord startSquadRun(SquadRunSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("squad run specification is required");
        }
        final String leaderId = HarnessSupport.requireText(spec.getLeaderId(), "squad leaderId");
        if (spec.getLeaderTurnBudget() <= 0) {
            throw new HarnessValidationException("leaderTurnBudget must be >= 1");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final String squadRunId = HarnessSupport.valueOrGenerated(spec.getSquadRunId(), "squad_");
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<SquadRunRecord>() {
            @Override
            public SquadRunRecord apply(HarnessState state) {
                if (state.getSquadRuns().containsKey(squadRunId)) {
                    throw new HarnessConflictException("squad run already exists: " + squadRunId);
                }
                String scopeKey = HarnessSupport.normalizeScope(spec.getScopeKey());
                String taskId = HarnessSupport.trimToNull(spec.getTaskId());
                if (taskId != null) {
                    TaskRecord task = HarnessSupport.requireTask(state, taskId);
                    HarnessSupport.assertCompatibleScope(scopeKey, task.getScopeKey(),
                            "squad run and task scopes must match");
                    if (scopeKey == null) {
                        scopeKey = task.getScopeKey();
                    }
                }
                SquadRunRecord record = SquadRunRecord.builder()
                        .squadRunId(squadRunId)
                        .scopeKey(scopeKey)
                        .taskId(taskId)
                        .leaderId(leaderId)
                        .objective(spec.getObjective())
                        .leaderTurnBudget(spec.getLeaderTurnBudget())
                        .leaderTurnsUsed(0)
                        .delegationCount(0)
                        .status(SquadRunStatus.RUNNING)
                        .memberIds(HarnessSupport.copyList(spec.getMemberIds()))
                        .createdAtEpochMs(HarnessSupport.now())
                        .version(1L)
                        .build();
                state.getSquadRuns().put(squadRunId, record);
                eventJournal.addEvent(state, "squad.started", squadRunId, effectiveActor,
                        HarnessSupport.mapOf("leaderId", leaderId,
                                "leaderTurnBudget", spec.getLeaderTurnBudget(), "taskId", taskId));
                return record.copy();
            }
        });
    }

    public SquadRunRecord recordSquadLeaderTurn(String squadRunId,
                                                String action,
                                                String targetMemberId,
                                                String summary,
                                                String memberResponse,
                                                HarnessActor actor) {
        final String id = HarnessSupport.requireText(squadRunId, "squadRunId");
        final String normalizedAction = HarnessSupport.requireText(action, "squad leader action").toLowerCase();
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<SquadRunRecord>() {
            @Override
            public SquadRunRecord apply(HarnessState state) {
                SquadRunRecord run = state.getSquadRuns().get(id);
                if (run == null) {
                    throw new HarnessValidationException("squad run not found: " + id);
                }
                if (run.getStatus() == SquadRunStatus.BUDGET_EXHAUSTED) {
                    throw new HarnessConflictException(ERROR_SQUAD_BUDGET_EXHAUSTED,
                            "squad leader turn budget exhausted (" + run.getLeaderTurnsUsed()
                                    + "/" + run.getLeaderTurnBudget() + "): " + id);
                }
                if (run.getStatus() != SquadRunStatus.RUNNING) {
                    throw new HarnessConflictException("squad run is not RUNNING: " + id + " (" + run.getStatus() + ")");
                }
                if (run.getLeaderTurnsUsed() >= run.getLeaderTurnBudget()) {
                    run.setStatus(SquadRunStatus.BUDGET_EXHAUSTED);
                    run.setFinishedAtEpochMs(HarnessSupport.now());
                    if (run.getSynthesisReport() == null) {
                        run.setSynthesisReport(buildForcedSynthesis(run));
                    }
                    throw new HarnessConflictException(ERROR_SQUAD_BUDGET_EXHAUSTED,
                            "squad leader turn budget exhausted (" + run.getLeaderTurnsUsed()
                                    + "/" + run.getLeaderTurnBudget() + "): " + id);
                }
                long now = HarnessSupport.now();
                int turnNumber = run.getLeaderTurnsUsed() + 1;
                run.setLeaderTurnsUsed(turnNumber);
                if ("delegate".equals(normalizedAction)) {
                    run.setDelegationCount(run.getDelegationCount() + 1);
                }
                List<SquadTurnRecord> turns = run.getTurns() == null
                        ? new ArrayList<SquadTurnRecord>() : new ArrayList<SquadTurnRecord>(run.getTurns());
                turns.add(SquadTurnRecord.builder()
                        .turnNumber(turnNumber)
                        .action(normalizedAction)
                        .targetMemberId(targetMemberId)
                        .summary(summary)
                        .memberResponse(memberResponse)
                        .recordedAtEpochMs(now)
                        .build());
                run.setTurns(turns);
                if ("converge".equals(normalizedAction) || "synthesize".equals(normalizedAction)) {
                    run.setStatus(SquadRunStatus.CONVERGED);
                    run.setSynthesisReport(HarnessSupport.trimToNull(memberResponse) != null
                            ? memberResponse : (HarnessSupport.trimToNull(summary) != null
                            ? summary : buildForcedSynthesis(run)));
                    run.setFinishedAtEpochMs(now);
                } else if (run.getLeaderTurnsUsed() >= run.getLeaderTurnBudget()) {
                    run.setStatus(SquadRunStatus.BUDGET_EXHAUSTED);
                    run.setSynthesisReport(buildForcedSynthesis(run));
                    run.setFinishedAtEpochMs(now);
                }
                run.setVersion(Math.max(1L, run.getVersion()) + 1L);
                eventJournal.addEvent(state, "squad.turn_recorded", id, effectiveActor,
                        HarnessSupport.mapOf("turnNumber", turnNumber, "action", normalizedAction,
                                "status", run.getStatus().name()));
                return run.copy();
            }
        });
    }

    public SquadRunRecord convergeSquadRun(String squadRunId, String synthesisReport, HarnessActor actor) {
        final String id = HarnessSupport.requireText(squadRunId, "squadRunId");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<SquadRunRecord>() {
            @Override
            public SquadRunRecord apply(HarnessState state) {
                SquadRunRecord run = state.getSquadRuns().get(id);
                if (run == null) {
                    throw new HarnessValidationException("squad run not found: " + id);
                }
                long now = HarnessSupport.now();
                run.setStatus(SquadRunStatus.CONVERGED);
                run.setSynthesisReport(HarnessSupport.trimToNull(synthesisReport) != null
                        ? synthesisReport : buildForcedSynthesis(run));
                run.setFinishedAtEpochMs(now);
                run.setVersion(Math.max(1L, run.getVersion()) + 1L);
                eventJournal.addEvent(state, "squad.converged", id, effectiveActor,
                        HarnessSupport.mapOf("turnsUsed", run.getLeaderTurnsUsed()));
                return run.copy();
            }
        });
    }

    public SquadRunRecord getSquadRun(String squadRunId) {
        String id = HarnessSupport.requireText(squadRunId, "squadRunId");
        HarnessState state = store.load();
        SquadRunRecord run = state == null ? null : state.getSquadRuns().get(id);
        return run == null ? null : run.copy();
    }

    private static String buildForcedSynthesis(SquadRunRecord run) {
        StringBuilder sb = new StringBuilder();
        sb.append("Forced synthesis for squad ").append(run.getSquadRunId())
                .append(" (budget ").append(run.getLeaderTurnsUsed()).append("/")
                .append(run.getLeaderTurnBudget()).append("): ");
        if (run.getTurns() != null) {
            for (SquadTurnRecord turn : run.getTurns()) {
                if (turn == null) continue;
                sb.append("[turn ").append(turn.getTurnNumber()).append(" ").append(turn.getAction());
                if (turn.getTargetMemberId() != null) {
                    sb.append("->").append(turn.getTargetMemberId());
                }
                if (turn.getMemberResponse() != null) {
                    sb.append(": ").append(turn.getMemberResponse());
                } else if (turn.getSummary() != null) {
                    sb.append(": ").append(turn.getSummary());
                }
                sb.append("] ");
            }
        }
        return sb.toString().trim();
    }

    // ---- Todo -> Task -> Work 3-Tier Granularity ----

    public TodoRecord createTodo(String todoId,
                                 String scopeKey,
                                 String title,
                                 String notes,
                                 Map<String, Object> metadata,
                                 HarnessActor actor) {
        final String effectiveTitle = HarnessSupport.requireText(title, "todo title");
        final String id = HarnessSupport.valueOrGenerated(todoId, "todo_");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<TodoRecord>() {
            @Override
            public TodoRecord apply(HarnessState state) {
                if (state.getTodos().containsKey(id)) {
                    throw new HarnessConflictException("todo already exists: " + id);
                }
                long now = HarnessSupport.now();
                TodoRecord todo = TodoRecord.builder()
                        .todoId(id)
                        .scopeKey(HarnessSupport.normalizeScope(scopeKey))
                        .title(effectiveTitle)
                        .notes(notes)
                        .status(TodoStatus.OPEN)
                        .createdBy(HarnessSupport.copyActor(effectiveActor))
                        .createdAtEpochMs(now)
                        .updatedAtEpochMs(now)
                        .version(1L)
                        .metadata(HarnessSupport.copyMap(metadata))
                        .build();
                state.getTodos().put(id, todo);
                eventJournal.addEvent(state, "todo.created", id, effectiveActor,
                        HarnessSupport.mapOf("title", effectiveTitle));
                return todo.copy();
            }
        });
    }

    public TaskRecord promoteTodoToTask(String todoId,
                                        HarnessTaskSpec taskSpec,
                                        HarnessCommandGateway gateway,
                                        HarnessActor actor) {
        final String id = HarnessSupport.requireText(todoId, "todo id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        TodoRecord todo = HarnessSupport.write(store, new HarnessSupport.StateCommand<TodoRecord>() {
            @Override
            public TodoRecord apply(HarnessState state) {
                TodoRecord existing = state.getTodos().get(id);
                if (existing == null) {
                    throw new HarnessValidationException("todo not found: " + id);
                }
                if (existing.getStatus() == TodoStatus.DISMISSED) {
                    throw new HarnessConflictException("cannot promote dismissed todo: " + id);
                }
                return existing.copy();
            }
        });
        if (todo.getStatus() == TodoStatus.PROMOTED && todo.getPromotedTaskId() != null) {
            TaskRecord existingTask = gateway.getTask(todo.getPromotedTaskId());
            if (existingTask != null) {
                return existingTask;
            }
        }
        HarnessTaskSpec effectiveSpec = taskSpec == null ? HarnessTaskSpec.builder().build() : taskSpec;
        String resolvedScope = HarnessSupport.normalizeScope(effectiveSpec.getScopeKey());
        HarnessSupport.assertCompatibleScope(resolvedScope, todo.getScopeKey(),
                "promoted task and todo scopes must match");
        if (resolvedScope == null) {
            resolvedScope = todo.getScopeKey();
        }
        String resolvedTitle = HarnessSupport.trimToNull(effectiveSpec.getTitle()) != null
                ? effectiveSpec.getTitle() : todo.getTitle();
        String resolvedGoal = HarnessSupport.trimToNull(effectiveSpec.getGoal()) != null
                ? effectiveSpec.getGoal() : todo.getNotes();
        final TaskRecord createdTask = gateway.createTask(effectiveSpec.toBuilder()
                .scopeKey(resolvedScope)
                .title(resolvedTitle)
                .goal(resolvedGoal)
                .build(), effectiveActor);

        HarnessSupport.write(store, new HarnessSupport.StateCommand<TodoRecord>() {
            @Override
            public TodoRecord apply(HarnessState state) {
                TodoRecord target = state.getTodos().get(id);
                if (target != null) {
                    long now = HarnessSupport.now();
                    target.setStatus(TodoStatus.PROMOTED);
                    target.setPromotedTaskId(createdTask.getTaskId());
                    target.setUpdatedAtEpochMs(now);
                    target.setVersion(Math.max(1L, target.getVersion()) + 1L);
                    eventJournal.addEvent(state, "todo.promoted", id, effectiveActor,
                            HarnessSupport.mapOf("taskId", createdTask.getTaskId()));
                }
                return target == null ? null : target.copy();
            }
        });
        return createdTask;
    }

    public WorkRecord createWork(String workId,
                                 String scopeKey,
                                 String title,
                                 String goal,
                                 Map<String, Object> metadata,
                                 HarnessActor actor) {
        final String effectiveTitle = HarnessSupport.requireText(title, "work title");
        final String id = HarnessSupport.valueOrGenerated(workId, "work_");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<WorkRecord>() {
            @Override
            public WorkRecord apply(HarnessState state) {
                if (state.getWorks().containsKey(id)) {
                    throw new HarnessConflictException("work already exists: " + id);
                }
                long now = HarnessSupport.now();
                WorkRecord work = WorkRecord.builder()
                        .workId(id)
                        .scopeKey(HarnessSupport.normalizeScope(scopeKey))
                        .title(effectiveTitle)
                        .goal(goal)
                        .status(WorkStatus.OPEN)
                        .createdBy(HarnessSupport.copyActor(effectiveActor))
                        .createdAtEpochMs(now)
                        .updatedAtEpochMs(now)
                        .version(1L)
                        .metadata(HarnessSupport.copyMap(metadata))
                        .build();
                state.getWorks().put(id, work);
                eventJournal.addEvent(state, "work.created", id, effectiveActor,
                        HarnessSupport.mapOf("title", effectiveTitle));
                return work.copy();
            }
        });
    }

    public WorkRecord attachTaskToWork(String workId, String taskId, HarnessActor actor) {
        final String wId = HarnessSupport.requireText(workId, "work id");
        final String tId = HarnessSupport.requireText(taskId, "task id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<WorkRecord>() {
            @Override
            public WorkRecord apply(HarnessState state) {
                WorkRecord work = state.getWorks().get(wId);
                if (work == null) {
                    throw new HarnessValidationException("work not found: " + wId);
                }
                TaskRecord task = HarnessSupport.requireTask(state, tId);
                HarnessSupport.assertCompatibleScope(work.getScopeKey(), task.getScopeKey(),
                        "work and task scopes must match");
                if (work.getScopeKey() == null && task.getScopeKey() != null) {
                    work.setScopeKey(task.getScopeKey());
                }
                List<String> taskIds = work.getTaskIds() == null
                        ? new ArrayList<String>() : new ArrayList<String>(work.getTaskIds());
                if (!taskIds.contains(tId)) {
                    taskIds.add(tId);
                }
                work.setTaskIds(taskIds);
                if (work.getStatus() == WorkStatus.OPEN) {
                    work.setStatus(WorkStatus.ACTIVE);
                }
                long now = HarnessSupport.now();
                work.setUpdatedAtEpochMs(now);
                work.setVersion(Math.max(1L, work.getVersion()) + 1L);
                task.setWorkId(wId);
                task.setUpdatedAtEpochMs(now);
                task.setVersion(Math.max(1L, task.getVersion()) + 1L);
                eventJournal.addEvent(state, "work.task_attached", wId, effectiveActor,
                        HarnessSupport.mapOf("taskId", tId));
                return work.copy();
            }
        });
    }

    public WorkProgress getWorkProgress(String workId) {
        String id = HarnessSupport.requireText(workId, "work id");
        HarnessState state = store.load();
        WorkRecord work = state == null ? null : state.getWorks().get(id);
        if (work == null) {
            throw new HarnessValidationException("work not found: " + id);
        }
        int total = 0;
        int done = 0;
        int active = 0;
        int blocked = 0;
        int inReview = 0;
        int cancelled = 0;
        for (String taskId : HarnessSupport.copyList(work.getTaskIds())) {
            TaskRecord task = state.getTasks().get(taskId);
            if (task == null) {
                continue;
            }
            total++;
            if (task.getStatus() == TaskStatus.DONE) done++;
            else if (task.getStatus() == TaskStatus.CANCELLED) cancelled++;
            else if (task.getStatus() == TaskStatus.BLOCKED) blocked++;
            else if (task.getStatus() == TaskStatus.IN_REVIEW) inReview++;
            else active++;
        }
        boolean allDone = total > 0 && done + cancelled == total && done > 0;
        WorkStatus computedStatus = allDone ? WorkStatus.DONE : work.getStatus();
        return WorkProgress.builder()
                .workId(id)
                .status(computedStatus)
                .totalTasks(total)
                .doneTasks(done)
                .activeTasks(active)
                .blockedTasks(blocked)
                .inReviewTasks(inReview)
                .cancelledTasks(cancelled)
                .allDone(allDone)
                .build();
    }

    // ---- Schedule (DETECT | REMEDIATE) with Ledger Idempotent Claim ----

    public ScheduleRecord createSchedule(ScheduleSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("schedule specification is required");
        }
        final String name = HarnessSupport.requireText(spec.getName(), "schedule name");
        final ScheduleMode mode = spec.getMode() == null ? ScheduleMode.DETECT : spec.getMode();
        final String scheduleId = HarnessSupport.valueOrGenerated(spec.getScheduleId(), "sched_");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<ScheduleRecord>() {
            @Override
            public ScheduleRecord apply(HarnessState state) {
                if (state.getSchedules().containsKey(scheduleId)) {
                    throw new HarnessConflictException("schedule already exists: " + scheduleId);
                }
                ScheduleRecord record = ScheduleRecord.builder()
                        .scheduleId(scheduleId)
                        .scopeKey(HarnessSupport.normalizeScope(spec.getScopeKey()))
                        .name(name)
                        .mode(mode)
                        .cronExpression(spec.getCronExpression())
                        .description(spec.getDescription())
                        .targetNodeId(HarnessSupport.trimToNull(spec.getTargetNodeId()))
                        .enabled(spec.isEnabled())
                        .createdBy(HarnessSupport.copyActor(effectiveActor))
                        .createdAtEpochMs(HarnessSupport.now())
                        .version(1L)
                        .metadata(HarnessSupport.copyMap(spec.getMetadata()))
                        .build();
                state.getSchedules().put(scheduleId, record);
                eventJournal.addEvent(state, "schedule.created", scheduleId, effectiveActor,
                        HarnessSupport.mapOf("name", name, "mode", mode.name()));
                return record.copy();
            }
        });
    }

    public ScheduleRunRecord triggerSchedule(String scheduleId,
                                             String tickKey,
                                             String claimingNodeId,
                                             ScheduleHandler handler,
                                             HarnessCommandGateway gateway,
                                             HarnessActor actor) {
        final String id = HarnessSupport.requireText(scheduleId, "schedule id");
        final String tick = HarnessSupport.requireText(tickKey, "schedule tickKey");
        if (handler == null) {
            throw new HarnessValidationException("schedule handler is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final String runId = "schedrun:" + id + ":" + tick;
        final String nodeId = HarnessSupport.trimToNull(claimingNodeId) == null
                ? "local" : claimingNodeId.trim();

        final ScheduleRecord[] claimedSchedule = new ScheduleRecord[1];
        ScheduleRunRecord existingOrClaimed = HarnessSupport.write(store, new HarnessSupport.StateCommand<ScheduleRunRecord>() {
            @Override
            public ScheduleRunRecord apply(HarnessState state) {
                ScheduleRecord schedule = state.getSchedules().get(id);
                if (schedule == null) {
                    throw new HarnessValidationException("schedule not found: " + id);
                }
                ScheduleRunRecord existing = state.getScheduleRuns().get(runId);
                if (existing != null) {
                    return existing.copy();
                }
                if (!schedule.isEnabled()) {
                    throw new HarnessConflictException("schedule is disabled: " + id);
                }
                long now = HarnessSupport.now();
                ScheduleRunRecord claimed = ScheduleRunRecord.builder()
                        .scheduleRunId(runId)
                        .scheduleId(id)
                        .scopeKey(schedule.getScopeKey())
                        .tickKey(tick)
                        .mode(schedule.getMode())
                        .claimedByNodeId(nodeId)
                        .status(ScheduleRunStatus.CLAIMED)
                        .createdAtEpochMs(now)
                        .build();
                state.getScheduleRuns().put(runId, claimed);
                schedule.setLastClaimTickKey(tick);
                schedule.setLastRunId(runId);
                schedule.setLastTriggeredAtEpochMs(now);
                schedule.setVersion(Math.max(1L, schedule.getVersion()) + 1L);
                claimedSchedule[0] = schedule.copy();
                eventJournal.addEvent(state, "schedule.run_claimed", runId, effectiveActor,
                        HarnessSupport.mapOf("scheduleId", id, "tickKey", tick, "nodeId", nodeId));
                return null;
            }
        });
        if (existingOrClaimed != null) {
            return existingOrClaimed;
        }

        final ScheduleContext context = new ScheduleContext(gateway, claimedSchedule[0], tick, effectiveActor);
        ScheduleRunStatus finalStatus = ScheduleRunStatus.COMPLETED;
        String summary = null;
        String error = null;
        try {
            summary = handler.run(context);
        } catch (Exception ex) {
            finalStatus = ScheduleRunStatus.FAILED;
            error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        }
        final ScheduleRunStatus resolvedStatus = finalStatus;
        final String resolvedSummary = summary;
        final String resolvedError = error;

        return HarnessSupport.write(store, new HarnessSupport.StateCommand<ScheduleRunRecord>() {
            @Override
            public ScheduleRunRecord apply(HarnessState state) {
                ScheduleRunRecord run = state.getScheduleRuns().get(runId);
                long now = HarnessSupport.now();
                run.setStatus(resolvedStatus);
                run.setSummary(resolvedSummary);
                run.setError(resolvedError);
                run.setRecordedFactIds(new ArrayList<String>(context.getRecordedFactIds()));
                run.setAlerts(new ArrayList<String>(context.getAlerts()));
                run.setRemediationTaskId(context.getRemediationTaskId());
                run.setRemediationExecutionId(context.getRemediationExecutionId());
                run.setFinishedAtEpochMs(now);

                ScheduleRecord schedule = state.getSchedules().get(id);
                if (schedule != null) {
                    schedule.setLastRunSummary(resolvedSummary != null ? resolvedSummary : resolvedError);
                }
                eventJournal.addEvent(state, "schedule.run_" + resolvedStatus.name().toLowerCase(), runId,
                        effectiveActor, HarnessSupport.mapOf("scheduleId", id, "tickKey", tick,
                                "factCount", context.getRecordedFactIds().size(),
                                "alertCount", context.getAlerts().size()));
                return run.copy();
            }
        });
    }
}
