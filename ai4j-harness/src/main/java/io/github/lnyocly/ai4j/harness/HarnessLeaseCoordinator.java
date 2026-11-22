package io.github.lnyocly.ai4j.harness;

import java.util.Map;

/**
 * Cohesive lease and writer-epoch coordinator managing execution leases,
 * session mutex leases, lease-expiry fencing, reconciliation, and split-brain
 * writer epoch claims.
 */
public final class HarnessLeaseCoordinator {

    private static final long DEFAULT_LEASE_MILLIS = 60_000L;

    private final HarnessStore store;
    private final HarnessContract contract;
    private final HarnessEventJournal eventJournal;
    private final HarnessActor defaultActor;

    public HarnessLeaseCoordinator(HarnessStore store,
                                   HarnessContract contract,
                                   HarnessEventJournal eventJournal,
                                   HarnessActor defaultActor) {
        if (store == null) {
            throw new IllegalArgumentException("Harness store is required");
        }
        if (eventJournal == null) {
            throw new IllegalArgumentException("Harness event journal is required");
        }
        this.store = store;
        this.contract = contract == null ? HarnessContract.builder().build() : contract;
        this.eventJournal = eventJournal;
        this.defaultActor = HarnessSupport.normalizeActor(defaultActor, HarnessActor.agent("harness-agent"));
    }

    public WriterEpochFence claimWriterEpoch(String nodeId, long expectedEpoch) {
        String normalizedNodeId = HarnessSupport.requireText(nodeId, "node id");
        if (expectedEpoch < 0L) {
            throw new HarnessValidationException("expected writer epoch must be >= 0");
        }
        if (store instanceof HarnessEventStore) {
            return ((HarnessEventStore) store).claimWriterEpoch(normalizedNodeId, expectedEpoch);
        }
        return eventJournal.claimFallbackEpoch(normalizedNodeId, expectedEpoch);
    }

    public WriterEpochFence currentWriterEpoch() {
        if (store instanceof HarnessEventStore) {
            return ((HarnessEventStore) store).currentWriterEpoch();
        }
        return eventJournal.currentFallbackEpoch();
    }

