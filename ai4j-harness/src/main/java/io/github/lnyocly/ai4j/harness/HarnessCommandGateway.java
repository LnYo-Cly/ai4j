package io.github.lnyocly.ai4j.harness;

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
 * task means, while this facade coordinates durable task decomposition,
 * dependency safety, provenance, leases, epoch fencing, CQRS event journaling,
 * projections, waits, checkpoints, and completion gates via cohesive
 * collaborators ({@link HarnessEventJournal}, {@link HarnessProjectionEngine},
 * and {@link HarnessLeaseCoordinator}).</p>
 */
public final class HarnessCommandGateway implements AutoCloseable {

    private final HarnessStore store;
    private final HarnessContract contract;
    private final HarnessActor defaultActor;
    private final HarnessEventJournal eventJournal;
    private final HarnessProjectionEngine projectionEngine;
    private final HarnessLeaseCoordinator leaseCoordinator;
    private final KnowledgeLedgerService knowledgeLedgerService;
    private final GovernanceReviewService governanceReviewService;
    private final AgendaQueryService agendaQueryService;
    private final ExecutionCoordinationService executionCoordinationService;

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
        this.defaultActor = HarnessSupport.normalizeActor(defaultActor, HarnessActor.agent("harness-agent"));
        this.eventJournal = new HarnessEventJournal(this.store, this.defaultActor);
        this.projectionEngine = new HarnessProjectionEngine(this.store, this.contract);
        this.leaseCoordinator = new HarnessLeaseCoordinator(
                this.store, this.contract, this.eventJournal, this.defaultActor);
        this.knowledgeLedgerService = new KnowledgeLedgerService(
                this.store, this.contract, this.eventJournal, this.defaultActor);
        this.governanceReviewService = new GovernanceReviewService(
                this.store, this.contract, this.eventJournal, this.projectionEngine, this.defaultActor);
        this.agendaQueryService = new AgendaQueryService(
                this.store, this.contract, this.eventJournal, this.projectionEngine,
                this.leaseCoordinator, this.defaultActor);
        this.executionCoordinationService = new ExecutionCoordinationService(
                this.store, this.eventJournal, this.leaseCoordinator, this.defaultActor);
    }

    public HarnessStore getStore() {
        return store;
    }

    public HarnessContract getContract() {
        return contract;
    }

    public HarnessActor getDefaultActor() {
        return HarnessSupport.copyActor(defaultActor);
    }

    public HarnessEventJournal getEventJournal() {
        return eventJournal;
    }

    public HarnessProjectionEngine getProjectionEngine() {
        return projectionEngine;
    }

    public HarnessLeaseCoordinator getLeaseCoordinator() {
        return leaseCoordinator;
    }

    public KnowledgeLedgerService getKnowledgeLedgerService() {
        return knowledgeLedgerService;
    }

    public GovernanceReviewService getGovernanceReviewService() {
        return governanceReviewService;
    }

    public AgendaQueryService getAgendaQueryService() {
        return agendaQueryService;
    }

    public ExecutionCoordinationService getExecutionCoordinationService() {
        return executionCoordinationService;
    }

    public HarnessState getState() {
        return projectionEngine.getState();
    }

    // ---- Read Projections & Cursor Pagination ----

    public TaskRecord getTask(String taskId) {
        return projectionEngine.getTask(taskId);
    }

    /** Returns a Task only when it belongs to the caller's opaque scope. */
    public TaskRecord getTaskInScope(String taskId, String scopeKey) {
        return projectionEngine.getTaskInScope(taskId, scopeKey);
    }

    public List<TaskRecord> listTasks() {
        return listTasks(null);
    }

    public List<TaskRecord> listTasks(String scopeKey) {
        return projectionEngine.listTasks(scopeKey);
    }

    public CursorPage<TaskRecord> listTasksPage(TaskStatus status, String cursor, int limit) {
        return projectionEngine.listTasksPage(status, cursor, limit);
    }

    public CursorPage<TaskRecord> listTasksPage(String scopeKey,
                                                TaskStatus status,
                                                String cursor,
                                                int limit) {
        return projectionEngine.listTasksPage(scopeKey, status, cursor, limit);
    }

    public CursorPage<HarnessEntityRecord> listEntitiesPage(String entityType,
                                                            String statusFilter,
                                                            String cursor,
                                                            int limit) {
        return projectionEngine.listEntitiesPage(entityType, statusFilter, cursor, limit);
    }

    public List<TaskRecord> listRunnableTasks() {
        return listRunnableTasks(null);
    }

    public List<TaskRecord> listRunnableTasks(String scopeKey) {
        return projectionEngine.listRunnableTasks(scopeKey);
    }

    public ExecutionRecord getExecution(String executionId) {
        return projectionEngine.getExecution(executionId);
    }

    public List<ExecutionRecord> listExecutions() {
        return projectionEngine.listExecutions();
    }

    public WaitRecord getWait(String waitId) {
        return projectionEngine.getWait(waitId);
    }

    /**
     * Returns every unresolved wait belonging to one Execution in creation order.
     */
    public List<WaitRecord> listOpenWaits(String executionId) {
        return projectionEngine.listOpenWaits(executionId);
    }

    public AgentSessionSnapshot getSessionSnapshot(String sessionId) {
        return projectionEngine.getSessionSnapshot(sessionId);
    }

    public CheckpointRecord getCheckpoint(String checkpointId) {
        return projectionEngine.getCheckpoint(checkpointId);
    }

    public WakeupRecord getLatestWakeup(String executionId) {
        return projectionEngine.getLatestWakeup(executionId);
    }

    public List<WaitRecord> listOpenWaits() {
        return listOpenWaitsInScope(null);
    }

    public List<WaitRecord> listOpenWaitsInScope(String scopeKey) {
        return projectionEngine.listOpenWaitsInScope(scopeKey);
    }

    public List<FactRecord> listFactsInScope(String scopeKey) {
        return projectionEngine.listFactsInScope(scopeKey);
    }

    public List<DecisionRecord> listDecisionsInScope(String scopeKey) {
        return projectionEngine.listDecisionsInScope(scopeKey);
    }

    public List<EvidenceRecord> listEvidenceInScope(String scopeKey) {
        return projectionEngine.listEvidenceInScope(scopeKey);
    }

    public List<RelationRecord> listRelations() {
        return listRelationsInScope(null);
    }

    public List<RelationRecord> listRelationsInScope(String scopeKey) {
        return projectionEngine.listRelationsInScope(scopeKey);
    }

    public RelationRecord getRelation(String relationId) {
        return projectionEngine.getRelation(relationId);
    }

    /** Returns a Relation only when it belongs to the caller's opaque scope. */
    public RelationRecord getRelationInScope(String relationId, String scopeKey) {
        return projectionEngine.getRelationInScope(relationId, scopeKey);
    }

    public ToolInvocationRecord getToolInvocation(String invocationId) {
        return projectionEngine.getToolInvocation(invocationId);
    }

    public ToolInvocationRecord getToolInvocationInScope(String invocationId, String scopeKey) {
        return projectionEngine.getToolInvocationInScope(invocationId, scopeKey);
    }

    public List<ToolInvocationRecord> listToolInvocationsInScope(String scopeKey) {
        return projectionEngine.listToolInvocationsInScope(scopeKey);
    }

    public List<HarnessRule> listLearnedRulesInScope(String scopeKey) {
        return projectionEngine.listLearnedRulesInScope(scopeKey);
    }

    public boolean toolRequiresTask(String executionId, String toolName) {
        return contract.requiresTaskForTool(toolName)
                || projectionEngine.hasLearnedToolRule(executionId, toolName, RuleKind.REQUIRE_TASK_FOR_TOOL);
    }

    public boolean toolRequiresApproval(String executionId, String toolName) {
        return contract.requiresApprovalForTool(toolName)
                || projectionEngine.hasLearnedToolRule(executionId, toolName, RuleKind.REQUIRE_APPROVAL_FOR_TOOL);
    }

    public List<AcceptanceRecord> listAcceptances(String executionId) {
        return projectionEngine.listAcceptances(executionId);
    }

    /** Returns the durable repair lineage from a root execution to the given execution. */
    public List<ExecutionRecord> listExecutionLineage(String executionId) {
        return projectionEngine.listExecutionLineage(executionId);
    }

    /** Returns acceptance records attached to every execution in the lineage, root first. */
    public List<AcceptanceRecord> listAcceptanceLineage(String executionId) {
        return projectionEngine.listAcceptanceLineage(executionId);
    }

    public HarnessLineageSummary summarizeLineage(String executionId) {
        return projectionEngine.summarizeLineage(executionId);
    }

    public Map<String, Object> delivery(String waitId) {
        WaitRecord wait = getWait(waitId);
        if (wait == null || wait.getPayload() == null) {
            return Collections.emptyMap();
        }
        Object value = wait.getPayload().get("delivery");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("value", value);
        result.put("waitId", wait.getWaitId());
        result.put("type", wait.getType());
        return result;
    }

    // ---- CQRS Event Ledger, Epoch Fencing & Fenced Mutations ----

    /** Records a durable observation without exposing the store to callers. */
    public HarnessEventRecord recordEvent(String type,
                                          String entityId,
                                          Map<String, Object> payload,
                                          HarnessActor actor) {
        return eventJournal.recordEvent(type, entityId, payload, actor);
    }

    public List<HarnessEventRecord> eventsSince(long afterSeq, int limit) {
        return eventJournal.eventsSince(afterSeq, limit);
    }

    public WriterEpochFence claimWriterEpoch(String nodeId, long expectedEpoch) {
        return leaseCoordinator.claimWriterEpoch(nodeId, expectedEpoch);
    }

    public WriterEpochFence currentWriterEpoch() {
        return leaseCoordinator.currentWriterEpoch();
    }

    public MutationReceipt executeFencedMutation(String entityType,
                                                 String entityId,
                                                 String eventType,
                                                 Long expectedVersion,
                                                 long writerEpoch,
                                                 String idempotencyKey,
                                                 Map<String, Object> payload,
                                                 HarnessActor actor) {
        return eventJournal.executeFencedMutation(entityType, entityId, eventType,
                expectedVersion, writerEpoch, idempotencyKey, payload, actor);
    }

    public MutationReceipt executeFencedMutation(HarnessEventRecord event) {
        return eventJournal.appendEventAndProject(event);
    }

    public MutationReceipt executeFencedMutation(HarnessEventRecord event,
                                                 String entityStatus,
                                                 String entityPayloadJson) {
        return eventJournal.appendEventAndProject(event, entityStatus, entityPayloadJson);
    }

    public int rebuildEntitiesFromEvents() {
        return eventJournal.rebuildEntitiesFromEvents();
    }

    public List<HarnessEntityRecord> snapshotProjectedEntities() {
        return projectionEngine.snapshotProjectedEntities();
    }

    public boolean applySyncedEntity(final HarnessEntityRecord entity, final HarnessActor actor) {
        if (entity == null) {
            return false;
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<Boolean>() {
            @Override
            public Boolean apply(HarnessState state) {
                boolean applied = HarnessProjectionEngine.applyProjectedEntity(state, entity);
                if (applied) {
                    eventJournal.addEvent(state, "fleet.entity_synced", entity.getEntityId(),
                            effectiveActor, HarnessSupport.mapOf("entityType", entity.getEntityType(),
                                    "entityVersion", entity.getEntityVersion(), "status", entity.getStatus()));
                }
                return Boolean.valueOf(applied);
            }
        }).booleanValue();
    }

    // ---- Tool Invocations & Approvals ----

    ToolInvocationRecord findApprovedWaitingToolInvocation(String executionId,
                                                           String scopeKey,
                                                           String toolName,
                                                           String callId,
                                                           String arguments) {
        HarnessState state = getState();
        ToolInvocationRecord match = null;
        for (ToolInvocationRecord invocation : state.getToolInvocations().values()) {
            if (invocation == null || !ToolInvocationStatus.WAITING.equals(invocation.getStatus())
                    || !HarnessSupport.safeEquals(executionId, invocation.getExecutionId())
                    || !HarnessSupport.visibleScope(invocation.getScopeKey(), scopeKey)
                    || !HarnessSupport.safeEquals(toolName, invocation.getToolName())
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

    public ToolInvocationRecord beginToolInvocation(HarnessToolInvocationSpec spec,
                                                    HarnessActor actor) {
        return reserveToolInvocation(spec, actor).getInvocation();
    }

    public ToolInvocationReservation reserveToolInvocation(HarnessToolInvocationSpec spec) {
        return reserveToolInvocation(spec, defaultActor);
    }

    public ToolInvocationReservation reserveToolInvocation(HarnessToolInvocationSpec spec,
                                                             HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("tool invocation specification is required");
        }
        final String invocationId = HarnessSupport.requireText(spec.getInvocationId(), "tool invocation id");
        final String executionId = HarnessSupport.requireText(spec.getExecutionId(), "execution id");
        final String toolName = HarnessSupport.requireText(spec.getToolName(), "tool name");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<ToolInvocationReservation>() {
            @Override
            public ToolInvocationReservation apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionId);
                String invocationScope = HarnessSupport.normalizeScope(spec.getScopeKey());
                HarnessSupport.assertCompatibleScope(invocationScope, execution.getScopeKey(),
                        "tool invocation and execution scopes must match");
                if (invocationScope == null) {
                    invocationScope = execution.getScopeKey();
                }
                if (spec.getTaskId() != null && !HarnessSupport.safeEquals(spec.getTaskId(), execution.getTaskId())) {
                    throw new HarnessConflictException("tool invocation task does not belong to execution: "
                            + invocationId);
                }
                if (spec.getSessionId() != null
                        && !HarnessSupport.safeEquals(spec.getSessionId(), execution.getSessionId())) {
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
                        existing.setUpdatedAtEpochMs(HarnessSupport.now());
                        existing.setVersion(existing.getVersion() + 1L);
                        eventJournal.addEvent(state, "tool.invocation_restarted_after_approval", invocationId,
                                effectiveActor, HarnessSupport.mapOf("executionId", executionId,
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
                long currentTime = HarnessSupport.now();
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
                        .createdBy(HarnessSupport.actorKey(effectiveActor, defaultActor))
                        .createdAtEpochMs(currentTime)
                        .updatedAtEpochMs(currentTime)
                        .version(1L)
                        .build();
                state.getToolInvocations().put(invocationId, invocation);
                eventJournal.addEvent(state, "tool.invocation_started", invocationId, effectiveActor,
                        HarnessSupport.mapOf("executionId", executionId, "taskId", invocation.getTaskId(),
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
        final String id = HarnessSupport.requireText(invocationId, "tool invocation id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<ToolInvocationRecord>() {
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
                            && !HarnessSupport.safeEquals(operationId, invocation.getOperationId())) {
                        throw new HarnessConflictException("tool invocation operation id changed: " + id);
                    }
                    if (waitId != null && invocation.getWaitId() != null
                            && !HarnessSupport.safeEquals(waitId, invocation.getWaitId())) {
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
                invocation.setUpdatedAtEpochMs(HarnessSupport.now());
                invocation.setVersion(invocation.getVersion() + 1L);
                eventJournal.addEvent(state, "tool.invocation_waiting", id, effectiveActor,
                        HarnessSupport.mapOf("operationId", operationId, "waitId", waitId));
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
        final String id = HarnessSupport.requireText(invocationId, "tool invocation id");
        if (status == null || ToolInvocationStatus.STARTED.equals(status)
                || ToolInvocationStatus.WAITING.equals(status)) {
            throw new HarnessValidationException("tool invocation completion must be terminal");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<ToolInvocationRecord>() {
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
                invocation.setUpdatedAtEpochMs(HarnessSupport.now());
                invocation.setVersion(invocation.getVersion() + 1L);
                eventJournal.addEvent(state, "tool.invocation_" + status.name().toLowerCase(), id,
                        effectiveActor, HarnessSupport.mapOf("operationId", operationId, "waitId", waitId,
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
        final String id = HarnessSupport.requireText(invocationId, "tool invocation id");
        if (resolution == null || ToolInvocationStatus.STARTED.equals(resolution)
                || ToolInvocationStatus.WAITING.equals(resolution)) {
            throw new HarnessValidationException("tool invocation reconciliation must be terminal");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<ToolInvocationRecord>() {
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
                invocation.setUpdatedAtEpochMs(HarnessSupport.now());
                invocation.setVersion(invocation.getVersion() + 1L);
                eventJournal.addEvent(state, "tool.invocation_reconciled", id, effectiveActor,
                        HarnessSupport.mapOf("resolution", resolution, "error", error));
                return invocation.copy();
            }
        });
    }

    // ---- Tasks & Relations ----

    public TaskRecord createTask(HarnessTaskSpec spec) {
        return createTask(spec, defaultActor);
    }

    public TaskRecord createTask(HarnessTaskSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("task specification is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final String taskId = HarnessSupport.valueOrGenerated(spec.getTaskId(), "task_");
        return write(new HarnessSupport.StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                String taskScope = taskSpecScope(state, spec);
                HarnessTaskSpec effectiveSpec = withScope(spec, taskScope);
                String existingId = idempotentId(state, HarnessSupport.IDEMPOTENCY_TASK, taskScope,
                        spec.getIdempotencyKey());
                if (existingId != null) {
                    TaskRecord existing = state.getTasks().get(existingId);
                    if (existing != null) {
                        return existing.copy();
                    }
                }
                TaskRecord task = createTaskInternal(state, effectiveSpec, taskId, effectiveActor);
                rememberIdempotency(state, HarnessSupport.IDEMPOTENCY_TASK, taskScope,
                        spec.getIdempotencyKey(), task.getTaskId());
                return task.copy();
            }
        });
    }

    /**
     * Creates a Task and attaches an unbound Execution in one durable state mutation.
     */
    public TaskRecord createTaskAndAttachExecution(HarnessTaskSpec spec,
                                                   String executionId,
                                                   HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("task specification is required");
        }
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final String taskId = HarnessSupport.valueOrGenerated(spec.getTaskId(), "task_");
        return write(new HarnessSupport.StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
                String taskScope = taskSpecScope(state, spec);
                HarnessSupport.assertCompatibleScope(taskScope, execution.getScopeKey(),
                        "task and execution scopes must match");
                if (taskScope == null) {
                    taskScope = HarnessSupport.normalizeScope(execution.getScopeKey());
                }
                HarnessTaskSpec effectiveSpec = withScope(spec, taskScope);
                String existingId = idempotentId(state, HarnessSupport.IDEMPOTENCY_TASK, taskScope,
                        spec.getIdempotencyKey());
                TaskRecord task = existingId == null ? null : state.getTasks().get(existingId);
                if (task == null) {
                    task = createTaskInternal(state, effectiveSpec, taskId, effectiveActor);
                    rememberIdempotency(state, HarnessSupport.IDEMPOTENCY_TASK, taskScope,
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
        final String parentId = HarnessSupport.requireText(parentTaskId, "parent task id");
        final List<HarnessTaskSpec> requested = children == null
                ? Collections.<HarnessTaskSpec>emptyList() : new ArrayList<HarnessTaskSpec>(children);
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<List<TaskRecord>>() {
            @Override
            public List<TaskRecord> apply(HarnessState state) {
                TaskRecord parent = HarnessSupport.requireTask(state, parentId);
                if (TaskStatus.DONE.equals(parent.getStatus()) || TaskStatus.CANCELLED.equals(parent.getStatus())) {
                    throw new HarnessValidationException("cannot split a terminal task: " + parentId);
                }
                List<TaskRecord> result = new ArrayList<TaskRecord>();
                for (HarnessTaskSpec childSpec : requested) {
                    if (childSpec == null) {
                        continue;
                    }
                    HarnessSupport.assertCompatibleScope(childSpec.getScopeKey(), parent.getScopeKey(),
                            "child task and parent task scopes must match");
                    HarnessTaskSpec effectiveSpec = childSpec.toBuilder()
                            .parentTaskId(parentId)
                            .scopeKey(childSpec.getScopeKey() == null
                                    ? parent.getScopeKey() : childSpec.getScopeKey())
                            .build();
                    String childId = HarnessSupport.valueOrGenerated(effectiveSpec.getTaskId(), "task_");
                    String childScope = HarnessSupport.normalizeScope(effectiveSpec.getScopeKey());
                    String existingId = idempotentId(state, HarnessSupport.IDEMPOTENCY_TASK, childScope,
                            effectiveSpec.getIdempotencyKey());
                    TaskRecord child = existingId == null ? null : state.getTasks().get(existingId);
                    if (child == null) {
                        child = createTaskInternal(state, effectiveSpec, childId, effectiveActor);
                        rememberIdempotency(state, HarnessSupport.IDEMPOTENCY_TASK, childScope,
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
        final String id = HarnessSupport.requireText(taskId, "task id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                TaskRecord task = HarnessSupport.requireTask(state, id);
                if (title != null && !title.trim().isEmpty()) task.setTitle(title.trim());
                if (goal != null) task.setGoal(goal);
                if (plan != null) task.setPlan(plan);
                if (metadata != null) task.setMetadata(new LinkedHashMap<String, Object>(metadata));
                task.setUpdatedAtEpochMs(HarnessSupport.now());
                task.setVersion(task.getVersion() + 1L);
                eventJournal.addEvent(state, "task.updated", id, effectiveActor, HarnessSupport.mapOf(
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
        final String id = HarnessSupport.requireText(taskId, "task id");
        if (target == null) {
            throw new HarnessValidationException("task target status is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final String transitionReason = reason;
        return write(new HarnessSupport.StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                TaskRecord task = HarnessSupport.requireTask(state, id);
                requireTaskTransition(task.getStatus(), target);
                if (TaskStatus.DONE.equals(target)) {
                    throw new HarnessValidationException("use completeTask for DONE transitions");
                }
                if (TaskStatus.IN_REVIEW.equals(target) && effectiveActor.isAgent()) {
                    throw new HarnessValidationException(
                            "an Agent must submit a task for review; it cannot enter review directly");
                }
                if (TaskStatus.ACTIVE.equals(target)
                        && !HarnessProjectionEngine.dependenciesSatisfied(state, id)) {
                    throw new HarnessConflictException("task dependencies are not complete: " + id);
                }
                TaskStatus previousStatus = task.getStatus();
                task.setStatus(target);
                task.setBlockedReason(TaskStatus.BLOCKED.equals(target) ? transitionReason : null);
                task.setUpdatedAtEpochMs(HarnessSupport.now());
                task.setVersion(task.getVersion() + 1L);
                if (TaskStatus.CANCELLED.equals(target)) {
                    cancelTaskActivity(state, id, transitionReason, effectiveActor);
                }
                eventJournal.addEvent(state, "task.transitioned", id, effectiveActor, HarnessSupport.mapOf(
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
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<RelationRecord>() {
            @Override
            public RelationRecord apply(HarnessState state) {
                validateRelationSpec(state, spec);
                RelationRecord existing = findRelation(state, spec);
                if (existing != null) {
                    return existing.copy();
                }
                if (isGraphRelation(spec.getType())
                        && HarnessProjectionEngine.wouldCreateCycle(state,
                        spec.getType(), spec.getFromId(), spec.getToId())) {
                    throw new HarnessConflictException("relation would create a cycle: "
                            + spec.getFromId() + " -> " + spec.getToId());
                }
                String relationScope = resolveRelationScope(state, spec);
                String id = HarnessSupport.valueOrGenerated(null, "rel_");
                RelationRecord relation = RelationRecord.builder()
                        .relationId(id)
                        .scopeKey(relationScope)
                        .type(spec.getType())
                        .fromKind(spec.getFromKind())
                        .fromId(spec.getFromId())
                        .toKind(spec.getToKind())
                        .toId(spec.getToId())
                        .createdBy(HarnessSupport.actorKey(effectiveActor, defaultActor))
                        .createdAtEpochMs(HarnessSupport.now())
                        .metadata(HarnessSupport.copyMap(spec.getMetadata()))
                        .build();
                state.getRelations().put(id, relation);
                eventJournal.addEvent(state, "relation.created", id, effectiveActor, HarnessSupport.mapOf(
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

    // ---- Facts, Decisions, Learned Rules, Evidence & Acceptance ----

    public FactRecord recordFact(HarnessFactSpec spec) {
        return recordFact(spec, defaultActor);
    }

    public FactRecord recordFact(HarnessFactSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("fact specification is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent() && !contract.acceptsAgentFact(spec)) {
            throw new HarnessValidationException("the Harness contract rejected this Agent fact");
        }
        final String factId = HarnessSupport.valueOrGenerated(spec.getFactId(), "fact_");
        return write(new HarnessSupport.StateCommand<FactRecord>() {
            @Override
            public FactRecord apply(HarnessState state) {
                if (state.getFacts().containsKey(factId)) {
                    throw new HarnessConflictException("fact already exists: " + factId);
                }
                String factTaskId = HarnessSupport.trimToNull(spec.getTaskId());
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
                        .statement(HarnessSupport.requireText(spec.getStatement(), "fact statement"))
                        .source(spec.getSource())
                        .confidence(spec.getConfidence())
                        .valid(true)
                        .version(1L)
                        .factTypeId(spec.getFactTypeId())
                        .factTypeVersion(spec.getFactTypeVersion())
                        .typedPayloadJson(spec.getTypedPayloadJson())
                        .createdAtEpochMs(HarnessSupport.now())
                        .provenance(HarnessSupport.provenance(effectiveActor, defaultActor, null, null, "fact", factId))
                        .evidenceIds(HarnessSupport.copyList(spec.getEvidenceIds()))
                        .metadata(HarnessSupport.copyMap(spec.getMetadata()))
                        .build();
                state.getFacts().put(factId, fact);
                eventJournal.addEvent(state, "fact.recorded", factId, effectiveActor, HarnessSupport.mapOf(
                        "taskId", fact.getTaskId(), "statement", fact.getStatement()));
                return fact.copy();
            }
        });
    }

    public FactRecord invalidateFact(String factId, String reason) {
        return invalidateFact(factId, reason, defaultActor);
    }

    public FactRecord invalidateFact(String factId, String reason, HarnessActor actor) {
        final String id = HarnessSupport.requireText(factId, "fact id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<FactRecord>() {
            @Override
            public FactRecord apply(HarnessState state) {
                FactRecord fact = state.getFacts().get(id);
                if (fact == null) {
                    throw new HarnessValidationException("fact not found: " + id);
                }
                fact.setValid(false);
                fact.setInvalidatedBy(HarnessSupport.actorKey(effectiveActor, defaultActor)
                        + (reason == null ? "" : ": " + reason));
                fact.setInvalidatedAtEpochMs(HarnessSupport.now());
                fact.setVersion(Math.max(1L, fact.getVersion()) + 1L);
                eventJournal.addEvent(state, "fact.invalidated", id, effectiveActor,
                        HarnessSupport.mapOf("reason", reason));
                return fact.copy();
            }
        });
    }

    public DecisionRecord proposeDecision(HarnessDecisionSpec spec) {
        return proposeDecision(spec, defaultActor);
    }

    public DecisionRecord proposeDecision(HarnessDecisionSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("decision specification is required");
        }
        if (contract.requiresFalsifiableDecisions()) {
            HarnessSupport.requireText(spec.getChosenOption(), "falsifiable decision chosen option");
            if (spec.getRejected() == null || spec.getRejected().isEmpty()) {
                throw new HarnessValidationException("falsifiable decision requires rejected alternatives");
            }
            if (spec.getClaims() == null || spec.getClaims().isEmpty()) {
                throw new HarnessValidationException("falsifiable decision requires verifiable claims");
            }
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final String decisionId = HarnessSupport.valueOrGenerated(spec.getDecisionId(), "decision_");
        return write(new HarnessSupport.StateCommand<DecisionRecord>() {
            @Override
            public DecisionRecord apply(HarnessState state) {
                if (state.getDecisions().containsKey(decisionId)) {
                    throw new HarnessConflictException("decision already exists: " + decisionId);
                }
                String decisionTaskId = HarnessSupport.trimToNull(spec.getTaskId());
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
                        .question(HarnessSupport.requireText(spec.getQuestion(), "decision question"))
                        .chosenOption(spec.getChosenOption())
                        .rejected(HarnessSupport.copyList(spec.getRejected()))
                        .claims(KnowledgeLedgerService.normalizeClaims(spec.getClaims()))
                        .rationale(spec.getRationale())
                        .status(DecisionStatus.PROPOSED)
                        .reckonStatus(ReckonStatus.UNRECKONED)
                        .proposer(HarnessSupport.copyActor(effectiveActor))
                        .createdAtEpochMs(HarnessSupport.now())
                        .version(1L)
                        .factIds(HarnessSupport.copyList(spec.getFactIds()))
                        .evidenceIds(HarnessSupport.copyList(spec.getEvidenceIds()))
                        .build();
                state.getDecisions().put(decisionId, decision);
                eventJournal.addEvent(state, "decision.proposed", decisionId, effectiveActor, HarnessSupport.mapOf(
                        "taskId", decision.getTaskId(), "question", decision.getQuestion()));
                return decision.copy();
            }
        });
    }

    public DecisionRecord resolveDecision(String decisionId,
                                          DecisionStatus status,
                                          String rationale,
                                          HarnessActor actor) {
        final String id = HarnessSupport.requireText(decisionId, "decision id");
        if (status != DecisionStatus.ACCEPTED && status != DecisionStatus.REJECTED) {
            throw new HarnessValidationException("decision resolution must be ACCEPTED or REJECTED");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent()) {
            throw new HarnessValidationException("an Agent may propose a decision but cannot resolve it");
        }
        return write(new HarnessSupport.StateCommand<DecisionRecord>() {
            @Override
            public DecisionRecord apply(HarnessState state) {
                DecisionRecord decision = state.getDecisions().get(id);
                if (decision == null) {
                    throw new HarnessValidationException("decision not found: " + id);
                }
                decision.setStatus(status);
                decision.setRationale(rationale == null ? decision.getRationale() : rationale);
                decision.setArbiter(HarnessSupport.copyActor(effectiveActor));
                decision.setResolvedAtEpochMs(HarnessSupport.now());
                decision.setVersion(Math.max(1L, decision.getVersion()) + 1L);
                eventJournal.addEvent(state, "decision.resolved", id, effectiveActor,
                        HarnessSupport.mapOf("status", status));
                return decision.copy();
            }
        });
    }

    public HarnessRule promoteLesson(String decisionId, HarnessRuleSpec spec) {
        return promoteLesson(decisionId, spec, defaultActor);
    }

    public HarnessRule promoteLesson(final String decisionId,
                                     final HarnessRuleSpec spec,
                                     HarnessActor actor) {
        final String id = HarnessSupport.requireText(decisionId, "decision id");
        if (spec == null || spec.getKind() == null) {
            throw new HarnessValidationException("rule kind is required");
        }
        final String payload = HarnessSupport.requireText(spec.getPayload(), "rule payload");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (!contract.mayPromoteLesson(effectiveActor)) {
            throw new HarnessValidationException("actor is not allowed to promote lessons into rules");
        }
        return write(new HarnessSupport.StateCommand<HarnessRule>() {
            @Override
            public HarnessRule apply(HarnessState state) {
                DecisionRecord decision = state.getDecisions().get(id);
                if (decision == null) {
                    throw new HarnessValidationException("decision not found: " + id);
                }
                if (decision.getStatus() != DecisionStatus.ACCEPTED) {
                    throw new HarnessConflictException(
                            "only an ACCEPTED decision can be promoted to a rule: " + id);
                }
                String preset = HarnessSupport.trimToNull(spec.getPreset());
                for (HarnessRule existing : state.getLearnedRules().values()) {
                    if (existing != null && existing.isEnabled()
                            && id.equals(existing.getDecisionId())
                            && spec.getKind() == existing.getKind()
                            && payload.equals(existing.getPayload())
                            && HarnessSupport.safeEquals(preset, existing.getPreset())) {
                        return existing.copy();
                    }
                }
                String ruleId = HarnessSupport.valueOrGenerated(spec.getRuleId(), "rule_");
                HarnessRule rule = HarnessRule.builder()
                        .ruleId(ruleId)
                        .scopeKey(decision.getScopeKey())
                        .decisionId(id)
                        .kind(spec.getKind())
                        .payload(payload)
                        .preset(preset)
                        .promotedBy(HarnessSupport.copyActor(effectiveActor))
                        .promotedAtEpochMs(HarnessSupport.now())
                        .enabled(true)
                        .build();
                state.getLearnedRules().put(ruleId, rule);
                eventJournal.addEvent(state, "rule.promoted", ruleId, effectiveActor,
                        HarnessSupport.mapOf("decisionId", id, "kind", spec.getKind().name(), "payload", payload));
                return rule.copy();
            }
        });
    }

    public HarnessRule revokeLesson(String ruleId) {
        return revokeLesson(ruleId, defaultActor);
    }

    public HarnessRule revokeLesson(final String ruleId, HarnessActor actor) {
        final String id = HarnessSupport.requireText(ruleId, "rule id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (!contract.mayPromoteLesson(effectiveActor)) {
            throw new HarnessValidationException("actor is not allowed to revoke learned rules");
        }
        return write(new HarnessSupport.StateCommand<HarnessRule>() {
            @Override
            public HarnessRule apply(HarnessState state) {
                HarnessRule rule = state.getLearnedRules().get(id);
                if (rule == null) {
                    throw new HarnessValidationException("learned rule not found: " + id);
                }
                if (rule.isEnabled()) {
                    rule.setEnabled(false);
                    rule.setRevokedBy(HarnessSupport.copyActor(effectiveActor));
                    rule.setRevokedAtEpochMs(HarnessSupport.now());
                    eventJournal.addEvent(state, "rule.revoked", id, effectiveActor,
                            HarnessSupport.mapOf("decisionId", rule.getDecisionId()));
                }
                return rule.copy();
            }
        });
    }

    public EvidenceRecord recordEvidence(HarnessEvidenceSpec spec) {
        return recordEvidence(spec, defaultActor);
    }

    public EvidenceRecord recordEvidence(HarnessEvidenceSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("evidence specification is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final String evidenceId = HarnessSupport.valueOrGenerated(spec.getEvidenceId(), "evidence_");
        return write(new HarnessSupport.StateCommand<EvidenceRecord>() {
            @Override
            public EvidenceRecord apply(HarnessState state) {
                if (state.getEvidence().containsKey(evidenceId)) {
                    throw new HarnessConflictException("evidence already exists: " + evidenceId);
                }
                String evidenceTaskId = HarnessSupport.trimToNull(spec.getTaskId());
                String evidenceExecutionId = HarnessSupport.trimToNull(spec.getExecutionId());
                String evidenceScope = relatedScope(state, spec.getScopeKey(),
                        evidenceTaskId, EntityKind.TASK,
                        "evidence and task scopes must match");
                if (evidenceExecutionId != null) {
                    ExecutionRecord execution = state.getExecutions().get(evidenceExecutionId);
                    if (execution == null) {
                        throw new HarnessValidationException("execution not found: " + evidenceExecutionId);
                    }
                    String executionTaskId = HarnessSupport.trimToNull(execution.getTaskId());
                    if (evidenceTaskId != null && !evidenceTaskId.equals(executionTaskId)) {
                        throw new HarnessConflictException("evidence task and execution tasks must match");
                    }
                    if (evidenceTaskId == null && executionTaskId != null) {
                        evidenceTaskId = executionTaskId;
                    }
                    if (evidenceTaskId != null) {
                        HarnessSupport.requireTask(state, evidenceTaskId);
                        HarnessSupport.assertSameScope(evidenceScope, execution.getScopeKey(),
                                "evidence and execution scopes must match");
                    } else {
                        HarnessSupport.assertCompatibleScope(evidenceScope, execution.getScopeKey(),
                                "evidence and execution scopes must match");
                        if (evidenceScope == null) {
                            evidenceScope = HarnessSupport.normalizeScope(execution.getScopeKey());
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
                        .createdAtEpochMs(HarnessSupport.now())
                        .provenance(HarnessSupport.provenance(effectiveActor, defaultActor,
                                evidenceExecutionId, null, "evidence", evidenceId))
                        .build();
                state.getEvidence().put(evidenceId, evidence);
                eventJournal.addEvent(state, "evidence.recorded", evidenceId, effectiveActor,
                        HarnessSupport.mapOf("taskId", evidence.getTaskId(),
                                "executionId", evidence.getExecutionId(), "kind", evidence.getKind()));
                return evidence.copy();
            }
        });
    }

    public AcceptanceRecord recordAcceptance(final AcceptanceRecord record) {
        return recordAcceptance(record, defaultActor);
    }

    public AcceptanceRecord recordAcceptance(final AcceptanceRecord record, HarnessActor actor) {
        if (record == null || record.getAcceptanceId() == null || record.getExecutionId() == null
                || record.getCheckId() == null || record.getStatus() == null) {
            throw new HarnessValidationException("acceptance record requires id, execution, check and status");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<AcceptanceRecord>() {
            @Override
            public AcceptanceRecord apply(HarnessState state) {
                ExecutionRecord execution = state.getExecutions().get(record.getExecutionId());
                if (execution == null) {
                    throw new HarnessValidationException("acceptance execution not found: " + record.getExecutionId());
                }
                if (record.getTaskId() != null
                        && !HarnessSupport.safeEquals(record.getTaskId(), execution.getTaskId())) {
                    throw new HarnessConflictException("acceptance task does not match execution");
                }
                if (record.getSubmissionId() != null) {
                    SubmissionRecord submission = state.getSubmissions().get(record.getSubmissionId());
                    if (submission == null) {
                        throw new HarnessValidationException(
                                "acceptance submission not found: " + record.getSubmissionId());
                    }
                    if (!HarnessSupport.safeEquals(submission.getExecutionId(), record.getExecutionId())
                            || !HarnessSupport.safeEquals(submission.getTaskId(), execution.getTaskId())) {
                        throw new HarnessConflictException("acceptance submission does not match execution");
                    }
                }
                AcceptanceRecord stored = record.copy();
                stored.setTaskId(execution.getTaskId());
                stored.setEvaluatedAtEpochMs(stored.getEvaluatedAtEpochMs() == 0
                        ? HarnessSupport.now() : stored.getEvaluatedAtEpochMs());
                state.getAcceptances().put(stored.getAcceptanceId(), stored);
                eventJournal.addEvent(state, "acceptance.recorded", stored.getAcceptanceId(), effectiveActor,
                        HarnessSupport.mapOf("executionId", stored.getExecutionId(),
                                "submissionId", stored.getSubmissionId(), "status", stored.getStatus().name()));
                return stored.copy();
            }
        });
    }

    public AcceptanceRecord bindAcceptanceToSubmission(final String acceptanceId,
                                                       final String submissionId) {
        final AcceptanceRecord source = getState().getAcceptances().get(acceptanceId);
        if (source == null) {
            throw new HarnessValidationException("acceptance not found: " + acceptanceId);
        }
        if (source.getStatus() != HarnessAcceptanceStatus.PASS) {
            throw new HarnessConflictException("only a PASS acceptance can be bound to a submission");
        }
        SubmissionRecord submission = getState().getSubmissions().get(submissionId);
        if (submission == null) {
            throw new HarnessValidationException("submission not found: " + submissionId);
        }
        if (!HarnessSupport.safeEquals(source.getTaskId(), submission.getTaskId())
                || !HarnessSupport.safeEquals(source.getExecutionId(), submission.getExecutionId())) {
            throw new HarnessConflictException("acceptance and submission lineage must match");
        }
        for (AcceptanceRecord existing : getState().getAcceptances().values()) {
            if (existing != null && HarnessSupport.safeEquals(existing.getSubmissionId(), submissionId)
                    && HarnessSupport.safeEquals(existing.getCheckId(), source.getCheckId())
                    && HarnessSupport.safeEquals(existing.getSummary(), source.getSummary())) {
                return existing.copy();
            }
        }
        return recordAcceptance(source.toBuilder()
                .acceptanceId("acc_" + UUID.randomUUID().toString().replace("-", ""))
                .submissionId(submissionId).evaluatedAtEpochMs(HarnessSupport.now()).build());
    }

    // ---- Submissions, Reviews & Completion ----

    public SubmissionRecord submitTask(String taskId,
                                       String executionId,
                                       HarnessSubmissionSpec spec) {
        return submitTask(taskId, executionId, spec, defaultActor);
    }

    public SubmissionRecord submitTaskWithAcceptance(String taskId, String executionId,
                                                     String acceptanceId,
                                                     HarnessSubmissionSpec spec,
                                                     HarnessActor actor) {
        return submitTaskWithAcceptances(taskId, executionId,
                Collections.singletonList(acceptanceId), spec, actor);
    }

    public SubmissionRecord submitTaskWithAcceptances(String taskId,
                                                      String executionId,
                                                      List<String> acceptanceIds,
                                                      HarnessSubmissionSpec spec,
                                                      HarnessActor actor) {
        String taskKey = HarnessSupport.requireText(taskId, "task id");
        String executionKey = HarnessSupport.requireText(executionId, "execution id");
        if (acceptanceIds == null || acceptanceIds.isEmpty()) {
            throw new HarnessValidationException("acceptance ids are required");
        }
        HarnessState state = getState();
        Set<String> uniqueAcceptanceIds = new LinkedHashSet<String>();
        Set<String> evidenceIds = new LinkedHashSet<String>();
        for (String rawAcceptanceId : acceptanceIds) {
            String acceptanceId = HarnessSupport.requireText(rawAcceptanceId, "acceptance id");
            if (!uniqueAcceptanceIds.add(acceptanceId)) {
                continue;
            }
            AcceptanceRecord acceptance = state.getAcceptances().get(acceptanceId);
            if (acceptance == null || acceptance.getStatus() != HarnessAcceptanceStatus.PASS
                    || !taskKey.equals(acceptance.getTaskId()) || !executionKey.equals(acceptance.getExecutionId())) {
                throw new HarnessConflictException("PASS acceptance does not belong to current task and execution: "
                        + acceptanceId);
            }
            boolean hasEvidence = false;
            for (EvidenceRecord evidence : state.getEvidence().values()) {
                if (evidence != null && executionKey.equals(evidence.getExecutionId())
                        && acceptanceId.equals(evidence.getContentRef())) {
                    evidenceIds.add(evidence.getEvidenceId());
                    hasEvidence = true;
                }
            }
            if (!hasEvidence) {
                throw new HarnessConflictException("PASS acceptance has no durable evidence: " + acceptanceId);
            }
        }
        HarnessSubmissionSpec base = spec == null ? HarnessSubmissionSpec.builder().build() : spec;
        SubmissionRecord submission = submitTask(taskKey, executionKey, base.toBuilder()
                .evidenceIds(new ArrayList<String>(evidenceIds)).build(), actor);
        for (String acceptanceId : uniqueAcceptanceIds) {
            bindAcceptanceToSubmission(acceptanceId, submission.getSubmissionId());
        }
        return submission;
    }

    public SubmissionRecord submitTask(String taskId,
                                       String executionId,
                                       HarnessSubmissionSpec spec,
                                       HarnessActor actor) {
        final String id = HarnessSupport.requireText(taskId, "task id");
        final HarnessSubmissionSpec requested = spec == null ? HarnessSubmissionSpec.builder().build() : spec;
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<SubmissionRecord>() {
            @Override
            public SubmissionRecord apply(HarnessState state) {
                TaskRecord task = HarnessSupport.requireTask(state, id);
                if (TaskStatus.DONE.equals(task.getStatus()) || TaskStatus.CANCELLED.equals(task.getStatus())) {
                    throw new HarnessValidationException("cannot submit a terminal task: " + id);
                }
                String executionKey = HarnessSupport.trimToNull(executionId);
                if (executionKey != null) {
                    ExecutionRecord execution = state.getExecutions().get(executionKey);
                    if (execution == null) {
                        throw new HarnessValidationException("execution not found: " + executionKey);
                    }
                    if (!id.equals(execution.getTaskId())) {
                        throw new HarnessValidationException("execution does not belong to task: " + id);
                    }
                    assertCurrentTaskExecution(task, executionKey);
                    HarnessSupport.assertSameScope(task.getScopeKey(), execution.getScopeKey(),
                            "submission and execution scopes must match");
                    KnowledgeLedgerService.assertReadSetFresh(state, executionKey);
                }
                resolveEvidenceReferenceScope(state, requested.getEvidenceIds(),
                        task.getScopeKey(), true,
                        "submission and evidence scopes must match");
                String submissionId = HarnessSupport.valueOrGenerated(null, "submission_");
                SubmissionRecord submission = SubmissionRecord.builder()
                        .submissionId(submissionId)
                        .taskId(id)
                        .executionId(executionKey)
                        .submitter(HarnessSupport.copyActor(effectiveActor))
                        .completionClaim(requested.getCompletionClaim())
                        .verificationNotes(requested.getVerificationNotes())
                        .deliverables(HarnessSupport.copyList(requested.getDeliverables()))
                        .evidenceIds(HarnessSupport.copyList(requested.getEvidenceIds()))
                        .knownGaps(HarnessSupport.copyList(requested.getKnownGaps()))
                        .residualRisks(HarnessSupport.copyList(requested.getResidualRisks()))
                        .createdAtEpochMs(HarnessSupport.now())
                        .build();
                submission.setSubmissionDigest(HarnessDigests.computeSubmissionDigest(submission));
                state.getSubmissions().put(submissionId, submission);
                task.setSubmissionId(submissionId);
                task.setStatus(TaskStatus.IN_REVIEW);
                task.setUpdatedAtEpochMs(HarnessSupport.now());
                task.setVersion(task.getVersion() + 1L);
                eventJournal.addEvent(state, "submission.created", submissionId, effectiveActor,
                        HarnessSupport.mapOf("taskId", id));
                return submission.copy();
            }
        });
    }

    public ReviewRecord reviewSubmission(String submissionId,
                                         ReviewVerdict verdict,
                                         String findings,
                                         String rationale,
                                         HarnessActor actor) {
        final String id = HarnessSupport.requireText(submissionId, "submission id");
        if (verdict == null) {
            throw new HarnessValidationException("review verdict is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<ReviewRecord>() {
            @Override
            public ReviewRecord apply(HarnessState state) {
                SubmissionRecord submission = state.getSubmissions().get(id);
                if (submission == null) {
                    throw new HarnessValidationException("submission not found: " + id);
                }
                TaskRecord task = HarnessSupport.requireTask(state, submission.getTaskId());
                if (!id.equals(task.getSubmissionId())) {
                    throw new HarnessConflictException("submission is no longer the current task submission: " + id);
                }
                String executionId = HarnessSupport.trimToNull(submission.getExecutionId());
                if (executionId != null) {
                    ExecutionRecord execution = HarnessSupport.requireExecution(state, executionId);
                    if (!submission.getTaskId().equals(execution.getTaskId())) {
                        throw new HarnessValidationException("submission execution does not belong to task: "
                                + submission.getTaskId());
                    }
                    assertCurrentTaskExecution(task, executionId);
                    HarnessSupport.assertSameScope(task.getScopeKey(), execution.getScopeKey(),
                            "submission and execution scopes must match");
                }
                if (!contract.mayApprove(effectiveActor, submission)) {
                    throw new HarnessValidationException("actor is not allowed to review this submission");
                }
                String reviewId = HarnessSupport.valueOrGenerated(null, "review_");
                ReviewRecord review = ReviewRecord.builder()
                        .reviewId(reviewId)
                        .submissionId(id)
                        .taskId(submission.getTaskId())
                        .reviewer(HarnessSupport.copyActor(effectiveActor))
                        .verdict(verdict)
                        .findings(findings)
                        .rationale(rationale)
                        .createdAtEpochMs(HarnessSupport.now())
                        .build();
                state.getReviews().put(reviewId, review);
                if (verdict == ReviewVerdict.CHANGES_REQUESTED) {
                    task.setStatus(TaskStatus.ACTIVE);
                    task.setUpdatedAtEpochMs(HarnessSupport.now());
                    task.setVersion(task.getVersion() + 1L);
                }
                eventJournal.addEvent(state, "review.recorded", reviewId, effectiveActor,
                        HarnessSupport.mapOf("submissionId", id, "verdict", verdict));
                return review.copy();
            }
        });
    }

    public TaskRecord completeTask(String taskId, String submissionId, HarnessActor actor) {
        final String id = HarnessSupport.requireText(taskId, "task id");
        final String submissionKey = HarnessSupport.requireText(submissionId, "submission id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final HarnessState snapshot = getState();
        final long evaluatedVersion = snapshot.getVersion();
        SubmissionRecord candidate = validateCompletion(snapshot, id, submissionKey, effectiveActor);
        final List<GateResult> gateResults = contract.evaluateCompletion(
                HarnessSupport.requireTask(snapshot, id), candidate, snapshot);
        final CompletionFailure failure = new CompletionFailure();
        TaskRecord result = write(new HarnessSupport.StateCommand<TaskRecord>() {
            @Override
            public TaskRecord apply(HarnessState state) {
                SubmissionRecord submission = validateCompletion(state, id, submissionKey, effectiveActor);
                if (state.getVersion() != evaluatedVersion) {
                    throw new HarnessConflictException(
                            "Harness state changed during completion evaluation; retry completion");
                }
                TaskRecord task = HarnessSupport.requireTask(state, id);
                String executionId = submission.getExecutionId();
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
                        String gateId = HarnessSupport.valueOrGenerated(null, "gate_");
                        state.getGates().put(gateId, GateRecord.builder()
                                .gateId(gateId)
                                .taskId(id)
                                .executionId(executionId)
                                .submissionId(submissionKey)
                                .name(gateName)
                                .status(gateResult != null && gateResult.isPassed() ? GateStatus.PASS : GateStatus.FAIL)
                                .reason(gateResult == null ? "gate returned no result" : gateResult.getReason())
                                .evaluatedAtEpochMs(HarnessSupport.now())
                                .build());
                    }
                }
                if (!passed) {
                    failure.reason = failedReason == null ? "completion gates did not pass" : failedReason;
                    eventJournal.addEvent(state, "task.completion_rejected", id, effectiveActor,
                            HarnessSupport.mapOf("reason", failure.reason));
                    return task.copy();
                }
                task.setStatus(TaskStatus.DONE);
                task.setBlockedReason(null);
                task.setUpdatedAtEpochMs(HarnessSupport.now());
                task.setVersion(task.getVersion() + 1L);
                eventJournal.addEvent(state, "task.completed", id, effectiveActor,
                        HarnessSupport.mapOf("submissionId", submissionKey, "executionId", executionId));
                return task.copy();
            }
        });
        if (failure.reason != null) {
            throw new HarnessValidationException(failure.reason);
        }
        return result;
    }

    // ---- Executions, Leases, Checkpoints, Waits & Wakeups ----

    public ExecutionRecord createExecution(HarnessExecutionSpec spec) {
        return createExecution(spec, defaultActor);
    }

    public ExecutionRecord createExecution(HarnessExecutionSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("execution specification is required");
        }
        final String executionId = HarnessSupport.valueOrGenerated(spec.getExecutionId(), "exe_");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                String executionScope = executionSpecScope(state, spec);
                String existingId = idempotentId(state, HarnessSupport.IDEMPOTENCY_EXECUTION, executionScope,
                        spec.getIdempotencyKey());
                if (existingId != null && state.getExecutions().get(existingId) != null) {
                    ExecutionRecord existing = state.getExecutions().get(existingId);
                    if (!HarnessSupport.safeEquals(HarnessSupport.trimToNull(spec.getTaskId()),
                            HarnessSupport.trimToNull(existing.getTaskId()))
                            || !HarnessSupport.safeEquals(HarnessSupport.trimToNull(spec.getParentExecutionId()),
                            HarnessSupport.trimToNull(existing.getParentExecutionId()))) {
                        throw new HarnessConflictException(
                                "idempotency key belongs to a different execution task or parent");
                    }
                    return existing.copy();
                }
                if (state.getExecutions().containsKey(executionId)) {
                    throw new HarnessConflictException("execution already exists: " + executionId);
                }
                String executionTaskId = HarnessSupport.trimToNull(spec.getTaskId());
                String parentId = HarnessSupport.trimToNull(spec.getParentExecutionId());
                if (parentId != null) {
                    ExecutionRecord parent = state.getExecutions().get(parentId);
                    if (parent == null) {
                        throw new HarnessValidationException("parent execution not found: " + parentId);
                    }
                    if (!HarnessSupport.safeEquals(executionTaskId, parent.getTaskId())
                            || !HarnessSupport.safeEquals(executionScope, parent.getScopeKey())) {
                        throw new HarnessConflictException("parent execution task and scope must match");
                    }
                    if (parent.getStatus() != ExecutionStatus.SUCCEEDED
                            && parent.getStatus() != ExecutionStatus.FAILED) {
                        throw new HarnessConflictException(
                                "parent execution must have succeeded or failed; resume or reconcile it first");
                    }
                }
                if (executionTaskId != null) {
                    TaskRecord task = HarnessSupport.requireTask(state, executionTaskId);
                    if (TaskStatus.DONE.equals(task.getStatus()) || TaskStatus.CANCELLED.equals(task.getStatus())) {
                        throw new HarnessValidationException("cannot execute a terminal task: " + executionTaskId);
                    }
                    if (TaskStatus.BLOCKED.equals(task.getStatus())
                            || TaskStatus.IN_REVIEW.equals(task.getStatus())) {
                        throw new HarnessConflictException("task cannot create an execution in status "
                                + task.getStatus() + ": " + executionTaskId);
                    }
                    if (!HarnessProjectionEngine.dependenciesSatisfied(state, executionTaskId)) {
                        throw new HarnessConflictException("task dependencies are not complete: " + executionTaskId);
                    }
                    if (HarnessProjectionEngine.hasOutstandingExecution(state, executionTaskId)) {
                        throw new HarnessConflictException("task already has an outstanding execution: "
                                + executionTaskId);
                    }
                }
                int attempt = nextAttempt(state, executionTaskId);
                ExecutionRecord execution = ExecutionRecord.builder()
                        .executionId(executionId)
                        .parentExecutionId(HarnessSupport.trimToNull(spec.getParentExecutionId()))
                        .taskId(executionTaskId)
                        .scopeKey(executionScope)
                        .sessionId(HarnessSupport.trimToNull(spec.getSessionId()))
                        .runId(resolveExecutionRunId(state, spec))
                        .status(ExecutionStatus.READY)
                        .attempt(attempt)
                        .inputSummary(spec.getInputSummary())
                        .targetNodeId(HarnessSupport.trimToNull(spec.getTargetNodeId()))
                        .requiredTags(HarnessSupport.copyList(spec.getRequiredTags()))
                        .targetModel(HarnessSupport.trimToNull(spec.getTargetModel()))
                        .createdAtEpochMs(HarnessSupport.now())
                        .updatedAtEpochMs(HarnessSupport.now())
                        .version(1L)
                        .build();
                state.getExecutions().put(executionId, execution);
                if (execution.getTaskId() != null) {
                    TaskRecord task = state.getTasks().get(execution.getTaskId());
                    if (task != null) {
                        task.setLastExecutionId(executionId);
                        task.setUpdatedAtEpochMs(HarnessSupport.now());
                        task.setVersion(task.getVersion() + 1L);
                    }
                }
                rememberIdempotency(state, HarnessSupport.IDEMPOTENCY_EXECUTION, executionScope,
                        spec.getIdempotencyKey(), executionId);
                eventJournal.addEvent(state, "execution.created", executionId, effectiveActor, HarnessSupport.mapOf(
                        "taskId", execution.getTaskId(), "attempt", attempt,
                        "parentExecutionId", execution.getParentExecutionId()));
                return execution.copy();
            }
        });
    }

    public ExecutionRecord attachExecutionToTask(String executionId, String taskId) {
        return attachExecutionToTask(executionId, taskId, defaultActor);
    }

    public ExecutionRecord attachExecutionToTask(String executionId, String taskId, HarnessActor actor) {
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        final String taskKey = HarnessSupport.requireText(taskId, "task id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                return attachExecutionToTaskInternal(state, executionKey, taskKey, effectiveActor);
            }
        });
    }

    public ExecutionRecord claimExecution(String executionId, String workerId, long leaseDurationMillis) {
        return leaseCoordinator.claimExecution(executionId, workerId, leaseDurationMillis);
    }

    public ExecutionRecord markExecutionUnknownIfLeaseLost(String executionId,
                                                            String leaseId,
                                                            long fencingToken,
                                                            String workerId,
                                                            String reason) {
        return leaseCoordinator.markExecutionUnknownIfLeaseLost(
                executionId, leaseId, fencingToken, workerId, reason);
    }

    public ExecutionRecord heartbeat(String executionId,
                                     String leaseId,
                                     long fencingToken,
                                     String workerId,
                                     long leaseDurationMillis) {
        return leaseCoordinator.heartbeat(executionId, leaseId, fencingToken, workerId, leaseDurationMillis);
    }

    public ExecutionRecord releaseExecution(String executionId,
                                            String leaseId,
                                            long fencingToken,
                                            String workerId) {
        return leaseCoordinator.releaseExecution(executionId, leaseId, fencingToken, workerId);
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

    public ExecutionRecord reconcileExecution(String executionId,
                                              ExecutionStatus resolution,
                                              String reason,
                                              String outputText,
                                              HarnessActor actor) {
        return leaseCoordinator.reconcileExecution(executionId, resolution, reason, outputText, actor);
    }

    public ExecutionRecord persistExecutionOutcome(HarnessExecutionOutcome outcome) {
        if (outcome == null) {
            throw new HarnessValidationException("execution outcome is required");
        }
        final String executionKey = HarnessSupport.requireText(outcome.getExecutionId(), "execution id");
        if (outcome.getStatus() == null) {
            throw new HarnessValidationException("execution outcome status is required");
        }
        if (ExecutionStatus.RUNNING.equals(outcome.getStatus())) {
            throw new HarnessValidationException(
                    "RUNNING is not a persistable execution outcome; return READY, WAITING, or a terminal status");
        }
        if (!ExecutionStatus.WAITING.equals(outcome.getStatus())
                && (HarnessSupport.trimToNull(outcome.getWaitId()) != null
                || HarnessSupport.trimToNull(outcome.getOperationId()) != null)) {
            throw new HarnessValidationException(
                    "only a WAITING execution outcome may reference a wait or operation");
        }
        return write(new HarnessSupport.StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
                leaseCoordinator.assertLease(state, execution, outcome.getLeaseId(),
                        outcome.getFencingToken(), execution.getWorkerId());
                assertSnapshotMatchesExecution(execution, outcome.getSessionSnapshot());
                String workerId = execution.getWorkerId();
                long currentTime = HarnessSupport.now();
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
                    state.getSessions().put(outcome.getSessionSnapshot().getSessionId(),
                            outcome.getSessionSnapshot());
                }
                String waitId = outcome.getWaitId();
                String operationId = outcome.getOperationId();
                if (!cancelledByTask && ExecutionStatus.WAITING.equals(persistedStatus)) {
                    WaitRecord requestedWait = waitId == null || waitId.trim().isEmpty()
                            ? null : state.getWaits().get(waitId.trim());
                    if (requestedWait != null && !executionKey.equals(requestedWait.getExecutionId())) {
                        throw new HarnessConflictException("wait id is already bound to another execution: " + waitId);
                    }
                    List<WaitRecord> openWaitRecords = HarnessProjectionEngine.openWaits(state, executionKey);
                    if (openWaitRecords.isEmpty()) {
                        String newWaitId = requestedWait == null
                                ? (waitId == null || waitId.trim().isEmpty()
                                ? HarnessSupport.valueOrGenerated(null, "wait_") : waitId.trim())
                                : HarnessSupport.valueOrGenerated(null, "wait_");
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
                        openWaitRecords = HarnessProjectionEngine.openWaits(state, executionKey);
                    }
                    WaitRecord representative = openWaitRecords.get(0);
                    waitId = representative.getWaitId();
                    operationId = representative.getOperationId() == null
                            ? operationId : representative.getOperationId();
                } else {
                    waitId = null;
                    operationId = null;
                    leaseCoordinator.cancelOpenWaitsForExecution(state, executionKey,
                            "execution outcome no longer waits for external input",
                            HarnessActor.worker(workerId), currentTime);
                }
                String checkpointId = cancelledByTask ? execution.getCheckpointId() : null;
                if (!cancelledByTask && (outcome.getSessionSnapshot() != null || outcome.getCheckpointSummary() != null
                        || (outcome.getCheckpointState() != null && !outcome.getCheckpointState().isEmpty()))) {
                    checkpointId = HarnessSupport.valueOrGenerated(null, "checkpoint_");
                    Map<String, Object> checkpointState = HarnessSupport.copyMap(outcome.getCheckpointState());
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
                execution.setFinishedAtEpochMs(HarnessLeaseCoordinator.isTerminalExecution(persistedStatus)
                        ? currentTime : 0L);
                execution.setVersion(execution.getVersion() + 1L);
                LeaseRecord lease = state.getLeases().get(outcome.getLeaseId());
                if (lease != null) {
                    lease.setReleasedAtEpochMs(currentTime);
                }
                leaseCoordinator.releaseSessionLease(state, execution, outcome.getLeaseId(),
                        outcome.getFencingToken(), workerId, currentTime);
                execution.setWorkerId(null);
                execution.setLeaseId(null);
                execution.setFencingToken(0L);
                if (task != null) {
                    task.setLastExecutionId(executionKey);
                    if (cancelledByTask) {
                        // Cancellation is a terminal handoff boundary.
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
                eventJournal.addEvent(state, quarantinedLateOutcome ? "execution.outcome_quarantined"
                                : "execution." + persistedStatus.name().toLowerCase(), executionKey,
                        HarnessActor.worker(workerId), HarnessSupport.mapOf(
                                "taskId", execution.getTaskId(), "waitId", waitId,
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
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<CheckpointRecord>() {
            @Override
            public CheckpointRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
                String checkpointId = HarnessSupport.valueOrGenerated(null, "checkpoint_");
                CheckpointRecord checkpoint = CheckpointRecord.builder()
                        .checkpointId(checkpointId)
                        .executionId(executionKey)
                        .taskId(execution.getTaskId())
                        .sessionId(execution.getSessionId())
                        .runId(execution.getRunId())
                        .summary(summary)
                        .createdAtEpochMs(HarnessSupport.now())
                        .state(HarnessSupport.copyMap(checkpointState))
                        .build();
                state.getCheckpoints().put(checkpointId, checkpoint);
                execution.setCheckpointId(checkpointId);
                execution.setUpdatedAtEpochMs(HarnessSupport.now());
                eventJournal.addEvent(state, "checkpoint.created", checkpointId, effectiveActor,
                        HarnessSupport.mapOf("executionId", executionKey));
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
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        final String requestedWaitId = waitId == null || waitId.trim().isEmpty()
                ? HarnessSupport.valueOrGenerated(null, "wait_") : waitId.trim();
        final WaitType waitType = type == null ? WaitType.EXTERNAL_EVENT : type;
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<WaitRecord>() {
            @Override
            public WaitRecord apply(HarnessState state) {
                return ensureWaitInternal(state, executionKey, taskId, requestedWaitId,
                        waitType, operationId, externalKey, payload, effectiveActor);
            }
        });
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
        return requestApproval(executionId, taskId, toolName, callId, arguments, null, actor);
    }

    public WaitRecord requestApproval(String executionId,
                                      String taskId,
                                      String toolName,
                                      String callId,
                                      String arguments,
                                      String invocationId,
                                      HarnessActor actor) {
        final String executionKey = HarnessSupport.requireText(executionId, "execution id");
        final String invocationKey = HarnessSupport.trimToNull(invocationId);
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<WaitRecord>() {
            @Override
            public WaitRecord apply(HarnessState state) {
                ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
                ToolInvocationRecord invocation = invocationKey == null
                        ? null : requireApprovalInvocation(state, invocationKey, execution,
                        toolName, callId, arguments);
                for (WaitRecord wait : state.getWaits().values()) {
                    if (wait == null || !WaitType.APPROVAL.equals(wait.getType())
                            || !executionKey.equals(wait.getExecutionId())) {
                        continue;
                    }
                    Map<String, Object> existingPayload = wait.getPayload();
                    if (existingPayload == null) {
                        existingPayload = new LinkedHashMap<String, Object>();
                    }
                    if (HarnessSupport.safeEquals(toolName, String.valueOf(existingPayload.get("toolName")))
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
                        HarnessSupport.valueOrGenerated(null, "wait_"), WaitType.APPROVAL, null,
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

    public boolean isApprovalGranted(String executionId,
                                     String toolName,
                                     String callId,
                                     String arguments) {
        HarnessState state = getState();
        for (WaitRecord wait : state.getWaits().values()) {
            if (wait == null || !WaitType.APPROVAL.equals(wait.getType())
                    || !HarnessSupport.safeEquals(executionId, wait.getExecutionId())
                    || !WaitStatus.DELIVERED.equals(wait.getStatus())) {
                continue;
            }
            Map<String, Object> payload = wait.getPayload();
            if (payload == null || !HarnessSupport.safeEquals(toolName, String.valueOf(payload.get("toolName")))) {
                continue;
            }
            if (deliveryApproved(payload.get("delivery"))
                    && approvalMatches(payload, callId, arguments)) {
                return true;
            }
        }
        return false;
    }

    public WakeupRecord deliverWait(String waitId, Object input) {
        return deliverWait(waitId, input, null, defaultActor);
    }

    public WakeupRecord deliverWait(String waitId, Object input, HarnessActor actor) {
        return deliverWait(waitId, input, null, actor);
    }

    public WakeupRecord deliverWait(String waitId,
                                    Object input,
                                    AgentSessionSnapshot sessionSnapshot,
                                    HarnessActor actor) {
        return deliverWaitInternal(waitId, input, sessionSnapshot, null, actor);
    }

    public WakeupRecord deliverAdapterWait(String waitId,
                                           Object input,
                                           HarnessAdapterState adapterState,
                                           HarnessActor actor) {
        return deliverAdapterWait(waitId, input, adapterState, null, actor);
    }

    public WakeupRecord deliverAdapterWait(String waitId,
                                           Object input,
                                           HarnessAdapterState adapterState,
                                           AgentSessionSnapshot sessionSnapshot,
                                           HarnessActor actor) {
        return deliverWaitInternal(waitId, input, sessionSnapshot, adapterState, actor);
    }

    // ---- 2.0 Governance: ExecutionReview vs ContentPinnedConsent & Witness ----

    public ExecutionReviewRecord submitExecutionReview(ExecutionReviewSpec spec) {
        return governanceReviewService.submitExecutionReview(spec, defaultActor);
    }

    public ExecutionReviewRecord submitExecutionReview(ExecutionReviewSpec spec, HarnessActor actor) {
        return governanceReviewService.submitExecutionReview(spec, actor);
    }

    public ContentPinnedConsentRecord reviewConsent(ContentPinnedConsentSpec spec, HarnessActor actor) {
        return governanceReviewService.reviewConsent(spec, actor);
    }

    public List<ExecutionReviewRecord> listExecutionReviews(String submissionId) {
        return governanceReviewService.listExecutionReviews(submissionId);
    }

    public List<ContentPinnedConsentRecord> listConsents(String submissionId) {
        return governanceReviewService.listConsents(submissionId);
    }

    public GateRunRecord witnessRerun(WitnessRunSpec spec, WitnessRunner runner) {
        return governanceReviewService.witnessRerun(spec, runner, defaultActor);
    }

    public GateRunRecord witnessRerun(WitnessRunSpec spec, WitnessRunner runner, HarnessActor actor) {
        return governanceReviewService.witnessRerun(spec, runner, actor);
    }

    public List<GateRunRecord> listGateRuns(String taskId) {
        return governanceReviewService.listGateRuns(taskId);
    }

    public List<GateRunRecord> listGateRunsForExecution(String executionId) {
        return governanceReviewService.listGateRunsForExecution(executionId);
    }

    // ---- 2.0 Escalate, SupervisoryAgenda & Explain ----

    public EscalationRecord escalate(HarnessEscalateSpec spec) {
        return agendaQueryService.escalate(spec, defaultActor);
    }

    public EscalationRecord escalate(HarnessEscalateSpec spec, HarnessActor actor) {
        return agendaQueryService.escalate(spec, actor);
    }

    public EscalationRecord escalate(String taskId,
                                     String question,
                                     List<String> options,
                                     String recommend,
                                     String reason) {
        return escalate(taskId, null, question, options, recommend, reason, null, defaultActor);
    }

    public EscalationRecord escalate(String taskId,
                                     String executionId,
                                     String question,
                                     List<String> options,
                                     String recommend,
                                     String reason,
                                     String targetActorId,
                                     HarnessActor actor) {
        return agendaQueryService.escalate(HarnessEscalateSpec.builder()
                .taskId(taskId)
                .executionId(executionId)
                .question(question)
                .options(HarnessSupport.copyList(options))
                .recommend(recommend)
                .reason(reason)
                .targetActorId(targetActorId)
                .build(), actor);
    }

    public EscalationRecord answerEscalation(String escalationId,
                                             String answer,
                                             String selectedOption,
                                             String rationale,
                                             HarnessActor actor) {
        return agendaQueryService.answerEscalation(escalationId, answer, selectedOption, rationale, actor);
    }

    public EscalationRecord followUpEscalation(String escalationId, String followUpNotes) {
        return agendaQueryService.followUpEscalation(escalationId, followUpNotes, defaultActor);
    }

    public EscalationRecord followUpEscalation(String escalationId,
                                               String followUpNotes,
                                               HarnessActor actor) {
        return agendaQueryService.followUpEscalation(escalationId, followUpNotes, actor);
    }

    public EscalationRecord getEscalation(String escalationId) {
        return agendaQueryService.getEscalation(escalationId);
    }

    public List<EscalationRecord> listEscalations(String scopeKey) {
        return agendaQueryService.listEscalations(scopeKey);
    }

    public SupervisoryAgenda projectAgenda(String actorId) {
        return agendaQueryService.projectAgenda(actorId, null);
    }

    public SupervisoryAgenda projectAgenda(String actorId, String scopeKey) {
        return agendaQueryService.projectAgenda(actorId, scopeKey);
    }

    public BlockerExplanation explain(String entityRef) {
        return agendaQueryService.explain(entityRef);
    }

    // ---- 2.0 ReadSet, Supersession, Falsifiable Decisions, Blueprints & Vertical/Fact Types ----

    public ReadSetEntry recordFactRead(String executionId, String factId) {
        return knowledgeLedgerService.recordFactRead(executionId, factId, defaultActor);
    }

    public ReadSetEntry recordFactRead(String executionId, String factId, HarnessActor actor) {
        return knowledgeLedgerService.recordFactRead(executionId, factId, actor);
    }

    public ReadSetEntry recordDecisionRead(String executionId, String decisionId) {
        return knowledgeLedgerService.recordDecisionRead(executionId, decisionId, defaultActor);
    }

    public ReadSetEntry recordDecisionRead(String executionId, String decisionId, HarnessActor actor) {
        return knowledgeLedgerService.recordDecisionRead(executionId, decisionId, actor);
    }

    public List<ReadSetEntry> reconfirmReadSet(String executionId, String note, HarnessActor actor) {
        return knowledgeLedgerService.reconfirmReadSet(executionId, null, note, actor);
    }

    public List<ReadSetEntry> reconfirmReadSet(String executionId,
                                               List<String> entityIds,
                                               String note,
                                               HarnessActor actor) {
        return knowledgeLedgerService.reconfirmReadSet(executionId, entityIds, note, actor);
    }

    public List<ReadSetEntry> listReadSet(String executionId) {
        return knowledgeLedgerService.listReadSet(executionId);
    }

    public FactRecord supersedeFact(String oldFactId, HarnessFactSpec newSpec, String reason) {
        return knowledgeLedgerService.supersedeFact(oldFactId, newSpec, reason, defaultActor);
    }

    public FactRecord supersedeFact(String oldFactId,
                                    HarnessFactSpec newSpec,
                                    String reason,
                                    HarnessActor actor) {
        return knowledgeLedgerService.supersedeFact(oldFactId, newSpec, reason, actor);
    }

    public DecisionRecord amendDecision(String decisionId,
                                        HarnessDecisionSpec amendment,
                                        String reason,
                                        HarnessActor actor) {
        return knowledgeLedgerService.amendDecision(decisionId, amendment, reason, actor);
    }

    public DecisionReckonResult reckonDecision(String decisionId,
                                               List<DecisionFulfillment> fulfillments,
                                               boolean autoRevokeRules,
                                               String summary,
                                               HarnessActor actor) {
        return knowledgeLedgerService.reckonDecision(decisionId, fulfillments, autoRevokeRules, summary, actor);
    }

    public AgentBlueprintRecord registerAgentBlueprint(AgentBlueprintSpec spec) {
        return knowledgeLedgerService.registerAgentBlueprint(spec, defaultActor);
    }

    public AgentBlueprintRecord registerAgentBlueprint(AgentBlueprintSpec spec, HarnessActor actor) {
        return knowledgeLedgerService.registerAgentBlueprint(spec, actor);
    }

    public List<AgentBlueprintRecord> listAgentBlueprintVersions(String name) {
        return knowledgeLedgerService.listAgentBlueprintVersions(name);
    }

    public AgentBlueprintRecord getActiveAgentBlueprint(String name) {
        return knowledgeLedgerService.getActiveAgentBlueprint(name);
    }

    public AgentBlueprintDiff diffAgentBlueprints(String name, long fromVersion, long toVersion) {
        return knowledgeLedgerService.diffAgentBlueprints(name, fromVersion, toVersion);
    }

    public AgentBlueprintRecord rollbackAgentBlueprint(String name,
                                                     long targetVersion,
                                                     String reason,
                                                     HarnessActor actor) {
        return knowledgeLedgerService.rollbackAgentBlueprint(name, targetVersion, reason, actor);
    }

    public <T> VerticalKind<T> registerVerticalKind(VerticalKind<T> kind) {
        return knowledgeLedgerService.getVerticalKinds().register(kind);
    }

    public <T> TypedArtifactRecord recordTypedArtifact(VerticalKind<T> kind,
                                                       String taskId,
                                                       String executionId,
                                                       String scopeKey,
                                                       T payload,
                                                       HarnessActor actor) {
        return knowledgeLedgerService.recordTypedArtifact(kind, taskId, executionId, scopeKey, payload, actor);
    }

    public <T> T readTypedArtifact(VerticalKind<T> kind, String artifactId) {
        return knowledgeLedgerService.readTypedArtifact(kind, artifactId);
    }

    public <T> FactType<T> registerFactType(FactType<T> factType) {
        return knowledgeLedgerService.getFactTypes().register(factType);
    }

    public <T> FactRecord recordTypedFact(FactType<T> factType,
                                          String taskId,
                                          String scopeKey,
                                          String statement,
                                          T payload,
                                          String source,
                                          List<String> evidenceIds,
                                          HarnessActor actor) {
        return knowledgeLedgerService.recordTypedFact(factType, taskId, scopeKey, statement,
                payload, source, evidenceIds, actor);
    }

    public <T> T readTypedFactPayload(FactType<T> factType, String factId) {
        return knowledgeLedgerService.readTypedFactPayload(factType, factId);
    }

    // ---- 2.0 Steer, Handoff, SquadRun, Todo/Work & Schedule ----

    public SteerRecord steer(String executionId,
                             String expectedTaskId,
                             long expectedVersion,
                             String instructions,
                             HarnessActor actor) {
        return executionCoordinationService.steer(executionId, expectedTaskId, expectedVersion, instructions, actor);
    }

    public List<SteerRecord> listSteers(String executionId) {
        return executionCoordinationService.listSteers(executionId);
    }

    public List<SteerRecord> consumePendingSteers(String executionId, int step) {
        return executionCoordinationService.consumePendingSteers(executionId, step);
    }

    public HandoffRecord exportHandoff(String executionId,
                                       String targetNodeId,
                                       String targetModel,
                                       String reason,
                                       long ttlMillis,
                                       HarnessActor actor) {
        return executionCoordinationService.exportHandoff(executionId, targetNodeId, targetModel,
                reason, ttlMillis, actor);
    }

    public HandoffRecord claimHandoff(String handoffId,
                                      String claimingNodeId,
                                      String claimingWorkerId,
                                      long leaseDurationMillis,
                                      HarnessActor actor) {
        return executionCoordinationService.claimHandoff(handoffId, claimingNodeId, claimingWorkerId,
                leaseDurationMillis, actor);
    }

    public HandoffRecord revokeHandoff(String handoffId, String reason, HarnessActor actor) {
        return executionCoordinationService.revokeHandoff(handoffId, reason, actor);
    }

    public HandoffRecord getHandoff(String handoffId) {
        return executionCoordinationService.getHandoff(handoffId);
    }

    public List<HandoffRecord> listHandoffs(String scopeKey) {
        return executionCoordinationService.listHandoffs(scopeKey);
    }

    public SquadRunRecord startSquadRun(SquadRunSpec spec) {
        return executionCoordinationService.startSquadRun(spec, defaultActor);
    }

    public SquadRunRecord startSquadRun(SquadRunSpec spec, HarnessActor actor) {
        return executionCoordinationService.startSquadRun(spec, actor);
    }

    public SquadRunRecord recordSquadLeaderTurn(String squadRunId,
                                                String action,
                                                String targetMemberId,
                                                String summary,
                                                String memberResponse,
                                                HarnessActor actor) {
        return executionCoordinationService.recordSquadLeaderTurn(squadRunId, action, targetMemberId,
                summary, memberResponse, actor);
    }

    public SquadRunRecord convergeSquadRun(String squadRunId, String synthesisReport, HarnessActor actor) {
        return executionCoordinationService.convergeSquadRun(squadRunId, synthesisReport, actor);
    }

    public SquadRunRecord getSquadRun(String squadRunId) {
        return executionCoordinationService.getSquadRun(squadRunId);
    }

    public TodoRecord createTodo(String todoId,
                                 String scopeKey,
                                 String title,
                                 String notes,
                                 Map<String, Object> metadata,
                                 HarnessActor actor) {
        return executionCoordinationService.createTodo(todoId, scopeKey, title, notes, metadata, actor);
    }

    public TaskRecord promoteTodoToTask(String todoId, HarnessTaskSpec taskSpec, HarnessActor actor) {
        return executionCoordinationService.promoteTodoToTask(todoId, taskSpec, this, actor);
    }

    public WorkRecord createWork(String workId,
                                 String scopeKey,
                                 String title,
                                 String goal,
                                 Map<String, Object> metadata,
                                 HarnessActor actor) {
        return executionCoordinationService.createWork(workId, scopeKey, title, goal, metadata, actor);
    }

    public WorkRecord attachTaskToWork(String workId, String taskId, HarnessActor actor) {
        return executionCoordinationService.attachTaskToWork(workId, taskId, actor);
    }

    public WorkProgress getWorkProgress(String workId) {
        return executionCoordinationService.getWorkProgress(workId);
    }

    public ScheduleRecord createSchedule(ScheduleSpec spec, HarnessActor actor) {
        return executionCoordinationService.createSchedule(spec, actor);
    }

    public ScheduleRunRecord triggerSchedule(String scheduleId,
                                             String tickKey,
                                             String claimingNodeId,
                                             ScheduleHandler handler,
                                             HarnessActor actor) {
        return executionCoordinationService.triggerSchedule(scheduleId, tickKey, claimingNodeId,
                handler, this, actor);
    }

    @Override
    public void close() {
        store.close();
    }

    // ---- Internal Helpers ----

    private WaitRecord ensureWaitInternal(HarnessState state,
                                          String executionKey,
                                          String taskId,
                                          String requestedWaitId,
                                          WaitType waitType,
                                          String operationId,
                                          String externalKey,
                                          Map<String, Object> payload,
                                          HarnessActor actor) {
        ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
        WaitRecord existing = state.getWaits().get(requestedWaitId);
        if (existing != null) {
            if (!executionKey.equals(existing.getExecutionId())) {
                throw new HarnessConflictException("wait id is already bound to another execution: "
                        + requestedWaitId);
            }
            return existing.copy();
        }
        String effectiveTaskId = taskId == null ? execution.getTaskId() : HarnessSupport.trimToNull(taskId);
        if (effectiveTaskId != null) {
            if (!HarnessSupport.safeEquals(effectiveTaskId, execution.getTaskId())) {
                throw new HarnessConflictException("wait task does not belong to execution: " + executionKey);
            }
            TaskRecord task = HarnessSupport.requireTask(state, effectiveTaskId);
            HarnessSupport.assertCompatibleScope(task.getScopeKey(), execution.getScopeKey(),
                    "wait and execution scopes must match");
            if (TaskStatus.DONE.equals(task.getStatus()) || TaskStatus.CANCELLED.equals(task.getStatus())) {
                throw new HarnessConflictException("cannot create a wait for a terminal task: " + effectiveTaskId);
            }
            if (TaskStatus.BLOCKED.equals(task.getStatus())
                    || TaskStatus.IN_REVIEW.equals(task.getStatus())) {
                throw new HarnessConflictException("cannot create a wait for task in status "
                        + task.getStatus() + ": " + effectiveTaskId);
            }
        }
        long currentTime = HarnessSupport.now();
        WaitRecord wait = WaitRecord.builder()
                .waitId(requestedWaitId)
                .executionId(executionKey)
                .taskId(effectiveTaskId)
                .type(waitType)
                .status(WaitStatus.OPEN)
                .operationId(operationId)
                .externalKey(externalKey)
                .createdAtEpochMs(currentTime)
                .payload(HarnessSupport.copyMap(payload))
                .build();
        state.getWaits().put(requestedWaitId, wait);
        WaitRecord representative = HarnessProjectionEngine.firstOpenWait(state, executionKey);
        execution.setWaitId(representative == null ? requestedWaitId : representative.getWaitId());
        execution.setOperationId(representative == null ? operationId : representative.getOperationId());
        execution.setUpdatedAtEpochMs(currentTime);
        if (effectiveTaskId != null) {
            TaskRecord task = state.getTasks().get(effectiveTaskId);
            if (task != null && !TaskStatus.DONE.equals(task.getStatus())
                    && !TaskStatus.CANCELLED.equals(task.getStatus())) {
                task.setStatus(TaskStatus.WAITING);
                task.setUpdatedAtEpochMs(currentTime);
                task.setVersion(task.getVersion() + 1L);
            }
        }
        eventJournal.addEvent(state, "wait.created", requestedWaitId, actor, HarnessSupport.mapOf(
                "executionId", executionKey, "type", waitType, "operationId", operationId));
        return wait.copy();
    }

    private WakeupRecord deliverWaitInternal(String waitId,
                                             Object input,
                                             AgentSessionSnapshot sessionSnapshot,
                                             HarnessAdapterState adapterState,
                                             HarnessActor actor) {
        final String id = HarnessSupport.requireText(waitId, "wait id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return write(new HarnessSupport.StateCommand<WakeupRecord>() {
            @Override
            public WakeupRecord apply(HarnessState state) {
                WaitRecord wait = state.getWaits().get(id);
                if (wait == null) {
                    throw new HarnessValidationException("wait not found: " + id);
                }
                if (WaitStatus.DELIVERED.equals(wait.getStatus())) {
                    for (WakeupRecord existing : state.getWakeups().values()) {
                        if (existing != null && id.equals(existing.getWaitId())) {
                            return existing.copy();
                        }
                    }
                }
                if (!WaitStatus.OPEN.equals(wait.getStatus())) {
                    throw new HarnessConflictException("wait is not open: " + id);
                }
                long currentTime = HarnessSupport.now();
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
                String wakeupId = HarnessSupport.valueOrGenerated(null, "wakeup_");
                WakeupRecord wakeup = WakeupRecord.builder()
                        .wakeupId(wakeupId)
                        .waitId(id)
                        .executionId(wait.getExecutionId())
                        .type(wait.getType())
                        .dueAtEpochMs(wait.getDueAtEpochMs())
                        .deliveredAtEpochMs(currentTime)
                        .payload(HarnessSupport.copyMap(waitPayload))
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
                        checkpointState.put("harnessAdapterState", HarnessJson.toMap(adapterState));
                        checkpoint.setState(checkpointState);
                    }
                }
                TaskRecord task = wait.getTaskId() == null ? null : state.getTasks().get(wait.getTaskId());
                if (execution != null && (ExecutionStatus.WAITING.equals(execution.getStatus())
                        || ExecutionStatus.READY.equals(execution.getStatus()))) {
                    List<WaitRecord> remainingWaits = HarnessProjectionEngine.openWaits(state, wait.getExecutionId());
                    WaitRecord representative = remainingWaits.isEmpty() ? null : remainingWaits.get(0);
                    if (representative == null) {
                        execution.setStatus(ExecutionStatus.READY);
                        execution.setWaitId(null);
                        execution.setOperationId(null);
                    } else {
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
                eventJournal.addEvent(state, "wakeup.delivered", wakeupId, effectiveActor,
                        HarnessSupport.mapOf("waitId", id, "executionId", wait.getExecutionId()));
                return wakeup.copy();
            }
        });
    }

    private SubmissionRecord validateCompletion(HarnessState state,
                                                String id,
                                                String submissionKey,
                                                HarnessActor effectiveActor) {
        TaskRecord task = HarnessSupport.requireTask(state, id);
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
        String executionId = HarnessSupport.trimToNull(submission.getExecutionId());
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
        HarnessSupport.assertSameScope(task.getScopeKey(), execution.getScopeKey(),
                "submission and execution scopes must match");
        if (!ExecutionStatus.SUCCEEDED.equals(execution.getStatus())) {
            throw new HarnessConflictException("submission execution must be SUCCEEDED before completion: "
                    + executionId);
        }
        if (!HarnessProjectionEngine.dependenciesSatisfied(state, id)) {
            throw new HarnessConflictException("task dependencies are not complete: " + id);
        }
        if (!contract.mayComplete(effectiveActor, submission)) {
            throw new HarnessValidationException("actor is not allowed to complete this task");
        }
        KnowledgeLedgerService.assertReadSetFresh(state, executionId);
        governanceReviewService.validateCompletionGovernance(state, task, submission);
        if (contract.requiresCompletionEvidence(task, submission)) {
            if (submission.getEvidenceIds() == null || submission.getEvidenceIds().isEmpty()) {
                throw new HarnessValidationException("completion evidence is required for governed task");
            }
            for (String evidenceId : submission.getEvidenceIds()) {
                EvidenceRecord evidence = state.getEvidence().get(evidenceId);
                if (evidence == null || !id.equals(evidence.getTaskId())
                        || !executionId.equals(evidence.getExecutionId())) {
                    throw new HarnessConflictException(
                            "completion evidence is missing or not bound to current execution: " + evidenceId);
                }
            }
        }
        validateRequiredAcceptances(state, task, submission, executionId);
        return submission;
    }

    private void validateRequiredAcceptances(HarnessState state,
                                             TaskRecord task,
                                             SubmissionRecord submission,
                                             String executionId) {
        Set<String> requiredChecks = projectionEngine.effectiveRequiredChecks(state, task, submission);
        if (requiredChecks.isEmpty()) {
            if (!contract.requiresCompletionEvidence(task, submission)) {
                return;
            }
            for (AcceptanceRecord acceptance : state.getAcceptances().values()) {
                if (acceptance != null && HarnessAcceptanceStatus.PASS == acceptance.getStatus()
                        && HarnessSupport.safeEquals(task.getTaskId(), acceptance.getTaskId())
                        && HarnessSupport.safeEquals(executionId, acceptance.getExecutionId())
                        && HarnessSupport.safeEquals(submission.getSubmissionId(), acceptance.getSubmissionId())) {
                    return;
                }
            }
            throw new HarnessConflictException("a PASS acceptance record for the current submission is required");
        }
        for (String checkId : requiredChecks) {
            boolean accepted = false;
            for (AcceptanceRecord acceptance : state.getAcceptances().values()) {
                if (acceptance != null && HarnessAcceptanceStatus.PASS == acceptance.getStatus()
                        && checkId.equals(HarnessSupport.trimToNull(acceptance.getCheckId()))
                        && HarnessSupport.safeEquals(task.getTaskId(), acceptance.getTaskId())
                        && HarnessSupport.safeEquals(executionId, acceptance.getExecutionId())
                        && HarnessSupport.safeEquals(submission.getSubmissionId(), acceptance.getSubmissionId())) {
                    accepted = true;
                    break;
                }
            }
            if (!accepted) {
                throw new HarnessConflictException("a PASS acceptance record for required check is missing: " + checkId);
            }
        }
    }

    private TaskRecord createTaskInternal(HarnessState state,
                                          HarnessTaskSpec spec,
                                          String taskId,
                                          HarnessActor actor) {
        if (state.getTasks().containsKey(taskId)) {
            throw new HarnessConflictException("task already exists: " + taskId);
        }
        String title = HarnessSupport.requireText(spec.getTitle(), "task title");
        String parentId = HarnessSupport.trimToNull(spec.getParentTaskId());
        String taskScope = HarnessSupport.normalizeScope(spec.getScopeKey());
        if (parentId != null) {
            TaskRecord parent = HarnessSupport.requireTask(state, parentId);
            if (taskScope != null) {
                HarnessSupport.assertSameScope(taskScope, parent.getScopeKey(),
                        "child task and parent task scopes must match");
            }
            if (taskScope == null) {
                taskScope = parent.getScopeKey();
            }
            if (HarnessProjectionEngine.wouldCreateCycle(state, RelationType.PARENT_OF, parentId, taskId)) {
                throw new HarnessConflictException("parent relation would create a cycle");
            }
        }
        long currentTime = HarnessSupport.now();
        TaskRecord task = TaskRecord.builder()
                .taskId(taskId)
                .scopeKey(taskScope)
                .title(title)
                .goal(spec.getGoal())
                .plan(spec.getPlan())
                .status(TaskStatus.PLANNED)
                .workId(HarnessSupport.trimToNull(spec.getWorkId()))
                .verticalKind(HarnessSupport.trimToNull(spec.getVerticalKind()))
                .targetNodeId(HarnessSupport.trimToNull(spec.getTargetNodeId()))
                .requiredTags(HarnessSupport.copyList(spec.getRequiredTags()))
                .assignedAgentId(HarnessSupport.trimToNull(spec.getAssignedAgentId()))
                .createdBy(HarnessSupport.actorKey(actor, defaultActor))
                .createdAtEpochMs(currentTime)
                .updatedAtEpochMs(currentTime)
                .version(1L)
                .tags(HarnessSupport.copyList(spec.getTags()))
                .metadata(HarnessSupport.copyMap(spec.getMetadata()))
                .build();
        state.getTasks().put(taskId, task);
        if (parentId != null) {
            String relationId = HarnessSupport.valueOrGenerated(null, "rel_");
            RelationRecord relation = RelationRecord.builder()
                    .relationId(relationId)
                    .scopeKey(task.getScopeKey())
                    .type(RelationType.PARENT_OF)
                    .fromKind(EntityKind.TASK)
                    .fromId(parentId)
                    .toKind(EntityKind.TASK)
                    .toId(taskId)
                    .createdBy(HarnessSupport.actorKey(actor, defaultActor))
                    .createdAtEpochMs(currentTime)
                    .metadata(new LinkedHashMap<String, Object>())
                    .build();
            state.getRelations().put(relationId, relation);
            eventJournal.addEvent(state, "relation.created", relationId, actor, HarnessSupport.mapOf(
                    "type", RelationType.PARENT_OF, "from", parentId, "to", taskId));
        }
        eventJournal.addEvent(state, "task.created", taskId, actor, HarnessSupport.mapOf(
                "title", title, "parentTaskId", parentId));
        return task;
    }

    private ExecutionRecord attachExecutionToTaskInternal(HarnessState state,
                                                          String executionKey,
                                                          String taskKey,
                                                          HarnessActor actor) {
        ExecutionRecord execution = HarnessSupport.requireExecution(state, executionKey);
        TaskRecord task = HarnessSupport.requireTask(state, taskKey);
        if (TaskStatus.DONE.equals(task.getStatus()) || TaskStatus.CANCELLED.equals(task.getStatus())) {
            throw new HarnessValidationException("cannot attach execution to a terminal task: " + taskKey);
        }
        if (TaskStatus.BLOCKED.equals(task.getStatus()) || TaskStatus.IN_REVIEW.equals(task.getStatus())) {
            throw new HarnessConflictException("cannot attach execution to task in status "
                    + task.getStatus() + ": " + taskKey);
        }
        if (!HarnessProjectionEngine.dependenciesSatisfied(state, taskKey)) {
            throw new HarnessConflictException("task dependencies are not complete: " + taskKey);
        }
        if (execution.getTaskId() != null && !taskKey.equals(execution.getTaskId())) {
            throw new HarnessConflictException("execution is already attached to another task: " + executionKey);
        }
        if (execution.getTaskId() == null
                && HarnessProjectionEngine.hasOutstandingExecution(state, taskKey, executionKey)) {
            throw new HarnessConflictException("task already has an outstanding execution: " + taskKey);
        }
        if (execution.getScopeKey() != null) {
            HarnessSupport.assertSameScope(execution.getScopeKey(), task.getScopeKey(),
                    "execution and task scopes must match");
        }
        if (execution.getScopeKey() == null) {
            execution.setScopeKey(task.getScopeKey());
        }
        execution.setTaskId(taskKey);
        task.setLastExecutionId(executionKey);
        task.setUpdatedAtEpochMs(HarnessSupport.now());
        task.setVersion(task.getVersion() + 1L);
        eventJournal.addEvent(state, "execution.attached", executionKey, actor,
                HarnessSupport.mapOf("taskId", taskKey));
        return execution.copy();
    }

    private void cancelTaskActivity(HarnessState state,
                                    String taskId,
                                    String reason,
                                    HarnessActor actor) {
        long currentTime = HarnessSupport.now();
        String cancellationReason = reason == null || reason.trim().isEmpty()
                ? "task cancelled" : reason;
        for (WaitRecord wait : state.getWaits().values()) {
            if (wait == null || !HarnessSupport.safeEquals(taskId, wait.getTaskId())
                    || !WaitStatus.OPEN.equals(wait.getStatus())) {
                continue;
            }
            wait.setStatus(WaitStatus.CANCELLED);
            wait.setResolvedAtEpochMs(currentTime);
            Map<String, Object> payload = wait.getPayload() == null
                    ? new LinkedHashMap<String, Object>() : wait.getPayload();
            payload.put("cancellationReason", cancellationReason);
            wait.setPayload(payload);
            eventJournal.addEvent(state, "wait.cancelled", wait.getWaitId(), actor,
                    HarnessSupport.mapOf("taskId", taskId, "reason", cancellationReason));
        }
        for (ToolInvocationRecord invocation : state.getToolInvocations().values()) {
            if (invocation == null || !HarnessSupport.safeEquals(taskId, invocation.getTaskId())
                    || (ToolInvocationStatus.STARTED != invocation.getStatus()
                    && ToolInvocationStatus.WAITING != invocation.getStatus())) {
                continue;
            }
            invocation.setStatus(ToolInvocationStatus.CANCELLED);
            invocation.setError(cancellationReason);
            invocation.setUpdatedAtEpochMs(currentTime);
            invocation.setVersion(invocation.getVersion() + 1L);
            eventJournal.addEvent(state, "tool.invocation_cancelled", invocation.getInvocationId(), actor,
                    HarnessSupport.mapOf("taskId", taskId, "executionId", invocation.getExecutionId(),
                            "waitId", invocation.getWaitId(), "operationId", invocation.getOperationId(),
                            "reason", cancellationReason));
        }
        for (ExecutionRecord execution : state.getExecutions().values()) {
            if (execution == null || !HarnessSupport.safeEquals(taskId, execution.getTaskId())
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
            eventJournal.addEvent(state, "execution.cancelled", execution.getExecutionId(), actor,
                    HarnessSupport.mapOf("taskId", taskId, "reason", cancellationReason));
        }
    }

    private void validateRelationSpec(HarnessState state, HarnessRelationSpec spec) {
        if (spec.getFromKind() == null || spec.getToKind() == null) {
            throw new HarnessValidationException("relation endpoints must have kinds");
        }
        String fromId = HarnessSupport.requireText(spec.getFromId(), "relation from id");
        String toId = HarnessSupport.requireText(spec.getToId(), "relation to id");
        if (spec.getType() == RelationType.PARENT_OF || spec.getType() == RelationType.DEPENDS_ON) {
            if (spec.getFromKind() != EntityKind.TASK || spec.getToKind() != EntityKind.TASK) {
                throw new HarnessValidationException(spec.getType() + " relations must connect tasks");
            }
        }
        if (!HarnessProjectionEngine.entityExists(state, spec.getFromKind(), fromId)
                || !HarnessProjectionEngine.entityExists(state, spec.getToKind(), toId)) {
            throw new HarnessValidationException("relation endpoint does not exist");
        }
        resolveRelationScope(state, spec);
    }

    private String resolveRelationScope(HarnessState state, HarnessRelationSpec spec) {
        String explicit = HarnessSupport.normalizeScope(spec.getScopeKey());
        String fromScope = HarnessProjectionEngine.entityScope(state, spec.getFromKind(), spec.getFromId());
        String toScope = HarnessProjectionEngine.entityScope(state, spec.getToKind(), spec.getToId());
        HarnessSupport.assertCompatibleScope(fromScope, toScope,
                "relation endpoints must use the same scope");
        HarnessSupport.assertCompatibleScope(explicit, fromScope,
                "relation scope does not match its source entity");
        HarnessSupport.assertCompatibleScope(explicit, toScope,
                "relation scope does not match its target entity");
        return explicit == null ? (fromScope == null ? toScope : fromScope) : explicit;
    }

    private String taskSpecScope(HarnessState state, HarnessTaskSpec spec) {
        String taskScope = HarnessSupport.normalizeScope(spec.getScopeKey());
        String parentTaskId = HarnessSupport.trimToNull(spec.getParentTaskId());
        if (parentTaskId == null) {
            return taskScope;
        }
        TaskRecord parent = HarnessSupport.requireTask(state, parentTaskId);
        if (taskScope != null) {
            HarnessSupport.assertSameScope(taskScope, parent.getScopeKey(),
                    "child task and parent task scopes must match");
        }
        return taskScope == null ? HarnessSupport.normalizeScope(parent.getScopeKey()) : taskScope;
    }

    private String executionSpecScope(HarnessState state, HarnessExecutionSpec spec) {
        String executionScope = HarnessSupport.normalizeScope(spec.getScopeKey());
        String taskId = HarnessSupport.trimToNull(spec.getTaskId());
        if (taskId == null) {
            return executionScope;
        }
        TaskRecord task = HarnessSupport.requireTask(state, taskId);
        if (executionScope != null) {
            HarnessSupport.assertSameScope(executionScope, task.getScopeKey(),
                    "execution and task scopes must match");
        }
        return executionScope == null ? HarnessSupport.normalizeScope(task.getScopeKey()) : executionScope;
    }

    private HarnessTaskSpec withScope(HarnessTaskSpec spec, String scopeKey) {
        String normalizedScope = HarnessSupport.normalizeScope(scopeKey);
        if (HarnessSupport.safeEquals(normalizedScope, HarnessSupport.normalizeScope(spec.getScopeKey()))) {
            return spec;
        }
        return spec.toBuilder().scopeKey(normalizedScope).build();
    }

    private String resolveEvidenceReferenceScope(HarnessState state,
                                                 List<String> evidenceIds,
                                                 String currentScope,
                                                 boolean scopeAnchored,
                                                 String mismatchMessage) {
        String resolvedScope = HarnessSupport.normalizeScope(currentScope);
        boolean scopeBound = scopeAnchored || resolvedScope != null;
        for (String evidenceId : HarnessSupport.copyList(evidenceIds)) {
            String id = HarnessSupport.requireText(evidenceId, "evidence id");
            EvidenceRecord evidence = state.getEvidence().get(id);
            if (evidence == null) {
                throw new HarnessValidationException("evidence not found: " + id);
            }
            String evidenceScope = HarnessSupport.normalizeScope(evidence.getScopeKey());
            if (!scopeBound) {
                resolvedScope = evidenceScope;
                scopeBound = true;
            } else {
                HarnessSupport.assertSameScope(resolvedScope, evidenceScope, mismatchMessage);
            }
        }
        return resolvedScope;
    }

    private String resolveFactReferenceScope(HarnessState state,
                                             List<String> factIds,
                                             String currentScope,
                                             boolean scopeAnchored,
                                             String mismatchMessage) {
        String resolvedScope = HarnessSupport.normalizeScope(currentScope);
        boolean scopeBound = scopeAnchored || resolvedScope != null;
        for (String factId : HarnessSupport.copyList(factIds)) {
            String id = HarnessSupport.requireText(factId, "fact id");
            FactRecord fact = state.getFacts().get(id);
            if (fact == null) {
                throw new HarnessValidationException("fact not found: " + id);
            }
            String factScope = HarnessSupport.normalizeScope(fact.getScopeKey());
            if (!scopeBound) {
                resolvedScope = factScope;
                scopeBound = true;
            } else {
                HarnessSupport.assertSameScope(resolvedScope, factScope, mismatchMessage);
            }
        }
        return resolvedScope;
    }

    private String relatedScope(HarnessState state,
                                String explicitScope,
                                String entityId,
                                EntityKind entityKind,
                                String mismatchMessage) {
        String scope = HarnessSupport.normalizeScope(explicitScope);
        if (entityId == null || entityId.trim().isEmpty()) {
            return scope;
        }
        if (!HarnessProjectionEngine.entityExists(state, entityKind, entityId)) {
            throw new HarnessValidationException(entityKind + " not found: " + entityId);
        }
        String entityScope = HarnessProjectionEngine.entityScope(state, entityKind, entityId);
        if (scope != null) {
            HarnessSupport.assertSameScope(scope, entityScope, mismatchMessage);
        }
        return scope == null ? entityScope : scope;
    }

    private RelationRecord findRelation(HarnessState state, HarnessRelationSpec spec) {
        for (RelationRecord relation : state.getRelations().values()) {
            if (relation == null) {
                continue;
            }
            if (relation.getType() == spec.getType()
                    && relation.getFromKind() == spec.getFromKind()
                    && relation.getToKind() == spec.getToKind()
                    && HarnessSupport.safeEquals(relation.getFromId(), spec.getFromId())
                    && HarnessSupport.safeEquals(relation.getToId(), spec.getToId())) {
                return relation;
            }
        }
        return null;
    }

    private void assertSameToolInvocation(ToolInvocationRecord existing,
                                          HarnessToolInvocationSpec requested,
                                          String scopeKey) {
        if (!HarnessSupport.safeEquals(existing.getExecutionId(), requested.getExecutionId())
                || !HarnessSupport.safeEquals(existing.getToolName(), requested.getToolName())
                || !HarnessSupport.safeEquals(existing.getCallId(), requested.getCallId())
                || !HarnessSupport.safeEquals(existing.getTaskId(), requested.getTaskId())
                || !HarnessSupport.safeEquals(existing.getSessionId(), requested.getSessionId())
                || !HarnessSupport.safeEquals(existing.getScopeKey(), scopeKey)
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
        if (!HarnessSupport.safeEquals(invocation.getExecutionId(), execution.getExecutionId())
                || !HarnessSupport.safeEquals(invocation.getToolName(), toolName)
                || !HarnessSupport.safeEquals(invocation.getCallId(), callId)
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
                    && !HarnessSupport.safeEquals(invocation.getWaitId(), wait.getWaitId())) {
                throw new HarnessConflictException("tool invocation approval wait changed: "
                        + invocation.getInvocationId());
            }
            if (invocation.getWaitId() == null) {
                invocation.setWaitId(wait.getWaitId());
                invocation.setUpdatedAtEpochMs(HarnessSupport.now());
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
        invocation.setUpdatedAtEpochMs(HarnessSupport.now());
        invocation.setVersion(invocation.getVersion() + 1L);
        eventJournal.addEvent(state, "tool.invocation_waiting", invocation.getInvocationId(), actor,
                HarnessSupport.mapOf("operationId", null, "waitId", wait.getWaitId(), "reason", "approval"));
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
        String waitId = HarnessSupport.trimToNull(invocation.getWaitId());
        if (waitId == null) {
            return false;
        }
        WaitRecord wait = state.getWaits().get(waitId);
        if (wait == null || !WaitType.APPROVAL.equals(wait.getType())
                || !WaitStatus.DELIVERED.equals(wait.getStatus())
                || !HarnessSupport.safeEquals(wait.getExecutionId(), invocation.getExecutionId())) {
            return false;
        }
        Map<String, Object> payload = wait.getPayload();
        if (payload == null
                || !HarnessSupport.safeEquals(invocation.getInvocationId(), String.valueOf(
                payload.get(AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID)))
                || !HarnessSupport.safeEquals(toolName, String.valueOf(payload.get("toolName")))
                || !deliveryApproved(payload.get("delivery"))) {
            return false;
        }
        return approvalMatches(payload, callId, arguments);
    }

    private void assertSameToolInvocationForApprovedRetry(ToolInvocationRecord existing,
                                                          HarnessToolInvocationSpec requested,
                                                          String scopeKey) {
        if (!HarnessSupport.safeEquals(existing.getExecutionId(), requested.getExecutionId())
                || !HarnessSupport.safeEquals(existing.getToolName(), requested.getToolName())
                || !HarnessSupport.safeEquals(existing.getTaskId(), requested.getTaskId())
                || !HarnessSupport.safeEquals(existing.getSessionId(), requested.getSessionId())
                || !HarnessSupport.safeEquals(existing.getScopeKey(), scopeKey)
                || !equivalentNullableArguments(existing.getArguments(), requested.getArguments())) {
            throw new HarnessConflictException("approved retry does not match the original tool invocation: "
                    + existing.getInvocationId());
        }
    }

    private boolean equivalentNullableArguments(String left, String right) {
        if (left == null || right == null) {
            return HarnessSupport.safeEquals(left, right);
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
        eventJournal.addEvent(state, "async.completion_quarantined", invocation.getInvocationId(), actor,
                HarnessSupport.mapOf("waitId", effectiveWaitId,
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
        if (from == to) {
            return;
        }
        boolean allowed = false;
        if (TaskStatus.PLANNED.equals(from)) {
            allowed = to == TaskStatus.ACTIVE || to == TaskStatus.BLOCKED || to == TaskStatus.CANCELLED;
        }
        if (TaskStatus.ACTIVE.equals(from)) {
            allowed = to == TaskStatus.WAITING || to == TaskStatus.BLOCKED
                    || to == TaskStatus.IN_REVIEW || to == TaskStatus.CANCELLED;
        }
        if (TaskStatus.WAITING.equals(from)) {
            allowed = to == TaskStatus.ACTIVE || to == TaskStatus.BLOCKED || to == TaskStatus.CANCELLED;
        }
        if (TaskStatus.BLOCKED.equals(from)) {
            allowed = to == TaskStatus.PLANNED || to == TaskStatus.ACTIVE || to == TaskStatus.CANCELLED;
        }
        if (TaskStatus.IN_REVIEW.equals(from)) {
            allowed = to == TaskStatus.ACTIVE || to == TaskStatus.CANCELLED;
        }
        if (!allowed || TaskStatus.DONE.equals(from) || TaskStatus.CANCELLED.equals(from)) {
            throw new HarnessConflictException("illegal task transition: " + from + " -> " + to);
        }
    }

    private void assertSnapshotMatchesExecution(ExecutionRecord execution,
                                                AgentSessionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (!HarnessSupport.safeEquals(HarnessSupport.trimToNull(execution.getSessionId()),
                HarnessSupport.trimToNull(snapshot.getSessionId()))) {
            throw new HarnessConflictException("session snapshot does not belong to execution session: "
                    + execution.getExecutionId());
        }
        String snapshotRunId = HarnessSupport.trimToNull(snapshot.getRunId());
        if (snapshotRunId != null
                && !HarnessSupport.safeEquals(HarnessSupport.trimToNull(execution.getRunId()), snapshotRunId)) {
            throw new HarnessConflictException("session snapshot run id does not match execution: "
                    + execution.getExecutionId());
        }
    }

    private String resolveExecutionRunId(HarnessState state, HarnessExecutionSpec spec) {
        String requestedRunId = HarnessSupport.trimToNull(spec.getRunId());
        String sessionId = HarnessSupport.trimToNull(spec.getSessionId());
        AgentSessionSnapshot snapshot = sessionId == null || state == null
                ? null : state.getSessions().get(sessionId);
        String sessionRunId = snapshot == null ? null : HarnessSupport.trimToNull(snapshot.getRunId());
        if (requestedRunId != null) {
            if (sessionRunId != null && !HarnessSupport.safeEquals(requestedRunId, sessionRunId)) {
                throw new HarnessConflictException("execution run id does not match session snapshot: "
                        + sessionId);
            }
            return requestedRunId;
        }
        return sessionRunId == null ? HarnessSupport.valueOrGenerated(null, "run_") : sessionRunId;
    }

    private void assertCurrentTaskExecution(TaskRecord task, String executionId) {
        String currentExecutionId = HarnessSupport.trimToNull(task == null ? null : task.getLastExecutionId());
        if (currentExecutionId != null && !currentExecutionId.equals(executionId)) {
            throw new HarnessConflictException("execution is no longer the current task execution: "
                    + executionId);
        }
    }

    boolean deliveryApproved(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object approved = map.get("approved");
            if (approved instanceof Boolean) {
                return ((Boolean) approved).booleanValue();
            }
            Object decision = map.get("decision");
            if (decision != null) {
                return isApprovalWord(String.valueOf(decision));
            }
        }
        return value != null && isApprovalWord(String.valueOf(value));
    }

    private boolean approvalMatches(Map<String, Object> payload,
                                    String callId,
                                    String arguments) {
        String approvedCallId = HarnessSupport.trimToNull(
                payload == null ? null : HarnessSupport.stringValue(payload.get("callId")));
        String approvedArguments = HarnessSupport.trimToNull(
                payload == null ? null : HarnessSupport.stringValue(payload.get("arguments")));
        String requestedCallId = HarnessSupport.trimToNull(callId);
        String requestedArguments = HarnessSupport.trimToNull(arguments);
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
            Object leftValue = HarnessJson.parseAny(left);
            Object rightValue = HarnessJson.parseAny(right);
            return leftValue == null ? rightValue == null : leftValue.equals(rightValue);
        } catch (RuntimeException ignored) {
            return false;
        }
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
            if (execution != null && HarnessSupport.safeEquals(taskId, execution.getTaskId())) {
                max = Math.max(max, execution.getAttempt());
            }
        }
        return max + 1;
    }

    private <T> T write(final HarnessSupport.StateCommand<T> command) {
        return HarnessSupport.write(store, command);
    }

    private String idempotentId(HarnessState state,
                                String entityType,
                                String scopeKey,
                                String key) {
        String normalizedKey = HarnessSupport.trimToNull(key);
        if (normalizedKey == null) {
            return null;
        }
        String normalizedScope = HarnessSupport.normalizeScope(scopeKey);
        String namespacedId = state.getIdempotency().get(
                HarnessSupport.idempotencyNamespace(entityType, normalizedScope, normalizedKey));
        if (isIdempotencyTarget(state, entityType, namespacedId)
                && sameEntityScope(state, entityType, namespacedId, normalizedScope)) {
            return namespacedId;
        }
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
        String normalizedKey = HarnessSupport.trimToNull(key);
        if (normalizedKey != null && id != null) {
            state.getIdempotency().put(HarnessSupport.idempotencyNamespace(entityType,
                    HarnessSupport.normalizeScope(scopeKey), normalizedKey), id);
        }
    }

    private boolean isIdempotencyTarget(HarnessState state, String entityType, String id) {
        if (id == null) {
            return false;
        }
        if (HarnessSupport.IDEMPOTENCY_TASK.equals(entityType)) {
            return state.getTasks().containsKey(id);
        }
        if (HarnessSupport.IDEMPOTENCY_EXECUTION.equals(entityType)) {
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
        return HarnessSupport.safeEquals(HarnessSupport.normalizeScope(scopeKey),
                HarnessProjectionEngine.entityScope(state,
                        HarnessSupport.IDEMPOTENCY_TASK.equals(entityType)
                                ? EntityKind.TASK : EntityKind.EXECUTION, id));
    }

    private boolean isGraphRelation(RelationType type) {
        return RelationType.PARENT_OF.equals(type) || RelationType.DEPENDS_ON.equals(type);
    }

    private static final class CompletionFailure {
        private String reason;
    }
}
