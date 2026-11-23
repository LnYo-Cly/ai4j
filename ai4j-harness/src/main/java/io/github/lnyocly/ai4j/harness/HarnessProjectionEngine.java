package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.harness.fleet.FleetNodeRecord;
import io.github.lnyocly.ai4j.harness.fleet.OrphanHandoffExport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cohesive CQRS projection, event-folding, cursor-pagination, and read-model
 * query collaborator for Harness state and stores.
 */
public final class HarnessProjectionEngine {

    static final int DEFAULT_PAGE_LIMIT = 50;

    private final HarnessStore store;
    private final HarnessContract contract;

    public HarnessProjectionEngine(HarnessStore store, HarnessContract contract) {
        if (store == null) {
            throw new IllegalArgumentException("Harness store is required");
        }
        this.store = store;
        this.contract = contract == null ? HarnessContract.builder().build() : contract;
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

    public TaskRecord getTaskInScope(String taskId, String scopeKey) {
        TaskRecord task = getState().getTasks().get(taskId);
        return task != null && HarnessSupport.visibleScope(task.getScopeKey(), scopeKey)
                ? task.copy() : null;
    }

    public List<TaskRecord> listTasks(String scopeKey) {
        List<TaskRecord> result = new ArrayList<TaskRecord>();
        for (TaskRecord task : getState().getTasks().values()) {
            if (task != null && HarnessSupport.visibleScope(task.getScopeKey(), scopeKey)) {
                result.add(task.copy());
            }
        }
        return result;
    }

    public List<HarnessEntityRecord> snapshotProjectedEntities() {
        HarnessState state = getState();
        String harnessId = HarnessSupport.trimToNull(state.getHarnessId()) == null
                ? "default" : state.getHarnessId().trim();
        return new ArrayList<HarnessEntityRecord>(extractEntitiesFromState(harnessId, state).values());
    }

    public static boolean applyProjectedEntity(HarnessState state, HarnessEntityRecord entity) {
        return applyEntityToState(state, entity);
    }

    public CursorPage<HarnessEntityRecord> listEntitiesPage(String entityType,
                                                            String statusFilter,
                                                            String cursor,
                                                            int limit) {
        if (store instanceof HarnessEventStore) {
            return ((HarnessEventStore) store).listEntitiesPage(entityType, statusFilter, cursor, limit);
        }
        HarnessState state = getState();
        String harnessId = HarnessSupport.trimToNull(state.getHarnessId()) == null
                ? "default" : state.getHarnessId().trim();
        Map<EntityKey, HarnessEntityRecord> projected = extractEntitiesFromState(harnessId, state);
        return paginateEntities(projected.values(), entityType, statusFilter, cursor, limit);
    }

    public CursorPage<TaskRecord> listTasksPage(TaskStatus status, String cursor, int limit) {
        String statusFilter = status == null ? null : status.name();
        CursorPage<HarnessEntityRecord> page = listEntitiesPage("TASK", statusFilter, cursor, limit);
        List<TaskRecord> tasks = new ArrayList<TaskRecord>(page.size());
        if (page.getItems() != null) {
            for (HarnessEntityRecord entity : page.getItems()) {
                TaskRecord decoded = entity == null ? null : entity.decode(TaskRecord.class);
                if (decoded != null) {
                    tasks.add(decoded);
                }
            }
        }
        return CursorPage.<TaskRecord>builder()
                .items(tasks)
                .nextCursor(page.getNextCursor())
                .hasMore(page.isHasMore())
                .totalCount(page.getTotalCount())
                .build();
    }

    public CursorPage<TaskRecord> listTasksPage(String scopeKey,
                                                TaskStatus status,
                                                String cursor,
                                                int limit) {
        String normalizedScope = HarnessSupport.normalizeScope(scopeKey);
        if (normalizedScope == null) {
            return listTasksPage(status, cursor, limit);
        }
        List<HarnessEntityRecord> matching = new ArrayList<HarnessEntityRecord>();
        HarnessState state = getState();
        String harnessId = HarnessSupport.trimToNull(state.getHarnessId()) == null
                ? "default" : state.getHarnessId().trim();
        for (TaskRecord task : state.getTasks().values()) {
            if (task == null || !HarnessSupport.visibleScope(task.getScopeKey(), normalizedScope)) {
                continue;
            }
            if (status != null && !status.equals(task.getStatus())) {
                continue;
            }
            matching.add(toEntityRecord(harnessId, "TASK", task.getTaskId(), task.getVersion(),
                    task.getStatus() == null ? null : task.getStatus().name(),
                    task.getUpdatedAtEpochMs(), task));
        }
        CursorPage<HarnessEntityRecord> page = paginateEntities(matching, "TASK",
                status == null ? null : status.name(), cursor, limit);
        List<TaskRecord> tasks = new ArrayList<TaskRecord>(page.size());
        for (HarnessEntityRecord item : page.getItems()) {
            TaskRecord decoded = item.decode(TaskRecord.class);
            if (decoded != null) {
                tasks.add(decoded);
            }
        }
        return CursorPage.<TaskRecord>builder()
                .items(tasks)
                .nextCursor(page.getNextCursor())
                .hasMore(page.isHasMore())
                .totalCount(page.getTotalCount())
                .build();
    }

    public List<TaskRecord> listRunnableTasks(String scopeKey) {
        HarnessState state = getState();
        List<TaskRecord> result = new ArrayList<TaskRecord>();
        for (TaskRecord task : state.getTasks().values()) {
            if (task == null || !isRunnableStatus(task.getStatus())
                    || !HarnessSupport.visibleScope(task.getScopeKey(), scopeKey)
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

    public List<WaitRecord> listOpenWaits(String executionId) {
        String id = HarnessSupport.trimToNull(executionId);
        if (id == null) {
            return Collections.emptyList();
        }
        List<WaitRecord> result = new ArrayList<WaitRecord>();
        for (WaitRecord wait : openWaits(getState(), id)) {
            result.add(wait.copy());
        }
        return result;
    }

    public List<WaitRecord> listOpenWaitsInScope(String scopeKey) {
        HarnessState state = getState();
        List<WaitRecord> result = new ArrayList<WaitRecord>();
        for (WaitRecord wait : state.getWaits().values()) {
            ExecutionRecord execution = wait == null ? null : state.getExecutions().get(wait.getExecutionId());
            if (wait != null && WaitStatus.OPEN.equals(wait.getStatus())
                    && execution != null && HarnessSupport.visibleScope(execution.getScopeKey(), scopeKey)) {
                result.add(wait.copy());
            }
        }
        return result;
    }

    public AgentSessionSnapshot getSessionSnapshot(String sessionId) {
        String id = HarnessSupport.trimToNull(sessionId);
        if (id == null) {
            return null;
        }
        AgentSessionSnapshot snapshot = getState().getSessions().get(id);
        return snapshot == null ? null : HarnessJson.copy(snapshot, AgentSessionSnapshot.class);
    }

    public CheckpointRecord getCheckpoint(String checkpointId) {
        String id = HarnessSupport.trimToNull(checkpointId);
        if (id == null) {
            return null;
        }
        CheckpointRecord checkpoint = getState().getCheckpoints().get(id);
        return checkpoint == null ? null : checkpoint.copy();
    }

    public WakeupRecord getLatestWakeup(String executionId) {
        String id = HarnessSupport.trimToNull(executionId);
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

    public List<FactRecord> listFactsInScope(String scopeKey) {
        List<FactRecord> result = new ArrayList<FactRecord>();
        for (FactRecord fact : getState().getFacts().values()) {
            if (fact != null && HarnessSupport.visibleScope(fact.getScopeKey(), scopeKey)) {
                result.add(fact.copy());
            }
        }
        return result;
    }

    public List<DecisionRecord> listDecisionsInScope(String scopeKey) {
        List<DecisionRecord> result = new ArrayList<DecisionRecord>();
        for (DecisionRecord decision : getState().getDecisions().values()) {
            if (decision != null && HarnessSupport.visibleScope(decision.getScopeKey(), scopeKey)) {
                result.add(decision.copy());
            }
        }
        return result;
    }

    public List<EvidenceRecord> listEvidenceInScope(String scopeKey) {
        List<EvidenceRecord> result = new ArrayList<EvidenceRecord>();
        for (EvidenceRecord item : getState().getEvidence().values()) {
            if (item != null && HarnessSupport.visibleScope(item.getScopeKey(), scopeKey)) {
                result.add(item.copy());
            }
        }
        return result;
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

    public RelationRecord getRelationInScope(String relationId, String scopeKey) {
        RelationRecord relation = getRelation(relationId);
        return relation != null && visibleRelationScope(relation, scopeKey) ? relation : null;
    }

    public ToolInvocationRecord getToolInvocation(String invocationId) {
        ToolInvocationRecord invocation = getState().getToolInvocations().get(invocationId);
        return invocation == null ? null : invocation.copy();
    }

    public ToolInvocationRecord getToolInvocationInScope(String invocationId, String scopeKey) {
        ToolInvocationRecord invocation = getToolInvocation(invocationId);
        return invocation != null && HarnessSupport.visibleScope(invocation.getScopeKey(), scopeKey)
                ? invocation : null;
    }

    public List<ToolInvocationRecord> listToolInvocationsInScope(String scopeKey) {
        List<ToolInvocationRecord> result = new ArrayList<ToolInvocationRecord>();
        for (ToolInvocationRecord invocation : getState().getToolInvocations().values()) {
            if (invocation != null && HarnessSupport.visibleScope(invocation.getScopeKey(), scopeKey)) {
                result.add(invocation.copy());
            }
        }
        return result;
    }

    public List<HarnessRule> listLearnedRulesInScope(String scopeKey) {
        List<HarnessRule> result = new ArrayList<HarnessRule>();
        for (HarnessRule rule : getState().getLearnedRules().values()) {
            if (rule != null && HarnessSupport.visibleScope(rule.getScopeKey(), scopeKey)) {
                result.add(rule.copy());
            }
        }
        return result;
    }

    public List<AcceptanceRecord> listAcceptances(String executionId) {
        List<AcceptanceRecord> result = new ArrayList<AcceptanceRecord>();
        for (AcceptanceRecord record : getState().getAcceptances().values()) {
            if (record != null && (executionId == null || HarnessSupport.safeEquals(executionId, record.getExecutionId()))) {
                result.add(record.copy());
            }
        }
        return result;
    }

    public List<ExecutionRecord> listExecutionLineage(String executionId) {
        String currentId = HarnessSupport.requireText(executionId, "execution id");
        return executionLineage(getState(), currentId);
    }

    public List<AcceptanceRecord> listAcceptanceLineage(String executionId) {
        HarnessState state = getState();
        return acceptanceLineage(state, executionLineage(state,
                HarnessSupport.requireText(executionId, "execution id")));
    }

    public HarnessLineageSummary summarizeLineage(String executionId) {
        HarnessState state = getState();
        List<ExecutionRecord> executions = executionLineage(state,
                HarnessSupport.requireText(executionId, "execution id"));
        List<AcceptanceRecord> acceptances = acceptanceLineage(state, executions);
        AcceptanceRecord finalAcceptance = acceptances.isEmpty() ? null : acceptances.get(acceptances.size() - 1);
        HarnessAcceptanceStatus finalStatus = finalAcceptance == null ? null : finalAcceptance.getStatus();
        ExecutionRecord targetExecution = executions.get(executions.size() - 1);
        TaskRecord task = targetExecution.getTaskId() == null ? null : state.getTasks().get(targetExecution.getTaskId());
        SubmissionRecord submission = latestSubmissionForExecution(state, targetExecution.getExecutionId());
        Set<String> required = effectiveRequiredChecks(state, task, submission);
        Map<String, AcceptanceRecord> latestByCheck = latestAcceptanceByCheck(acceptances, submission);
        Set<String> missing = new LinkedHashSet<String>(required);
        missing.removeAll(latestByCheck.keySet());
        HarnessAcceptanceStatus aggregate = aggregateAcceptanceStatus(required, latestByCheck, missing);
        boolean requiredPassed = required.isEmpty()
                ? HarnessAcceptanceStatus.PASS == finalStatus : HarnessAcceptanceStatus.PASS == aggregate;
        return new HarnessLineageSummary(executions, acceptances, Math.max(0, executions.size() - 1), finalStatus,
                aggregate, requiredPassed, latestByCheck, required, missing, requiredPassed);
    }

    boolean hasLearnedToolRule(String executionId, String toolName, RuleKind kind) {
        String tool = HarnessSupport.trimToNull(toolName);
        String execId = HarnessSupport.trimToNull(executionId);
        if (tool == null || execId == null) {
            return false;
        }
        HarnessState state = getState();
        ExecutionRecord execution = state.getExecutions().get(execId);
        if (execution == null) {
            return false;
        }
        TaskRecord task = execution.getTaskId() == null
                ? null : state.getTasks().get(execution.getTaskId());
        String scope = execution.getScopeKey();
        for (HarnessRule rule : state.getLearnedRules().values()) {
            if (rule != null && rule.isEnabled() && rule.getKind() == kind
                    && tool.equals(rule.getPayload())
                    && HarnessSupport.safeEquals(HarnessSupport.normalizeScope(rule.getScopeKey()),
                    HarnessSupport.normalizeScope(scope))
                    && rule.appliesTo(task)) {
                return true;
            }
        }
        return false;
    }

    Set<String> effectiveRequiredChecks(HarnessState state,
                                        TaskRecord task,
                                        SubmissionRecord submission) {
        Set<String> required = normalizedChecks(contract.requiredAcceptanceChecks(task, submission));
        if (task == null) {
            return required;
        }
        for (HarnessRule rule : state.getLearnedRules().values()) {
            if (rule != null && rule.isEnabled()
                    && rule.getKind() == RuleKind.REQUIRE_ACCEPTANCE_CHECK
                    && HarnessSupport.safeEquals(HarnessSupport.normalizeScope(rule.getScopeKey()),
                    HarnessSupport.normalizeScope(task.getScopeKey()))
                    && rule.appliesTo(task)) {
                String payload = HarnessSupport.trimToNull(rule.getPayload());
                if (payload != null) {
                    required.add(payload);
                }
            }
        }
        return required;
    }

    static List<WaitRecord> openWaits(HarnessState state, String executionId) {
        List<WaitRecord> result = new ArrayList<WaitRecord>();
        if (state == null || executionId == null) {
            return result;
        }
        for (WaitRecord wait : state.getWaits().values()) {
            if (wait != null && HarnessSupport.safeEquals(executionId, wait.getExecutionId())
                    && WaitStatus.OPEN.equals(wait.getStatus())) {
                result.add(wait);
            }
        }
        Collections.sort(result, new Comparator<WaitRecord>() {
            @Override
            public int compare(WaitRecord left, WaitRecord right) {
                int time = Long.compare(left.getCreatedAtEpochMs(), right.getCreatedAtEpochMs());
                return time != 0 ? time : String.valueOf(left.getWaitId())
                        .compareTo(String.valueOf(right.getWaitId()));
            }
        });
        return result;
    }

    static WaitRecord firstOpenWait(HarnessState state, String executionId) {
        List<WaitRecord> waits = openWaits(state, executionId);
        return waits.isEmpty() ? null : waits.get(0);
    }

    static boolean dependenciesSatisfied(HarnessState state, String taskId) {
        for (RelationRecord relation : state.getRelations().values()) {
            if (relation == null || relation.getType() != RelationType.DEPENDS_ON
                    || !HarnessSupport.safeEquals(relation.getFromId(), taskId)) {
                continue;
            }
            TaskRecord dependency = state.getTasks().get(relation.getToId());
            if (dependency == null || !TaskStatus.DONE.equals(dependency.getStatus())) {
                return false;
            }
        }
        return true;
    }

    static boolean hasOutstandingExecution(HarnessState state, String taskId) {
        return hasOutstandingExecution(state, taskId, null);
    }

    static boolean hasOutstandingExecution(HarnessState state,
                                           String taskId,
                                           String excludedExecutionId) {
        for (ExecutionRecord execution : state.getExecutions().values()) {
            if (execution != null && HarnessSupport.safeEquals(taskId, execution.getTaskId())
                    && !HarnessSupport.safeEquals(excludedExecutionId, execution.getExecutionId())
                    && (ExecutionStatus.READY.equals(execution.getStatus())
                    || ExecutionStatus.RUNNING.equals(execution.getStatus())
                    || ExecutionStatus.WAITING.equals(execution.getStatus()))) {
                return true;
            }
        }
        return false;
    }

    static boolean wouldCreateCycle(HarnessState state, RelationType type, String fromId, String toId) {
        if (HarnessSupport.safeEquals(fromId, toId)) {
            return true;
        }
        Set<String> visited = new LinkedHashSet<String>();
        return reaches(state, type, toId, fromId, visited);
    }

    static boolean entityExists(HarnessState state, EntityKind kind, String id) {
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
            case EXECUTION_REVIEW: return state.getExecutionReviews().containsKey(id);
            case CONSENT: return state.getConsents().containsKey(id);
            case ESCALATION: return state.getEscalations().containsKey(id);
            case READ_SET: return state.getReadSets().containsKey(id);
            case GATE_RUN: return state.getGateRuns().containsKey(id);
            case STEER: return state.getSteers().containsKey(id);
            case HANDOFF: return state.getHandoffs().containsKey(id);
            case AGENT_BLUEPRINT: return state.getAgentBlueprints().containsKey(id);
            case SQUAD_RUN: return state.getSquadRuns().containsKey(id);
            case TYPED_ARTIFACT: return state.getTypedArtifacts().containsKey(id);
            case TODO: return state.getTodos().containsKey(id);
            case WORK: return state.getWorks().containsKey(id);
            case SCHEDULE: return state.getSchedules().containsKey(id);
            case SCHEDULE_RUN: return state.getScheduleRuns().containsKey(id);
            case FLEET_NODE: return state.getFleetNodes().containsKey(id);
            case ORPHAN_EXPORT: return state.getOrphanExports().containsKey(id);
            default: return false;
        }
    }

    static String entityScope(HarnessState state, EntityKind kind, String id) {
        if (state == null || kind == null || id == null) {
            return null;
        }
        switch (kind) {
            case TASK:
                TaskRecord task = state.getTasks().get(id);
                return task == null ? null : HarnessSupport.normalizeScope(task.getScopeKey());
            case FACT:
                FactRecord fact = state.getFacts().get(id);
                return fact == null ? null : HarnessSupport.normalizeScope(fact.getScopeKey());
            case DECISION:
                DecisionRecord decision = state.getDecisions().get(id);
                return decision == null ? null : HarnessSupport.normalizeScope(decision.getScopeKey());
            case EXECUTION:
                ExecutionRecord execution = state.getExecutions().get(id);
                return execution == null ? null : HarnessSupport.normalizeScope(execution.getScopeKey());
            case EVIDENCE:
                EvidenceRecord evidence = state.getEvidence().get(id);
                return evidence == null ? null : HarnessSupport.normalizeScope(evidence.getScopeKey());
            case ESCALATION:
                EscalationRecord esc = state.getEscalations().get(id);
                return esc == null ? null : HarnessSupport.normalizeScope(esc.getScopeKey());
            case WORK:
                WorkRecord work = state.getWorks().get(id);
                return work == null ? null : HarnessSupport.normalizeScope(work.getScopeKey());
            case TODO:
                TodoRecord todo = state.getTodos().get(id);
                return todo == null ? null : HarnessSupport.normalizeScope(todo.getScopeKey());
            default:
                return null;
        }
    }

    static boolean isRunnableStatus(TaskStatus status) {
        return TaskStatus.PLANNED.equals(status) || TaskStatus.ACTIVE.equals(status);
    }

    static boolean visibleRelationScope(RelationRecord relation, String requestedScope) {
        if (relation == null) {
            return false;
        }
        String scope = HarnessSupport.normalizeScope(requestedScope);
        if (scope == null) {
            return true;
        }
        return HarnessSupport.visibleScope(relation.getScopeKey(), scope);
    }

    // ---- Shared CQRS projection, event folding & cursor pagination ----

    static int effectivePageLimit(int limit) {
        return limit <= 0 ? DEFAULT_PAGE_LIMIT : limit;
    }

    static String encodeCursor(String entityType, String entityId) {
        String raw = (entityType == null ? "" : entityType) + "\n" + (entityId == null ? "" : entityId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static EntityCursor decodeCursor(String cursor) {
        String normalized = HarnessSupport.trimToNull(cursor);
        if (normalized == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(normalized);
            String text = new String(decoded, StandardCharsets.UTF_8);
            int sep = text.indexOf('\n');
            if (sep < 0) {
                throw new HarnessValidationException("invalid pagination cursor");
            }
            return new EntityCursor(text.substring(0, sep), text.substring(sep + 1));
        } catch (IllegalArgumentException error) {
            throw new HarnessValidationException("invalid pagination cursor");
        }
    }

    static CursorPage<HarnessEntityRecord> paginateEntities(Iterable<HarnessEntityRecord> source,
                                                            String entityType,
                                                            String statusFilter,
                                                            String cursor,
                                                            int limit) {
        String typeFilter = HarnessSupport.trimToNull(entityType);
        String normalizedStatus = HarnessSupport.trimToNull(statusFilter);
        EntityCursor parsedCursor = decodeCursor(cursor);
        int effectiveLimit = effectivePageLimit(limit);

        List<HarnessEntityRecord> filtered = new ArrayList<HarnessEntityRecord>();
        if (source != null) {
            for (HarnessEntityRecord entity : source) {
                if (entity == null) {
                    continue;
                }
                if (typeFilter != null && !typeFilter.equals(entity.getEntityType())) {
                    continue;
                }
                if (normalizedStatus != null && !normalizedStatus.equals(entity.getStatus())) {
                    continue;
                }
                filtered.add(entity.copy());
            }
        }
        Collections.sort(filtered, ENTITY_ORDER);

        long totalCount = filtered.size();
        List<HarnessEntityRecord> pageItems = new ArrayList<HarnessEntityRecord>();
        boolean hasMore = false;
        for (HarnessEntityRecord entity : filtered) {
            if (parsedCursor != null && !isAfterCursor(entity, parsedCursor)) {
                continue;
            }
            if (pageItems.size() < effectiveLimit) {
                pageItems.add(entity);
            } else {
                hasMore = true;
                break;
            }
        }
        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            HarnessEntityRecord last = pageItems.get(pageItems.size() - 1);
            nextCursor = encodeCursor(last.getEntityType(), last.getEntityId());
        }
        return CursorPage.<HarnessEntityRecord>builder()
                .items(pageItems)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .build();
    }

    static HarnessEntityRecord projectEvent(String harnessId,
                                            HarnessEntityRecord current,
                                            HarnessEventRecord event,
                                            String explicitStatus,
                                            String explicitEntityPayloadJson) {
        String effectiveHarnessId = HarnessSupport.trimToNull(harnessId) == null
                ? "default" : harnessId.trim();
        String entityType = HarnessSupport.requireText(event.getEntityType(), "entity type");
        String entityId = HarnessSupport.requireText(event.getEntityId(), "entity id");
        long prevVersion = current == null ? 0L : current.getEntityVersion();
        long nextVersion = event.getEntityVersion() > 0L ? event.getEntityVersion() : prevVersion + 1L;
        long updatedAt = event.getTimestampEpochMs() > 0L
                ? event.getTimestampEpochMs() : System.currentTimeMillis();

        String nextPayloadJson;
        if (explicitEntityPayloadJson != null) {
            nextPayloadJson = explicitEntityPayloadJson;
        } else {
            nextPayloadJson = mergeJsonPayload(current == null ? null : current.getPayloadJson(),
                    event.getPayloadJson());
        }

        String nextStatus = HarnessSupport.trimToNull(explicitStatus);
        if (nextStatus == null) {
            nextStatus = inferStatus(nextPayloadJson, current == null ? null : current.getStatus());
        }

        return HarnessEntityRecord.builder()
                .harnessId(effectiveHarnessId)
                .entityType(entityType)
                .entityId(entityId)
                .entityVersion(nextVersion)
                .status(nextStatus)
                .updatedAtEpochMs(updatedAt)
                .payloadJson(nextPayloadJson)
                .build();
    }

    static Map<EntityKey, HarnessEntityRecord> foldEvents(String harnessId,
                                                          List<HarnessEventRecord> events) {
        Map<EntityKey, HarnessEntityRecord> projection = new LinkedHashMap<EntityKey, HarnessEntityRecord>();
        if (events == null || events.isEmpty()) {
            return projection;
        }
        List<HarnessEventRecord> sorted = new ArrayList<HarnessEventRecord>(events);
        Collections.sort(sorted, new Comparator<HarnessEventRecord>() {
            @Override
            public int compare(HarnessEventRecord left, HarnessEventRecord right) {
                return Long.compare(left.getSeq(), right.getSeq());
            }
        });
        for (HarnessEventRecord event : sorted) {
            if (event == null || HarnessSupport.trimToNull(event.getEntityType()) == null
                    || HarnessSupport.trimToNull(event.getEntityId()) == null) {
                continue;
            }
            EntityKey key = new EntityKey(event.getEntityType(), event.getEntityId());
            HarnessEntityRecord current = projection.get(key);
            HarnessEntityRecord next = projectEvent(harnessId, current, event, null, null);
            projection.put(key, next);
        }
        return projection;
    }

    static Map<EntityKey, HarnessEntityRecord> extractEntitiesFromState(String harnessId,
                                                                        HarnessState state) {
        Map<EntityKey, HarnessEntityRecord> result = new LinkedHashMap<EntityKey, HarnessEntityRecord>();
        if (state == null) {
            return result;
        }
        state.ensureCollections();
        String hid = HarnessSupport.trimToNull(harnessId) == null
                ? (HarnessSupport.trimToNull(state.getHarnessId()) == null ? "default" : state.getHarnessId().trim())
                : harnessId.trim();

        for (TaskRecord r : state.getTasks().values()) {
            if (r != null && r.getTaskId() != null) {
                putEntity(result, toEntityRecord(hid, "TASK", r.getTaskId(), Math.max(1L, r.getVersion()),
                        enumName(r.getStatus()), r.getUpdatedAtEpochMs(), r));
            }
        }
        for (ExecutionRecord r : state.getExecutions().values()) {
            if (r != null && r.getExecutionId() != null) {
                putEntity(result, toEntityRecord(hid, "EXECUTION", r.getExecutionId(), Math.max(1L, r.getVersion()),
                        enumName(r.getStatus()), r.getUpdatedAtEpochMs(), r));
            }
        }
        for (FactRecord r : state.getFacts().values()) {
            if (r != null && r.getFactId() != null) {
                putEntity(result, toEntityRecord(hid, "FACT", r.getFactId(), Math.max(1L, r.getVersion()),
                        r.isValid() ? "VALID" : "INVALIDATED",
                        Math.max(r.getCreatedAtEpochMs(), r.getInvalidatedAtEpochMs()), r));
            }
        }
        for (DecisionRecord r : state.getDecisions().values()) {
            if (r != null && r.getDecisionId() != null) {
                putEntity(result, toEntityRecord(hid, "DECISION", r.getDecisionId(), Math.max(1L, r.getVersion()),
                        enumName(r.getStatus()),
                        Math.max(r.getCreatedAtEpochMs(), Math.max(r.getResolvedAtEpochMs(), r.getAmendedAtEpochMs())), r));
            }
        }
        for (EvidenceRecord r : state.getEvidence().values()) {
            if (r != null && r.getEvidenceId() != null) {
                putEntity(result, toEntityRecord(hid, "EVIDENCE", r.getEvidenceId(), 1L,
                        "RECORDED", r.getCreatedAtEpochMs(), r));
            }
        }
        for (RelationRecord r : state.getRelations().values()) {
            if (r != null && r.getRelationId() != null) {
                putEntity(result, toEntityRecord(hid, "RELATION", r.getRelationId(), 1L,
                        enumName(r.getType()), r.getCreatedAtEpochMs(), r));
            }
        }
        for (CheckpointRecord r : state.getCheckpoints().values()) {
            if (r != null && r.getCheckpointId() != null) {
                putEntity(result, toEntityRecord(hid, "CHECKPOINT", r.getCheckpointId(), 1L,
                        "RECORDED", r.getCreatedAtEpochMs(), r));
            }
        }
        for (WaitRecord r : state.getWaits().values()) {
            if (r != null && r.getWaitId() != null) {
                putEntity(result, toEntityRecord(hid, "WAIT", r.getWaitId(), 1L,
                        enumName(r.getStatus()),
                        Math.max(r.getCreatedAtEpochMs(), r.getResolvedAtEpochMs()), r));
            }
        }
        for (WakeupRecord r : state.getWakeups().values()) {
            if (r != null && r.getWakeupId() != null) {
                putEntity(result, toEntityRecord(hid, "WAKEUP", r.getWakeupId(), 1L,
                        "DELIVERED", r.getDeliveredAtEpochMs(), r));
            }
        }
        for (LeaseRecord r : state.getLeases().values()) {
            if (r != null && r.getLeaseId() != null) {
                putEntity(result, toEntityRecord(hid, "LEASE", r.getLeaseId(), 1L,
                        r.getReleasedAtEpochMs() > 0L ? "RELEASED" : "ACTIVE",
                        Math.max(r.getAcquiredAtEpochMs(), r.getReleasedAtEpochMs()), r));
            }
        }
        for (SessionLeaseRecord r : state.getSessionLeases().values()) {
            if (r != null && r.getSessionId() != null) {
                putEntity(result, toEntityRecord(hid, "SESSION_LEASE", r.getSessionId(), 1L,
                        r.getReleasedAtEpochMs() > 0L ? "RELEASED" : "ACTIVE",
                        Math.max(r.getAcquiredAtEpochMs(), r.getReleasedAtEpochMs()), r));
            }
        }
        for (GateRecord r : state.getGates().values()) {
            if (r != null && r.getGateId() != null) {
                putEntity(result, toEntityRecord(hid, "GATE", r.getGateId(), 1L,
                        enumName(r.getStatus()), r.getEvaluatedAtEpochMs(), r));
            }
        }
        for (AcceptanceRecord r : state.getAcceptances().values()) {
            if (r != null && r.getAcceptanceId() != null) {
                putEntity(result, toEntityRecord(hid, "ACCEPTANCE", r.getAcceptanceId(), 1L,
                        enumName(r.getStatus()), r.getEvaluatedAtEpochMs(), r));
            }
        }
        for (SubmissionRecord r : state.getSubmissions().values()) {
            if (r != null && r.getSubmissionId() != null) {
                putEntity(result, toEntityRecord(hid, "SUBMISSION", r.getSubmissionId(), 1L,
                        "SUBMITTED", r.getCreatedAtEpochMs(), r));
            }
        }
        for (ReviewRecord r : state.getReviews().values()) {
            if (r != null && r.getReviewId() != null) {
                putEntity(result, toEntityRecord(hid, "REVIEW", r.getReviewId(), 1L,
                        enumName(r.getVerdict()), r.getCreatedAtEpochMs(), r));
            }
        }
        for (ExecutionReviewRecord r : state.getExecutionReviews().values()) {
            if (r != null && r.getReviewId() != null) {
                putEntity(result, toEntityRecord(hid, "EXECUTION_REVIEW", r.getReviewId(), 1L,
                        enumName(r.getVerdict()), r.getCreatedAtEpochMs(), r));
            }
        }
        for (ContentPinnedConsentRecord r : state.getConsents().values()) {
            if (r != null && r.getConsentId() != null) {
                putEntity(result, toEntityRecord(hid, "CONSENT", r.getConsentId(), Math.max(1L, r.getVersion()),
                        r.isApproved() ? "APPROVED" : "REJECTED", r.getConsentedAtEpochMs(), r));
            }
        }
        for (EscalationRecord r : state.getEscalations().values()) {
            if (r != null && r.getEscalationId() != null) {
                putEntity(result, toEntityRecord(hid, "ESCALATION", r.getEscalationId(), Math.max(1L, r.getVersion()),
                        enumName(r.getStatus()),
                        Math.max(r.getCreatedAtEpochMs(), Math.max(r.getAnsweredAtEpochMs(), r.getFollowedUpAtEpochMs())), r));
            }
        }
        for (ReadSetEntry r : state.getReadSets().values()) {
            if (r != null && r.getReadId() != null) {
                putEntity(result, toEntityRecord(hid, "READ_SET", r.getReadId(), 1L,
                        r.getObservedStatus(), Math.max(r.getReadAtEpochMs(), r.getReconfirmedAtEpochMs()), r));
            }
        }
        for (GateRunRecord r : state.getGateRuns().values()) {
            if (r != null && r.getGateRunId() != null) {
                putEntity(result, toEntityRecord(hid, "GATE_RUN", r.getGateRunId(), 1L,
                        r.isPassed() ? "PASS" : "FAIL", r.getCreatedAtEpochMs(), r));
            }
        }
        for (SteerRecord r : state.getSteers().values()) {
            if (r != null && r.getSteerId() != null) {
                putEntity(result, toEntityRecord(hid, "STEER", r.getSteerId(), 1L,
                        enumName(r.getStatus()), Math.max(r.getCreatedAtEpochMs(), r.getAppliedAtEpochMs()), r));
            }
        }
        for (HandoffRecord r : state.getHandoffs().values()) {
            if (r != null && r.getHandoffId() != null) {
                putEntity(result, toEntityRecord(hid, "HANDOFF", r.getHandoffId(), Math.max(1L, r.getVersion()),
                        enumName(r.getStatus()),
                        Math.max(r.getExportedAtEpochMs(), Math.max(r.getClaimedAtEpochMs(), r.getRevokedAtEpochMs())), r));
            }
        }
        for (AgentBlueprintRecord r : state.getAgentBlueprints().values()) {
            if (r != null && r.getBlueprintId() != null) {
                putEntity(result, toEntityRecord(hid, "AGENT_BLUEPRINT", r.getBlueprintId(), Math.max(1L, r.getVersion()),
                        r.isActive() ? "ACTIVE" : "INACTIVE", r.getCreatedAtEpochMs(), r));
            }
        }
        for (SquadRunRecord r : state.getSquadRuns().values()) {
            if (r != null && r.getSquadRunId() != null) {
                putEntity(result, toEntityRecord(hid, "SQUAD_RUN", r.getSquadRunId(), Math.max(1L, r.getVersion()),
                        enumName(r.getStatus()), Math.max(r.getCreatedAtEpochMs(), r.getFinishedAtEpochMs()), r));
            }
        }
        for (TypedArtifactRecord r : state.getTypedArtifacts().values()) {
            if (r != null && r.getArtifactId() != null) {
                putEntity(result, toEntityRecord(hid, "TYPED_ARTIFACT", r.getArtifactId(), 1L,
                        r.getKindId(), r.getCreatedAtEpochMs(), r));
            }
        }
        for (TodoRecord r : state.getTodos().values()) {
            if (r != null && r.getTodoId() != null) {
                putEntity(result, toEntityRecord(hid, "TODO", r.getTodoId(), Math.max(1L, r.getVersion()),
                        enumName(r.getStatus()), Math.max(r.getCreatedAtEpochMs(), r.getUpdatedAtEpochMs()), r));
            }
        }
        for (WorkRecord r : state.getWorks().values()) {
            if (r != null && r.getWorkId() != null) {
                putEntity(result, toEntityRecord(hid, "WORK", r.getWorkId(), Math.max(1L, r.getVersion()),
                        enumName(r.getStatus()), Math.max(r.getCreatedAtEpochMs(), r.getUpdatedAtEpochMs()), r));
            }
        }
        for (ScheduleRecord r : state.getSchedules().values()) {
            if (r != null && r.getScheduleId() != null) {
                putEntity(result, toEntityRecord(hid, "SCHEDULE", r.getScheduleId(), Math.max(1L, r.getVersion()),
                        r.isEnabled() ? "ENABLED" : "DISABLED",
                        Math.max(r.getCreatedAtEpochMs(), r.getLastTriggeredAtEpochMs()), r));
            }
        }
        for (ScheduleRunRecord r : state.getScheduleRuns().values()) {
            if (r != null && r.getScheduleRunId() != null) {
                putEntity(result, toEntityRecord(hid, "SCHEDULE_RUN", r.getScheduleRunId(), 1L,
                        enumName(r.getStatus()), Math.max(r.getCreatedAtEpochMs(), r.getFinishedAtEpochMs()), r));
            }
        }
        for (FleetNodeRecord r : state.getFleetNodes().values()) {
            if (r != null && r.getNodeId() != null) {
                putEntity(result, toEntityRecord(hid, "FLEET_NODE", r.getNodeId(), Math.max(1L, r.getVersion()),
                        enumName(r.getStatus()), Math.max(r.getJoinedAtEpochMs(), r.getLastHeartbeatEpochMs()), r));
            }
        }
        for (OrphanHandoffExport r : state.getOrphanExports().values()) {
            if (r != null && r.getExportId() != null) {
                putEntity(result, toEntityRecord(hid, "ORPHAN_EXPORT", r.getExportId(), 1L,
                        enumName(r.getStatus()), Math.max(r.getExportedAtEpochMs(), r.getTakenOverAtEpochMs()), r));
            }
        }
        for (ToolInvocationRecord r : state.getToolInvocations().values()) {
            if (r != null && r.getInvocationId() != null) {
                putEntity(result, toEntityRecord(hid, "TOOL_INVOCATION", r.getInvocationId(),
                        Math.max(1L, r.getVersion()), enumName(r.getStatus()), r.getUpdatedAtEpochMs(), r));
            }
        }
        for (HarnessRule r : state.getLearnedRules().values()) {
            if (r != null && r.getRuleId() != null) {
                putEntity(result, toEntityRecord(hid, "RULE", r.getRuleId(), 1L,
                        r.isEnabled() ? "ENABLED" : "REVOKED",
                        Math.max(r.getPromotedAtEpochMs(), r.getRevokedAtEpochMs()), r));
            }
        }
        for (Map.Entry<String, AgentSessionSnapshot> entry : state.getSessions().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                long updatedAt = entry.getValue().getMetadata() == null
                        ? 0L : entry.getValue().getMetadata().getUpdatedAtEpochMs();
                putEntity(result, toEntityRecord(hid, "SESSION", entry.getKey(), 1L,
                        "ACTIVE", updatedAt, entry.getValue()));
            }
        }
        return result;
    }

    static boolean applyEntityToState(HarnessState state, HarnessEntityRecord entity) {
        if (state == null || entity == null || entity.getEntityType() == null
                || entity.getEntityId() == null || entity.getPayloadJson() == null) {
            return false;
        }
        state.ensureCollections();
        String type = entity.getEntityType();
        String id = entity.getEntityId();
        String json = entity.getPayloadJson();
        try {
            if ("TASK".equals(type)) {
                TaskRecord r = HarnessJson.fromJson(json, TaskRecord.class);
                if (r != null) {
                    if (r.getTaskId() == null) r.setTaskId(id);
                    r.setVersion(entity.getEntityVersion());
                    r.setUpdatedAtEpochMs(entity.getUpdatedAtEpochMs());
                    state.getTasks().put(id, r);
                    return true;
                }
            } else if ("EXECUTION".equals(type)) {
                ExecutionRecord r = HarnessJson.fromJson(json, ExecutionRecord.class);
                if (r != null) {
                    if (r.getExecutionId() == null) r.setExecutionId(id);
                    r.setVersion(entity.getEntityVersion());
                    r.setUpdatedAtEpochMs(entity.getUpdatedAtEpochMs());
                    state.getExecutions().put(id, r);
                    return true;
                }
            } else if ("FACT".equals(type)) {
                FactRecord r = HarnessJson.fromJson(json, FactRecord.class);
                if (r != null) {
                    if (r.getFactId() == null) r.setFactId(id);
                    state.getFacts().put(id, r);
                    return true;
                }
            } else if ("DECISION".equals(type)) {
                DecisionRecord r = HarnessJson.fromJson(json, DecisionRecord.class);
                if (r != null) {
                    if (r.getDecisionId() == null) r.setDecisionId(id);
                    state.getDecisions().put(id, r);
                    return true;
                }
            } else if ("EVIDENCE".equals(type)) {
                EvidenceRecord r = HarnessJson.fromJson(json, EvidenceRecord.class);
                if (r != null) {
                    if (r.getEvidenceId() == null) r.setEvidenceId(id);
                    state.getEvidence().put(id, r);
                    return true;
                }
            } else if ("RELATION".equals(type)) {
                RelationRecord r = HarnessJson.fromJson(json, RelationRecord.class);
                if (r != null) {
                    if (r.getRelationId() == null) r.setRelationId(id);
                    state.getRelations().put(id, r);
                    return true;
                }
            } else if ("CHECKPOINT".equals(type)) {
                CheckpointRecord r = HarnessJson.fromJson(json, CheckpointRecord.class);
                if (r != null) {
                    if (r.getCheckpointId() == null) r.setCheckpointId(id);
                    state.getCheckpoints().put(id, r);
                    return true;
                }
            } else if ("WAIT".equals(type)) {
                WaitRecord r = HarnessJson.fromJson(json, WaitRecord.class);
                if (r != null) {
                    if (r.getWaitId() == null) r.setWaitId(id);
                    state.getWaits().put(id, r);
                    return true;
                }
            } else if ("WAKEUP".equals(type)) {
                WakeupRecord r = HarnessJson.fromJson(json, WakeupRecord.class);
                if (r != null) {
                    if (r.getWakeupId() == null) r.setWakeupId(id);
                    state.getWakeups().put(id, r);
                    return true;
                }
            } else if ("LEASE".equals(type)) {
                LeaseRecord r = HarnessJson.fromJson(json, LeaseRecord.class);
                if (r != null) {
                    if (r.getLeaseId() == null) r.setLeaseId(id);
                    state.getLeases().put(id, r);
                    return true;
                }
            } else if ("SESSION_LEASE".equals(type)) {
                SessionLeaseRecord r = HarnessJson.fromJson(json, SessionLeaseRecord.class);
                if (r != null) {
                    if (r.getSessionId() == null) r.setSessionId(id);
                    state.getSessionLeases().put(id, r);
                    return true;
                }
            } else if ("GATE".equals(type)) {
                GateRecord r = HarnessJson.fromJson(json, GateRecord.class);
                if (r != null) {
                    if (r.getGateId() == null) r.setGateId(id);
                    state.getGates().put(id, r);
                    return true;
                }
            } else if ("ACCEPTANCE".equals(type)) {
                AcceptanceRecord r = HarnessJson.fromJson(json, AcceptanceRecord.class);
                if (r != null) {
                    if (r.getAcceptanceId() == null) r.setAcceptanceId(id);
                    state.getAcceptances().put(id, r);
                    return true;
                }
            } else if ("SUBMISSION".equals(type)) {
                SubmissionRecord r = HarnessJson.fromJson(json, SubmissionRecord.class);
                if (r != null) {
                    if (r.getSubmissionId() == null) r.setSubmissionId(id);
                    state.getSubmissions().put(id, r);
                    return true;
                }
            } else if ("REVIEW".equals(type)) {
                ReviewRecord r = HarnessJson.fromJson(json, ReviewRecord.class);
                if (r != null) {
                    if (r.getReviewId() == null) r.setReviewId(id);
                    state.getReviews().put(id, r);
                    return true;
                }
            } else if ("EXECUTION_REVIEW".equals(type)) {
                ExecutionReviewRecord r = HarnessJson.fromJson(json, ExecutionReviewRecord.class);
                if (r != null) {
                    if (r.getReviewId() == null) r.setReviewId(id);
                    state.getExecutionReviews().put(id, r);
                    return true;
                }
            } else if ("CONSENT".equals(type)) {
                ContentPinnedConsentRecord r = HarnessJson.fromJson(json, ContentPinnedConsentRecord.class);
                if (r != null) {
                    if (r.getConsentId() == null) r.setConsentId(id);
                    r.setVersion(entity.getEntityVersion());
                    state.getConsents().put(id, r);
                    return true;
                }
            } else if ("ESCALATION".equals(type)) {
                EscalationRecord r = HarnessJson.fromJson(json, EscalationRecord.class);
                if (r != null) {
                    if (r.getEscalationId() == null) r.setEscalationId(id);
                    r.setVersion(entity.getEntityVersion());
                    state.getEscalations().put(id, r);
                    return true;
                }
            } else if ("READ_SET".equals(type)) {
                ReadSetEntry r = HarnessJson.fromJson(json, ReadSetEntry.class);
                if (r != null) {
                    if (r.getReadId() == null) r.setReadId(id);
                    state.getReadSets().put(id, r);
                    return true;
                }
            } else if ("GATE_RUN".equals(type)) {
                GateRunRecord r = HarnessJson.fromJson(json, GateRunRecord.class);
                if (r != null) {
                    if (r.getGateRunId() == null) r.setGateRunId(id);
                    state.getGateRuns().put(id, r);
                    return true;
                }
            } else if ("STEER".equals(type)) {
                SteerRecord r = HarnessJson.fromJson(json, SteerRecord.class);
                if (r != null) {
                    if (r.getSteerId() == null) r.setSteerId(id);
                    state.getSteers().put(id, r);
                    return true;
                }
            } else if ("HANDOFF".equals(type)) {
                HandoffRecord r = HarnessJson.fromJson(json, HandoffRecord.class);
                if (r != null) {
                    if (r.getHandoffId() == null) r.setHandoffId(id);
                    r.setVersion(entity.getEntityVersion());
                    state.getHandoffs().put(id, r);
                    return true;
                }
            } else if ("AGENT_BLUEPRINT".equals(type)) {
                AgentBlueprintRecord r = HarnessJson.fromJson(json, AgentBlueprintRecord.class);
                if (r != null) {
                    if (r.getBlueprintId() == null) r.setBlueprintId(id);
                    r.setVersion(entity.getEntityVersion());
                    state.getAgentBlueprints().put(id, r);
                    return true;
                }
            } else if ("SQUAD_RUN".equals(type)) {
                SquadRunRecord r = HarnessJson.fromJson(json, SquadRunRecord.class);
                if (r != null) {
                    if (r.getSquadRunId() == null) r.setSquadRunId(id);
                    r.setVersion(entity.getEntityVersion());
                    state.getSquadRuns().put(id, r);
                    return true;
                }
            } else if ("TYPED_ARTIFACT".equals(type)) {
                TypedArtifactRecord r = HarnessJson.fromJson(json, TypedArtifactRecord.class);
                if (r != null) {
                    if (r.getArtifactId() == null) r.setArtifactId(id);
                    state.getTypedArtifacts().put(id, r);
                    return true;
                }
            } else if ("TODO".equals(type)) {
                TodoRecord r = HarnessJson.fromJson(json, TodoRecord.class);
                if (r != null) {
                    if (r.getTodoId() == null) r.setTodoId(id);
                    r.setVersion(entity.getEntityVersion());
                    state.getTodos().put(id, r);
                    return true;
                }
            } else if ("WORK".equals(type)) {
                WorkRecord r = HarnessJson.fromJson(json, WorkRecord.class);
                if (r != null) {
                    if (r.getWorkId() == null) r.setWorkId(id);
                    r.setVersion(entity.getEntityVersion());
                    state.getWorks().put(id, r);
                    return true;
                }
            } else if ("SCHEDULE".equals(type)) {
                ScheduleRecord r = HarnessJson.fromJson(json, ScheduleRecord.class);
                if (r != null) {
                    if (r.getScheduleId() == null) r.setScheduleId(id);
                    r.setVersion(entity.getEntityVersion());
                    state.getSchedules().put(id, r);
                    return true;
                }
            } else if ("SCHEDULE_RUN".equals(type)) {
                ScheduleRunRecord r = HarnessJson.fromJson(json, ScheduleRunRecord.class);
                if (r != null) {
                    if (r.getScheduleRunId() == null) r.setScheduleRunId(id);
                    state.getScheduleRuns().put(id, r);
                    return true;
                }
            } else if ("FLEET_NODE".equals(type)) {
                FleetNodeRecord r = HarnessJson.fromJson(json, FleetNodeRecord.class);
                if (r != null) {
                    if (r.getNodeId() == null) r.setNodeId(id);
                    r.setVersion(entity.getEntityVersion());
                    state.getFleetNodes().put(id, r);
                    return true;
                }
            } else if ("ORPHAN_EXPORT".equals(type)) {
                OrphanHandoffExport r = HarnessJson.fromJson(json, OrphanHandoffExport.class);
                if (r != null) {
                    if (r.getExportId() == null) r.setExportId(id);
                    state.getOrphanExports().put(id, r);
                    return true;
                }
            } else if ("TOOL_INVOCATION".equals(type)) {
                ToolInvocationRecord r = HarnessJson.fromJson(json, ToolInvocationRecord.class);
                if (r != null) {
                    if (r.getInvocationId() == null) r.setInvocationId(id);
                    r.setVersion(entity.getEntityVersion());
                    r.setUpdatedAtEpochMs(entity.getUpdatedAtEpochMs());
                    state.getToolInvocations().put(id, r);
                    return true;
                }
            } else if ("RULE".equals(type)) {
                HarnessRule r = HarnessJson.fromJson(json, HarnessRule.class);
                if (r != null) {
                    if (r.getRuleId() == null) r.setRuleId(id);
                    state.getLearnedRules().put(id, r);
                    return true;
                }
            } else if ("SESSION".equals(type)) {
                AgentSessionSnapshot r = HarnessJson.fromJson(json, AgentSessionSnapshot.class);
                if (r != null) {
                    state.getSessions().put(id, r);
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    static HarnessEventRecord enrichEventForLedger(String harnessId,
                                                   long seq,
                                                   HarnessEventRecord source,
                                                   Map<EntityKey, HarnessEntityRecord> entities) {
        String eventType = HarnessSupport.trimToNull(source.getEventType());
        String entityId = HarnessSupport.trimToNull(source.getEntityId());
        String entityType = HarnessSupport.trimToNull(source.getEntityType());
        if (entityType == null) {
            entityType = inferEntityType(eventType);
        }
        HarnessEntityRecord matched = entityType != null && entityId != null
                ? entities.get(new EntityKey(entityType, entityId)) : null;
        if (matched == null && entityId != null) {
            for (HarnessEntityRecord candidate : entities.values()) {
                if (candidate != null && entityId.equals(candidate.getEntityId())) {
                    matched = candidate;
                    entityType = candidate.getEntityType();
                    break;
                }
            }
        }
        long entityVersion = source.getEntityVersion();
        if (entityVersion <= 0L) {
            entityVersion = matched != null ? matched.getEntityVersion() : 1L;
        }
        String payloadJson = source.getPayloadJson();
        if (matched != null && matched.getPayloadJson() != null) {
            payloadJson = mergeJsonPayload(matched.getPayloadJson(), payloadJson);
        } else if (payloadJson == null) {
            payloadJson = "{}";
        }
        String eventId = HarnessSupport.trimToNull(source.getEventId());
        if (eventId == null) {
            eventId = "evt_" + seq + "_" + HarnessSupport.valueOrGenerated(null, "");
        }
        return HarnessEventRecord.builder()
                .seq(seq)
                .eventId(eventId)
                .harnessId(harnessId)
                .entityType(entityType == null ? "HARNESS" : entityType)
                .entityId(entityId == null ? harnessId : entityId)
                .eventType(eventType == null ? "event" : eventType)
                .expectedVersion(source.getExpectedVersion())
                .entityVersion(entityVersion)
                .writerEpoch(source.getWriterEpoch())
                .actor(source.getActor())
                .idempotencyKey(HarnessSupport.trimToNull(source.getIdempotencyKey()))
                .timestampEpochMs(source.getTimestampEpochMs() > 0L ? source.getTimestampEpochMs() : System.currentTimeMillis())
                .payloadJson(payloadJson)
                .build();
    }

    static String inferEntityType(String eventType) {
        if (eventType == null) {
            return "HARNESS";
        }
        String lower = eventType.toLowerCase();
        if (lower.startsWith("task.")) return "TASK";
        if (lower.startsWith("execution.")) return "EXECUTION";
        if (lower.startsWith("wait.")) return "WAIT";
        if (lower.startsWith("wakeup.")) return "WAKEUP";
        if (lower.startsWith("fact.")) return "FACT";
        if (lower.startsWith("decision.")) return "DECISION";
        if (lower.startsWith("evidence.")) return "EVIDENCE";
        if (lower.startsWith("relation.")) return "RELATION";
        if (lower.startsWith("checkpoint.")) return "CHECKPOINT";
        if (lower.startsWith("submission.")) return "SUBMISSION";
        if (lower.startsWith("review.")) return "REVIEW";
        if (lower.startsWith("execution_review.")) return "EXECUTION_REVIEW";
        if (lower.startsWith("consent.")) return "CONSENT";
        if (lower.startsWith("escalation.")) return "ESCALATION";
        if (lower.startsWith("readset.")) return "READ_SET";
        if (lower.startsWith("witness.")) return "GATE_RUN";
        if (lower.startsWith("steer.")) return "STEER";
        if (lower.startsWith("handoff.")) return "HANDOFF";
        if (lower.startsWith("blueprint.")) return "AGENT_BLUEPRINT";
        if (lower.startsWith("squad.")) return "SQUAD_RUN";
        if (lower.startsWith("artifact.")) return "TYPED_ARTIFACT";
        if (lower.startsWith("todo.")) return "TODO";
        if (lower.startsWith("work.")) return "WORK";
        if (lower.startsWith("schedule.run")) return "SCHEDULE_RUN";
        if (lower.startsWith("schedule.")) return "SCHEDULE";
        if (lower.startsWith("fleet.node")) return "FLEET_NODE";
        if (lower.startsWith("fleet.orphan")) return "ORPHAN_EXPORT";
        if (lower.startsWith("acceptance.")) return "ACCEPTANCE";
        if (lower.startsWith("gate.")) return "GATE";
        if (lower.startsWith("tool.invocation")) return "TOOL_INVOCATION";
        if (lower.startsWith("rule.")) return "RULE";
        if (lower.startsWith("session.lease")) return "SESSION_LEASE";
        return "HARNESS";
    }

    static String mergeJsonPayload(String baseJson, String overlayJson) {
        String trimmedOverlay = HarnessSupport.trimToNull(overlayJson);
        String trimmedBase = HarnessSupport.trimToNull(baseJson);
        if (trimmedOverlay == null) {
            return trimmedBase == null ? "{}" : trimmedBase;
        }
        if (trimmedBase == null) {
            return trimmedOverlay;
        }
        try {
            Map<String, Object> baseMap = HarnessJson.parseMap(trimmedBase);
            Map<String, Object> overlayMap = HarnessJson.parseMap(trimmedOverlay);
            if (baseMap == null || overlayMap == null) {
                return trimmedOverlay;
            }
            Map<String, Object> merged = new LinkedHashMap<String, Object>(baseMap);
            for (Map.Entry<String, Object> entry : overlayMap.entrySet()) {
                if (entry.getValue() == null) {
                    merged.remove(entry.getKey());
                } else {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
            return HarnessJson.toJson(merged);
        } catch (RuntimeException ignored) {
            return trimmedOverlay;
        }
    }

    private static String inferStatus(String payloadJson, String fallbackStatus) {
        if (payloadJson != null && !payloadJson.trim().isEmpty()) {
            try {
                Map<String, Object> map = HarnessJson.parseMap(payloadJson);
                if (map != null) {
                    Object status = map.get("status");
                    if (status != null && !String.valueOf(status).trim().isEmpty()) {
                        return String.valueOf(status).trim();
                    }
                    Object verdict = map.get("verdict");
                    if (verdict != null && !String.valueOf(verdict).trim().isEmpty()) {
                        return String.valueOf(verdict).trim();
                    }
                    Object valid = map.get("valid");
                    if (valid instanceof Boolean) {
                        return ((Boolean) valid).booleanValue() ? "VALID" : "INVALIDATED";
                    }
                    Object enabled = map.get("enabled");
                    if (enabled instanceof Boolean) {
                        return ((Boolean) enabled).booleanValue() ? "ENABLED" : "REVOKED";
                    }
                }
            } catch (RuntimeException ignored) {
                // Non-object JSON payload has no inferred status field.
            }
        }
        return fallbackStatus;
    }

    private static HarnessEntityRecord toEntityRecord(String harnessId,
                                                      String entityType,
                                                      String entityId,
                                                      long entityVersion,
                                                      String status,
                                                      long updatedAtEpochMs,
                                                      Object payload) {
        return HarnessEntityRecord.builder()
                .harnessId(harnessId)
                .entityType(entityType)
                .entityId(entityId)
                .entityVersion(entityVersion)
                .status(status)
                .updatedAtEpochMs(updatedAtEpochMs)
                .payloadJson(HarnessJson.toJson(payload))
                .build();
    }

    private static void putEntity(Map<EntityKey, HarnessEntityRecord> target,
                                  HarnessEntityRecord record) {
        target.put(new EntityKey(record.getEntityType(), record.getEntityId()), record);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static boolean isAfterCursor(HarnessEntityRecord entity, EntityCursor cursor) {
        int typeCmp = compareNullable(entity.getEntityType(), cursor.entityType);
        if (typeCmp != 0) {
            return typeCmp > 0;
        }
        return compareNullable(entity.getEntityId(), cursor.entityId) > 0;
    }

    private static int compareNullable(String left, String right) {
        String l = left == null ? "" : left;
        String r = right == null ? "" : right;
        return l.compareTo(r);
    }

    private static final Comparator<HarnessEntityRecord> ENTITY_ORDER = new Comparator<HarnessEntityRecord>() {
        @Override
        public int compare(HarnessEntityRecord left, HarnessEntityRecord right) {
            int typeCmp = compareNullable(left.getEntityType(), right.getEntityType());
            if (typeCmp != 0) {
                return typeCmp;
            }
            return compareNullable(left.getEntityId(), right.getEntityId());
        }
    };

    private static boolean reaches(HarnessState state,
                                   RelationType type,
                                   String current,
                                   String target,
                                   Set<String> visited) {
        if (!visited.add(current)) {
            return false;
        }
        for (RelationRecord relation : state.getRelations().values()) {
            if (relation == null || relation.getType() != type
                    || !HarnessSupport.safeEquals(relation.getFromId(), current)) {
                continue;
            }
            if (HarnessSupport.safeEquals(relation.getToId(), target)
                    || reaches(state, type, relation.getToId(), target, visited)) {
                return true;
            }
        }
        return false;
    }

    private List<ExecutionRecord> executionLineage(HarnessState state, String executionId) {
        String currentId = HarnessSupport.requireText(executionId, "execution id");
        List<ExecutionRecord> reverse = new ArrayList<ExecutionRecord>();
        Set<String> seen = new HashSet<String>();
        while (currentId != null && seen.add(currentId)) {
            ExecutionRecord current = state.getExecutions().get(currentId);
            if (current == null) {
                throw new HarnessValidationException("execution not found: " + currentId);
            }
            reverse.add(current.copy());
            currentId = HarnessSupport.trimToNull(current.getParentExecutionId());
        }
        Collections.reverse(reverse);
        return reverse;
    }

    private List<AcceptanceRecord> acceptanceLineage(HarnessState state,
                                                     List<ExecutionRecord> executions) {
        List<AcceptanceRecord> result = new ArrayList<AcceptanceRecord>();
        for (ExecutionRecord execution : executions) {
            for (AcceptanceRecord acceptance : state.getAcceptances().values()) {
                if (acceptance != null
                        && HarnessSupport.safeEquals(execution.getExecutionId(), acceptance.getExecutionId())) {
                    result.add(acceptance.copy());
                }
            }
        }
        Collections.sort(result, new Comparator<AcceptanceRecord>() {
            @Override
            public int compare(AcceptanceRecord left, AcceptanceRecord right) {
                int byTime = Long.compare(left.getEvaluatedAtEpochMs(), right.getEvaluatedAtEpochMs());
                return byTime != 0 ? byTime : String.valueOf(left.getAcceptanceId())
                        .compareTo(String.valueOf(right.getAcceptanceId()));
            }
        });
        return result;
    }

    private SubmissionRecord latestSubmissionForExecution(HarnessState state, String executionId) {
        SubmissionRecord latest = null;
        for (SubmissionRecord candidate : state.getSubmissions().values()) {
            if (candidate == null || !HarnessSupport.safeEquals(executionId, candidate.getExecutionId())) {
                continue;
            }
            if (latest == null || candidate.getCreatedAtEpochMs() > latest.getCreatedAtEpochMs()
                    || (candidate.getCreatedAtEpochMs() == latest.getCreatedAtEpochMs()
                    && String.valueOf(candidate.getSubmissionId())
                    .compareTo(String.valueOf(latest.getSubmissionId())) > 0)) {
                latest = candidate;
            }
        }
        return latest;
    }

    private Set<String> normalizedChecks(Set<String> checks) {
        Set<String> normalized = new LinkedHashSet<String>();
        if (checks == null) {
            return normalized;
        }
        for (String check : checks) {
            String value = HarnessSupport.trimToNull(check);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private Map<String, AcceptanceRecord> latestAcceptanceByCheck(List<AcceptanceRecord> acceptances,
                                                                   SubmissionRecord currentSubmission) {
        Map<String, AcceptanceRecord> latest = new LinkedHashMap<String, AcceptanceRecord>();
        String currentSubmissionId = currentSubmission == null ? null : currentSubmission.getSubmissionId();
        if (acceptances == null) {
            return latest;
        }
        for (AcceptanceRecord candidate : acceptances) {
            if (candidate == null) {
                continue;
            }
            String checkId = HarnessSupport.trimToNull(candidate.getCheckId());
            if (checkId == null) {
                continue;
            }
            AcceptanceRecord existing = latest.get(checkId);
            if (existing == null || preferredAcceptance(candidate, existing, currentSubmissionId)) {
                latest.put(checkId, candidate.copy());
            }
        }
        return latest;
    }

    private boolean preferredAcceptance(AcceptanceRecord candidate,
                                        AcceptanceRecord existing,
                                        String currentSubmissionId) {
        int candidateRank = acceptanceRank(candidate, currentSubmissionId);
        int existingRank = acceptanceRank(existing, currentSubmissionId);
        if (candidateRank != existingRank) {
            return candidateRank > existingRank;
        }
        if (candidate.getEvaluatedAtEpochMs() != existing.getEvaluatedAtEpochMs()) {
            return candidate.getEvaluatedAtEpochMs() > existing.getEvaluatedAtEpochMs();
        }
        return String.valueOf(candidate.getAcceptanceId())
                .compareTo(String.valueOf(existing.getAcceptanceId())) > 0;
    }

    private int acceptanceRank(AcceptanceRecord acceptance, String currentSubmissionId) {
        String submissionId = HarnessSupport.trimToNull(acceptance == null ? null : acceptance.getSubmissionId());
        if (currentSubmissionId != null && currentSubmissionId.equals(submissionId)) {
            return 3;
        }
        return submissionId == null ? 1 : 2;
    }

    private HarnessAcceptanceStatus aggregateAcceptanceStatus(Set<String> required,
                                                              Map<String, AcceptanceRecord> latestByCheck,
                                                              Set<String> missing) {
        if (required == null || required.isEmpty()) {
            return null;
        }
        boolean error = false;
        boolean fail = false;
        boolean notRun = missing != null && !missing.isEmpty();
        for (String checkId : required) {
            AcceptanceRecord acceptance = latestByCheck.get(checkId);
            if (acceptance == null) {
                continue;
            }
            if (HarnessAcceptanceStatus.ERROR == acceptance.getStatus()) {
                error = true;
            } else if (HarnessAcceptanceStatus.FAIL == acceptance.getStatus()) {
                fail = true;
            } else if (HarnessAcceptanceStatus.NOT_RUN == acceptance.getStatus()) {
                notRun = true;
            }
        }
        if (error) return HarnessAcceptanceStatus.ERROR;
        if (fail) return HarnessAcceptanceStatus.FAIL;
        if (notRun) return HarnessAcceptanceStatus.NOT_RUN;
        return HarnessAcceptanceStatus.PASS;
    }

    static final class EntityCursor {
        final String entityType;
        final String entityId;

        EntityCursor(String entityType, String entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
        }
    }

    static final class EntityKey {
        final String entityType;
        final String entityId;

        EntityKey(String entityType, String entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EntityKey)) return false;
            EntityKey other = (EntityKey) obj;
            return HarnessSupport.safeEquals(entityType, other.entityType)
                    && HarnessSupport.safeEquals(entityId, other.entityId);
        }

        @Override
        public int hashCode() {
            int h = entityType == null ? 0 : entityType.hashCode();
            return 31 * h + (entityId == null ? 0 : entityId.hashCode());
        }
    }
}
