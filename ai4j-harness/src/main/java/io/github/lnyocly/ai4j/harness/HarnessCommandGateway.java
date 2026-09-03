package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The only command surface that mutates a Harness ledger.
 *
 * <p>The gateway is deliberately domain neutral. Applications decide what a
 * task means, while this class owns durable task decomposition, dependency
 * safety, provenance, leases, waits, checkpoints and completion gates.</p>
 */
public final class HarnessCommandGateway implements AutoCloseable {

    private static final String IDEMPOTENCY_TASK = "task";
    private static final String IDEMPOTENCY_EXECUTION = "execution";

    private final HarnessStore store;
    private final HarnessContract contract;
    private final HarnessActor defaultActor;

    public HarnessCommandGateway(HarnessStore store) {
        this(store, HarnessContract.builder().build(), HarnessActor.agent("harness-agent"));
    }

    public HarnessCommandGateway(HarnessStore store,
                                 HarnessContract contract,
                                 HarnessActor defaultActor) {
        if (store == null) {
            throw new IllegalArgumentException("Harness store is required");
        }
        this.store = store;
        this.contract = contract == null ? HarnessContract.builder().build() : contract;
        this.defaultActor = normalizeActor(defaultActor, HarnessActor.agent("harness-agent"));
    }

    public HarnessStore getStore() {
        return store;
    }

    public HarnessContract getContract() {
        return contract;
    }

    public HarnessActor getDefaultActor() {
        return copyActor(defaultActor);
    }

    public HarnessState getState() {
        HarnessState state = store.load();
        if (state == null) {
            return HarnessState.empty("default");
        }
        state.ensureCollections();
        return state.copy();
    }

    public TaskRecord getTask(String taskId) {
        TaskRecord task = getState().getTasks().get(taskId);
        return task == null ? null : task.copy();
    }

    /** Returns a Task only when it belongs to the caller's opaque scope. */
    public TaskRecord getTaskInScope(String taskId, String scopeKey) {
        TaskRecord task = getState().getTasks().get(taskId);
        return task != null && visibleScope(task.getScopeKey(), scopeKey)
                ? task.copy() : null;
    }

    public List<TaskRecord> listTasks() {
        return listTasks(null);
    }

    public List<TaskRecord> listTasks(String scopeKey) {
        List<TaskRecord> result = new ArrayList<TaskRecord>();
        for (TaskRecord task : getState().getTasks().values()) {
            if (task != null && visibleScope(task.getScopeKey(), scopeKey)) {
                result.add(task.copy());
            }
        }
        return result;
    }

    public List<TaskRecord> listRunnableTasks() {
        return listRunnableTasks(null);
    }

    public List<TaskRecord> listRunnableTasks(String scopeKey) {
        HarnessState state = getState();
        List<TaskRecord> result = new ArrayList<TaskRecord>();
        for (TaskRecord task : state.getTasks().values()) {
            if (task == null || !isRunnableStatus(task.getStatus())
                    || !visibleScope(task.getScopeKey(), scopeKey)
                    || !dependenciesSatisfied(state, task.getTaskId())
                    || hasOutstandingExecution(state, task.getTaskId())) {
                continue;
            }
            result.add(task.copy());
        }
        return result;
    }

    public ExecutionRecord getExecution(String executionId) {
        ExecutionRecord execution = getState().getExecutions().get(executionId);
        return execution == null ? null : execution.copy();
    }

    public List<ExecutionRecord> listExecutions() {
        List<ExecutionRecord> result = new ArrayList<ExecutionRecord>();
        for (ExecutionRecord execution : getState().getExecutions().values()) {
            if (execution != null) {
                result.add(execution.copy());
            }
        }
        return result;
    }

    public WaitRecord getWait(String waitId) {
        WaitRecord wait = getState().getWaits().get(waitId);
        return wait == null ? null : wait.copy();
    }

    /**
     * Returns every unresolved wait belonging to one Execution in creation
     * order.  Execution.waitId is only a representative id; callers that need
     * to deliver multiple parallel operations must use this view instead of
     * assuming that an Execution has at most one wait.
     */
    public List<WaitRecord> listOpenWaits(String executionId) {
        String id = trimToNull(executionId);
        if (id == null) {
            return Collections.emptyList();
        }
        List<WaitRecord> result = new ArrayList<WaitRecord>();
        for (WaitRecord wait : openWaits(getState(), id)) {
            result.add(wait.copy());
        }
        return result;
    }

    public AgentSessionSnapshot getSessionSnapshot(String sessionId) {
        String id = trimToNull(sessionId);
        if (id == null) {
            return null;
        }
        AgentSessionSnapshot snapshot = getState().getSessions().get(id);
        return snapshot == null ? null : HarnessJson.copy(snapshot, AgentSessionSnapshot.class);
    }

    public CheckpointRecord getCheckpoint(String checkpointId) {
        String id = trimToNull(checkpointId);
        if (id == null) {
            return null;
        }
        CheckpointRecord checkpoint = getState().getCheckpoints().get(id);
        return checkpoint == null ? null : checkpoint.copy();
    }

    public WakeupRecord getLatestWakeup(String executionId) {
        String id = trimToNull(executionId);
        if (id == null) {
            return null;
        }
        WakeupRecord latest = null;
        for (WakeupRecord wakeup : getState().getWakeups().values()) {
            if (wakeup == null || !id.equals(wakeup.getExecutionId())) {
                continue;
            }
            if (latest == null || wakeup.getDeliveredAtEpochMs() > latest.getDeliveredAtEpochMs()) {
                latest = wakeup;
            }
        }
        return latest == null ? null : latest.copy();
    }