    public ExecutionRecord claimExecution(String executionId, String workerId, long leaseDurationMillis) {
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        final String worker = HarnessSupport.requireText(workerId, "worker id");
        final long duration = leaseDurationMillis <= 0L ? DEFAULT_LEASE_MILLIS : leaseDurationMillis;
        final HarnessSupport.ConflictHolder conflict = new HarnessSupport.ConflictHolder();
        ExecutionRecord result = HarnessSupport.write(store, new HarnessSupport.StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
                TaskRecord task = execution.getTaskId() == null
                        ? null : state.getTasks().get(execution.getTaskId());
                if (task != null && (TaskStatus.CANCELLED.equals(task.getStatus())
                        || TaskStatus.DONE.equals(task.getStatus())
                        || TaskStatus.BLOCKED.equals(task.getStatus())
                        || TaskStatus.IN_REVIEW.equals(task.getStatus()))) {
                    throw new HarnessConflictException("cannot claim execution for task in status "
                            + task.getStatus() + ": " + execution.getTaskId());
                }
                long currentTime = HarnessSupport.now();
                LeaseRecord currentLease = execution.getLeaseId() == null
                        ? null : state.getLeases().get(execution.getLeaseId());
                if (ExecutionStatus.RUNNING.equals(execution.getStatus()) && currentLease != null
                        && !currentLease.isExpired(currentTime)) {
                    if (worker.equals(currentLease.getWorkerId())) {
                        if (!ensureSessionLeaseForRunning(state, execution, currentLease, currentTime)) {
                            conflict.message = "session lease expired and execution was marked UNKNOWN: " + executionKey;
                        }
                        return execution.copy();
                    }
                    throw new HarnessConflictException("execution is already leased by " + currentLease.getWorkerId());
                }
                if (ExecutionStatus.RUNNING.equals(execution.getStatus())
                        && (currentLease == null || currentLease.isExpired(currentTime))) {
                    markExecutionUnknown(state, execution, currentTime,
                            currentLease == null
                                    ? "execution lease is missing; side effects require reconciliation"
                                    : "execution lease expired; side effects require reconciliation",
                            HarnessActor.worker(worker));
                    conflict.message = "execution lease expired and was marked UNKNOWN: " + executionKey;
                    return execution.copy();
                }
                if (ExecutionStatus.UNKNOWN.equals(execution.getStatus())) {
                    throw new HarnessConflictException("execution is UNKNOWN and needs explicit reconciliation: " + executionKey);
                }
                if (ExecutionStatus.WAITING.equals(execution.getStatus())) {
                    throw new HarnessConflictException("execution is waiting for an external wakeup: " + executionKey);
                }
                if (!ExecutionStatus.READY.equals(execution.getStatus())) {
                    throw new HarnessConflictException("execution cannot be claimed from status " + execution.getStatus());
                }
                if (HarnessSupport.trimToNull(execution.getSessionId()) == null) {
                    execution.setSessionId(HarnessSupport.valueOrGenerated(null, "session_"));
                    execution.setUpdatedAtEpochMs(currentTime);
                    execution.setVersion(execution.getVersion() + 1L);
                    eventJournal.addEvent(state, "execution.session_bound", executionKey,
                            HarnessActor.worker(worker), HarnessSupport.mapOf("sessionId", execution.getSessionId()));
                }
                long token = nextFencingToken(state);
                String leaseId = HarnessSupport.valueOrGenerated(null, "lease_");
                LeaseRecord lease = LeaseRecord.builder()
                        .leaseId(leaseId)
                        .executionId(executionKey)
                        .workerId(worker)
                        .fencingToken(token)
                        .acquiredAtEpochMs(currentTime)
                        .expiresAtEpochMs(currentTime + duration)
                        .build();
                acquireSessionLease(state, execution, worker, leaseId, token,
                        currentTime, currentTime + duration);
                state.getLeases().put(leaseId, lease);
                execution.setStatus(ExecutionStatus.RUNNING);
                execution.setWorkerId(worker);
                execution.setLeaseId(leaseId);
                execution.setFencingToken(token);
                execution.setStartedAtEpochMs(execution.getStartedAtEpochMs() <= 0L
                        ? currentTime : execution.getStartedAtEpochMs());
                execution.setUpdatedAtEpochMs(currentTime);
                execution.setVersion(execution.getVersion() + 1L);
                if (task != null) {
                    if (TaskStatus.PLANNED.equals(task.getStatus()) || TaskStatus.WAITING.equals(task.getStatus())) {
                        task.setStatus(TaskStatus.ACTIVE);
                        task.setBlockedReason(null);
                        task.setUpdatedAtEpochMs(currentTime);
                        task.setVersion(task.getVersion() + 1L);
                    }
                }
                eventJournal.addEvent(state, "execution.claimed", executionKey,
                        HarnessActor.worker(worker), HarnessSupport.mapOf(
                                "workerId", worker, "fencingToken", token));
                return execution.copy();
            }
        });
        if (conflict.message != null) {
            throw new HarnessConflictException(conflict.message);
        }
        return result;
    }

    public ExecutionRecord markExecutionUnknownIfLeaseLost(String executionId,
                                                            String leaseId,
                                                            long fencingToken,
                                                            String workerId,
                                                            String reason) {
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        final String leaseKey = HarnessSupport.requireText(leaseId, "lease id");
        final String worker = HarnessSupport.requireText(workerId, "worker id");
        final String unknownReason = HarnessSupport.trimToNull(reason) == null
                ? "execution lease was lost; side effects require reconciliation" : reason.trim();
        final HarnessSupport.ConflictHolder conflict = new HarnessSupport.ConflictHolder();
        ExecutionRecord result = HarnessSupport.write(store, new HarnessSupport.StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
                if (!ExecutionStatus.RUNNING.equals(execution.getStatus())) {
                    return execution.copy();
                }
                if (!HarnessSupport.safeEquals(leaseKey, execution.getLeaseId())
                        || execution.getFencingToken() != fencingToken
                        || !HarnessSupport.safeEquals(worker, execution.getWorkerId())) {
                    conflict.message = "execution lease was already fenced or reclaimed: " + executionKey;
                    return execution.copy();
                }
                long currentTime = HarnessSupport.now();
                LeaseRecord lease = state.getLeases().get(leaseKey);
                SessionLeaseRecord sessionLease = execution.getSessionId() == null
                        ? null : state.getSessionLeases().get(execution.getSessionId());
                boolean executionLeaseLost = lease == null || lease.isExpired(currentTime)
                        || lease.getFencingToken() != fencingToken
                        || !HarnessSupport.safeEquals(worker, lease.getWorkerId());
                boolean sessionLeaseLost = execution.getSessionId() != null
                        && (sessionLease == null || sessionLease.isExpired(currentTime)
                        || !sameSessionLease(sessionLease, execution, leaseKey, fencingToken, worker));
                if (!executionLeaseLost && !sessionLeaseLost) {
                    conflict.message = "execution lease is still active; outcome was not persisted: " + executionKey;
                    return execution.copy();
                }
                markExecutionUnknown(state, execution, currentTime, unknownReason,
                        HarnessActor.worker(worker));
                return execution.copy();
            }
        });
        if (conflict.message != null) {
            throw new HarnessConflictException(conflict.message);
        }
        return result;
    }

    public ExecutionRecord heartbeat(String executionId,
                                     String leaseId,
                                     long fencingToken,
                                     String workerId,
                                     long leaseDurationMillis) {
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        final String leaseKey = HarnessSupport.requireText(leaseId, "lease id");
        final String worker = HarnessSupport.requireText(workerId, "worker id");
        final long duration = leaseDurationMillis <= 0L ? DEFAULT_LEASE_MILLIS : leaseDurationMillis;
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
                assertLease(state, execution, leaseKey, fencingToken, worker);
                LeaseRecord lease = state.getLeases().get(leaseKey);
                long currentTime = HarnessSupport.now();
                lease.setExpiresAtEpochMs(currentTime + duration);
                if (!refreshSessionLease(state, execution, lease, currentTime)) {
                    return execution.copy();
                }
                execution.setUpdatedAtEpochMs(currentTime);
                execution.setVersion(execution.getVersion() + 1L);
                return execution.copy();
            }
        });
    }

    public ExecutionRecord releaseExecution(String executionId,
                                            String leaseId,
                                            long fencingToken,
                                            String workerId) {
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        final String leaseKey = HarnessSupport.requireText(leaseId, "lease id");
        final String worker = HarnessSupport.requireText(workerId, "worker id");
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
                assertLease(state, execution, leaseKey, fencingToken, worker);
                LeaseRecord lease = state.getLeases().get(leaseKey);
                long currentTime = HarnessSupport.now();
                lease.setReleasedAtEpochMs(currentTime);
                releaseSessionLease(state, execution, leaseKey, fencingToken, worker, currentTime);
                TaskRecord task = execution.getTaskId() == null
                        ? null : state.getTasks().get(execution.getTaskId());
                boolean cancelled = task != null && TaskStatus.CANCELLED.equals(task.getStatus());
                execution.setStatus(cancelled ? ExecutionStatus.CANCELLED : ExecutionStatus.READY);
                execution.setWorkerId(null);
                execution.setLeaseId(null);
                execution.setFencingToken(0L);
                if (cancelled) {
                    execution.setWaitId(null);
                    execution.setOperationId(null);
                    execution.setError(task.getBlockedReason() == null
                            ? "task was cancelled while the execution was running"
                            : task.getBlockedReason());
                    execution.setFinishedAtEpochMs(currentTime);
                }
                execution.setUpdatedAtEpochMs(currentTime);
                execution.setVersion(execution.getVersion() + 1L);
                eventJournal.addEvent(state, cancelled ? "execution.cancelled" : "execution.requeued",
                        executionKey, HarnessActor.worker(worker),
                        HarnessSupport.mapOf("taskId", execution.getTaskId()));
                return execution.copy();
            }
        });
    }

    public ExecutionRecord reconcileExecution(String executionId,
                                              ExecutionStatus resolution,
                                              String reason,
                                              String outputText,
                                              HarnessActor actor) {
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        if (resolution == null || resolution == ExecutionStatus.RUNNING
                || resolution == ExecutionStatus.WAITING
                || resolution == ExecutionStatus.UNKNOWN) {
            throw new HarnessValidationException(
                    "execution reconciliation must resolve to READY, SUCCEEDED, FAILED, or CANCELLED");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
                if (!ExecutionStatus.UNKNOWN.equals(execution.getStatus())) {
                    throw new HarnessConflictException(
                            "only UNKNOWN executions can be reconciled: " + executionKey);
                }
                if (!contract.mayReconcile(effectiveActor, execution, resolution)) {
                    throw new HarnessValidationException("actor is not allowed to reconcile this execution");
                }
                long currentTime = HarnessSupport.now();
                LeaseRecord lease = execution.getLeaseId() == null
                        ? null : state.getLeases().get(execution.getLeaseId());
                releaseSessionLease(state, execution, execution.getLeaseId(),
                        execution.getFencingToken(), execution.getWorkerId(), currentTime);
                if (lease != null) {
                    lease.setReleasedAtEpochMs(currentTime);
                }
                execution.setStatus(resolution);
                execution.setWorkerId(null);
                execution.setLeaseId(null);
                execution.setFencingToken(0L);
                execution.setWaitId(null);
                execution.setOperationId(null);
                if (outputText != null) {
                    execution.setOutputText(outputText);
                }
                execution.setError(ExecutionStatus.SUCCEEDED.equals(resolution) ? null : reason);
                execution.setFinishedAtEpochMs(isTerminalExecution(resolution) ? currentTime : 0L);
                execution.setUpdatedAtEpochMs(currentTime);
                execution.setVersion(execution.getVersion() + 1L);
                TaskRecord task = execution.getTaskId() == null
                        ? null : state.getTasks().get(execution.getTaskId());
                if (task != null) {
                    task.setLastExecutionId(executionKey);
                    if (!TaskStatus.DONE.equals(task.getStatus())
                            && !TaskStatus.CANCELLED.equals(task.getStatus())
                            && !TaskStatus.IN_REVIEW.equals(task.getStatus())) {
                        task.setStatus(TaskStatus.ACTIVE);
                        task.setBlockedReason(null);
                        task.setUpdatedAtEpochMs(currentTime);
                        task.setVersion(task.getVersion() + 1L);
                    }
                }
                eventJournal.addEvent(state, "execution.reconciled", executionKey, effectiveActor,
                        HarnessSupport.mapOf("resolution", resolution, "reason", reason));
                return execution.copy();
            }
        });
    }

    void assertLease(HarnessState state,
                     ExecutionRecord execution,
                     String leaseId,
                     long fencingToken,
                     String workerId) {
        if (leaseId == null || execution.getLeaseId() == null || !leaseId.equals(execution.getLeaseId())
                || execution.getFencingToken() != fencingToken || workerId == null
                || !workerId.equals(execution.getWorkerId())) {
            throw new HarnessConflictException("stale execution lease or fencing token: "
                    + execution.getExecutionId());
        }
        LeaseRecord lease = state.getLeases().get(leaseId);
        if (lease == null || lease.isExpired(HarnessSupport.now()) || lease.getFencingToken() != fencingToken
                || !workerId.equals(lease.getWorkerId())) {
            throw new HarnessConflictException("execution lease is expired or fenced: "
                    + execution.getExecutionId());
        }
    }

    void releaseSessionLease(HarnessState state,
                             ExecutionRecord execution,
                             String leaseId,
                             long fencingToken,
                             String workerId,
                             long releasedAt) {
        String sessionId = execution == null ? null : HarnessSupport.trimToNull(execution.getSessionId());
        if (sessionId == null) {
            return;
        }
        SessionLeaseRecord current = state.getSessionLeases().get(sessionId);
        if (current == null || !sameSessionLease(current, execution, leaseId, fencingToken, workerId)) {
            return;
        }
        current.setReleasedAtEpochMs(releasedAt);
        eventJournal.addEvent(state, "session.lease_released", sessionId,
                HarnessActor.worker(workerId == null ? "unknown" : workerId),
                HarnessSupport.mapOf("executionId", execution.getExecutionId(),
                        "leaseId", leaseId, "fencingToken", fencingToken));
    }

    void cancelOpenWaitsForExecution(HarnessState state,
                                     String executionId,
                                     String reason,
                                     HarnessActor actor,
                                     long currentTime) {
        for (WaitRecord wait : state.getWaits().values()) {
            if (wait == null || !HarnessSupport.safeEquals(executionId, wait.getExecutionId())
                    || !WaitStatus.OPEN.equals(wait.getStatus())) {
                continue;
            }
            wait.setStatus(WaitStatus.CANCELLED);
            wait.setResolvedAtEpochMs(currentTime);
            Map<String, Object> payload = HarnessSupport.copyMap(wait.getPayload());
            payload.put("cancellationReason", reason);
            payload.put("executionUnknown", true);
            wait.setPayload(payload);
            eventJournal.addEvent(state, "wait.cancelled", wait.getWaitId(), actor,
                    HarnessSupport.mapOf("executionId", executionId, "reason", reason));
        }
    }

    static boolean isTerminalExecution(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCEEDED || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.UNKNOWN || status == ExecutionStatus.CANCELLED;
    }

    private void acquireSessionLease(HarnessState state,
                                     ExecutionRecord execution,
                                     String workerId,
                                     String leaseId,
                                     long fencingToken,
                                     long acquiredAt,
                                     long expiresAt) {
        String sessionId = HarnessSupport.trimToNull(execution.getSessionId());
        if (sessionId == null) {
            return;
        }
        SessionLeaseRecord current = state.getSessionLeases().get(sessionId);
        if (current != null && !current.isExpired(acquiredAt)) {
            if (sameSessionLease(current, execution, leaseId, fencingToken, workerId)) {
                current.setExpiresAtEpochMs(Math.max(current.getExpiresAtEpochMs(), expiresAt));
                return;
            }
            throw new HarnessConflictException("session is already leased by execution "
                    + current.getExecutionId() + " for worker " + current.getWorkerId());
        }
        if (current != null) {
            ExecutionRecord previous = state.getExecutions().get(current.getExecutionId());
            if (previous != null && ExecutionStatus.RUNNING.equals(previous.getStatus())
                    && sameSessionLease(current, previous, current.getLeaseId(),
                    current.getFencingToken(), current.getWorkerId())) {
                markExecutionUnknown(state, previous, acquiredAt,
                        "session lease expired; side effects require reconciliation",
                        HarnessActor.worker(workerId));
            } else {
                current.setReleasedAtEpochMs(acquiredAt);
            }
        }
        SessionLeaseRecord replacement = SessionLeaseRecord.builder()
                .sessionId(sessionId)
                .executionId(execution.getExecutionId())
                .workerId(workerId)
                .leaseId(leaseId)
                .fencingToken(fencingToken)
                .acquiredAtEpochMs(acquiredAt)
                .expiresAtEpochMs(expiresAt)
                .build();
        state.getSessionLeases().put(sessionId, replacement);
        eventJournal.addEvent(state, "session.lease_acquired", sessionId, HarnessActor.worker(workerId),
                HarnessSupport.mapOf("executionId", execution.getExecutionId(),
                        "leaseId", leaseId, "fencingToken", fencingToken));
    }

    private boolean ensureSessionLeaseForRunning(HarnessState state,
                                                 ExecutionRecord execution,
                                                 LeaseRecord executionLease,
                                                 long currentTime) {
        String sessionId = HarnessSupport.trimToNull(execution.getSessionId());
        if (sessionId == null) {
            return true;
        }
        SessionLeaseRecord current = state.getSessionLeases().get(sessionId);
        if (current == null) {
            SessionLeaseRecord reconstructed = SessionLeaseRecord.builder()
                    .sessionId(sessionId)
                    .executionId(execution.getExecutionId())
                    .workerId(execution.getWorkerId())
                    .leaseId(executionLease.getLeaseId())
                    .fencingToken(executionLease.getFencingToken())
                    .acquiredAtEpochMs(executionLease.getAcquiredAtEpochMs())
                    .expiresAtEpochMs(executionLease.getExpiresAtEpochMs())
                    .build();
            state.getSessionLeases().put(sessionId, reconstructed);
            eventJournal.addEvent(state, "session.lease_reconstructed", sessionId,
                    HarnessActor.worker(execution.getWorkerId()),
                    HarnessSupport.mapOf("executionId", execution.getExecutionId(),
                            "leaseId", executionLease.getLeaseId()));
            return true;
        }
        if (current.isExpired(currentTime)) {
            markExecutionUnknown(state, execution, currentTime,
                    "session lease expired; side effects require reconciliation",
                    HarnessActor.worker(execution.getWorkerId()));
            return false;
        }
        if (!sameSessionLease(current, execution, executionLease.getLeaseId(),
                executionLease.getFencingToken(), execution.getWorkerId())) {
            throw new HarnessConflictException("session is owned by another execution: " + sessionId);
        }
        current.setExpiresAtEpochMs(Math.max(current.getExpiresAtEpochMs(),
                executionLease.getExpiresAtEpochMs()));
        return true;
    }

    private boolean refreshSessionLease(HarnessState state,
                                        ExecutionRecord execution,
                                        LeaseRecord executionLease,
                                        long currentTime) {
        String sessionId = HarnessSupport.trimToNull(execution.getSessionId());
        if (sessionId == null) {
            return true;
        }
        SessionLeaseRecord current = state.getSessionLeases().get(sessionId);
        if (current == null || current.isExpired(currentTime)
                || !sameSessionLease(current, execution, executionLease.getLeaseId(),
                executionLease.getFencingToken(), execution.getWorkerId())) {
            markExecutionUnknown(state, execution, currentTime,
                    "session lease was lost during heartbeat; side effects require reconciliation",
                    HarnessActor.worker(execution.getWorkerId()));
            return false;
        }
        current.setExpiresAtEpochMs(executionLease.getExpiresAtEpochMs());
        return true;
    }

    private boolean sameSessionLease(SessionLeaseRecord sessionLease,
                                     ExecutionRecord execution,
                                     String leaseId,
                                     long fencingToken,
                                     String workerId) {
        return sessionLease != null && execution != null
                && HarnessSupport.safeEquals(sessionLease.getSessionId(),
                HarnessSupport.trimToNull(execution.getSessionId()))
                && HarnessSupport.safeEquals(sessionLease.getExecutionId(), execution.getExecutionId())
                && HarnessSupport.safeEquals(sessionLease.getLeaseId(), leaseId)
                && sessionLease.getFencingToken() == fencingToken
                && HarnessSupport.safeEquals(sessionLease.getWorkerId(), workerId);
    }

    private void markExecutionUnknown(HarnessState state,
                                      ExecutionRecord execution,
                                      long currentTime,
                                      String reason,
                                      HarnessActor actor) {
        if (execution == null || ExecutionStatus.UNKNOWN.equals(execution.getStatus())) {
            return;
        }
        String executionId = execution.getExecutionId();
        String oldLeaseId = execution.getLeaseId();
        long oldFencingToken = execution.getFencingToken();
        String oldWorkerId = execution.getWorkerId();
        LeaseRecord lease = oldLeaseId == null ? null : state.getLeases().get(oldLeaseId);
        if (lease != null && lease.getReleasedAtEpochMs() <= 0L) {
            lease.setReleasedAtEpochMs(currentTime);
        }
        releaseSessionLease(state, execution, oldLeaseId, oldFencingToken, oldWorkerId, currentTime);
        cancelOpenWaitsForExecution(state, executionId, reason, actor, currentTime);
        markExecutionToolInvocationsUnknown(state, executionId, reason, actor, currentTime);
        execution.setStatus(ExecutionStatus.UNKNOWN);
        execution.setWaitId(null);
        execution.setOperationId(null);
        execution.setError(reason);
        execution.setWorkerId(null);
        execution.setLeaseId(null);
        execution.setFencingToken(0L);
        execution.setFinishedAtEpochMs(currentTime);
        execution.setUpdatedAtEpochMs(currentTime);
        execution.setVersion(execution.getVersion() + 1L);
        TaskRecord task = execution.getTaskId() == null
                ? null : state.getTasks().get(execution.getTaskId());
        if (task != null && !TaskStatus.DONE.equals(task.getStatus())
                && !TaskStatus.CANCELLED.equals(task.getStatus())
                && !TaskStatus.IN_REVIEW.equals(task.getStatus())) {
            task.setStatus(TaskStatus.BLOCKED);
            task.setBlockedReason(reason);
            task.setUpdatedAtEpochMs(currentTime);
            task.setVersion(task.getVersion() + 1L);
        }
        eventJournal.addEvent(state, "execution.unknown", executionId, actor,
                HarnessSupport.mapOf("taskId", execution.getTaskId(), "workerId", oldWorkerId,
                        "leaseId", oldLeaseId, "fencingToken", oldFencingToken, "reason", reason));
    }

    private void markExecutionToolInvocationsUnknown(HarnessState state,
                                                     String executionId,
                                                     String reason,
                                                     HarnessActor actor,
                                                     long currentTime) {
        for (ToolInvocationRecord invocation : state.getToolInvocations().values()) {
            if (invocation == null || !HarnessSupport.safeEquals(executionId, invocation.getExecutionId())
                    || (ToolInvocationStatus.STARTED != invocation.getStatus()
                    && ToolInvocationStatus.WAITING != invocation.getStatus())) {
                continue;
            }
            invocation.setStatus(ToolInvocationStatus.UNKNOWN);
            invocation.setError(reason);
            invocation.setUpdatedAtEpochMs(currentTime);
            invocation.setVersion(invocation.getVersion() + 1L);
            eventJournal.addEvent(state, "tool.invocation_unknown", invocation.getInvocationId(), actor,
                    HarnessSupport.mapOf("executionId", executionId, "waitId", invocation.getWaitId(),
                            "operationId", invocation.getOperationId(), "reason", reason));
        }
    }

    private long nextFencingToken(HarnessState state) {
        long max = 0L;
        for (LeaseRecord lease : state.getLeases().values()) {
            if (lease != null) {
                max = Math.max(max, lease.getFencingToken());
            }
        }
        return max + 1L;
    }
}