    /** Records a durable observation without exposing the store to callers. */
    public HarnessEventRecord recordEvent(String type,
                                          String entityId,
                                          Map<String, Object> payload,
                                          HarnessActor actor) {
        final String eventType = requireText(type, "event type");
        final String eventEntityId = trimToNull(entityId);
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<HarnessEventRecord>() {
            @Override
            public HarnessEventRecord apply(HarnessState state) {
                HarnessEventRecord event = HarnessEventRecord.builder()
                        .type(eventType)
                        .entityId(eventEntityId)
                        .actorId(actorKey(effectiveActor))
                        .recordedAtEpochMs(now())
                        .payload(copyMap(payload))
                        .build();
                List<HarnessEventRecord> events = state.getEvents();
                long sequence = events == null || events.isEmpty()
                        ? 1L : events.get(events.size() - 1).getSequence() + 1L;
                event.setSequence(sequence);
                if (events == null) {
                    events = new ArrayList<HarnessEventRecord>();
                    state.setEvents(events);
                }
                events.add(event);
                return event.copy();
            }
        });
    }

    public List<WaitRecord> listOpenWaits() {
        return listOpenWaitsInScope(null);
    }

    public List<WaitRecord> listOpenWaitsInScope(String scopeKey) {
        HarnessState state = getState();
        List<WaitRecord> result = new ArrayList<WaitRecord>();
        for (WaitRecord wait : state.getWaits().values()) {
            ExecutionRecord execution = wait == null ? null : state.getExecutions().get(wait.getExecutionId());
            if (wait != null && WaitStatus.OPEN.equals(wait.getStatus())
                    && execution != null && visibleScope(execution.getScopeKey(), scopeKey)) {
                result.add(wait.copy());
            }
        }
        return result;
    }

    public List<FactRecord> listFactsInScope(String scopeKey) {
        List<FactRecord> result = new ArrayList<FactRecord>();
        for (FactRecord fact : getState().getFacts().values()) {
            if (fact != null && visibleScope(fact.getScopeKey(), scopeKey)) {
                result.add(fact.copy());
            }
        }
        return result;
    }

    public List<DecisionRecord> listDecisionsInScope(String scopeKey) {
        List<DecisionRecord> result = new ArrayList<DecisionRecord>();
        for (DecisionRecord decision : getState().getDecisions().values()) {
            if (decision != null && visibleScope(decision.getScopeKey(), scopeKey)) {
                result.add(decision.copy());
            }
        }
        return result;
    }

    public List<EvidenceRecord> listEvidenceInScope(String scopeKey) {
        List<EvidenceRecord> result = new ArrayList<EvidenceRecord>();
        for (EvidenceRecord item : getState().getEvidence().values()) {
            if (item != null && visibleScope(item.getScopeKey(), scopeKey)) {
                result.add(item.copy());
            }
        }
        return result;
    }

    public List<RelationRecord> listRelations() {
        return listRelationsInScope(null);
    }

    public List<RelationRecord> listRelationsInScope(String scopeKey) {
        List<RelationRecord> result = new ArrayList<RelationRecord>();
        for (RelationRecord relation : getState().getRelations().values()) {
            if (relation != null && visibleRelationScope(relation, scopeKey)) {
                result.add(relation.copy());
            }
        }
        return result;
    }

    public RelationRecord getRelation(String relationId) {
        RelationRecord relation = getState().getRelations().get(relationId);
        return relation == null ? null : relation.copy();
    }

    /** Returns a Relation only when it belongs to the caller's opaque scope. */
    public RelationRecord getRelationInScope(String relationId, String scopeKey) {
        RelationRecord relation = getRelation(relationId);
        return relation != null && visibleRelationScope(relation, scopeKey)
                ? relation : null;
    }

    public ToolInvocationRecord getToolInvocation(String invocationId) {
        ToolInvocationRecord invocation = getState().getToolInvocations().get(invocationId);
        return invocation == null ? null : invocation.copy();
    }

    public ToolInvocationRecord getToolInvocationInScope(String invocationId, String scopeKey) {
        ToolInvocationRecord invocation = getToolInvocation(invocationId);
        return invocation != null && visibleScope(invocation.getScopeKey(), scopeKey)
                ? invocation : null;
    }

    public List<ToolInvocationRecord> listToolInvocationsInScope(String scopeKey) {
        List<ToolInvocationRecord> result = new ArrayList<ToolInvocationRecord>();
        for (ToolInvocationRecord invocation : getState().getToolInvocations().values()) {
            if (invocation != null && visibleScope(invocation.getScopeKey(), scopeKey)) {
                result.add(invocation.copy());
            }
        }
        return result;
    }

    /**
     * Finds the sole approved invocation waiting for a provider retry whose
     * call id may have changed across Agent sessions. Ambiguous matches are
     * deliberately rejected so two identical parallel calls are never
     * rebound to the wrong side effect.
     */
    ToolInvocationRecord findApprovedWaitingToolInvocation(String executionId,
                                                           String scopeKey,
                                                           String toolName,
                                                           String callId,
                                                           String arguments) {
        HarnessState state = getState();
        ToolInvocationRecord match = null;
        for (ToolInvocationRecord invocation : state.getToolInvocations().values()) {
            if (invocation == null || !ToolInvocationStatus.WAITING.equals(invocation.getStatus())
                    || !safeEquals(executionId, invocation.getExecutionId())
                    || !visibleScope(invocation.getScopeKey(), scopeKey)
                    || !safeEquals(toolName, invocation.getToolName())
                    || !equivalentNullableArguments(invocation.getArguments(), arguments)
                    || !approvedInvocationRetry(state, invocation, toolName, callId, arguments)) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = invocation;
        }
        return match == null ? null : match.copy();
    }

    public ToolInvocationRecord beginToolInvocation(HarnessToolInvocationSpec spec) {
        return beginToolInvocation(spec, defaultActor);
    }

    /** Records STARTED before a business tool is allowed to perform a side effect. */
    public ToolInvocationRecord beginToolInvocation(HarnessToolInvocationSpec spec,
                                                    HarnessActor actor) {
        return reserveToolInvocation(spec, actor).getInvocation();
    }

    /**
     * Atomically records or observes a STARTED invocation.
     *
     * <p>The boolean is part of the command result because a read followed by
     * {@link #beginToolInvocation} cannot distinguish the creator from a
     * concurrent observer. Callers that may perform an external side effect
     * must execute only when {@link ToolInvocationReservation#isCreated()} is
     * {@code true}.</p>
     */
    public ToolInvocationReservation reserveToolInvocation(HarnessToolInvocationSpec spec) {
        return reserveToolInvocation(spec, defaultActor);
    }

    /** Atomically records or observes a STARTED invocation with actor attribution. */
    public ToolInvocationReservation reserveToolInvocation(HarnessToolInvocationSpec spec,
                                                            HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("tool invocation specification is required");
        }
        final String invocationId = requireText(spec.getInvocationId(), "tool invocation id");
        final String executionId = requireText(spec.getExecutionId(), "execution id");
        final String toolName = requireText(spec.getToolName(), "tool name");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<ToolInvocationReservation>() {
            @Override
            public ToolInvocationReservation apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionId);
                String invocationScope = normalizeScope(spec.getScopeKey());
                assertCompatibleScope(invocationScope, execution.getScopeKey(),
                        "tool invocation and execution scopes must match");
                if (invocationScope == null) {
                    invocationScope = execution.getScopeKey();
                }
                if (spec.getTaskId() != null && !safeEquals(spec.getTaskId(), execution.getTaskId())) {
                    throw new HarnessConflictException("tool invocation task does not belong to execution: "
                            + invocationId);
                }
                if (spec.getSessionId() != null && !safeEquals(spec.getSessionId(), execution.getSessionId())) {
                    throw new HarnessConflictException("tool invocation session does not belong to execution: "
                            + invocationId);
                }
                ToolInvocationRecord existing = state.getToolInvocations().get(invocationId);
                if (existing != null) {
                    if (ToolInvocationStatus.WAITING.equals(existing.getStatus())
                            && approvedInvocationRetry(state, existing, spec)) {
                        assertSameToolInvocationForApprovedRetry(existing, spec, invocationScope);
                        existing.setStatus(ToolInvocationStatus.STARTED);
                        existing.setOperationId(null);
                        existing.setWaitId(null);
                        existing.setOutput(null);
                        existing.setError(null);
                        existing.setUpdatedAtEpochMs(now());
                        existing.setVersion(existing.getVersion() + 1L);
                        addEvent(state, "tool.invocation_restarted_after_approval", invocationId,
                                effectiveActor, mapOf("executionId", executionId,
                                        "toolName", toolName, "callId", spec.getCallId()));
                        return ToolInvocationReservation.builder()
                                .invocation(existing.copy())
                                .created(true)
                                .build();
                    }
                    assertSameToolInvocation(existing, spec, invocationScope);
                    return ToolInvocationReservation.builder()
                            .invocation(existing.copy())
                            .created(false)
                            .build();
                }
                long currentTime = now();
                ToolInvocationRecord invocation = ToolInvocationRecord.builder()
                        .invocationId(invocationId)
                        .executionId(executionId)
                        .taskId(spec.getTaskId() == null ? execution.getTaskId() : spec.getTaskId())
                        .sessionId(spec.getSessionId() == null ? execution.getSessionId() : spec.getSessionId())
                        .scopeKey(invocationScope)
                        .toolName(toolName)
                        .callId(spec.getCallId())
                        .arguments(spec.getArguments())
                        .status(ToolInvocationStatus.STARTED)
                        .createdBy(actorKey(effectiveActor))
                        .createdAtEpochMs(currentTime)
                        .updatedAtEpochMs(currentTime)
                        .version(1L)
                        .build();
                state.getToolInvocations().put(invocationId, invocation);
                addEvent(state, "tool.invocation_started", invocationId, effectiveActor,
                        mapOf("executionId", executionId, "taskId", invocation.getTaskId(),
                                "toolName", toolName, "callId", spec.getCallId()));
                return ToolInvocationReservation.builder()
                        .invocation(invocation.copy())
                        .created(true)
                        .build();
            }
        });
    }

    public ToolInvocationRecord markToolInvocationWaiting(String invocationId,
                                                           String operationId,
                                                           String waitId) {
        return markToolInvocationWaiting(invocationId, operationId, waitId, defaultActor);
    }

    public ToolInvocationRecord markToolInvocationWaiting(String invocationId,
                                                           String operationId,
                                                           String waitId,
                                                           HarnessActor actor) {
        final String id = requireText(invocationId, "tool invocation id");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<ToolInvocationRecord>() {
            @Override
            public ToolInvocationRecord apply(HarnessState state) {
                ToolInvocationRecord invocation = state.getToolInvocations().get(id);
                if (invocation == null) {
                    throw new HarnessValidationException("tool invocation not found: " + id);
                }
                if (ToolInvocationStatus.CANCELLED.equals(invocation.getStatus())
                        || ToolInvocationStatus.SUCCEEDED.equals(invocation.getStatus())
                        || ToolInvocationStatus.FAILED.equals(invocation.getStatus())
                        || ToolInvocationStatus.UNKNOWN.equals(invocation.getStatus())) {
                    return invocation.copy();
                }
                if (ToolInvocationStatus.WAITING.equals(invocation.getStatus())) {
                    if (operationId != null && invocation.getOperationId() != null
                            && !safeEquals(operationId, invocation.getOperationId())) {
                        throw new HarnessConflictException("tool invocation operation id changed: " + id);
                    }
                    if (waitId != null && invocation.getWaitId() != null
                            && !safeEquals(waitId, invocation.getWaitId())) {
                        throw new HarnessConflictException("tool invocation wait id changed: " + id);
                    }
                    return invocation.copy();
                }
                if (!ToolInvocationStatus.STARTED.equals(invocation.getStatus())) {
                    throw new HarnessConflictException("tool invocation cannot wait from status "
                            + invocation.getStatus() + ": " + id);
                }
                invocation.setStatus(ToolInvocationStatus.WAITING);
                invocation.setOperationId(operationId);
                invocation.setWaitId(waitId);
                invocation.setUpdatedAtEpochMs(now());
                invocation.setVersion(invocation.getVersion() + 1L);
                addEvent(state, "tool.invocation_waiting", id, effectiveActor,
                        mapOf("operationId", operationId, "waitId", waitId));
                return invocation.copy();
            }
        });
    }

    public ToolInvocationRecord completeToolInvocation(String invocationId,
                                                        ToolInvocationStatus status,
                                                        String operationId,
                                                        String waitId,
                                                        String output,
                                                        String error) {
        return completeToolInvocation(invocationId, status, operationId, waitId,
                output, error, defaultActor);
    }

    public ToolInvocationRecord completeToolInvocation(String invocationId,
                                                        ToolInvocationStatus status,
                                                        String operationId,
                                                        String waitId,
                                                        String output,
                                                        String error,
                                                        HarnessActor actor) {
        final String id = requireText(invocationId, "tool invocation id");
        if (status == null || ToolInvocationStatus.STARTED.equals(status)
                || ToolInvocationStatus.WAITING.equals(status)) {
            throw new HarnessValidationException("tool invocation completion must be terminal");
        }
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<ToolInvocationRecord>() {
            @Override
            public ToolInvocationRecord apply(HarnessState state) {
                ToolInvocationRecord invocation = state.getToolInvocations().get(id);
                if (invocation == null) {
                    throw new HarnessValidationException("tool invocation not found: " + id);
                }
                if (ToolInvocationStatus.CANCELLED.equals(invocation.getStatus())) {
                    recordQuarantinedCompletion(state, invocation, status, operationId, waitId,
                            output, error, effectiveActor);
                    return invocation.copy();
                }
                if (ToolInvocationStatus.SUCCEEDED.equals(invocation.getStatus())
                        || ToolInvocationStatus.FAILED.equals(invocation.getStatus())
                        || ToolInvocationStatus.UNKNOWN.equals(invocation.getStatus())) {
                    return invocation.copy();
                }
                invocation.setStatus(status);
                if (operationId != null) invocation.setOperationId(operationId);
                if (waitId != null) invocation.setWaitId(waitId);
                invocation.setOutput(output);
                invocation.setError(error);
                invocation.setUpdatedAtEpochMs(now());
                invocation.setVersion(invocation.getVersion() + 1L);
                addEvent(state, "tool.invocation_" + status.name().toLowerCase(), id,
                        effectiveActor, mapOf("operationId", operationId, "waitId", waitId,
                                "error", error));
                return invocation.copy();
            }
        });
    }

    public ToolInvocationRecord reconcileToolInvocation(String invocationId,
                                                         ToolInvocationStatus resolution,
                                                         String output,
                                                         String error,
                                                         HarnessActor actor) {
        final String id = requireText(invocationId, "tool invocation id");
        if (resolution == null || ToolInvocationStatus.STARTED.equals(resolution)
                || ToolInvocationStatus.WAITING.equals(resolution)) {
            throw new HarnessValidationException("tool invocation reconciliation must be terminal");
        }
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<ToolInvocationRecord>() {
            @Override
            public ToolInvocationRecord apply(HarnessState state) {
                ToolInvocationRecord invocation = state.getToolInvocations().get(id);
                if (invocation == null) {
                    throw new HarnessValidationException("tool invocation not found: " + id);
                }
                if (!contract.mayReconcileToolInvocation(effectiveActor, invocation, resolution)) {
                    throw new HarnessValidationException(
                            "actor is not allowed to reconcile this tool invocation");
                }
                if (ToolInvocationStatus.CANCELLED.equals(invocation.getStatus())
                        || ToolInvocationStatus.SUCCEEDED.equals(invocation.getStatus())
                        || ToolInvocationStatus.FAILED.equals(invocation.getStatus())) {
                    throw new HarnessConflictException("tool invocation is already terminal: " + id);
                }
                invocation.setStatus(resolution);
                invocation.setOutput(output);
                invocation.setError(error);
                invocation.setUpdatedAtEpochMs(now());
                invocation.setVersion(invocation.getVersion() + 1L);
                addEvent(state, "tool.invocation_reconciled", id, effectiveActor,
                        mapOf("resolution", resolution, "error", error));
                return invocation.copy();
            }
        });
    }

    public TaskRecord createTask(HarnessTaskSpec spec) {
        return createTask(spec, defaultActor);
    }

    public TaskRecord createTask(HarnessTaskSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("task specification is required");
        }
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        final String taskId = valueOrGenerated(spec.getTaskId(), "task_");
        return write(new StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                String taskScope = taskSpecScope(state, spec);
                HarnessTaskSpec effectiveSpec = withScope(spec, taskScope);
                String existingId = idempotentId(state, IDEMPOTENCY_TASK, taskScope,
                        spec.getIdempotencyKey());
                if (existingId != null) {
                    TaskRecord existing = state.getTasks().get(existingId);
                    if (existing != null) {
                        return existing.copy();
                    }
                }
                TaskRecord task = createTaskInternal(state, effectiveSpec, taskId, effectiveActor);
                rememberIdempotency(state, IDEMPOTENCY_TASK, taskScope,
                        spec.getIdempotencyKey(), task.getTaskId());
                return task.copy();
            }
        });
    }

    /**
     * Creates a Task and attaches an unbound Execution in one durable state
     * mutation. This is used when an Agent discovers its first unit of work at
     * runtime; a failed attachment must not leave an orphan Task behind.
     */
    public TaskRecord createTaskAndAttachExecution(HarnessTaskSpec spec,
                                                    String executionId,
                                                    HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("task specification is required");
        }
        final String executionKey = requireText(executionId, "execution id");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        final String taskId = valueOrGenerated(spec.getTaskId(), "task_");
        return write(new StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionKey);
                String taskScope = taskSpecScope(state, spec);
                assertCompatibleScope(taskScope, execution.getScopeKey(),
                        "task and execution scopes must match");
                if (taskScope == null) {
                    taskScope = normalizeScope(execution.getScopeKey());
                }
                HarnessTaskSpec effectiveSpec = withScope(spec, taskScope);
                String existingId = idempotentId(state, IDEMPOTENCY_TASK, taskScope,
                        spec.getIdempotencyKey());
                TaskRecord task = existingId == null ? null : state.getTasks().get(existingId);
                if (task == null) {
                    task = createTaskInternal(state, effectiveSpec, taskId, effectiveActor);
                    rememberIdempotency(state, IDEMPOTENCY_TASK, taskScope,
                            spec.getIdempotencyKey(), task.getTaskId());
                }
                attachExecutionToTaskInternal(state, executionKey, task.getTaskId(), effectiveActor);
                return task.copy();
            }
        });
    }

    public List<TaskRecord> splitTask(String parentTaskId, List<HarnessTaskSpec> children) {
        return splitTask(parentTaskId, children, defaultActor);
    }

    public List<TaskRecord> splitTask(String parentTaskId,
                                      List<HarnessTaskSpec> children,
                                      HarnessActor actor) {
        final String parentId = requireText(parentTaskId, "parent task id");
        final List<HarnessTaskSpec> requested = children == null
                ? Collections.<HarnessTaskSpec>emptyList() : new ArrayList<HarnessTaskSpec>(children);
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<List<TaskRecord>>() {
            @Override
            public List<TaskRecord> apply(HarnessState state) {
                TaskRecord parent = requireTask(state, parentId);
                if (TaskStatus.DONE.equals(parent.getStatus()) || TaskStatus.CANCELLED.equals(parent.getStatus())) {
                    throw new HarnessValidationException("cannot split a terminal task: " + parentId);
                }
                List<TaskRecord> result = new ArrayList<TaskRecord>();
                for (HarnessTaskSpec childSpec : requested) {
                    if (childSpec == null) {
                        continue;
                    }
                    assertCompatibleScope(childSpec.getScopeKey(), parent.getScopeKey(),
                            "child task and parent task scopes must match");
                    HarnessTaskSpec effectiveSpec = childSpec.toBuilder()
                            .parentTaskId(parentId)
                            .scopeKey(childSpec.getScopeKey() == null
                                    ? parent.getScopeKey() : childSpec.getScopeKey())
                            .build();
                    String childId = valueOrGenerated(effectiveSpec.getTaskId(), "task_");
                    String childScope = normalizeScope(effectiveSpec.getScopeKey());
                    String existingId = idempotentId(state, IDEMPOTENCY_TASK, childScope,
                            effectiveSpec.getIdempotencyKey());
                    TaskRecord child = existingId == null ? null : state.getTasks().get(existingId);
                    if (child == null) {
                        child = createTaskInternal(state, effectiveSpec, childId, effectiveActor);
                        rememberIdempotency(state, IDEMPOTENCY_TASK, childScope,
                                effectiveSpec.getIdempotencyKey(), child.getTaskId());
                    }
                    result.add(child.copy());
                }
                return result;
            }
        });
    }

    public TaskRecord updateTask(String taskId,
                                 String title,
                                 String goal,
                                 String plan,
                                 Map<String, Object> metadata) {
        return updateTask(taskId, title, goal, plan, metadata, defaultActor);
    }

    public TaskRecord updateTask(String taskId,
                                 String title,
                                 String goal,
                                 String plan,
                                 Map<String, Object> metadata,
                                 HarnessActor actor) {
        final String id = requireText(taskId, "task id");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                TaskRecord task = requireTask(state, id);
                if (title != null && !title.trim().isEmpty()) task.setTitle(title.trim());
                if (goal != null) task.setGoal(goal);
                if (plan != null) task.setPlan(plan);
                if (metadata != null) task.setMetadata(new LinkedHashMap<String, Object>(metadata));
                task.setUpdatedAtEpochMs(now());
                task.setVersion(task.getVersion() + 1L);
                addEvent(state, "task.updated", id, effectiveActor, mapOf(
                        "title", task.getTitle(), "goal", task.getGoal(), "plan", task.getPlan()));
                return task.copy();
            }
        });
    }

    public TaskRecord transitionTask(String taskId, TaskStatus target, String reason) {
        return transitionTask(taskId, target, reason, defaultActor);
    }

    public TaskRecord transitionTask(String taskId,
                                     TaskStatus target,
                                     String reason,
                                     HarnessActor actor) {
        final String id = requireText(taskId, "task id");
        if (target == null) {
            throw new HarnessValidationException("task target status is required");
        }
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        final String transitionReason = reason;
        return write(new StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                TaskRecord task = requireTask(state, id);
                requireTaskTransition(task.getStatus(), target);
                if (TaskStatus.DONE.equals(target)) {
                    throw new HarnessValidationException("use completeTask for DONE transitions");
                }
                if (TaskStatus.IN_REVIEW.equals(target) && effectiveActor.isAgent()) {
                    throw new HarnessValidationException("an Agent must submit a task for review; it cannot enter review directly");
                }
                if (TaskStatus.ACTIVE.equals(target) && !dependenciesSatisfied(state, id)) {
                    throw new HarnessConflictException("task dependencies are not complete: " + id);
                }
                TaskStatus previousStatus = task.getStatus();
                task.setStatus(target);
                task.setBlockedReason(TaskStatus.BLOCKED.equals(target) ? transitionReason : null);
                task.setUpdatedAtEpochMs(now());
                task.setVersion(task.getVersion() + 1L);
                if (TaskStatus.CANCELLED.equals(target)) {
                    cancelTaskActivity(state, id, transitionReason, effectiveActor);
                }
                addEvent(state, "task.transitioned", id, effectiveActor, mapOf(
                        "from", previousStatus, "to", target, "reason", transitionReason));
                return task.copy();
            }
        });
    }

    public RelationRecord addRelation(HarnessRelationSpec spec) {
        return addRelation(spec, defaultActor);
    }

    public RelationRecord addRelation(HarnessRelationSpec spec, HarnessActor actor) {
        if (spec == null || spec.getType() == null) {
            throw new HarnessValidationException("relation type is required");
        }
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<RelationRecord>() {
            @Override
            public RelationRecord apply(HarnessState state) {
                validateRelationSpec(state, spec);
                RelationRecord existing = findRelation(state, spec);
                if (existing != null) {
                    return existing.copy();
                }
                if (isGraphRelation(spec.getType()) && wouldCreateCycle(state,
                        spec.getType(), spec.getFromId(), spec.getToId())) {
                    throw new HarnessConflictException("relation would create a cycle: "
                            + spec.getFromId() + " -> " + spec.getToId());
                }
                String relationScope = resolveRelationScope(state, spec);
                String id = valueOrGenerated(null, "rel_");
                RelationRecord relation = RelationRecord.builder()
                        .relationId(id)
                        .scopeKey(relationScope)
                        .type(spec.getType())
                        .fromKind(spec.getFromKind())
                        .fromId(spec.getFromId())
                        .toKind(spec.getToKind())
                        .toId(spec.getToId())
                        .createdBy(actorKey(effectiveActor))
                        .createdAtEpochMs(now())
                        .metadata(copyMap(spec.getMetadata()))
                        .build();
                state.getRelations().put(id, relation);
                addEvent(state, "relation.created", id, effectiveActor, mapOf(
                        "type", relation.getType(), "from", relation.getFromId(), "to", relation.getToId()));
                return relation.copy();
            }
        });
    }

    public RelationRecord addDependency(String taskId, String dependencyTaskId) {
        return addDependency(taskId, dependencyTaskId, defaultActor);
    }

    public RelationRecord addDependency(String taskId,
                                       String dependencyTaskId,
                                       HarnessActor actor) {
        return addRelation(HarnessRelationSpec.builder()
                .type(RelationType.DEPENDS_ON)
                .fromKind(EntityKind.TASK)
                .fromId(taskId)
                .toKind(EntityKind.TASK)
                .toId(dependencyTaskId)
                .build(), actor);
    }

    public FactRecord recordFact(HarnessFactSpec spec) {
        return recordFact(spec, defaultActor);
    }

    public FactRecord recordFact(HarnessFactSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("fact specification is required");
        }
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent() && !contract.acceptsAgentFact(spec)) {
            throw new HarnessValidationException("the Harness contract rejected this Agent fact");
        }
        final String factId = valueOrGenerated(spec.getFactId(), "fact_");
        return write(new StateCommand<FactRecord>() {
            @Override
            public FactRecord apply(HarnessState state) {
                if (state.getFacts().containsKey(factId)) {
                    throw new HarnessConflictException("fact already exists: " + factId);
                }
                String factTaskId = trimToNull(spec.getTaskId());
                String factScope = relatedScope(state, spec.getScopeKey(),
                        factTaskId, EntityKind.TASK,
                        "fact and task scopes must match");
                factScope = resolveEvidenceReferenceScope(state, spec.getEvidenceIds(),
                        factScope, factTaskId != null,
                        "fact and evidence scopes must match");
                FactRecord fact = FactRecord.builder()
                        .factId(factId)
                        .scopeKey(factScope)
                        .taskId(factTaskId)
                        .statement(requireText(spec.getStatement(), "fact statement"))
                        .source(spec.getSource())
                        .confidence(spec.getConfidence())
                        .valid(true)
                        .createdAtEpochMs(now())
                        .provenance(provenance(effectiveActor, null, null, "fact", factId))
                        .evidenceIds(copyList(spec.getEvidenceIds()))
                        .metadata(copyMap(spec.getMetadata()))
                        .build();
                state.getFacts().put(factId, fact);
                addEvent(state, "fact.recorded", factId, effectiveActor, mapOf(
                        "taskId", fact.getTaskId(), "statement", fact.getStatement()));
                return fact.copy();
            }
        });
    }

    public FactRecord invalidateFact(String factId, String reason) {
        return invalidateFact(factId, reason, defaultActor);
    }

    public FactRecord invalidateFact(String factId, String reason, HarnessActor actor) {
        final String id = requireText(factId, "fact id");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<FactRecord>() {
            @Override
            public FactRecord apply(HarnessState state) {
                FactRecord fact = state.getFacts().get(id);
                if (fact == null) throw new HarnessValidationException("fact not found: " + id);
                fact.setValid(false);
                fact.setInvalidatedBy(actorKey(effectiveActor) + (reason == null ? "" : ": " + reason));
                fact.setInvalidatedAtEpochMs(now());
                addEvent(state, "fact.invalidated", id, effectiveActor, mapOf("reason", reason));
                return fact.copy();
            }
        });
    }

    public DecisionRecord proposeDecision(HarnessDecisionSpec spec) {
        return proposeDecision(spec, defaultActor);
    }

    public DecisionRecord proposeDecision(HarnessDecisionSpec spec, HarnessActor actor) {
        if (spec == null) throw new HarnessValidationException("decision specification is required");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        final String decisionId = valueOrGenerated(spec.getDecisionId(), "decision_");
        return write(new StateCommand<DecisionRecord>() {
            @Override
            public DecisionRecord apply(HarnessState state) {
                if (state.getDecisions().containsKey(decisionId)) {
                    throw new HarnessConflictException("decision already exists: " + decisionId);
                }
                String decisionTaskId = trimToNull(spec.getTaskId());
                String decisionScope = relatedScope(state, spec.getScopeKey(),
                        decisionTaskId, EntityKind.TASK,
                        "decision and task scopes must match");
                decisionScope = resolveFactReferenceScope(state, spec.getFactIds(),
                        decisionScope, decisionTaskId != null,
                        "decision and fact scopes must match");
                decisionScope = resolveEvidenceReferenceScope(state, spec.getEvidenceIds(),
                        decisionScope, decisionTaskId != null,
                        "decision and evidence scopes must match");
                DecisionRecord decision = DecisionRecord.builder()
                        .decisionId(decisionId)
                        .scopeKey(decisionScope)
                        .taskId(decisionTaskId)
                        .question(requireText(spec.getQuestion(), "decision question"))
                        .chosenOption(spec.getChosenOption())
                        .rationale(spec.getRationale())
                        .status(DecisionStatus.PROPOSED)
                        .proposer(copyActor(effectiveActor))
                        .createdAtEpochMs(now())
                        .factIds(copyList(spec.getFactIds()))
                        .evidenceIds(copyList(spec.getEvidenceIds()))
                        .build();
                state.getDecisions().put(decisionId, decision);
                addEvent(state, "decision.proposed", decisionId, effectiveActor, mapOf(
                        "taskId", decision.getTaskId(), "question", decision.getQuestion()));
                return decision.copy();
            }
        });
    }

    public DecisionRecord resolveDecision(String decisionId,
                                          DecisionStatus status,
                                          String rationale,
                                          HarnessActor actor) {
        final String id = requireText(decisionId, "decision id");
        if (status != DecisionStatus.ACCEPTED && status != DecisionStatus.REJECTED) {
            throw new HarnessValidationException("decision resolution must be ACCEPTED or REJECTED");
        }
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent()) {
            throw new HarnessValidationException("an Agent may propose a decision but cannot resolve it");
        }
        return write(new StateCommand<DecisionRecord>() {
            @Override
            public DecisionRecord apply(HarnessState state) {
                DecisionRecord decision = state.getDecisions().get(id);
                if (decision == null) throw new HarnessValidationException("decision not found: " + id);
                decision.setStatus(status);
                decision.setRationale(rationale == null ? decision.getRationale() : rationale);
                decision.setArbiter(copyActor(effectiveActor));
                decision.setResolvedAtEpochMs(now());
                addEvent(state, "decision.resolved", id, effectiveActor, mapOf("status", status));
                return decision.copy();
            }
        });
    }

    public EvidenceRecord recordEvidence(HarnessEvidenceSpec spec) {
        return recordEvidence(spec, defaultActor);
    }

    public EvidenceRecord recordEvidence(HarnessEvidenceSpec spec, HarnessActor actor) {
        if (spec == null) throw new HarnessValidationException("evidence specification is required");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        final String evidenceId = valueOrGenerated(spec.getEvidenceId(), "evidence_");
        return write(new StateCommand<EvidenceRecord>() {
            @Override
            public EvidenceRecord apply(HarnessState state) {
                if (state.getEvidence().containsKey(evidenceId)) {
                    throw new HarnessConflictException("evidence already exists: " + evidenceId);
                }
                String evidenceTaskId = trimToNull(spec.getTaskId());
                String evidenceExecutionId = trimToNull(spec.getExecutionId());
                String evidenceScope = relatedScope(state, spec.getScopeKey(),
                        evidenceTaskId, EntityKind.TASK,
                        "evidence and task scopes must match");
                if (evidenceExecutionId != null) {
                    ExecutionRecord execution = state.getExecutions().get(evidenceExecutionId);
                    if (execution == null) {
                        throw new HarnessValidationException("execution not found: " + evidenceExecutionId);
                    }
                    String executionTaskId = trimToNull(execution.getTaskId());
                    if (evidenceTaskId != null && !evidenceTaskId.equals(executionTaskId)) {
                        throw new HarnessConflictException("evidence task and execution tasks must match");
                    }
                    if (evidenceTaskId == null && executionTaskId != null) {
                        evidenceTaskId = executionTaskId;
                    }
                    if (evidenceTaskId != null) {
                        requireTask(state, evidenceTaskId);
                        assertSameScope(evidenceScope, execution.getScopeKey(),
                                "evidence and execution scopes must match");
                    } else {
                        assertCompatibleScope(evidenceScope, execution.getScopeKey(),
                                "evidence and execution scopes must match");
                        if (evidenceScope == null) {
                            evidenceScope = normalizeScope(execution.getScopeKey());
                        }
                    }
                }
                EvidenceRecord evidence = EvidenceRecord.builder()
                        .evidenceId(evidenceId)
                        .scopeKey(evidenceScope)
                        .taskId(evidenceTaskId)
                        .executionId(evidenceExecutionId)
                        .kind(spec.getKind())
                        .location(spec.getLocation())
                        .summary(spec.getSummary())
                        .contentRef(spec.getContentRef())
                        .createdAtEpochMs(now())
                        .provenance(provenance(effectiveActor, evidenceExecutionId, null, "evidence", evidenceId))
                        .build();
                state.getEvidence().put(evidenceId, evidence);
                addEvent(state, "evidence.recorded", evidenceId, effectiveActor, mapOf(
                        "taskId", evidence.getTaskId(), "executionId", evidence.getExecutionId(), "kind", evidence.getKind()));
                return evidence.copy();
            }
        });
    }

    public SubmissionRecord submitTask(String taskId,
                                        String executionId,
                                        HarnessSubmissionSpec spec) {
        return submitTask(taskId, executionId, spec, defaultActor);
    }

    public SubmissionRecord submitTask(String taskId,
                                        String executionId,
                                        HarnessSubmissionSpec spec,
                                        HarnessActor actor) {
        final String id = requireText(taskId, "task id");
        final HarnessSubmissionSpec requested = spec == null ? HarnessSubmissionSpec.builder().build() : spec;
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<SubmissionRecord>() {
            @Override
            public SubmissionRecord apply(HarnessState state) {
                TaskRecord task = requireTask(state, id);
                if (TaskStatus.DONE.equals(task.getStatus()) || TaskStatus.CANCELLED.equals(task.getStatus())) {
                    throw new HarnessValidationException("cannot submit a terminal task: " + id);
                }
                String executionKey = trimToNull(executionId);
                if (executionKey != null) {
                    ExecutionRecord execution = state.getExecutions().get(executionKey);
                    if (execution == null) {
                        throw new HarnessValidationException("execution not found: " + executionKey);
                    }
                    if (!id.equals(execution.getTaskId())) {
                        throw new HarnessValidationException("execution does not belong to task: " + id);
                    }
                    assertCurrentTaskExecution(task, executionKey);
                    assertSameScope(task.getScopeKey(), execution.getScopeKey(),
                            "submission and execution scopes must match");
                }
                resolveEvidenceReferenceScope(state, requested.getEvidenceIds(),
                        task.getScopeKey(), true,
                        "submission and evidence scopes must match");
                String submissionId = valueOrGenerated(null, "submission_");
                SubmissionRecord submission = SubmissionRecord.builder()
                        .submissionId(submissionId)
                        .taskId(id)
                        .executionId(executionKey)
                        .submitter(copyActor(effectiveActor))
                        .completionClaim(requested.getCompletionClaim())
                        .verificationNotes(requested.getVerificationNotes())
                        .deliverables(copyList(requested.getDeliverables()))
                        .evidenceIds(copyList(requested.getEvidenceIds()))
                        .knownGaps(copyList(requested.getKnownGaps()))
                        .residualRisks(copyList(requested.getResidualRisks()))
                        .createdAtEpochMs(now())
                        .build();
                state.getSubmissions().put(submissionId, submission);
                task.setSubmissionId(submissionId);
                task.setStatus(TaskStatus.IN_REVIEW);
                task.setUpdatedAtEpochMs(now());
                task.setVersion(task.getVersion() + 1L);
                addEvent(state, "submission.created", submissionId, effectiveActor, mapOf("taskId", id));
                return submission.copy();
            }
        });
    }

    public ReviewRecord reviewSubmission(String submissionId,
                                         ReviewVerdict verdict,
                                         String findings,
                                         String rationale,
                                         HarnessActor actor) {
        final String id = requireText(submissionId, "submission id");
        if (verdict == null) throw new HarnessValidationException("review verdict is required");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<ReviewRecord>() {
            @Override
            public ReviewRecord apply(HarnessState state) {
                SubmissionRecord submission = state.getSubmissions().get(id);
                if (submission == null) throw new HarnessValidationException("submission not found: " + id);
                TaskRecord task = requireTask(state, submission.getTaskId());
                if (!id.equals(task.getSubmissionId())) {
                    throw new HarnessConflictException("submission is no longer the current task submission: " + id);
                }
                String executionId = trimToNull(submission.getExecutionId());
                if (executionId != null) {
                    ExecutionRecord execution = requireExecution(state, executionId);
                    if (!submission.getTaskId().equals(execution.getTaskId())) {
                        throw new HarnessValidationException("submission execution does not belong to task: "
                                + submission.getTaskId());
                    }
                    assertCurrentTaskExecution(task, executionId);
                    assertSameScope(task.getScopeKey(), execution.getScopeKey(),
                            "submission and execution scopes must match");
                }
                if (!contract.mayApprove(effectiveActor, submission)) {
                    throw new HarnessValidationException("actor is not allowed to review this submission");
                }
                String reviewId = valueOrGenerated(null, "review_");
                ReviewRecord review = ReviewRecord.builder()
                        .reviewId(reviewId)
                        .submissionId(id)
                        .taskId(submission.getTaskId())
                        .reviewer(copyActor(effectiveActor))
                        .verdict(verdict)
                        .findings(findings)
                        .rationale(rationale)
                        .createdAtEpochMs(now())
                        .build();
                state.getReviews().put(reviewId, review);
                if (task != null && verdict == ReviewVerdict.CHANGES_REQUESTED) {
                    task.setStatus(TaskStatus.ACTIVE);
                    task.setUpdatedAtEpochMs(now());
                    task.setVersion(task.getVersion() + 1L);
                }
                addEvent(state, "review.recorded", reviewId, effectiveActor, mapOf(
                        "submissionId", id, "verdict", verdict));
                return review.copy();
            }
        });
    }

    public TaskRecord completeTask(String taskId, String submissionId, HarnessActor actor) {
        final String id = requireText(taskId, "task id");
        final String submissionKey = requireText(submissionId, "submission id");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        final CompletionFailure failure = new CompletionFailure();
        TaskRecord result = write(new StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                TaskRecord task = requireTask(state, id);
                SubmissionRecord submission = state.getSubmissions().get(submissionKey);
                if (submission == null) {
                    throw new HarnessValidationException("submission not found: " + submissionKey);
                }
                if (!id.equals(submission.getTaskId())) {
                    throw new HarnessValidationException("submission does not belong to task: " + id);
                }
                if (!submissionKey.equals(task.getSubmissionId())) {
                    throw new HarnessConflictException("submission is no longer the current task submission: "
                            + submissionKey);
                }
                String executionId = trimToNull(submission.getExecutionId());
                if (executionId == null) {
                    throw new HarnessValidationException("submission must reference an execution before completion");
                }
                ExecutionRecord execution = state.getExecutions().get(executionId);
                if (execution == null) {
                    throw new HarnessValidationException("submission execution not found: " + executionId);
                }
                if (!id.equals(execution.getTaskId())) {
                    throw new HarnessValidationException("submission execution does not belong to task: " + id);
                }
                assertCurrentTaskExecution(task, executionId);
                assertSameScope(task.getScopeKey(), execution.getScopeKey(),
                        "submission and execution scopes must match");
                if (!ExecutionStatus.SUCCEEDED.equals(execution.getStatus())) {
                    throw new HarnessConflictException("submission execution must be SUCCEEDED before completion: "
                            + executionId);
                }
                if (!dependenciesSatisfied(state, id)) {
                    throw new HarnessConflictException("task dependencies are not complete: " + id);
                }
                if (!contract.mayComplete(effectiveActor, submission)) {
                    throw new HarnessValidationException("actor is not allowed to complete this task");
                }
                if (contract.requiresApprovedReview(task, submission)
                        && !hasApprovedReview(state, submission.getSubmissionId())) {
                    failure.reason = "an approved review is required before completion";
                    return task.copy();
                }
                List<GateResult> gateResults = contract.evaluateCompletion(task, submission, state);
                boolean passed = gateResults != null && !gateResults.isEmpty();
                String failedReason = null;
                if (gateResults != null) {
                    for (GateResult gateResult : gateResults) {
                        if (gateResult == null || !gateResult.isPassed()) {
                            passed = false;
                            if (failedReason == null) {
                                failedReason = gateResult == null ? "gate returned no result" : gateResult.getReason();
                            }
                        }
                        String gateName = gateResult == null ? "unknown" : gateResult.getName();
                        String gateId = valueOrGenerated(null, "gate_");
                        state.getGates().put(gateId, GateRecord.builder()
                                .gateId(gateId)
                                .taskId(id)
                                .name(gateName)
                                .status(gateResult != null && gateResult.isPassed() ? GateStatus.PASS : GateStatus.FAIL)
                                .reason(gateResult == null ? "gate returned no result" : gateResult.getReason())
                                .evaluatedAtEpochMs(now())
                                .build());
                    }
                }
                if (!passed) {
                    failure.reason = failedReason == null ? "completion gates did not pass" : failedReason;
                    addEvent(state, "task.completion_rejected", id, effectiveActor, mapOf("reason", failure.reason));
                    return task.copy();
                }
                task.setStatus(TaskStatus.DONE);
                task.setBlockedReason(null);
                task.setUpdatedAtEpochMs(now());
                task.setVersion(task.getVersion() + 1L);
                addEvent(state, "task.completed", id, effectiveActor, mapOf("submissionId", submissionKey,
                        "executionId", executionId));
                return task.copy();
            }
        });
        if (failure.reason != null) {
            throw new HarnessValidationException(failure.reason);
        }
        return result;
    }

    public ExecutionRecord createExecution(HarnessExecutionSpec spec) {
        return createExecution(spec, defaultActor);
    }

    public ExecutionRecord createExecution(HarnessExecutionSpec spec, HarnessActor actor) {
        if (spec == null) throw new HarnessValidationException("execution specification is required");
        final String executionId = valueOrGenerated(spec.getExecutionId(), "exe_");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                String executionScope = executionSpecScope(state, spec);
                String existingId = idempotentId(state, IDEMPOTENCY_EXECUTION, executionScope,
                        spec.getIdempotencyKey());
                if (existingId != null && state.getExecutions().get(existingId) != null) {
                    return state.getExecutions().get(existingId).copy();
                }
                if (state.getExecutions().containsKey(executionId)) {
                    throw new HarnessConflictException("execution already exists: " + executionId);
                }
                String executionTaskId = trimToNull(spec.getTaskId());
                if (executionTaskId != null) {
                    TaskRecord task = requireTask(state, executionTaskId);
                    if (TaskStatus.DONE.equals(task.getStatus()) || TaskStatus.CANCELLED.equals(task.getStatus())) {
                        throw new HarnessValidationException("cannot execute a terminal task: " + executionTaskId);
                    }
                    if (TaskStatus.BLOCKED.equals(task.getStatus())
                            || TaskStatus.IN_REVIEW.equals(task.getStatus())) {
                        throw new HarnessConflictException("task cannot create an execution in status "
                                + task.getStatus() + ": " + executionTaskId);
                    }
                    if (!dependenciesSatisfied(state, executionTaskId)) {
                        throw new HarnessConflictException("task dependencies are not complete: " + executionTaskId);
                    }
                    if (hasOutstandingExecution(state, executionTaskId)) {
                        throw new HarnessConflictException("task already has an outstanding execution: "
                                + executionTaskId);
                    }
                }
                int attempt = nextAttempt(state, executionTaskId);
                ExecutionRecord execution = ExecutionRecord.builder()
                        .executionId(executionId)
                        .taskId(executionTaskId)
                        .scopeKey(executionScope)
                        .sessionId(trimToNull(spec.getSessionId()))
                        .runId(resolveExecutionRunId(state, spec))
                        .status(ExecutionStatus.READY)
                        .attempt(attempt)
                        .inputSummary(spec.getInputSummary())
                        .createdAtEpochMs(now())
                        .updatedAtEpochMs(now())
                        .version(1L)
                        .build();
                state.getExecutions().put(executionId, execution);
                if (execution.getTaskId() != null) {
                    TaskRecord task = state.getTasks().get(execution.getTaskId());
                    if (task != null) {
                        task.setLastExecutionId(executionId);
                        task.setUpdatedAtEpochMs(now());
                        task.setVersion(task.getVersion() + 1L);
                    }
                }
                rememberIdempotency(state, IDEMPOTENCY_EXECUTION, executionScope,
                        spec.getIdempotencyKey(), executionId);
                addEvent(state, "execution.created", executionId, effectiveActor, mapOf(
                        "taskId", execution.getTaskId(), "attempt", attempt));
                return execution.copy();
            }
        });
    }

    public ExecutionRecord attachExecutionToTask(String executionId, String taskId) {
        return attachExecutionToTask(executionId, taskId, defaultActor);
    }

    public ExecutionRecord attachExecutionToTask(String executionId, String taskId, HarnessActor actor) {
        final String executionKey = requireText(executionId, "execution id");
        final String taskKey = requireText(taskId, "task id");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                return attachExecutionToTaskInternal(state, executionKey, taskKey, effectiveActor);
            }
        });
    }

    public ExecutionRecord claimExecution(String executionId, String workerId, long leaseDurationMillis) {
        final String executionKey = requireText(executionId, "execution id");
        final String worker = requireText(workerId, "worker id");
        final long duration = leaseDurationMillis <= 0L ? 60_000L : leaseDurationMillis;
        final ConflictHolder conflict = new ConflictHolder();
        ExecutionRecord result = write(new StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionKey);
                TaskRecord task = execution.getTaskId() == null
                        ? null : state.getTasks().get(execution.getTaskId());
                if (task != null && (TaskStatus.CANCELLED.equals(task.getStatus())
                        || TaskStatus.DONE.equals(task.getStatus())
                        || TaskStatus.BLOCKED.equals(task.getStatus())
                        || TaskStatus.IN_REVIEW.equals(task.getStatus()))) {
                    throw new HarnessConflictException("cannot claim execution for task in status "
                            + task.getStatus() + ": " + execution.getTaskId());
                }
                long currentTime = now();
                LeaseRecord currentLease = execution.getLeaseId() == null ? null : state.getLeases().get(execution.getLeaseId());
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
                if (trimToNull(execution.getSessionId()) == null) {
                    // A low-level caller may create an unbound Execution. Bind
                    // its runtime Session identity at the same durable
                    // mutation that acquires the execution lease so the
                    // adapter snapshot can be validated and recovered later.
                    execution.setSessionId(valueOrGenerated(null, "session_"));
                    execution.setUpdatedAtEpochMs(currentTime);
                    execution.setVersion(execution.getVersion() + 1L);
                    addEvent(state, "execution.session_bound", executionKey, HarnessActor.worker(worker),
                            mapOf("sessionId", execution.getSessionId()));
                }
                long token = nextFencingToken(state);
                String leaseId = valueOrGenerated(null, "lease_");
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
                execution.setStartedAtEpochMs(execution.getStartedAtEpochMs() <= 0L ? currentTime : execution.getStartedAtEpochMs());
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
                addEvent(state, "execution.claimed", executionKey, HarnessActor.worker(worker), mapOf(
                        "workerId", worker, "fencingToken", token));
                return execution.copy();
            }
        });
        if (conflict.message != null) {
            throw new HarnessConflictException(conflict.message);
        }
        return result;
    }

    /**
     * Fences a worker result after its execution or session lease was lost.
     * This mutation is intentionally separate from {@link #persistExecutionOutcome}
     * because the latter must reject a stale worker before it can write an
     * outcome.  The method never overwrites a newer owner: the execution and
     * lease tuple is checked again inside the durable mutation.
     */
    public ExecutionRecord markExecutionUnknownIfLeaseLost(String executionId,
                                                            String leaseId,
                                                            long fencingToken,
                                                            String workerId,
                                                            String reason) {
        final String executionKey = requireText(executionId, "execution id");
        final String leaseKey = requireText(leaseId, "lease id");
        final String worker = requireText(workerId, "worker id");
        final String unknownReason = trimToNull(reason) == null
                ? "execution lease was lost; side effects require reconciliation" : reason.trim();
        final ConflictHolder conflict = new ConflictHolder();
        ExecutionRecord result = write(new StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionKey);
                if (!ExecutionStatus.RUNNING.equals(execution.getStatus())) {
                    return execution.copy();
                }
                if (!safeEquals(leaseKey, execution.getLeaseId())
                        || execution.getFencingToken() != fencingToken
                        || !safeEquals(worker, execution.getWorkerId())) {
                    conflict.message = "execution lease was already fenced or reclaimed: " + executionKey;
                    return execution.copy();
                }
                long currentTime = now();
                LeaseRecord lease = state.getLeases().get(leaseKey);
                SessionLeaseRecord sessionLease = execution.getSessionId() == null
                        ? null : state.getSessionLeases().get(execution.getSessionId());
                boolean executionLeaseLost = lease == null || lease.isExpired(currentTime)
                        || lease.getFencingToken() != fencingToken
                        || !safeEquals(worker, lease.getWorkerId());
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

    public ExecutionRecord heartbeat(String executionId, String leaseId, long fencingToken, String workerId,
                                     long leaseDurationMillis) {
        final String executionKey = requireText(executionId, "execution id");
        final String leaseKey = requireText(leaseId, "lease id");
        final String worker = requireText(workerId, "worker id");
        final long duration = leaseDurationMillis <= 0L ? 60_000L : leaseDurationMillis;
        return write(new StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionKey);
                assertLease(state, execution, leaseKey, fencingToken, worker);
                LeaseRecord lease = state.getLeases().get(leaseKey);
                long currentTime = now();
                lease.setExpiresAtEpochMs(currentTime + duration);
                if (!refreshSessionLease(state, execution, lease, currentTime)) {
                    return execution.copy();
                }
                execution.setUpdatedAtEpochMs(currentTime);
                execution.setVersion(execution.getVersion() + 1L);
                // The lease timestamps are the durable heartbeat record. Do
                // not append one full HarnessState event for every heartbeat;
                // long-lived agents would otherwise grow the ledger without
                // adding a recovery decision.
                return execution.copy();
            }
        });
    }

    public ExecutionRecord releaseExecution(String executionId, String leaseId, long fencingToken, String workerId) {
        final String executionKey = requireText(executionId, "execution id");
        final String leaseKey = requireText(leaseId, "lease id");
        final String worker = requireText(workerId, "worker id");
        return write(new StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionKey);
                assertLease(state, execution, leaseKey, fencingToken, worker);
                LeaseRecord lease = state.getLeases().get(leaseKey);
                long currentTime = now();
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
                addEvent(state, cancelled ? "execution.cancelled" : "execution.requeued", executionKey,
                        HarnessActor.worker(worker), mapOf("taskId", execution.getTaskId()));
                return execution.copy();
            }
        });
    }

    public ExecutionRecord reconcileExecution(String executionId,
                                              ExecutionStatus resolution,
                                              String reason) {
        return reconcileExecution(executionId, resolution, reason, null, defaultActor);
    }

    public ExecutionRecord reconcileExecution(String executionId,
                                              ExecutionStatus resolution,
                                              String reason,
                                              HarnessActor actor) {
        return reconcileExecution(executionId, resolution, reason, null, actor);
    }

    /**
     * Resolves an UNKNOWN execution after an operator or an idempotent
     * external lookup has established what happened. A READY resolution
     * permits a retry; terminal resolutions record the external outcome while
     * leaving Task completion behind the normal Submission/Review/Gate path.
     */
    public ExecutionRecord reconcileExecution(String executionId,
                                              ExecutionStatus resolution,
                                              String reason,
                                              String outputText,
                                              HarnessActor actor) {
        final String executionKey = requireText(executionId, "execution id");
        if (resolution == null || resolution == ExecutionStatus.RUNNING
                || resolution == ExecutionStatus.WAITING
                || resolution == ExecutionStatus.UNKNOWN) {
            throw new HarnessValidationException(
                    "execution reconciliation must resolve to READY, SUCCEEDED, FAILED, or CANCELLED");
        }
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionKey);
                if (!ExecutionStatus.UNKNOWN.equals(execution.getStatus())) {
                    throw new HarnessConflictException(
                            "only UNKNOWN executions can be reconciled: " + executionKey);
                }
                if (!contract.mayReconcile(effectiveActor, execution, resolution)) {
                    throw new HarnessValidationException("actor is not allowed to reconcile this execution");
                }
                long currentTime = now();
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
                addEvent(state, "execution.reconciled", executionKey, effectiveActor, mapOf(
                        "resolution", resolution, "reason", reason));
                return execution.copy();
            }
        });
    }

    public ExecutionRecord persistExecutionOutcome(HarnessExecutionOutcome outcome) {
        if (outcome == null) throw new HarnessValidationException("execution outcome is required");
        final String executionKey = requireText(outcome.getExecutionId(), "execution id");
        if (outcome.getStatus() == null) throw new HarnessValidationException("execution outcome status is required");
        if (ExecutionStatus.RUNNING.equals(outcome.getStatus())) {
            throw new HarnessValidationException(
                    "RUNNING is not a persistable execution outcome; return READY, WAITING, or a terminal status");
        }
        if (!ExecutionStatus.WAITING.equals(outcome.getStatus())
                && (trimToNull(outcome.getWaitId()) != null
                || trimToNull(outcome.getOperationId()) != null)) {
            throw new HarnessValidationException(
                    "only a WAITING execution outcome may reference a wait or operation");
        }
        return write(new StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionKey);
                assertLease(state, execution, outcome.getLeaseId(), outcome.getFencingToken(), execution.getWorkerId());
                assertSnapshotMatchesExecution(execution, outcome.getSessionSnapshot());
                String workerId = execution.getWorkerId();
                long currentTime = now();
                TaskRecord task = execution.getTaskId() == null ? null : state.getTasks().get(execution.getTaskId());
                ExecutionStatus originalStatus = outcome.getStatus();
                ExecutionStatus persistedStatus = outcome.getStatus();
                String persistedError = outcome.getError();
                boolean cancelledByTask = task != null && TaskStatus.CANCELLED.equals(task.getStatus());
                boolean quarantinedLateOutcome = cancelledByTask
                        && !ExecutionStatus.CANCELLED.equals(persistedStatus);
                if (quarantinedLateOutcome) {
                    persistedStatus = ExecutionStatus.CANCELLED;
                    String lateStatus = originalStatus == null ? "UNKNOWN" : originalStatus.name();
                    String cancellationMessage = "task was cancelled while the execution was running; "
                            + "late outcome was " + lateStatus
                            + " and any external side effect requires reconciliation";
                    if (persistedError == null || persistedError.trim().isEmpty()) {
                        persistedError = cancellationMessage;
                    } else {
                        persistedError = cancellationMessage + ": " + persistedError;
                    }
                }
                if (!cancelledByTask && outcome.getSessionSnapshot() != null
                        && outcome.getSessionSnapshot().getSessionId() != null) {
                    state.getSessions().put(outcome.getSessionSnapshot().getSessionId(), outcome.getSessionSnapshot());
                }
                String waitId = outcome.getWaitId();
                String operationId = outcome.getOperationId();
                if (!cancelledByTask && ExecutionStatus.WAITING.equals(persistedStatus)) {
                    WaitRecord requestedWait = waitId == null || waitId.trim().isEmpty()
                            ? null : state.getWaits().get(waitId.trim());
                    if (requestedWait != null && !executionKey.equals(requestedWait.getExecutionId())) {
                        throw new HarnessConflictException("wait id is already bound to another execution: " + waitId);
                    }
                    List<WaitRecord> openWaitRecords = openWaits(state, executionKey);
                    if (openWaitRecords.isEmpty()) {
                        // A runtime may report WAITING without creating a
                        // Harness wait. Reuse a fresh requested id when it is
                        // available; never reopen a delivered/cancelled wait.
                        String newWaitId = requestedWait == null
                                ? (waitId == null || waitId.trim().isEmpty()
                                ? valueOrGenerated(null, "wait_") : waitId.trim())
                                : valueOrGenerated(null, "wait_");
                        WaitRecord wait = WaitRecord.builder()
                                .waitId(newWaitId)
                                .executionId(executionKey)
                                .taskId(execution.getTaskId())
                                .type(WaitType.ASYNC_OPERATION)
                                .status(WaitStatus.OPEN)
                                .operationId(operationId)
                                .createdAtEpochMs(currentTime)
                                .payload(new LinkedHashMap<String, Object>())
                                .build();
                        state.getWaits().put(newWaitId, wait);
                        openWaitRecords = openWaits(state, executionKey);
                    }
                    WaitRecord representative = openWaitRecords.get(0);
                    waitId = representative.getWaitId();
                    operationId = representative.getOperationId() == null
                            ? operationId : representative.getOperationId();
                } else {
                    waitId = null;
                    operationId = null;
                    cancelOpenWaitsForExecution(state, executionKey,
                            "execution outcome no longer waits for external input", HarnessActor.worker(workerId),
                            currentTime);
                }
                String checkpointId = cancelledByTask ? execution.getCheckpointId() : null;
                if (!cancelledByTask && (outcome.getSessionSnapshot() != null || outcome.getCheckpointSummary() != null
                        || (outcome.getCheckpointState() != null && !outcome.getCheckpointState().isEmpty()))) {
                    checkpointId = valueOrGenerated(null, "checkpoint_");
                    Map<String, Object> checkpointState = copyMap(outcome.getCheckpointState());
                    if (checkpointState == null) {
                        checkpointState = new LinkedHashMap<String, Object>();
                    }
                    checkpointState.put("waitId", waitId);
                    checkpointState.put("operationId", operationId);
                    CheckpointRecord checkpoint = CheckpointRecord.builder()
                            .checkpointId(checkpointId)
                            .executionId(executionKey)
                            .taskId(execution.getTaskId())
                            .sessionId(execution.getSessionId())
                            .runId(execution.getRunId())
                            .summary(outcome.getCheckpointSummary())
                            .createdAtEpochMs(currentTime)
                            .state(checkpointState)
                            .build();
                    state.getCheckpoints().put(checkpointId, checkpoint);
                }
                execution.setStatus(persistedStatus);
                execution.setWaitId(ExecutionStatus.WAITING.equals(persistedStatus) ? waitId : null);
                execution.setOperationId(ExecutionStatus.WAITING.equals(persistedStatus) ? operationId : null);
                execution.setCheckpointId(checkpointId);
                execution.setOutputText(outcome.getOutputText());
                execution.setError(persistedError);
                execution.setUpdatedAtEpochMs(currentTime);
                execution.setFinishedAtEpochMs(isTerminalExecution(persistedStatus) ? currentTime : 0L);
                execution.setVersion(execution.getVersion() + 1L);
                LeaseRecord lease = state.getLeases().get(outcome.getLeaseId());
                if (lease != null) lease.setReleasedAtEpochMs(currentTime);
                releaseSessionLease(state, execution, outcome.getLeaseId(),
                        outcome.getFencingToken(), workerId, currentTime);
                execution.setWorkerId(null);
                execution.setLeaseId(null);
                execution.setFencingToken(0L);
                if (task != null) {
                    task.setLastExecutionId(executionKey);
                    if (cancelledByTask) {
                        // Cancellation is a terminal handoff boundary. A late
                        // worker result must never reactivate the Task.
                    } else if (ExecutionStatus.WAITING.equals(persistedStatus)) {
                        task.setStatus(TaskStatus.WAITING);
                    } else if (ExecutionStatus.UNKNOWN.equals(persistedStatus)) {
                        task.setStatus(TaskStatus.BLOCKED);
                        task.setBlockedReason(persistedError);
                    } else if (!TaskStatus.DONE.equals(task.getStatus())
                            && !TaskStatus.CANCELLED.equals(task.getStatus())
                            && !TaskStatus.IN_REVIEW.equals(task.getStatus())) {
                        task.setStatus(TaskStatus.ACTIVE);
                        task.setBlockedReason(null);
                    }
                    task.setUpdatedAtEpochMs(currentTime);
                    task.setVersion(task.getVersion() + 1L);
                }
                addEvent(state, quarantinedLateOutcome ? "execution.outcome_quarantined"
                                : "execution." + persistedStatus.name().toLowerCase(), executionKey,
                        HarnessActor.worker(workerId), mapOf("taskId", execution.getTaskId(), "waitId", waitId,
                                "checkpointId", checkpointId, "error", persistedError,
                                "outcomeStatus", originalStatus));
                return execution.copy();
            }
        });
    }

    public CheckpointRecord recordCheckpoint(String executionId,
                                             String summary,
                                             Map<String, Object> checkpointState) {
        return recordCheckpoint(executionId, summary, checkpointState, defaultActor);
    }

    public CheckpointRecord recordCheckpoint(String executionId,
                                             String summary,
                                             Map<String, Object> checkpointState,
                                             HarnessActor actor) {
        final String executionKey = requireText(executionId, "execution id");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<CheckpointRecord>() {
            @Override
            public CheckpointRecord apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionKey);
                String checkpointId = valueOrGenerated(null, "checkpoint_");
                CheckpointRecord checkpoint = CheckpointRecord.builder()
                        .checkpointId(checkpointId)
                        .executionId(executionKey)
                        .taskId(execution.getTaskId())
                        .sessionId(execution.getSessionId())
                        .runId(execution.getRunId())
                        .summary(summary)
                        .createdAtEpochMs(now())
                        .state(copyMap(checkpointState))
                        .build();
                state.getCheckpoints().put(checkpointId, checkpoint);
                execution.setCheckpointId(checkpointId);
                execution.setUpdatedAtEpochMs(now());
                addEvent(state, "checkpoint.created", checkpointId, effectiveActor, mapOf("executionId", executionKey));
                return checkpoint.copy();
            }
        });
    }

    public WaitRecord ensureWait(String executionId,
                                 String taskId,
                                 String waitId,
                                 WaitType type,
                                 String operationId,
                                 String externalKey,
                                 Map<String, Object> payload) {
        return ensureWait(executionId, taskId, waitId, type, operationId, externalKey, payload, defaultActor);
    }

    public WaitRecord ensureWait(String executionId,
                                 String taskId,
                                 String waitId,
                                 WaitType type,
                                 String operationId,
                                 String externalKey,
                                 Map<String, Object> payload,
                                 HarnessActor actor) {
        final String executionKey = requireText(executionId, "execution id");
        final String requestedWaitId = waitId == null || waitId.trim().isEmpty()
                ? valueOrGenerated(null, "wait_") : waitId.trim();
        final WaitType waitType = type == null ? WaitType.EXTERNAL_EVENT : type;
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<WaitRecord>() {
            @Override
            public WaitRecord apply(HarnessState state) {
                return ensureWaitInternal(state, executionKey, taskId, requestedWaitId,
                        waitType, operationId, externalKey, payload, effectiveActor);
            }
        });
    }

    private WaitRecord ensureWaitInternal(HarnessState state,
                                          String executionKey,
                                          String taskId,
                                          String requestedWaitId,
                                          WaitType waitType,
                                          String operationId,
                                          String externalKey,
                                          Map<String, Object> payload,
                                          HarnessActor actor) {
        ExecutionRecord execution = requireExecution(state, executionKey);
        WaitRecord existing = state.getWaits().get(requestedWaitId);
        if (existing != null) {
            if (!executionKey.equals(existing.getExecutionId())) {
                throw new HarnessConflictException("wait id is already bound to another execution: "
                        + requestedWaitId);
            }
            return existing.copy();
        }
        String effectiveTaskId = taskId == null ? execution.getTaskId() : trimToNull(taskId);
        if (effectiveTaskId != null) {
            if (!safeEquals(effectiveTaskId, execution.getTaskId())) {
                throw new HarnessConflictException("wait task does not belong to execution: "
                        + executionKey);
            }
            TaskRecord task = requireTask(state, effectiveTaskId);
            assertCompatibleScope(task.getScopeKey(), execution.getScopeKey(),
                    "wait and execution scopes must match");
            if (TaskStatus.DONE.equals(task.getStatus()) || TaskStatus.CANCELLED.equals(task.getStatus())) {
                throw new HarnessConflictException("cannot create a wait for a terminal task: "
                        + effectiveTaskId);
            }
            if (TaskStatus.BLOCKED.equals(task.getStatus())
                    || TaskStatus.IN_REVIEW.equals(task.getStatus())) {
                throw new HarnessConflictException("cannot create a wait for task in status "
                        + task.getStatus() + ": " + effectiveTaskId);
            }
        }
        long currentTime = now();
        WaitRecord wait = WaitRecord.builder()
                .waitId(requestedWaitId)
                .executionId(executionKey)
                .taskId(effectiveTaskId)
                .type(waitType)
                .status(WaitStatus.OPEN)
                .operationId(operationId)
                .externalKey(externalKey)
                .createdAtEpochMs(currentTime)
                .payload(copyMap(payload))
                .build();
        state.getWaits().put(requestedWaitId, wait);
        WaitRecord representative = firstOpenWait(state, executionKey);
        execution.setWaitId(representative == null ? requestedWaitId : representative.getWaitId());
        execution.setOperationId(representative == null ? operationId : representative.getOperationId());
        execution.setUpdatedAtEpochMs(currentTime);
        if (effectiveTaskId != null) {
            TaskRecord task = state.getTasks().get(effectiveTaskId);
            if (task != null && !TaskStatus.DONE.equals(task.getStatus()) && !TaskStatus.CANCELLED.equals(task.getStatus())) {
                task.setStatus(TaskStatus.WAITING);
                task.setUpdatedAtEpochMs(currentTime);
                task.setVersion(task.getVersion() + 1L);
            }
        }
        addEvent(state, "wait.created", requestedWaitId, actor, mapOf(
                "executionId", executionKey, "type", waitType, "operationId", operationId));
        return wait.copy();
    }

    public WaitRecord requestApproval(String executionId,
                                      String taskId,
                                      String toolName,
                                      String callId,
                                      String arguments) {
        return requestApproval(executionId, taskId, toolName, callId, arguments, defaultActor);
    }

    public WaitRecord requestApproval(String executionId,
                                      String taskId,
                                      String toolName,
                                      String callId,
                                      String arguments,
                                      HarnessActor actor) {
        return requestApproval(executionId, taskId, toolName, callId, arguments,
                null, actor);
    }

    /**
     * Requests approval and, when supplied, atomically links a pre-reserved
     * business invocation to the approval wait. This closes the crash window
     * between creating the wait and marking the invocation as retryable.
     */
    public WaitRecord requestApproval(String executionId,
                                      String taskId,
                                      String toolName,
                                      String callId,
                                      String arguments,
                                      String invocationId,
                                      HarnessActor actor) {
        final String executionKey = requireText(executionId, "execution id");
        final String invocationKey = trimToNull(invocationId);
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<WaitRecord>() {
            @Override
            public WaitRecord apply(HarnessState state) {
                ExecutionRecord execution = requireExecution(state, executionKey);
                ToolInvocationRecord invocation = invocationKey == null
                        ? null : requireApprovalInvocation(state, invocationKey, execution,
                        toolName, callId, arguments);
                for (WaitRecord wait : state.getWaits().values()) {
                    if (wait == null || !WaitType.APPROVAL.equals(wait.getType())
                            || !executionKey.equals(wait.getExecutionId())) continue;
                    Map<String, Object> existingPayload = wait.getPayload();
                    if (existingPayload == null) {
                        existingPayload = new LinkedHashMap<String, Object>();
                    }
                    if (safeEquals(toolName, String.valueOf(existingPayload.get("toolName")))
                            && approvalMatches(existingPayload, callId, arguments)
                            && WaitStatus.OPEN.equals(wait.getStatus())) {
                        if (invocation != null) {
                            existingPayload.put(AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID,
                                    invocationKey);
                            wait.setPayload(existingPayload);
                            linkApprovalInvocation(invocation, wait, effectiveActor, state);
                        }
                        return wait.copy();
                    }
                }
                Map<String, Object> payload = new LinkedHashMap<String, Object>();
                payload.put("toolName", toolName);
                payload.put("callId", callId);
                payload.put("arguments", arguments);
                payload.put("approval", true);
                if (invocationKey != null) {
                    payload.put(AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID, invocationKey);
                }
                WaitRecord wait = ensureWaitInternal(state, executionKey, taskId,
                        valueOrGenerated(null, "wait_"), WaitType.APPROVAL, null,
                        toolName, payload, effectiveActor);
                if (invocation != null) {
                    linkApprovalInvocation(invocation, wait, effectiveActor, state);
                }
                return wait;
            }
        });
    }

    public boolean isApprovalGranted(String executionId, String toolName, String callId) {
        return isApprovalGranted(executionId, toolName, callId, null);
    }

    /**
     * Checks a delivered approval without allowing it to be reused for a
     * different invocation. Providers may change call ids across a retry, so
     * an exact argument match is also accepted.
     */
    public boolean isApprovalGranted(String executionId,
                                     String toolName,
                                     String callId,
                                     String arguments) {
        HarnessState state = getState();
        for (WaitRecord wait : state.getWaits().values()) {
            if (wait == null || !WaitType.APPROVAL.equals(wait.getType())
                    || !safeEquals(executionId, wait.getExecutionId())
                    || !WaitStatus.DELIVERED.equals(wait.getStatus())) continue;
            Map<String, Object> payload = wait.getPayload();
            if (payload == null || !safeEquals(toolName, String.valueOf(payload.get("toolName")))) continue;
            if (deliveryApproved(payload.get("delivery"))
                    && approvalMatches(payload, callId, arguments)) return true;
        }
        return false;
    }

    public WakeupRecord deliverWait(String waitId, Object input) {
        return deliverWait(waitId, input, null, defaultActor);
    }

    public WakeupRecord deliverWait(String waitId, Object input, HarnessActor actor) {
        return deliverWait(waitId, input, null, actor);
    }

    /**
     * Delivers a wait and optionally persists the resumed Agent session in the
     * same state mutation. This is the durable handoff used by AgentHarness.
     */
    public WakeupRecord deliverWait(String waitId,
                                    Object input,
                                    AgentSessionSnapshot sessionSnapshot,
                                    HarnessActor actor) {
        return deliverWaitInternal(waitId, input, sessionSnapshot, null, actor);
    }

    /**
     * Delivers a wait and updates an adapter-owned recovery payload in the
     * same ledger mutation. This keeps a replacement tool result durable
     * before a resumed adapter is opened.
     */
    public WakeupRecord deliverAdapterWait(String waitId,
                                           Object input,
                                           HarnessAdapterState adapterState,
                                           HarnessActor actor) {
        return deliverAdapterWait(waitId, input, adapterState, null, actor);
    }

    /**
     * Delivers an adapter wait while optionally updating the legacy Agent
     * session projection in the same state mutation. The projection is kept
     * only for compatibility with callers that still inspect
     * {@link #getSessionSnapshot(String)}; the adapter state remains the
     * generic recovery source.
     */
    public WakeupRecord deliverAdapterWait(String waitId,
                                           Object input,
                                           HarnessAdapterState adapterState,
                                           AgentSessionSnapshot sessionSnapshot,
                                           HarnessActor actor) {
        return deliverWaitInternal(waitId, input, sessionSnapshot, adapterState, actor);
    }

    private WakeupRecord deliverWaitInternal(String waitId,
                                             Object input,
                                             AgentSessionSnapshot sessionSnapshot,
                                             HarnessAdapterState adapterState,
                                             HarnessActor actor) {
        final String id = requireText(waitId, "wait id");
        final HarnessActor effectiveActor = normalizeActor(actor, defaultActor);
        return write(new StateCommand<WakeupRecord>() {
            @Override
            public WakeupRecord apply(HarnessState state) {
                WaitRecord wait = state.getWaits().get(id);
                if (wait == null) throw new HarnessValidationException("wait not found: " + id);
                if (WaitStatus.DELIVERED.equals(wait.getStatus())) {
                    for (WakeupRecord existing : state.getWakeups().values()) {
                        if (existing != null && id.equals(existing.getWaitId())) return existing.copy();
                    }
                }
                if (!WaitStatus.OPEN.equals(wait.getStatus())) {
                    throw new HarnessConflictException("wait is not open: " + id);
                }
                long currentTime = now();
                wait.setStatus(WaitStatus.DELIVERED);
                wait.setResolvedAtEpochMs(currentTime);
                Map<String, Object> waitPayload = wait.getPayload() == null
                        ? new LinkedHashMap<String, Object>() : wait.getPayload();
                waitPayload.put("delivery", input);
                wait.setPayload(waitPayload);
                if (sessionSnapshot != null && sessionSnapshot.getSessionId() != null) {
                    state.getSessions().put(sessionSnapshot.getSessionId(),
                            HarnessJson.copy(sessionSnapshot, AgentSessionSnapshot.class));
                }
                String wakeupId = valueOrGenerated(null, "wakeup_");
                WakeupRecord wakeup = WakeupRecord.builder()
                        .wakeupId(wakeupId)
                        .waitId(id)
                        .executionId(wait.getExecutionId())
                        .type(wait.getType())
                        .dueAtEpochMs(wait.getDueAtEpochMs())
                        .deliveredAtEpochMs(currentTime)
                        .payload(copyMap(waitPayload))
                        .build();
                state.getWakeups().put(wakeupId, wakeup);
                ExecutionRecord execution = state.getExecutions().get(wait.getExecutionId());
                if (adapterState != null && execution != null && execution.getCheckpointId() != null) {
                    CheckpointRecord checkpoint = state.getCheckpoints().get(execution.getCheckpointId());
                    if (checkpoint != null) {
                        Map<String, Object> checkpointState = checkpoint.getState() == null
                                ? new LinkedHashMap<String, Object>()
                                : new LinkedHashMap<String, Object>(checkpoint.getState());
                        checkpointState.put("harnessAdapterType", adapterState.getAdapterType());
                        checkpointState.put("harnessAdapterState",
                                JSON.parseObject(JSON.toJSONString(adapterState)));
                        checkpoint.setState(checkpointState);
                    }
                }
                TaskRecord task = wait.getTaskId() == null ? null : state.getTasks().get(wait.getTaskId());
                if (execution != null && (ExecutionStatus.WAITING.equals(execution.getStatus())
                        || ExecutionStatus.READY.equals(execution.getStatus()))) {
                    List<WaitRecord> remainingWaits = openWaits(state, wait.getExecutionId());
                    WaitRecord representative = remainingWaits.isEmpty()
                            ? null : remainingWaits.get(0);
                    if (representative == null) {
                        execution.setStatus(ExecutionStatus.READY);
                        execution.setWaitId(null);
                        execution.setOperationId(null);
                    } else {
                        // Parallel tool calls may leave more than one durable
                        // wait. Keep the Execution waiting until all of them
                        // have been delivered, while exposing one stable next
                        // wait for hosts that only support a single id.
                        execution.setStatus(ExecutionStatus.WAITING);
                        execution.setWaitId(representative.getWaitId());
                        execution.setOperationId(representative.getOperationId());
                    }
                    execution.setUpdatedAtEpochMs(currentTime);
                    execution.setVersion(execution.getVersion() + 1L);
                }
                if (task != null && TaskStatus.WAITING.equals(task.getStatus())
                        && execution != null && ExecutionStatus.READY.equals(execution.getStatus())) {
                    task.setStatus(TaskStatus.ACTIVE);
                    task.setUpdatedAtEpochMs(currentTime);
                    task.setVersion(task.getVersion() + 1L);
                }
                addEvent(state, "wakeup.delivered", wakeupId, effectiveActor, mapOf(
                        "waitId", id, "executionId", wait.getExecutionId()));
                return wakeup.copy();
            }
        });
    }

    public Map<String, Object> delivery(String waitId) {
        WaitRecord wait = getWait(waitId);
        if (wait == null || wait.getPayload() == null) return Collections.emptyMap();
        Object value = wait.getPayload().get("delivery");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("value", value);
        result.put("waitId", wait.getWaitId());
        result.put("type", wait.getType());
        return result;
    }

    @Override
    public void close() {
        store.close();
    }

    private TaskRecord createTaskInternal(HarnessState state,
                                          HarnessTaskSpec spec,
                                          String taskId,
                                          HarnessActor actor) {
        if (state.getTasks().containsKey(taskId)) {
            throw new HarnessConflictException("task already exists: " + taskId);
        }
        String title = requireText(spec.getTitle(), "task title");
        String parentId = trimToNull(spec.getParentTaskId());
        String taskScope = normalizeScope(spec.getScopeKey());
        if (parentId != null) {
            TaskRecord parent = requireTask(state, parentId);
            if (taskScope != null) {
                assertSameScope(taskScope, parent.getScopeKey(),
                        "child task and parent task scopes must match");
            }
            if (taskScope == null) {
                taskScope = parent.getScopeKey();
            }
            if (wouldCreateCycle(state, RelationType.PARENT_OF, parentId, taskId)) {
                throw new HarnessConflictException("parent relation would create a cycle");
            }
        }
        long currentTime = now();
        TaskRecord task = TaskRecord.builder()
                .taskId(taskId)
                .scopeKey(taskScope)
                .title(title)
                .goal(spec.getGoal())
                .plan(spec.getPlan())
                .status(TaskStatus.PLANNED)
                .createdBy(actorKey(actor))
                .createdAtEpochMs(currentTime)
                .updatedAtEpochMs(currentTime)
                .version(1L)
                .tags(copyList(spec.getTags()))
                .metadata(copyMap(spec.getMetadata()))
                .build();
        state.getTasks().put(taskId, task);
        if (parentId != null) {
            String relationId = valueOrGenerated(null, "rel_");
            RelationRecord relation = RelationRecord.builder()
                    .relationId(relationId)
                    .scopeKey(task.getScopeKey())
                    .type(RelationType.PARENT_OF)
                    .fromKind(EntityKind.TASK)
                    .fromId(parentId)
                    .toKind(EntityKind.TASK)
                    .toId(taskId)
                    .createdBy(actorKey(actor))
                    .createdAtEpochMs(currentTime)
                    .metadata(new LinkedHashMap<String, Object>())
                    .build();
            state.getRelations().put(relationId, relation);
            addEvent(state, "relation.created", relationId, actor, mapOf(
                    "type", RelationType.PARENT_OF, "from", parentId, "to", taskId));
        }
        addEvent(state, "task.created", taskId, actor, mapOf(
                "title", title, "parentTaskId", parentId));
        return task;
    }

    private ExecutionRecord attachExecutionToTaskInternal(HarnessState state,
                                                          String executionKey,
                                                          String taskKey,
                                                          HarnessActor actor) {
        ExecutionRecord execution = requireExecution(state, executionKey);
        TaskRecord task = requireTask(state, taskKey);
        if (TaskStatus.DONE.equals(task.getStatus()) || TaskStatus.CANCELLED.equals(task.getStatus())) {
            throw new HarnessValidationException("cannot attach execution to a terminal task: " + taskKey);
        }
        if (TaskStatus.BLOCKED.equals(task.getStatus()) || TaskStatus.IN_REVIEW.equals(task.getStatus())) {
            throw new HarnessConflictException("cannot attach execution to task in status "
                    + task.getStatus() + ": " + taskKey);
        }
        if (!dependenciesSatisfied(state, taskKey)) {
            throw new HarnessConflictException("task dependencies are not complete: " + taskKey);
        }
        if (execution.getTaskId() != null && !taskKey.equals(execution.getTaskId())) {
            throw new HarnessConflictException("execution is already attached to another task: " + executionKey);
        }
        if (execution.getTaskId() == null && hasOutstandingExecution(state, taskKey, executionKey)) {
            throw new HarnessConflictException("task already has an outstanding execution: " + taskKey);
        }
        if (execution.getScopeKey() != null) {
            assertSameScope(execution.getScopeKey(), task.getScopeKey(),
                    "execution and task scopes must match");
        }
        if (execution.getScopeKey() == null) {
            execution.setScopeKey(task.getScopeKey());
        }
        execution.setTaskId(taskKey);
        task.setLastExecutionId(executionKey);
        task.setUpdatedAtEpochMs(now());
        task.setVersion(task.getVersion() + 1L);
        addEvent(state, "execution.attached", executionKey, actor, mapOf("taskId", taskKey));
        return execution.copy();
    }

    private void validateRelationSpec(HarnessState state, HarnessRelationSpec spec) {
        if (spec.getFromKind() == null || spec.getToKind() == null) {
            throw new HarnessValidationException("relation endpoints must have kinds");
        }
        String fromId = requireText(spec.getFromId(), "relation from id");
        String toId = requireText(spec.getToId(), "relation to id");
        if (spec.getType() == RelationType.PARENT_OF || spec.getType() == RelationType.DEPENDS_ON) {
            if (spec.getFromKind() != EntityKind.TASK || spec.getToKind() != EntityKind.TASK) {
                throw new HarnessValidationException(spec.getType() + " relations must connect tasks");
            }
        }
        if (!entityExists(state, spec.getFromKind(), fromId) || !entityExists(state, spec.getToKind(), toId)) {
            throw new HarnessValidationException("relation endpoint does not exist");
        }
        resolveRelationScope(state, spec);
    }

    private String resolveRelationScope(HarnessState state, HarnessRelationSpec spec) {
        String explicit = normalizeScope(spec.getScopeKey());
        String fromScope = entityScope(state, spec.getFromKind(), spec.getFromId());
        String toScope = entityScope(state, spec.getToKind(), spec.getToId());
        assertCompatibleScope(fromScope, toScope,
                "relation endpoints must use the same scope");
        assertCompatibleScope(explicit, fromScope,
                "relation scope does not match its source entity");
        assertCompatibleScope(explicit, toScope,
                "relation scope does not match its target entity");
        return explicit == null ? (fromScope == null ? toScope : fromScope) : explicit;
    }

    private String taskSpecScope(HarnessState state, HarnessTaskSpec spec) {
        String taskScope = normalizeScope(spec.getScopeKey());
        String parentTaskId = trimToNull(spec.getParentTaskId());
        if (parentTaskId == null) {
            return taskScope;
        }
        TaskRecord parent = requireTask(state, parentTaskId);
        if (taskScope != null) {
            assertSameScope(taskScope, parent.getScopeKey(),
                    "child task and parent task scopes must match");
        }
        return taskScope == null ? normalizeScope(parent.getScopeKey()) : taskScope;
    }

    private String executionSpecScope(HarnessState state, HarnessExecutionSpec spec) {
        String executionScope = normalizeScope(spec.getScopeKey());
        String taskId = trimToNull(spec.getTaskId());
        if (taskId == null) {
            return executionScope;
        }
        TaskRecord task = requireTask(state, taskId);
        if (executionScope != null) {
            assertSameScope(executionScope, task.getScopeKey(),
                    "execution and task scopes must match");
        }
        return executionScope == null ? normalizeScope(task.getScopeKey()) : executionScope;
    }

    private HarnessTaskSpec withScope(HarnessTaskSpec spec, String scopeKey) {
        String normalizedScope = normalizeScope(scopeKey);
        if (safeEquals(normalizedScope, normalizeScope(spec.getScopeKey()))) {
            return spec;
        }
        return spec.toBuilder().scopeKey(normalizedScope).build();
    }

    private String resolveEvidenceReferenceScope(HarnessState state,
                                                 List<String> evidenceIds,
                                                 String currentScope,
                                                 boolean scopeAnchored,
                                                 String mismatchMessage) {
        String resolvedScope = normalizeScope(currentScope);
        boolean scopeBound = scopeAnchored || resolvedScope != null;
        for (String evidenceId : copyList(evidenceIds)) {
            String id = requireText(evidenceId, "evidence id");
            EvidenceRecord evidence = state.getEvidence().get(id);
            if (evidence == null) {
                throw new HarnessValidationException("evidence not found: " + id);
            }
            String evidenceScope = normalizeScope(evidence.getScopeKey());
            if (!scopeBound) {
                resolvedScope = evidenceScope;
                scopeBound = true;
            } else {
                assertSameScope(resolvedScope, evidenceScope, mismatchMessage);
            }
        }
        return resolvedScope;
    }

    private String resolveFactReferenceScope(HarnessState state,
                                             List<String> factIds,
                                             String currentScope,
                                             boolean scopeAnchored,
                                             String mismatchMessage) {
        String resolvedScope = normalizeScope(currentScope);
        boolean scopeBound = scopeAnchored || resolvedScope != null;
        for (String factId : copyList(factIds)) {
            String id = requireText(factId, "fact id");
            FactRecord fact = state.getFacts().get(id);
            if (fact == null) {
                throw new HarnessValidationException("fact not found: " + id);
            }
            String factScope = normalizeScope(fact.getScopeKey());
            if (!scopeBound) {
                resolvedScope = factScope;
                scopeBound = true;
            } else {
                assertSameScope(resolvedScope, factScope, mismatchMessage);
            }
        }
        return resolvedScope;
    }

    private String relatedScope(HarnessState state,
                                String explicitScope,
                                String entityId,
                                EntityKind entityKind,
                                String mismatchMessage) {
        String scope = normalizeScope(explicitScope);
        if (entityId == null || entityId.trim().isEmpty()) {
            return scope;
        }
        if (!entityExists(state, entityKind, entityId)) {
            throw new HarnessValidationException(entityKind + " not found: " + entityId);
        }
        String entityScope = entityScope(state, entityKind, entityId);
        if (scope != null) {
            assertSameScope(scope, entityScope, mismatchMessage);
        }
        return scope == null ? entityScope : scope;
    }

    private String entityScope(HarnessState state, EntityKind kind, String id) {
        if (state == null || kind == null || id == null) {
            return null;
        }
        switch (kind) {
            case TASK:
                TaskRecord task = state.getTasks().get(id);
                return task == null ? null : normalizeScope(task.getScopeKey());
            case FACT:
                FactRecord fact = state.getFacts().get(id);
                return fact == null ? null : normalizeScope(fact.getScopeKey());
            case DECISION:
                DecisionRecord decision = state.getDecisions().get(id);
                return decision == null ? null : normalizeScope(decision.getScopeKey());
            case EXECUTION:
                ExecutionRecord execution = state.getExecutions().get(id);
                return execution == null ? null : normalizeScope(execution.getScopeKey());
            case EVIDENCE:
                EvidenceRecord evidence = state.getEvidence().get(id);
                return evidence == null ? null : normalizeScope(evidence.getScopeKey());
            default:
                return null;
        }
    }

    private boolean entityExists(HarnessState state, EntityKind kind, String id) {
        switch (kind) {
            case TASK: return state.getTasks().containsKey(id);
            case FACT: return state.getFacts().containsKey(id);
            case DECISION: return state.getDecisions().containsKey(id);
            case EXECUTION: return state.getExecutions().containsKey(id);
            case EVIDENCE: return state.getEvidence().containsKey(id);
            case CHECKPOINT: return state.getCheckpoints().containsKey(id);
            case WAIT: return state.getWaits().containsKey(id);
            case REVIEW: return state.getReviews().containsKey(id);
            case SUBMISSION: return state.getSubmissions().containsKey(id);
            default: return false;
        }
    }

    private RelationRecord findRelation(HarnessState state, HarnessRelationSpec spec) {
        for (RelationRecord relation : state.getRelations().values()) {
            if (relation == null) continue;
            if (relation.getType() == spec.getType()
                    && relation.getFromKind() == spec.getFromKind()
                    && relation.getToKind() == spec.getToKind()
                    && safeEquals(relation.getFromId(), spec.getFromId())
                    && safeEquals(relation.getToId(), spec.getToId())) {
                return relation;
            }
        }
        return null;
    }

    private boolean wouldCreateCycle(HarnessState state, RelationType type, String fromId, String toId) {
        if (safeEquals(fromId, toId)) return true;
        Set<String> visited = new LinkedHashSet<String>();
        return reaches(state, type, toId, fromId, visited);
    }

    private boolean reaches(HarnessState state, RelationType type, String current, String target, Set<String> visited) {
        if (!visited.add(current)) return false;
        for (RelationRecord relation : state.getRelations().values()) {
            if (relation == null || relation.getType() != type || !safeEquals(relation.getFromId(), current)) continue;
            if (safeEquals(relation.getToId(), target) || reaches(state, type, relation.getToId(), target, visited)) return true;
        }
        return false;
    }

    private boolean dependenciesSatisfied(HarnessState state, String taskId) {
        for (RelationRecord relation : state.getRelations().values()) {
            if (relation == null || relation.getType() != RelationType.DEPENDS_ON
                    || !safeEquals(relation.getFromId(), taskId)) continue;
            TaskRecord dependency = state.getTasks().get(relation.getToId());
            if (dependency == null || !TaskStatus.DONE.equals(dependency.getStatus())) return false;
        }
        return true;
    }

    private boolean hasOutstandingExecution(HarnessState state, String taskId) {
        return hasOutstandingExecution(state, taskId, null);
    }

    private boolean hasOutstandingExecution(HarnessState state,
                                            String taskId,
                                            String excludedExecutionId) {
        for (ExecutionRecord execution : state.getExecutions().values()) {
            if (execution != null && safeEquals(taskId, execution.getTaskId())
                    && !safeEquals(excludedExecutionId, execution.getExecutionId())
                    && (ExecutionStatus.READY.equals(execution.getStatus())
                    || ExecutionStatus.RUNNING.equals(execution.getStatus())
                    || ExecutionStatus.WAITING.equals(execution.getStatus()))) return true;
        }
        return false;
    }

    private List<WaitRecord> openWaits(HarnessState state, String executionId) {
        List<WaitRecord> result = new ArrayList<WaitRecord>();
        if (state == null || executionId == null) {
            return result;
        }
        for (WaitRecord wait : state.getWaits().values()) {
            if (wait != null && safeEquals(executionId, wait.getExecutionId())
                    && WaitStatus.OPEN.equals(wait.getStatus())) {
                result.add(wait);
            }
        }
        Collections.sort(result, (left, right) -> {
            int time = Long.compare(left.getCreatedAtEpochMs(), right.getCreatedAtEpochMs());
            return time != 0 ? time : String.valueOf(left.getWaitId())
                    .compareTo(String.valueOf(right.getWaitId()));
        });
        return result;
    }

    private WaitRecord firstOpenWait(HarnessState state, String executionId) {
        List<WaitRecord> waits = openWaits(state, executionId);
        return waits.isEmpty() ? null : waits.get(0);
    }

    /**
     * Cancels resumable activity when a Task is cancelled. A RUNNING
     * Execution is left to its lease holder because its external side effect
     * may already be in flight; persistExecutionOutcome will fence its normal
     * success/continuation result and record cancellation instead.
     */
    private void cancelTaskActivity(HarnessState state,
                                    String taskId,
                                    String reason,
                                    HarnessActor actor) {
        long currentTime = now();
        String cancellationReason = reason == null || reason.trim().isEmpty()
                ? "task cancelled" : reason;
        for (WaitRecord wait : state.getWaits().values()) {
            if (wait == null || !safeEquals(taskId, wait.getTaskId())
                    || !WaitStatus.OPEN.equals(wait.getStatus())) {
                continue;
            }
            wait.setStatus(WaitStatus.CANCELLED);
            wait.setResolvedAtEpochMs(currentTime);
            Map<String, Object> payload = wait.getPayload() == null
                    ? new LinkedHashMap<String, Object>() : wait.getPayload();
            payload.put("cancellationReason", cancellationReason);
            wait.setPayload(payload);
            addEvent(state, "wait.cancelled", wait.getWaitId(), actor,
                    mapOf("taskId", taskId, "reason", cancellationReason));
        }
        for (ToolInvocationRecord invocation : state.getToolInvocations().values()) {
            if (invocation == null || !safeEquals(taskId, invocation.getTaskId())
                    || (ToolInvocationStatus.STARTED != invocation.getStatus()
                    && ToolInvocationStatus.WAITING != invocation.getStatus())) {
                continue;
            }
            invocation.setStatus(ToolInvocationStatus.CANCELLED);
            invocation.setError(cancellationReason);
            invocation.setUpdatedAtEpochMs(currentTime);
            invocation.setVersion(invocation.getVersion() + 1L);
            addEvent(state, "tool.invocation_cancelled", invocation.getInvocationId(), actor,
                    mapOf("taskId", taskId, "executionId", invocation.getExecutionId(),
                            "waitId", invocation.getWaitId(), "operationId", invocation.getOperationId(),
                            "reason", cancellationReason));
        }
        for (ExecutionRecord execution : state.getExecutions().values()) {
            if (execution == null || !safeEquals(taskId, execution.getTaskId())
                    || (ExecutionStatus.READY != execution.getStatus()
                    && ExecutionStatus.WAITING != execution.getStatus())) {
                continue;
            }
            execution.setStatus(ExecutionStatus.CANCELLED);
            execution.setWaitId(null);
            execution.setOperationId(null);
            execution.setError(cancellationReason);
            execution.setFinishedAtEpochMs(currentTime);
            execution.setUpdatedAtEpochMs(currentTime);
            execution.setVersion(execution.getVersion() + 1L);
            addEvent(state, "execution.cancelled", execution.getExecutionId(), actor,
                    mapOf("taskId", taskId, "reason", cancellationReason));
        }
    }

    private boolean isRunnableStatus(TaskStatus status) {
        return TaskStatus.PLANNED.equals(status) || TaskStatus.ACTIVE.equals(status);
    }

    private void acquireSessionLease(HarnessState state,
                                     ExecutionRecord execution,
                                     String workerId,
                                     String leaseId,
                                     long fencingToken,
                                     long acquiredAt,
                                     long expiresAt) {
        String sessionId = trimToNull(execution.getSessionId());
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
        addEvent(state, "session.lease_acquired", sessionId, HarnessActor.worker(workerId),
                mapOf("executionId", execution.getExecutionId(), "leaseId", leaseId,
                        "fencingToken", fencingToken));
    }

    /**
     * Ensures a RUNNING execution still owns its session lease. A missing
     * record can be reconstructed from the execution lease; an expired or
     * differently-owned record fences the execution instead of allowing a
     * session to be used concurrently.
     */
    private boolean ensureSessionLeaseForRunning(HarnessState state,
                                                 ExecutionRecord execution,
                                                 LeaseRecord executionLease,
                                                 long currentTime) {
        String sessionId = trimToNull(execution.getSessionId());
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
            addEvent(state, "session.lease_reconstructed", sessionId,
                    HarnessActor.worker(execution.getWorkerId()),
                    mapOf("executionId", execution.getExecutionId(), "leaseId", executionLease.getLeaseId()));
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
        String sessionId = trimToNull(execution.getSessionId());
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

    private void releaseSessionLease(HarnessState state,
                                     ExecutionRecord execution,
                                     String leaseId,
                                     long fencingToken,
                                     String workerId,
                                     long releasedAt) {
        String sessionId = execution == null ? null : trimToNull(execution.getSessionId());
        if (sessionId == null) {
            return;
        }
        SessionLeaseRecord current = state.getSessionLeases().get(sessionId);
        if (current == null || !sameSessionLease(current, execution, leaseId, fencingToken, workerId)) {
            return;
        }
        current.setReleasedAtEpochMs(releasedAt);
        addEvent(state, "session.lease_released", sessionId,
                HarnessActor.worker(workerId == null ? "unknown" : workerId),
                mapOf("executionId", execution.getExecutionId(), "leaseId", leaseId,
                        "fencingToken", fencingToken));
    }

    private boolean sameSessionLease(SessionLeaseRecord sessionLease,
                                     ExecutionRecord execution,
                                     String leaseId,
                                     long fencingToken,
                                     String workerId) {
        return sessionLease != null && execution != null
                && safeEquals(sessionLease.getSessionId(), trimToNull(execution.getSessionId()))
                && safeEquals(sessionLease.getExecutionId(), execution.getExecutionId())
                && safeEquals(sessionLease.getLeaseId(), leaseId)
                && sessionLease.getFencingToken() == fencingToken
                && safeEquals(sessionLease.getWorkerId(), workerId);
    }

    /** Marks an execution and its resumable activity UNKNOWN in one mutation. */
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
        addEvent(state, "execution.unknown", executionId, actor,
                mapOf("taskId", execution.getTaskId(), "workerId", oldWorkerId,
                        "leaseId", oldLeaseId, "fencingToken", oldFencingToken,
                        "reason", reason));
    }

    private void cancelOpenWaitsForExecution(HarnessState state,
                                             String executionId,
                                             String reason,
                                             HarnessActor actor,
                                             long currentTime) {
        for (WaitRecord wait : state.getWaits().values()) {
            if (wait == null || !safeEquals(executionId, wait.getExecutionId())
                    || !WaitStatus.OPEN.equals(wait.getStatus())) {
                continue;
            }
            wait.setStatus(WaitStatus.CANCELLED);
            wait.setResolvedAtEpochMs(currentTime);
            Map<String, Object> payload = copyMap(wait.getPayload());
            payload.put("cancellationReason", reason);
            payload.put("executionUnknown", true);
            wait.setPayload(payload);
            addEvent(state, "wait.cancelled", wait.getWaitId(), actor,
                    mapOf("executionId", executionId, "reason", reason));
        }
    }

    private void markExecutionToolInvocationsUnknown(HarnessState state,
                                                     String executionId,
                                                     String reason,
                                                     HarnessActor actor,
                                                     long currentTime) {
        for (ToolInvocationRecord invocation : state.getToolInvocations().values()) {
            if (invocation == null || !safeEquals(executionId, invocation.getExecutionId())
                    || (ToolInvocationStatus.STARTED != invocation.getStatus()
                    && ToolInvocationStatus.WAITING != invocation.getStatus())) {
                continue;
            }
            invocation.setStatus(ToolInvocationStatus.UNKNOWN);
            invocation.setError(reason);
            invocation.setUpdatedAtEpochMs(currentTime);
            invocation.setVersion(invocation.getVersion() + 1L);
            addEvent(state, "tool.invocation_unknown", invocation.getInvocationId(), actor,
                    mapOf("executionId", executionId, "waitId", invocation.getWaitId(),
                            "operationId", invocation.getOperationId(), "reason", reason));
        }
    }

    private void assertSameToolInvocation(ToolInvocationRecord existing,
                                          HarnessToolInvocationSpec requested,
                                          String scopeKey) {
        if (!safeEquals(existing.getExecutionId(), requested.getExecutionId())
                || !safeEquals(existing.getToolName(), requested.getToolName())
                || !safeEquals(existing.getCallId(), requested.getCallId())
                || !safeEquals(existing.getTaskId(), requested.getTaskId())
                || !safeEquals(existing.getSessionId(), requested.getSessionId())
                || !safeEquals(existing.getScopeKey(), scopeKey)
                || !equivalentNullableArguments(existing.getArguments(), requested.getArguments())) {
            throw new HarnessConflictException("tool invocation id is already bound to another call: "
                    + existing.getInvocationId());
        }
    }

    private ToolInvocationRecord requireApprovalInvocation(HarnessState state,
                                                           String invocationId,
                                                           ExecutionRecord execution,
                                                           String toolName,
                                                           String callId,
                                                           String arguments) {
        ToolInvocationRecord invocation = state.getToolInvocations().get(invocationId);
        if (invocation == null) {
            throw new HarnessValidationException("tool invocation not found: " + invocationId);
        }
        if (!safeEquals(invocation.getExecutionId(), execution.getExecutionId())
                || !safeEquals(invocation.getToolName(), toolName)
                || !safeEquals(invocation.getCallId(), callId)
                || !equivalentNullableArguments(invocation.getArguments(), arguments)) {
            throw new HarnessConflictException("approval invocation does not match the requested tool call: "
                    + invocationId);
        }
        if (!ToolInvocationStatus.STARTED.equals(invocation.getStatus())
                && !ToolInvocationStatus.WAITING.equals(invocation.getStatus())) {
            throw new HarnessConflictException("tool invocation cannot wait from status "
                    + invocation.getStatus() + ": " + invocationId);
        }
        return invocation;
    }

    private void linkApprovalInvocation(ToolInvocationRecord invocation,
                                        WaitRecord wait,
                                        HarnessActor actor,
                                        HarnessState state) {
        if (invocation == null || wait == null) {
            return;
        }
        if (ToolInvocationStatus.WAITING.equals(invocation.getStatus())) {
            if (invocation.getWaitId() != null
                    && !safeEquals(invocation.getWaitId(), wait.getWaitId())) {
                throw new HarnessConflictException("tool invocation approval wait changed: "
                        + invocation.getInvocationId());
            }
            if (invocation.getWaitId() == null) {
                invocation.setWaitId(wait.getWaitId());
                invocation.setUpdatedAtEpochMs(now());
                invocation.setVersion(invocation.getVersion() + 1L);
            }
            return;
        }
        if (!ToolInvocationStatus.STARTED.equals(invocation.getStatus())) {
            throw new HarnessConflictException("tool invocation cannot wait from status "
                    + invocation.getStatus() + ": " + invocation.getInvocationId());
        }
        invocation.setStatus(ToolInvocationStatus.WAITING);
        invocation.setOperationId(null);
        invocation.setWaitId(wait.getWaitId());
        invocation.setUpdatedAtEpochMs(now());
        invocation.setVersion(invocation.getVersion() + 1L);
        addEvent(state, "tool.invocation_waiting", invocation.getInvocationId(), actor,
                mapOf("operationId", null, "waitId", wait.getWaitId(), "reason", "approval"));
    }

    private boolean approvedInvocationRetry(HarnessState state,
                                            ToolInvocationRecord invocation,
                                            HarnessToolInvocationSpec requested) {
        return approvedInvocationRetry(state, invocation, requested.getToolName(),
                requested.getCallId(), requested.getArguments());
    }

    private boolean approvedInvocationRetry(HarnessState state,
                                            ToolInvocationRecord invocation,
                                            String toolName,
                                            String callId,
                                            String arguments) {
        String waitId = trimToNull(invocation.getWaitId());
        if (waitId == null) {
            return false;
        }
        WaitRecord wait = state.getWaits().get(waitId);
        if (wait == null || !WaitType.APPROVAL.equals(wait.getType())
                || !WaitStatus.DELIVERED.equals(wait.getStatus())
                || !safeEquals(wait.getExecutionId(), invocation.getExecutionId())) {
            return false;
        }
        Map<String, Object> payload = wait.getPayload();
        if (payload == null
                || !safeEquals(invocation.getInvocationId(), String.valueOf(
                payload.get(AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID)))
                || !safeEquals(toolName, String.valueOf(payload.get("toolName")))
                || !deliveryApproved(payload.get("delivery"))) {
            return false;
        }
        return approvalMatches(payload, callId, arguments);
    }

    private void assertSameToolInvocationForApprovedRetry(ToolInvocationRecord existing,
                                                          HarnessToolInvocationSpec requested,
                                                          String scopeKey) {
        if (!safeEquals(existing.getExecutionId(), requested.getExecutionId())
                || !safeEquals(existing.getToolName(), requested.getToolName())
                || !safeEquals(existing.getTaskId(), requested.getTaskId())
                || !safeEquals(existing.getSessionId(), requested.getSessionId())
                || !safeEquals(existing.getScopeKey(), scopeKey)
                || !equivalentNullableArguments(existing.getArguments(), requested.getArguments())) {
            throw new HarnessConflictException("approved retry does not match the original tool invocation: "
                    + existing.getInvocationId());
        }
    }

    private boolean equivalentNullableArguments(String left, String right) {
        if (left == null || right == null) {
            return safeEquals(left, right);
        }
        return equivalentArguments(left, right);
    }

    private void recordQuarantinedCompletion(HarnessState state,
                                             ToolInvocationRecord invocation,
                                             ToolInvocationStatus status,
                                             String operationId,
                                             String waitId,
                                             String output,
                                             String error,
                                             HarnessActor actor) {
        String effectiveWaitId = waitId == null ? invocation.getWaitId() : waitId;
        WaitRecord wait = effectiveWaitId == null ? null : state.getWaits().get(effectiveWaitId);
        String effectiveOperationId = operationId == null ? invocation.getOperationId() : operationId;
        addEvent(state, "async.completion_quarantined", invocation.getInvocationId(), actor,
                mapOf("waitId", effectiveWaitId,
                        "operationId", effectiveOperationId,
                        "invocationId", invocation.getInvocationId(),
                        "waitStatus", wait == null ? null : wait.getStatus(),
                        "delivery", "quarantined",
                        "error", error,
                        "output", output,
                        "lateCompletion", true,
                        "sideEffectStatus", ToolInvocationStatus.SUCCEEDED.equals(status)
                                ? "possibly_applied" : "unknown"));
    }

    private void requireTaskTransition(TaskStatus from, TaskStatus to) {
        if (from == to) return;
        boolean allowed = false;
        if (TaskStatus.PLANNED.equals(from)) allowed = to == TaskStatus.ACTIVE || to == TaskStatus.BLOCKED || to == TaskStatus.CANCELLED;
        if (TaskStatus.ACTIVE.equals(from)) allowed = to == TaskStatus.WAITING || to == TaskStatus.BLOCKED || to == TaskStatus.IN_REVIEW || to == TaskStatus.CANCELLED;
        if (TaskStatus.WAITING.equals(from)) allowed = to == TaskStatus.ACTIVE || to == TaskStatus.BLOCKED || to == TaskStatus.CANCELLED;
        if (TaskStatus.BLOCKED.equals(from)) allowed = to == TaskStatus.PLANNED || to == TaskStatus.ACTIVE || to == TaskStatus.CANCELLED;
        if (TaskStatus.IN_REVIEW.equals(from)) allowed = to == TaskStatus.ACTIVE || to == TaskStatus.CANCELLED;
        if (!allowed || TaskStatus.DONE.equals(from) || TaskStatus.CANCELLED.equals(from)) {
            throw new HarnessConflictException("illegal task transition: " + from + " -> " + to);
        }
    }

    private void assertLease(HarnessState state, ExecutionRecord execution, String leaseId,
                             long fencingToken, String workerId) {
        if (leaseId == null || execution.getLeaseId() == null || !leaseId.equals(execution.getLeaseId())
                || execution.getFencingToken() != fencingToken || workerId == null
                || !workerId.equals(execution.getWorkerId())) {
            throw new HarnessConflictException("stale execution lease or fencing token: " + execution.getExecutionId());
        }
        LeaseRecord lease = state.getLeases().get(leaseId);
        if (lease == null || lease.isExpired(now()) || lease.getFencingToken() != fencingToken
                || !workerId.equals(lease.getWorkerId())) {
            throw new HarnessConflictException("execution lease is expired or fenced: " + execution.getExecutionId());
        }
    }

    private void assertSnapshotMatchesExecution(ExecutionRecord execution,
                                                 AgentSessionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (!safeEquals(trimToNull(execution.getSessionId()), trimToNull(snapshot.getSessionId()))) {
            throw new HarnessConflictException("session snapshot does not belong to execution session: "
                    + execution.getExecutionId());
        }
        String snapshotRunId = trimToNull(snapshot.getRunId());
        if (snapshotRunId != null
                && !safeEquals(trimToNull(execution.getRunId()), snapshotRunId)) {
            throw new HarnessConflictException("session snapshot run id does not match execution: "
                    + execution.getExecutionId());
        }
    }

    /**
     * A Session has one stable Agent run identity across independent Harness
     * Executions. Reuse it when a new Execution targets a Session that already
     * has a durable snapshot; otherwise create the first run identity.
     */
    private String resolveExecutionRunId(HarnessState state, HarnessExecutionSpec spec) {
        String requestedRunId = trimToNull(spec.getRunId());
        String sessionId = trimToNull(spec.getSessionId());
        AgentSessionSnapshot snapshot = sessionId == null || state == null
                ? null : state.getSessions().get(sessionId);
        String sessionRunId = snapshot == null ? null : trimToNull(snapshot.getRunId());
        if (requestedRunId != null) {
            if (sessionRunId != null && !safeEquals(requestedRunId, sessionRunId)) {
                throw new HarnessConflictException("execution run id does not match session snapshot: "
                        + sessionId);
            }
            return requestedRunId;
        }
        return sessionRunId == null ? valueOrGenerated(null, "run_") : sessionRunId;
    }

    private boolean hasApprovedReview(HarnessState state, String submissionId) {
        ReviewRecord latest = null;
        for (ReviewRecord review : state.getReviews().values()) {
            if (review == null || !safeEquals(submissionId, review.getSubmissionId())) {
                continue;
            }
            if (latest == null || review.getCreatedAtEpochMs() >= latest.getCreatedAtEpochMs()) {
                latest = review;
            }
        }
        return latest != null && ReviewVerdict.APPROVED.equals(latest.getVerdict());
    }

    private void assertCurrentTaskExecution(TaskRecord task, String executionId) {
        String currentExecutionId = trimToNull(task == null ? null : task.getLastExecutionId());
        if (currentExecutionId != null && !currentExecutionId.equals(executionId)) {
            throw new HarnessConflictException("execution is no longer the current task execution: "
                    + executionId);
        }
    }

    boolean deliveryApproved(Object value) {
        if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object approved = map.get("approved");
            if (approved instanceof Boolean) return ((Boolean) approved).booleanValue();
            Object decision = map.get("decision");
            if (decision != null) return isApprovalWord(String.valueOf(decision));
        }
        return value != null && isApprovalWord(String.valueOf(value));
    }

    private boolean approvalMatches(Map<String, Object> payload,
                                    String callId,
                                    String arguments) {
        String approvedCallId = trimToNull(payload == null ? null : stringValue(payload.get("callId")));
        String approvedArguments = trimToNull(payload == null ? null : stringValue(payload.get("arguments")));
        String requestedCallId = trimToNull(callId);
        String requestedArguments = trimToNull(arguments);
        if (approvedCallId == null && approvedArguments == null) {
            return true;
        }
        if (approvedCallId != null && requestedCallId != null
                && approvedCallId.equals(requestedCallId)) {
            return approvedArguments == null || requestedArguments == null
                    || equivalentArguments(approvedArguments, requestedArguments);
        }
        return approvedArguments != null && requestedArguments != null
                && equivalentArguments(approvedArguments, requestedArguments);
    }

    private boolean equivalentArguments(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        try {
            Object leftValue = JSON.parse(left);
            Object rightValue = JSON.parse(right);
            return leftValue == null ? rightValue == null : leftValue.equals(rightValue);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isApprovalWord(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return "approve".equals(normalized) || "approved".equals(normalized)
                || "allow".equals(normalized) || "allowed".equals(normalized)
                || "yes".equals(normalized) || "true".equals(normalized);
    }

    private int nextAttempt(HarnessState state, String taskId) {
        int max = 0;
        for (ExecutionRecord execution : state.getExecutions().values()) {
            if (execution != null && safeEquals(taskId, execution.getTaskId())) max = Math.max(max, execution.getAttempt());
        }
        return max + 1;
    }

    private long nextFencingToken(HarnessState state) {
        long max = 0L;
        for (LeaseRecord lease : state.getLeases().values()) {
            if (lease != null) max = Math.max(max, lease.getFencingToken());
        }
        return max + 1L;
    }

    private boolean isTerminalExecution(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCEEDED || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.UNKNOWN || status == ExecutionStatus.CANCELLED;
    }

    private TaskRecord requireTask(HarnessState state, String taskId) {
        TaskRecord task = state.getTasks().get(taskId);
        if (task == null) throw new HarnessValidationException("task not found: " + taskId);
        return task;
    }

    private ExecutionRecord requireExecution(HarnessState state, String executionId) {
        ExecutionRecord execution = state.getExecutions().get(executionId);
        if (execution == null) throw new HarnessValidationException("execution not found: " + executionId);
        return execution;
    }

    private HarnessProvenance provenance(HarnessActor actor, String executionId, String sessionId,
                                         String sourceType, String sourceId) {
        return HarnessProvenance.builder()
                .actor(copyActor(actor))
                .executionId(executionId)
                .sessionId(sessionId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .recordedAtEpochMs(now())
                .build();
    }

    private void addEvent(HarnessState state, String type, String entityId,
                          HarnessActor actor, Map<String, Object> payload) {
        List<HarnessEventRecord> events = state.getEvents();
        long sequence = 1L;
        if (events != null && !events.isEmpty()) sequence = events.get(events.size() - 1).getSequence() + 1L;
        if (events == null) {
            events = new ArrayList<HarnessEventRecord>();
            state.setEvents(events);
        }
        events.add(HarnessEventRecord.builder()
                .sequence(sequence)
                .type(type)
                .entityId(entityId)
                .actorId(actorKey(actor))
                .recordedAtEpochMs(now())
                .payload(copyMap(payload))
                .build());
    }

    private <T> T write(final StateCommand<T> command) {
        if (command == null) throw new IllegalArgumentException("Harness command is required");
        final ValueHolder<T> holder = new ValueHolder<T>();
        store.update(new HarnessStateMutation() {
            @Override
            public HarnessState apply(HarnessState current) {
                current.ensureCollections();
                holder.value = command.apply(current);
                return current;
            }
        });
        return holder.value;
    }

    private String idempotentId(HarnessState state,
                                String entityType,
                                String scopeKey,
                                String key) {
        String normalizedKey = trimToNull(key);
        if (normalizedKey == null) {
            return null;
        }
        String normalizedScope = normalizeScope(scopeKey);
        String namespacedId = state.getIdempotency().get(
                idempotencyNamespace(entityType, normalizedScope, normalizedKey));
        if (isIdempotencyTarget(state, entityType, namespacedId)
                && sameEntityScope(state, entityType, namespacedId, normalizedScope)) {
            return namespacedId;
        }

        // State written before scoped idempotency was introduced used the raw
        // key. Read it only when the mapped entity has the exact requested
        // type and scope; a legacy raw key must never cross either boundary.
        String legacyId = state.getIdempotency().get(normalizedKey);
        if (isIdempotencyTarget(state, entityType, legacyId)
                && sameEntityScope(state, entityType, legacyId, normalizedScope)) {
            return legacyId;
        }
        return null;
    }

    private void rememberIdempotency(HarnessState state,
                                     String entityType,
                                     String scopeKey,
                                     String key,
                                     String id) {
        String normalizedKey = trimToNull(key);
        if (normalizedKey != null && id != null) {
            state.getIdempotency().put(idempotencyNamespace(entityType,
                    normalizeScope(scopeKey), normalizedKey), id);
        }
    }

    private String idempotencyNamespace(String entityType, String scopeKey, String key) {
        return "v2|" + idempotencySegment(entityType)
                + "|" + idempotencySegment(normalizeScope(scopeKey))
                + "|" + idempotencySegment(key);
    }

    private String idempotencySegment(String value) {
        if (value == null) {
            return "-";
        }
        return value.length() + ":" + value;
    }

    private boolean isIdempotencyTarget(HarnessState state, String entityType, String id) {
        if (id == null) {
            return false;
        }
        if (IDEMPOTENCY_TASK.equals(entityType)) {
            return state.getTasks().containsKey(id);
        }
        if (IDEMPOTENCY_EXECUTION.equals(entityType)) {
            return state.getExecutions().containsKey(id);
        }
        return false;
    }

    private boolean sameEntityScope(HarnessState state,
                                    String entityType,
                                    String id,
                                    String scopeKey) {
        if (!isIdempotencyTarget(state, entityType, id)) {
            return false;
        }
        return safeEquals(normalizeScope(scopeKey), entityScope(state,
                IDEMPOTENCY_TASK.equals(entityType) ? EntityKind.TASK : EntityKind.EXECUTION, id));
    }

    private String valueOrGenerated(String value, String prefix) {
        String normalized = trimToNull(value);
        return normalized == null ? prefix + UUID.randomUUID().toString().replace("-", "") : normalized;
    }

    private String requireText(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new HarnessValidationException(label + " is required");
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeScope(String value) {
        return trimToNull(value);
    }

    private void assertCompatibleScope(String left,
                                       String right,
                                       String message) {
        String normalizedLeft = normalizeScope(left);
        String normalizedRight = normalizeScope(right);
        if (normalizedLeft != null && normalizedRight != null
                && !normalizedLeft.equals(normalizedRight)) {
            throw new HarnessConflictException(message + ": "
                    + normalizedLeft + " != " + normalizedRight);
        }
    }

    private void assertSameScope(String left, String right, String message) {
        String normalizedLeft = normalizeScope(left);
        String normalizedRight = normalizeScope(right);
        if (!safeEquals(normalizedLeft, normalizedRight)) {
            throw new HarnessConflictException(message + ": "
                    + String.valueOf(normalizedLeft) + " != "
                    + String.valueOf(normalizedRight));
        }
    }

    private boolean visibleScope(String entityScope, String requestedScope) {
        String scope = normalizeScope(requestedScope);
        return scope == null || (normalizeScope(entityScope) != null
                && scope.equals(normalizeScope(entityScope)));
    }

    private boolean visibleRelationScope(RelationRecord relation, String requestedScope) {
        if (relation == null) {
            return false;
        }
        String scope = normalizeScope(requestedScope);
        if (scope == null) {
            return true;
        }
        return visibleScope(relation.getScopeKey(), scope);
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private String actorKey(HarnessActor actor) {
        HarnessActor effective = normalizeActor(actor, defaultActor);
        return effective.getKind() + ":" + effective.getId();
    }

    private HarnessActor normalizeActor(HarnessActor actor, HarnessActor fallback) {
        HarnessActor source = actor == null ? fallback : actor;
        if (source == null || trimToNull(source.getKind()) == null || trimToNull(source.getId()) == null) {
            throw new HarnessValidationException("actor kind and id are required");
        }
        return source;
    }

    private HarnessActor copyActor(HarnessActor actor) {
        return actor == null ? null : HarnessActor.builder().kind(actor.getKind()).id(actor.getId()).displayName(actor.getDisplayName()).build();
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(source);
    }

    private List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (values == null) return map;
        for (int i = 0; i + 1 < values.length; i += 2) map.put(String.valueOf(values[i]), values[i + 1]);
        return map;
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean isGraphRelation(RelationType type) {
        return RelationType.PARENT_OF.equals(type) || RelationType.DEPENDS_ON.equals(type);
    }

    private static class ValueHolder<T> {
        private T value;
    }

    private static class ConflictHolder {
        private String message;
    }

    private static class CompletionFailure {
        private String reason;
    }

    private interface StateCommand<T> {
        T apply(HarnessState state);
    }
}
