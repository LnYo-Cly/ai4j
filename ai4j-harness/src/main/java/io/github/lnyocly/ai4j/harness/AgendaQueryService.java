package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collaborator managing first-class {@link EscalationRecord} lifecycles,
 * dual-queue {@link SupervisoryAgenda} projections, and the recursive
 * {@link #explain(String)} blocker self-explanation engine.
 */
public final class AgendaQueryService {

    private final HarnessStore store;
    private final HarnessContract contract;
    private final HarnessEventJournal eventJournal;
    private final HarnessProjectionEngine projectionEngine;
    private final HarnessLeaseCoordinator leaseCoordinator;
    private final HarnessActor defaultActor;

    public AgendaQueryService(HarnessStore store,
                              HarnessContract contract,
                              HarnessEventJournal eventJournal,
                              HarnessProjectionEngine projectionEngine,
                              HarnessLeaseCoordinator leaseCoordinator,
                              HarnessActor defaultActor) {
        this.store = store;
        this.contract = contract == null ? HarnessContract.builder().build() : contract;
        this.eventJournal = eventJournal;
        this.projectionEngine = projectionEngine;
        this.leaseCoordinator = leaseCoordinator;
        this.defaultActor = HarnessSupport.normalizeActor(defaultActor, HarnessActor.agent("harness-agent"));
    }

    public EscalationRecord escalate(HarnessEscalateSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("escalation specification is required");
        }
        final String question = HarnessSupport.requireText(spec.getQuestion(), "escalation question");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        final String escalationId = HarnessSupport.valueOrGenerated(spec.getEscalationId(), "esc_");

        return HarnessSupport.write(store, new HarnessSupport.StateCommand<EscalationRecord>() {
            @Override
            public EscalationRecord apply(HarnessState state) {
                if (state.getEscalations().containsKey(escalationId)) {
                    throw new HarnessConflictException("escalation already exists: " + escalationId);
                }
                String taskId = HarnessSupport.trimToNull(spec.getTaskId());
                String executionId = HarnessSupport.trimToNull(spec.getExecutionId());
                String scopeKey = HarnessSupport.normalizeScope(spec.getScopeKey());

                TaskRecord task = null;
                ExecutionRecord execution = null;
                if (executionId != null) {
                    execution = HarnessSupport.requireExecution(state, executionId);
                    if (taskId == null) {
                        taskId = execution.getTaskId();
                    } else if (execution.getTaskId() != null && !taskId.equals(execution.getTaskId())) {
                        throw new HarnessConflictException("escalation task does not belong to execution: " + executionId);
                    }
                    HarnessSupport.assertCompatibleScope(scopeKey, execution.getScopeKey(),
                            "escalation and execution scopes must match");
                    if (scopeKey == null) {
                        scopeKey = execution.getScopeKey();
                    }
                }
                if (taskId != null) {
                    task = HarnessSupport.requireTask(state, taskId);
                    HarnessSupport.assertCompatibleScope(scopeKey, task.getScopeKey(),
                            "escalation and task scopes must match");
                    if (scopeKey == null) {
                        scopeKey = task.getScopeKey();
                    }
                }

                long now = HarnessSupport.now();
                String waitId = null;
                if (spec.isCreateWait() && execution != null
                        && (execution.getStatus() == ExecutionStatus.READY
                        || execution.getStatus() == ExecutionStatus.RUNNING
                        || execution.getStatus() == ExecutionStatus.WAITING)
                        && (task == null || (!TaskStatus.DONE.equals(task.getStatus())
                        && !TaskStatus.CANCELLED.equals(task.getStatus())
                        && !TaskStatus.BLOCKED.equals(task.getStatus())
                        && !TaskStatus.IN_REVIEW.equals(task.getStatus())))) {
                    waitId = HarnessSupport.valueOrGenerated(null, "wait_");
                    Map<String, Object> waitPayload = new LinkedHashMap<String, Object>();
                    waitPayload.put("escalationId", escalationId);
                    waitPayload.put("question", question);
                    waitPayload.put("options", HarnessSupport.copyList(spec.getOptions()));
                    waitPayload.put("recommend", spec.getRecommend());
                    waitPayload.put("reason", spec.getReason());
                    waitPayload.put("targetActorId", HarnessSupport.trimToNull(spec.getTargetActorId()));
                    WaitRecord wait = WaitRecord.builder()
                            .waitId(waitId)
                            .executionId(executionId)
                            .taskId(taskId)
                            .type(WaitType.ESCALATION)
                            .status(WaitStatus.OPEN)
                            .externalKey(escalationId)
                            .createdAtEpochMs(now)
                            .payload(waitPayload)
                            .build();
                    state.getWaits().put(waitId, wait);
                    execution.setWaitId(waitId);
                    execution.setUpdatedAtEpochMs(now);
                    if (task != null) {
                        task.setStatus(TaskStatus.WAITING);
                        task.setUpdatedAtEpochMs(now);
                        task.setVersion(task.getVersion() + 1L);
                    }
                } else if (task != null && (TaskStatus.PLANNED.equals(task.getStatus())
                        || TaskStatus.ACTIVE.equals(task.getStatus()))) {
                    task.setStatus(TaskStatus.BLOCKED);
                    task.setBlockedReason("ESCALATION: " + question);
                    task.setUpdatedAtEpochMs(now);
                    task.setVersion(task.getVersion() + 1L);
                }

                EscalationRecord record = EscalationRecord.builder()
                        .escalationId(escalationId)
                        .scopeKey(scopeKey)
                        .taskId(taskId)
                        .executionId(executionId)
                        .waitId(waitId)
                        .targetActorId(HarnessSupport.trimToNull(spec.getTargetActorId()))
                        .requester(HarnessSupport.copyActor(effectiveActor))
                        .question(question)
                        .options(HarnessSupport.copyList(spec.getOptions()))
                        .recommend(spec.getRecommend())
                        .reason(spec.getReason())
                        .status(EscalationStatus.OPEN)
                        .createdAtEpochMs(now)
                        .version(1L)
                        .metadata(HarnessSupport.copyMap(spec.getMetadata()))
                        .build();
                state.getEscalations().put(escalationId, record);
                eventJournal.addEvent(state, "escalation.created", escalationId, effectiveActor,
                        HarnessSupport.mapOf("taskId", taskId, "executionId", executionId,
                                "waitId", waitId, "question", question, "targetActorId", record.getTargetActorId()));
                return record.copy();
            }
        });
    }

    public EscalationRecord answerEscalation(String escalationId,
                                             String answer,
                                             String selectedOption,
                                             String rationale,
                                             HarnessActor actor) {
        final String id = HarnessSupport.requireText(escalationId, "escalation id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent()) {
            throw new HarnessValidationException("an Agent cannot rule on an Escalation; human/owner supervisor required");
        }
        if (HarnessSupport.trimToNull(answer) == null && HarnessSupport.trimToNull(selectedOption) == null) {
            throw new HarnessValidationException("escalation answer or selectedOption is required");
        }
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<EscalationRecord>() {
            @Override
            public EscalationRecord apply(HarnessState state) {
                EscalationRecord esc = state.getEscalations().get(id);
                if (esc == null) {
                    throw new HarnessValidationException("escalation not found: " + id);
                }
                if (esc.getStatus() != EscalationStatus.OPEN) {
                    throw new HarnessConflictException("escalation is not OPEN: " + id + " (" + esc.getStatus() + ")");
                }
                long now = HarnessSupport.now();
                esc.setStatus(EscalationStatus.ANSWERED);
                esc.setAnswer(answer);
                esc.setSelectedOption(selectedOption);
                esc.setRulingRationale(rationale);
                esc.setAnsweredBy(HarnessSupport.copyActor(effectiveActor));
                esc.setAnsweredAtEpochMs(now);
                esc.setVersion(Math.max(1L, esc.getVersion()) + 1L);

                if (esc.getWaitId() != null) {
                    WaitRecord wait = state.getWaits().get(esc.getWaitId());
                    if (wait != null && WaitStatus.OPEN.equals(wait.getStatus())) {
                        wait.setStatus(WaitStatus.DELIVERED);
                        wait.setResolvedAtEpochMs(now);
                        Map<String, Object> waitPayload = wait.getPayload() == null
                                ? new LinkedHashMap<String, Object>() : wait.getPayload();
                        Map<String, Object> delivery = new LinkedHashMap<String, Object>();
                        delivery.put("escalationId", id);
                        delivery.put("answer", answer);
                        delivery.put("selectedOption", selectedOption);
                        delivery.put("rationale", rationale);
                        waitPayload.put("delivery", delivery);
                        wait.setPayload(waitPayload);

                        String wakeupId = HarnessSupport.valueOrGenerated(null, "wakeup_");
                        WakeupRecord wakeup = WakeupRecord.builder()
                                .wakeupId(wakeupId)
                                .waitId(wait.getWaitId())
                                .executionId(wait.getExecutionId())
                                .type(wait.getType())
                                .dueAtEpochMs(wait.getDueAtEpochMs())
                                .deliveredAtEpochMs(now)
                                .payload(HarnessSupport.copyMap(waitPayload))
                                .build();
                        state.getWakeups().put(wakeupId, wakeup);

                        ExecutionRecord execution = wait.getExecutionId() == null
                                ? null : state.getExecutions().get(wait.getExecutionId());
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
                            execution.setUpdatedAtEpochMs(now);
                            execution.setVersion(execution.getVersion() + 1L);
                        }
                    }
                }

                if (esc.getTaskId() != null) {
                    TaskRecord task = state.getTasks().get(esc.getTaskId());
                    if (task != null && (TaskStatus.BLOCKED.equals(task.getStatus())
                            || TaskStatus.WAITING.equals(task.getStatus()))) {
                        boolean otherOpenEsc = hasOpenEscalationForTask(state, esc.getTaskId(), id);
                        boolean otherOpenWait = esc.getExecutionId() != null
                                && !HarnessProjectionEngine.openWaits(state, esc.getExecutionId()).isEmpty();
                        if (!otherOpenEsc && !otherOpenWait) {
                            task.setStatus(TaskStatus.ACTIVE);
                            task.setBlockedReason(null);
                            task.setUpdatedAtEpochMs(now);
                            task.setVersion(task.getVersion() + 1L);
                        }
                    }
                }

                eventJournal.addEvent(state, "escalation.answered", id, effectiveActor,
                        HarnessSupport.mapOf("selectedOption", selectedOption, "answer", answer));
                return esc.copy();
            }
        });
    }

    public EscalationRecord followUpEscalation(String escalationId,
                                               String followUpNotes,
                                               HarnessActor actor) {
        final String id = HarnessSupport.requireText(escalationId, "escalation id");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<EscalationRecord>() {
            @Override
            public EscalationRecord apply(HarnessState state) {
                EscalationRecord esc = state.getEscalations().get(id);
                if (esc == null) {
                    throw new HarnessValidationException("escalation not found: " + id);
                }
                if (esc.getStatus() == EscalationStatus.RESOLVED) {
                    return esc.copy();
                }
                if (esc.getStatus() != EscalationStatus.ANSWERED) {
                    throw new HarnessConflictException("only ANSWERED escalations can be followed up: " + id);
                }
                long now = HarnessSupport.now();
                esc.setStatus(EscalationStatus.RESOLVED);
                esc.setFollowUpNotes(followUpNotes);
                esc.setFollowedUpBy(HarnessSupport.copyActor(effectiveActor));
                esc.setFollowedUpAtEpochMs(now);
                esc.setVersion(Math.max(1L, esc.getVersion()) + 1L);
                eventJournal.addEvent(state, "escalation.followed_up", id, effectiveActor,
                        HarnessSupport.mapOf("notes", followUpNotes));
                return esc.copy();
            }
        });
    }

    public EscalationRecord getEscalation(String escalationId) {
        String id = HarnessSupport.requireText(escalationId, "escalation id");
        HarnessState state = store.load();
        EscalationRecord esc = state == null ? null : state.getEscalations().get(id);
        return esc == null ? null : esc.copy();
    }

    public List<EscalationRecord> listEscalations(String scopeKey) {
        HarnessState state = store.load();
        if (state == null || state.getEscalations() == null) {
            return Collections.emptyList();
        }
        List<EscalationRecord> result = new ArrayList<EscalationRecord>();
        for (EscalationRecord esc : state.getEscalations().values()) {
            if (esc != null && HarnessSupport.visibleScope(esc.getScopeKey(), scopeKey)) {
                result.add(esc.copy());
            }
        }
        return result;
    }

    public SupervisoryAgenda projectAgenda(String actorId, String scopeKey) {
        String normalizedActor = HarnessSupport.trimToNull(actorId);
        String normalizedScope = HarnessSupport.normalizeScope(scopeKey);
        HarnessState state = store.load();
        if (state == null) {
            state = HarnessState.empty("default");
        }
        state.ensureCollections();

        List<AgendaItem> awaiting = new ArrayList<AgendaItem>();
        List<AgendaItem> answered = new ArrayList<AgendaItem>();
        Set<String> escalationWaitIds = new LinkedHashSet<String>();

        for (EscalationRecord esc : state.getEscalations().values()) {
            if (esc == null || !HarnessSupport.visibleScope(esc.getScopeKey(), normalizedScope)) {
                continue;
            }
            if (esc.getWaitId() != null) {
                escalationWaitIds.add(esc.getWaitId());
            }
            if (!matchesActorTarget(normalizedActor, esc.getTargetActorId())) {
                continue;
            }
            if (esc.getStatus() == EscalationStatus.OPEN) {
                awaiting.add(AgendaItem.builder()
                        .itemId(esc.getEscalationId())
                        .kind(AgendaItemKind.ESCALATION)
                        .scopeKey(esc.getScopeKey())
                        .taskId(esc.getTaskId())
                        .executionId(esc.getExecutionId())
                        .waitId(esc.getWaitId())
                        .escalationId(esc.getEscalationId())
                        .targetActorId(esc.getTargetActorId())
                        .title("Escalation awaiting ruling")
                        .question(esc.getQuestion())
                        .options(HarnessSupport.copyList(esc.getOptions()))
                        .recommend(esc.getRecommend())
                        .reason(esc.getReason())
                        .status(esc.getStatus().name())
                        .createdAtEpochMs(esc.getCreatedAtEpochMs())
                        .updatedAtEpochMs(esc.getCreatedAtEpochMs())
                        .build());
            } else if (esc.getStatus() == EscalationStatus.ANSWERED) {
                answered.add(AgendaItem.builder()
                        .itemId(esc.getEscalationId())
                        .kind(AgendaItemKind.ESCALATION)
                        .scopeKey(esc.getScopeKey())
                        .taskId(esc.getTaskId())
                        .executionId(esc.getExecutionId())
                        .waitId(esc.getWaitId())
                        .escalationId(esc.getEscalationId())
                        .targetActorId(esc.getTargetActorId())
                        .title("Escalation answered; pending follow-up")
                        .question(esc.getQuestion())
                        .options(HarnessSupport.copyList(esc.getOptions()))
                        .recommend(esc.getRecommend())
                        .reason(esc.getReason())
                        .answer(esc.getAnswer())
                        .selectedOption(esc.getSelectedOption())
                        .status(esc.getStatus().name())
                        .createdAtEpochMs(esc.getCreatedAtEpochMs())
                        .updatedAtEpochMs(esc.getAnsweredAtEpochMs())
                        .build());
            }
        }

        for (WaitRecord wait : state.getWaits().values()) {
            if (wait == null || escalationWaitIds.contains(wait.getWaitId())) {
                continue;
            }
            ExecutionRecord execution = wait.getExecutionId() == null
                    ? null : state.getExecutions().get(wait.getExecutionId());
            String waitScope = execution == null ? null : execution.getScopeKey();
            if (!HarnessSupport.visibleScope(waitScope, normalizedScope)) {
                continue;
            }
            if (wait.getType() != WaitType.APPROVAL && wait.getType() != WaitType.USER_INPUT) {
                continue;
            }
            AgendaItemKind kind = wait.getType() == WaitType.APPROVAL
                    ? AgendaItemKind.APPROVAL_WAIT : AgendaItemKind.USER_INPUT_WAIT;
            if (wait.getStatus() == WaitStatus.OPEN) {
                awaiting.add(AgendaItem.builder()
                        .itemId(wait.getWaitId())
                        .kind(kind)
                        .scopeKey(waitScope)
                        .taskId(wait.getTaskId())
                        .executionId(wait.getExecutionId())
                        .waitId(wait.getWaitId())
                        .title(wait.getType() == WaitType.APPROVAL
                                ? "Tool approval requested" : "User input requested")
                        .question(wait.getExternalKey())
                        .status(wait.getStatus().name())
                        .createdAtEpochMs(wait.getCreatedAtEpochMs())
                        .updatedAtEpochMs(wait.getCreatedAtEpochMs())
                        .build());
            } else if (wait.getStatus() == WaitStatus.DELIVERED
                    && execution != null && execution.getStatus() == ExecutionStatus.READY) {
                answered.add(AgendaItem.builder()
                        .itemId(wait.getWaitId())
                        .kind(kind)
                        .scopeKey(waitScope)
                        .taskId(wait.getTaskId())
                        .executionId(wait.getExecutionId())
                        .waitId(wait.getWaitId())
                        .title("Delivered wait pending execution resume")
                        .question(wait.getExternalKey())
                        .status(wait.getStatus().name())
                        .createdAtEpochMs(wait.getCreatedAtEpochMs())
                        .updatedAtEpochMs(wait.getResolvedAtEpochMs())
                        .build());
            }
        }

        for (TaskRecord task : state.getTasks().values()) {
            if (task == null || task.getStatus() != TaskStatus.IN_REVIEW
                    || !HarnessSupport.visibleScope(task.getScopeKey(), normalizedScope)) {
                continue;
            }
            SubmissionRecord submission = task.getSubmissionId() == null
                    ? null : state.getSubmissions().get(task.getSubmissionId());
            if (submission == null) {
                continue;
            }
            boolean needsConsent = contract.requiresContentPinnedConsent(task, submission)
                    || GovernanceReviewService.hasExecutionReviews(state, submission.getSubmissionId());
            if (!needsConsent) {
                continue;
            }
            ContentPinnedConsentRecord consent = GovernanceReviewService.latestConsentForSubmission(
                    state, submission.getSubmissionId());
            if (consent == null || !consent.isApproved()) {
                awaiting.add(AgendaItem.builder()
                        .itemId("consent_pending:" + submission.getSubmissionId())
                        .kind(AgendaItemKind.PENDING_CONSENT)
                        .scopeKey(task.getScopeKey())
                        .taskId(task.getTaskId())
                        .executionId(submission.getExecutionId())
                        .submissionId(submission.getSubmissionId())
                        .title("Submission awaiting ContentPinnedConsent: " + task.getTitle())
                        .question(submission.getCompletionClaim())
                        .status("PENDING_CONSENT")
                        .createdAtEpochMs(submission.getCreatedAtEpochMs())
                        .updatedAtEpochMs(task.getUpdatedAtEpochMs())
                        .build());
            }
        }

        for (ExecutionRecord execution : state.getExecutions().values()) {
            if (execution == null || execution.getStatus() != ExecutionStatus.UNKNOWN
                    || !HarnessSupport.visibleScope(execution.getScopeKey(), normalizedScope)) {
                continue;
            }
            awaiting.add(AgendaItem.builder()
                    .itemId("unknown:" + execution.getExecutionId())
                    .kind(AgendaItemKind.UNKNOWN_EXECUTION)
                    .scopeKey(execution.getScopeKey())
                    .taskId(execution.getTaskId())
                    .executionId(execution.getExecutionId())
                    .title("Execution requires reconciliation")
                    .reason(execution.getError())
                    .status(ExecutionStatus.UNKNOWN.name())
                    .createdAtEpochMs(execution.getCreatedAtEpochMs())
                    .updatedAtEpochMs(execution.getUpdatedAtEpochMs())
                    .build());
        }

        Comparator<AgendaItem> order = new Comparator<AgendaItem>() {
            @Override
            public int compare(AgendaItem left, AgendaItem right) {
                int byTime = Long.compare(left.getCreatedAtEpochMs(), right.getCreatedAtEpochMs());
                return byTime != 0 ? byTime : String.valueOf(left.getItemId())
                        .compareTo(String.valueOf(right.getItemId()));
            }
        };
        Collections.sort(awaiting, order);
        Collections.sort(answered, order);

        return SupervisoryAgenda.builder()
                .actorId(normalizedActor)
                .scopeKey(normalizedScope)
                .generatedAtEpochMs(HarnessSupport.now())
                .awaitingRuling(awaiting)
                .answeredPendingFollowUp(answered)
                .build();
    }

    public BlockerExplanation explain(String entityRef) {
        String ref = HarnessSupport.requireText(entityRef, "entityRef");
        HarnessState state = store.load();
        if (state == null) {
            state = HarnessState.empty("default");
        }
        state.ensureCollections();

        String explicitType = null;
        String id = ref;
        int colon = ref.indexOf(':');
        if (colon > 0 && colon < ref.length() - 1) {
            explicitType = ref.substring(0, colon).trim().toUpperCase();
            id = ref.substring(colon + 1).trim();
        }

        String resolvedType = explicitType;
        if (resolvedType == null) {
            if (state.getTasks().containsKey(id)) resolvedType = "TASK";
            else if (state.getExecutions().containsKey(id)) resolvedType = "EXECUTION";
            else if (state.getSubmissions().containsKey(id)) resolvedType = "SUBMISSION";
            else if (state.getEscalations().containsKey(id)) resolvedType = "ESCALATION";
            else if (state.getDecisions().containsKey(id)) resolvedType = "DECISION";
            else if (state.getFacts().containsKey(id)) resolvedType = "FACT";
        }
        if (resolvedType == null) {
            throw new HarnessValidationException("entity not found for explain: " + ref);
        }

        Set<String> visited = new LinkedHashSet<String>();
        BlockerNode root;
        String status;
        if ("TASK".equals(resolvedType)) {
            TaskRecord task = HarnessSupport.requireTask(state, id);
            status = task.getStatus() == null ? null : task.getStatus().name();
            root = explainTaskNode(state, task, visited);
        } else if ("EXECUTION".equals(resolvedType)) {
            ExecutionRecord execution = HarnessSupport.requireExecution(state, id);
            status = execution.getStatus() == null ? null : execution.getStatus().name();
            root = explainExecutionNode(state, execution, visited);
        } else if ("SUBMISSION".equals(resolvedType)) {
            SubmissionRecord submission = state.getSubmissions().get(id);
            if (submission == null) {
                throw new HarnessValidationException("submission not found: " + id);
            }
            status = "SUBMITTED";
            root = explainSubmissionNode(state, submission);
        } else if ("ESCALATION".equals(resolvedType)) {
            EscalationRecord esc = state.getEscalations().get(id);
            if (esc == null) {
                throw new HarnessValidationException("escalation not found: " + id);
            }
            status = esc.getStatus() == null ? null : esc.getStatus().name();
            root = explainEscalationNode(esc);
        } else if ("DECISION".equals(resolvedType)) {
            DecisionRecord decision = state.getDecisions().get(id);
            if (decision == null) {
                throw new HarnessValidationException("decision not found: " + id);
            }
            status = decision.getStatus() == null ? null : decision.getStatus().name();
            root = explainDecisionNode(decision);
        } else if ("FACT".equals(resolvedType)) {
            FactRecord fact = state.getFacts().get(id);
            if (fact == null) {
                throw new HarnessValidationException("fact not found: " + id);
            }
            status = fact.isValid() ? "VALID" : "INVALIDATED";
            root = explainFactNode(fact);
        } else {
            throw new HarnessValidationException("unsupported entityRef type for explain: " + resolvedType);
        }

        List<BlockerNode> leafBlockers = new ArrayList<BlockerNode>();
        collectLeafBlockers(root, leafBlockers);
        boolean blocked = !leafBlockers.isEmpty();
        String summary = blocked
                ? leafBlockers.size() + " blocker(s) preventing completion/progress: " + leafBlockers.get(0).getMessage()
                : resolvedType + " " + id + " has no active blockers";
        return BlockerExplanation.builder()
                .entityRef(ref)
                .entityType(resolvedType)
                .entityId(id)
                .status(status)
                .blocked(blocked)
                .summary(summary)
                .explainedAtEpochMs(HarnessSupport.now())
                .root(root)
                .blockers(leafBlockers)
                .build();
    }

    private BlockerNode explainTaskNode(HarnessState state, TaskRecord task, Set<String> visited) {
        String key = "TASK:" + task.getTaskId();
        if (!visited.add(key)) {
            return BlockerNode.builder()
                    .code("CYCLE_DETECTED")
                    .entityType("TASK")
                    .entityId(task.getTaskId())
                    .status(task.getStatus() == null ? null : task.getStatus().name())
                    .message("Dependency cycle detected at task " + task.getTaskId())
                    .build();
        }
        List<BlockerNode> children = new ArrayList<BlockerNode>();
        if (TaskStatus.BLOCKED.equals(task.getStatus())) {
            children.add(BlockerNode.builder()
                    .code("TASK_BLOCKED")
                    .entityType("TASK")
                    .entityId(task.getTaskId())
                    .status(TaskStatus.BLOCKED.name())
                    .message(task.getBlockedReason() != null
                            ? task.getBlockedReason() : "Task is in BLOCKED status")
                    .remediationHint("Resolve blocking escalation/dependency or transition task to ACTIVE")
                    .build());
        }

        for (RelationRecord rel : state.getRelations().values()) {
            if (rel == null || rel.getType() != RelationType.DEPENDS_ON
                    || !HarnessSupport.safeEquals(task.getTaskId(), rel.getFromId())) {
                continue;
            }
            TaskRecord dep = state.getTasks().get(rel.getToId());
            if (dep == null) {
                children.add(BlockerNode.builder()
                        .code("MISSING_DEPENDENCY")
                        .entityType("TASK")
                        .entityId(rel.getToId())
                        .message("Dependency task missing: " + rel.getToId())
                        .build());
            } else if (!TaskStatus.DONE.equals(dep.getStatus())) {
                BlockerNode depNode = explainTaskNode(state, dep, new LinkedHashSet<String>(visited));
                children.add(BlockerNode.builder()
                        .code("UNSATISFIED_DEPENDENCY")
                        .entityType("TASK")
                        .entityId(dep.getTaskId())
                        .status(dep.getStatus() == null ? null : dep.getStatus().name())
                        .message("Prerequisite task " + dep.getTaskId() + " is " + dep.getStatus() + " (must be DONE)")
                        .remediationHint("Complete prerequisite task " + dep.getTaskId())
                        .children(depNode.getChildren())
                        .build());
            }
        }

        for (EscalationRecord esc : state.getEscalations().values()) {
            if (esc == null || !HarnessSupport.safeEquals(task.getTaskId(), esc.getTaskId())) {
                continue;
            }
            if (esc.getStatus() == EscalationStatus.OPEN || esc.getStatus() == EscalationStatus.ANSWERED) {
                children.add(explainEscalationNode(esc));
            }
        }

        for (DecisionRecord decision : state.getDecisions().values()) {
            if (decision != null && HarnessSupport.safeEquals(task.getTaskId(), decision.getTaskId())
                    && decision.getStatus() == DecisionStatus.FALSIFIED) {
                children.add(explainDecisionNode(decision));
            }
        }

        Set<String> seenExecutionIds = new LinkedHashSet<String>();
        String lastExecutionId = HarnessSupport.trimToNull(task.getLastExecutionId());
        if (lastExecutionId != null) {
            ExecutionRecord lastExec = state.getExecutions().get(lastExecutionId);
            if (lastExec != null) {
                seenExecutionIds.add(lastExecutionId);
                BlockerNode execNode = explainExecutionNode(state, lastExec, visited);
                if (!execNode.getChildren().isEmpty()) {
                    children.addAll(execNode.getChildren());
                }
            }
        }
        for (ExecutionRecord exec : state.getExecutions().values()) {
            if (exec != null && exec.getExecutionId() != null
                    && !seenExecutionIds.contains(exec.getExecutionId())
                    && HarnessSupport.safeEquals(task.getTaskId(), exec.getTaskId())) {
                seenExecutionIds.add(exec.getExecutionId());
                BlockerNode execNode = explainExecutionNode(state, exec, visited);
                if (!execNode.getChildren().isEmpty()) {
                    children.addAll(execNode.getChildren());
                }
            }
        }

        if (TaskStatus.IN_REVIEW.equals(task.getStatus()) || HarnessSupport.trimToNull(task.getSubmissionId()) != null) {
            SubmissionRecord submission = task.getSubmissionId() == null
                    ? null : state.getSubmissions().get(task.getSubmissionId());
            if (submission == null) {
                children.add(BlockerNode.builder()
                        .code("MISSING_SUBMISSION")
                        .entityType("TASK")
                        .entityId(task.getTaskId())
                        .status(task.getStatus() == null ? null : task.getStatus().name())
                        .message("Task is IN_REVIEW without an active SubmissionRecord")
                        .build());
            } else if (!TaskStatus.DONE.equals(task.getStatus())) {
                BlockerNode subNode = explainSubmissionNode(state, submission);
                children.addAll(subNode.getChildren());
            }
        }

        return BlockerNode.builder()
                .code(children.isEmpty() ? "READY" : "TASK_BLOCKED_ROOT")
                .entityType("TASK")
                .entityId(task.getTaskId())
                .status(task.getStatus() == null ? null : task.getStatus().name())
                .message(children.isEmpty() ? "No blockers on task " + task.getTaskId()
                        : "Task " + task.getTaskId() + " has " + children.size() + " blocker(s)")
                .children(children)
                .build();
    }

    private BlockerNode explainExecutionNode(HarnessState state,
                                             ExecutionRecord execution,
                                             Set<String> visited) {
        List<BlockerNode> children = new ArrayList<BlockerNode>();
        if (ExecutionStatus.UNKNOWN.equals(execution.getStatus())) {
            children.add(BlockerNode.builder()
                    .code("EXECUTION_UNKNOWN")
                    .entityType("EXECUTION")
                    .entityId(execution.getExecutionId())
                    .status(ExecutionStatus.UNKNOWN.name())
                    .message(execution.getError() != null
                            ? execution.getError() : "Execution lease expired/lost; reconciliation required")
                    .remediationHint("Run reconcileExecution as a human/system operator")
                    .build());
        } else if (ExecutionStatus.FAILED.equals(execution.getStatus())) {
            children.add(BlockerNode.builder()
                    .code("EXECUTION_FAILED")
                    .entityType("EXECUTION")
                    .entityId(execution.getExecutionId())
                    .status(ExecutionStatus.FAILED.name())
                    .message(execution.getError() != null ? execution.getError() : "Execution failed")
                    .remediationHint("Inspect error and launch a repair execution")
                    .build());
        } else if (ExecutionStatus.WAITING.equals(execution.getStatus())) {
            children.add(BlockerNode.builder()
                    .code("EXECUTION_WAITING")
                    .entityType("EXECUTION")
                    .entityId(execution.getExecutionId())
                    .status(ExecutionStatus.WAITING.name())
                    .message("Execution is waiting on external wakeup/waitId=" + execution.getWaitId())
                    .build());
        }

        for (WaitRecord wait : HarnessProjectionEngine.openWaits(state, execution.getExecutionId())) {
            children.add(BlockerNode.builder()
                    .code("OPEN_WAIT")
                    .entityType("WAIT")
                    .entityId(wait.getWaitId())
                    .status(wait.getStatus().name())
                    .message("Open wait of type " + wait.getType() + " (" + wait.getWaitId() + ")")
                    .remediationHint("Deliver wait " + wait.getWaitId())
                    .build());
        }

        for (KnowledgeLedgerService.StaleReadDetail stale
                : KnowledgeLedgerService.findStaleReadEntries(state, execution.getExecutionId())) {
            ReadSetEntry entry = stale.getEntry();
            children.add(BlockerNode.builder()
                    .code("STALE_READ_SET")
                    .entityType(entry.getEntityKind() == null ? "READ_SET" : entry.getEntityKind().name())
                    .entityId(entry.getEntityId())
                    .status("STALE")
                    .message(stale.getDescription())
                    .remediationHint("Call reconfirmReadSet for execution " + execution.getExecutionId()
                            + " or rerun with fresh state")
                    .details(HarnessSupport.mapOf("executionId", execution.getExecutionId(),
                            "observedVersion", entry.effectiveObservedVersion()))
                    .build());
        }

        return BlockerNode.builder()
                .code(children.isEmpty() ? "READY" : "EXECUTION_BLOCKED_ROOT")
                .entityType("EXECUTION")
                .entityId(execution.getExecutionId())
                .status(execution.getStatus() == null ? null : execution.getStatus().name())
                .message(children.isEmpty() ? "No blockers on execution " + execution.getExecutionId()
                        : "Execution " + execution.getExecutionId() + " has " + children.size() + " blocker(s)")
                .children(children)
                .build();
    }

    private BlockerNode explainSubmissionNode(HarnessState state, SubmissionRecord submission) {
        List<BlockerNode> children = new ArrayList<BlockerNode>();
        TaskRecord task = submission.getTaskId() == null ? null : state.getTasks().get(submission.getTaskId());
        String executionId = HarnessSupport.trimToNull(submission.getExecutionId());
        if (executionId == null) {
            children.add(BlockerNode.builder()
                    .code("MISSING_EXECUTION")
                    .entityType("SUBMISSION")
                    .entityId(submission.getSubmissionId())
                    .message("Submission does not reference an execution")
                    .build());
        } else {
            ExecutionRecord execution = state.getExecutions().get(executionId);
            if (execution == null || !ExecutionStatus.SUCCEEDED.equals(execution.getStatus())) {
                children.add(BlockerNode.builder()
                        .code("EXECUTION_NOT_SUCCEEDED")
                        .entityType("EXECUTION")
                        .entityId(executionId)
                        .status(execution == null || execution.getStatus() == null
                                ? "MISSING" : execution.getStatus().name())
                        .message("Submission execution must be SUCCEEDED before completion")
                        .build());
            }
            for (KnowledgeLedgerService.StaleReadDetail stale
                    : KnowledgeLedgerService.findStaleReadEntries(state, executionId)) {
                children.add(BlockerNode.builder()
                        .code("STALE_READ_SET")
                        .entityType(stale.getEntry().getEntityKind().name())
                        .entityId(stale.getEntry().getEntityId())
                        .status("STALE")
                        .message(stale.getDescription())
                        .remediationHint("Reconfirm read-set or rerun execution")
                        .build());
            }
        }

        boolean hasExecReviews = GovernanceReviewService.hasExecutionReviews(state, submission.getSubmissionId());
        boolean hasConsents = GovernanceReviewService.hasConsents(state, submission.getSubmissionId());
        boolean consentMode = contract.requiresContentPinnedConsent(task, submission) || hasExecReviews || hasConsents;
        if (consentMode) {
            ContentPinnedConsentRecord consent = GovernanceReviewService.latestConsentForSubmission(
                    state, submission.getSubmissionId());
            if (consent == null) {
                children.add(BlockerNode.builder()
                        .code("MISSING_CONSENT")
                        .entityType("SUBMISSION")
                        .entityId(submission.getSubmissionId())
                        .message("Human/owner ContentPinnedConsent is required")
                        .remediationHint("Sign reviewConsent pinning submissionDigest " + submission.getSubmissionDigest())
                        .build());
            } else {
                if (!consent.isApproved()) {
                    children.add(BlockerNode.builder()
                            .code("CONSENT_REJECTED")
                            .entityType("CONSENT")
                            .entityId(consent.getConsentId())
                            .status("REJECTED")
                            .message("ContentPinnedConsent rejected submission")
                            .build());
                }
                if (!HarnessSupport.safeEquals(submission.getSubmissionDigest(), consent.getSubmissionDigest())) {
                    children.add(BlockerNode.builder()
                            .code("CONSENT_DIGEST_MISMATCH")
                            .entityType("CONSENT")
                            .entityId(consent.getConsentId())
                            .message("Consent submissionDigest does not match current submission digest")
                            .build());
                }
                List<String> uncovered = GovernanceReviewService.findUncoveredFindings(
                        state, submission.getSubmissionId(), consent.getDispositions());
                if (!uncovered.isEmpty()) {
                    children.add(BlockerNode.builder()
                            .code("UNCOVERED_REVIEW_FINDING")
                            .entityType("CONSENT")
                            .entityId(consent.getConsentId())
                            .message("Uncovered ExecutionReview findings: " + uncovered)
                            .remediationHint("Provide FindingDisposition for every finding id")
                            .build());
                }
            }
        } else if (contract.requiresApprovedReview(task, submission)
                && !GovernanceReviewService.hasApprovedLegacyReview(state, submission.getSubmissionId())) {
            children.add(BlockerNode.builder()
                    .code("MISSING_REVIEW")
                    .entityType("SUBMISSION")
                    .entityId(submission.getSubmissionId())
                    .message("An approved review is required before completion")
                    .remediationHint("Approve submission via reviewSubmission")
                    .build());
        }

        if (contract.requiresCompletionEvidence(task, submission)
                && (submission.getEvidenceIds() == null || submission.getEvidenceIds().isEmpty())) {
            children.add(BlockerNode.builder()
                    .code("MISSING_EVIDENCE")
                    .entityType("SUBMISSION")
                    .entityId(submission.getSubmissionId())
                    .message("Completion evidence is required for governed task")
                    .build());
        }

        Set<String> requiredChecks = projectionEngine.effectiveRequiredChecks(state, task, submission);
        for (String checkId : requiredChecks) {
            boolean passed = false;
            for (AcceptanceRecord acceptance : state.getAcceptances().values()) {
                if (acceptance != null && HarnessAcceptanceStatus.PASS == acceptance.getStatus()
                        && checkId.equals(HarnessSupport.trimToNull(acceptance.getCheckId()))
                        && HarnessSupport.safeEquals(submission.getTaskId(), acceptance.getTaskId())
                        && HarnessSupport.safeEquals(submission.getExecutionId(), acceptance.getExecutionId())
                        && HarnessSupport.safeEquals(submission.getSubmissionId(), acceptance.getSubmissionId())) {
                    passed = true;
                    break;
                }
            }
            if (!passed) {
                children.add(BlockerNode.builder()
                        .code("MISSING_ACCEPTANCE_CHECK")
                        .entityType("ACCEPTANCE")
                        .entityId(checkId)
                        .message("Required acceptance check missing or not PASS: " + checkId)
                        .build());
            }
        }

        Set<String> requiredWitnesses = contract.requiredWitnessGates(task, submission);
        if (requiredWitnesses != null) {
            for (String gateName : requiredWitnesses) {
                GateRunRecord latest = GovernanceReviewService.latestGateRun(state,
                        submission.getTaskId(), submission.getExecutionId(), submission.getSubmissionId(), gateName);
                if (latest == null) {
                    children.add(BlockerNode.builder()
                            .code("MISSING_WITNESS_GATE_RUN")
                            .entityType("GATE_RUN")
                            .entityId(gateName)
                            .message("Required Witness gate run missing: " + gateName)
                            .remediationHint("Run witnessRerun for gate '" + gateName + "'")
                            .build());
                } else if (!latest.isPassed()) {
                    children.add(BlockerNode.builder()
                            .code("FAILED_WITNESS_GATE_RUN")
                            .entityType("GATE_RUN")
                            .entityId(latest.getGateRunId())
                            .status("FAIL")
                            .message("Witness gate '" + gateName + "' failed (exitCode=" + latest.getExitCode() + ")")
                            .build());
                }
            }
        }

        if (task != null) {
            List<GateResult> gateResults = contract.evaluateCompletion(task, submission, state);
            if (gateResults != null) {
                for (GateResult gr : gateResults) {
                    if (gr != null && !gr.isPassed()) {
                        children.add(BlockerNode.builder()
                                .code("FAILED_GATE")
                                .entityType("GATE")
                                .entityId(gr.getName())
                                .status("FAIL")
                                .message("Completion gate '" + gr.getName() + "' failed: " + gr.getReason())
                                .build());
                    }
                }
            }
        }

        return BlockerNode.builder()
                .code(children.isEmpty() ? "READY" : "SUBMISSION_BLOCKED_ROOT")
                .entityType("SUBMISSION")
                .entityId(submission.getSubmissionId())
                .status("SUBMITTED")
                .message(children.isEmpty() ? "Submission ready for completion"
                        : "Submission has " + children.size() + " unmet gate(s)")
                .children(children)
                .build();
    }

    private BlockerNode explainEscalationNode(EscalationRecord esc) {
        String code = esc.getStatus() == EscalationStatus.OPEN
                ? "OPEN_ESCALATION"
                : esc.getStatus() == EscalationStatus.ANSWERED ? "ESCALATION_PENDING_FOLLOWUP" : "RESOLVED";
        List<BlockerNode> children = new ArrayList<BlockerNode>();
        if (esc.getStatus() == EscalationStatus.OPEN || esc.getStatus() == EscalationStatus.ANSWERED) {
            children.add(BlockerNode.builder()
                    .code(code)
                    .entityType("ESCALATION")
                    .entityId(esc.getEscalationId())
                    .status(esc.getStatus().name())
                    .message(esc.getStatus() == EscalationStatus.OPEN
                            ? "Open escalation awaiting supervisory ruling: " + esc.getQuestion()
                            : "Answered escalation awaiting execution follow-up: " + esc.getQuestion())
                    .remediationHint(esc.getStatus() == EscalationStatus.OPEN
                            ? "Call answerEscalation" : "Call followUpEscalation after acting on ruling")
                    .build());
        }
        return BlockerNode.builder()
                .code(code)
                .entityType("ESCALATION")
                .entityId(esc.getEscalationId())
                .status(esc.getStatus() == null ? null : esc.getStatus().name())
                .message(esc.getQuestion())
                .children(children)
                .build();
    }

    private BlockerNode explainDecisionNode(DecisionRecord decision) {
        List<BlockerNode> children = new ArrayList<BlockerNode>();
        if (decision.getStatus() == DecisionStatus.FALSIFIED) {
            children.add(BlockerNode.builder()
                    .code("FALSIFIED_DECISION")
                    .entityType("DECISION")
                    .entityId(decision.getDecisionId())
                    .status(DecisionStatus.FALSIFIED.name())
                    .message("Decision '" + decision.getQuestion() + "' was falsified during reckoning")
                    .remediationHint("Amend or replace decision " + decision.getDecisionId())
                    .build());
        }
        return BlockerNode.builder()
                .code(children.isEmpty() ? "READY" : "FALSIFIED_DECISION")
                .entityType("DECISION")
                .entityId(decision.getDecisionId())
                .status(decision.getStatus() == null ? null : decision.getStatus().name())
                .message(decision.getQuestion())
                .children(children)
                .build();
    }

    private BlockerNode explainFactNode(FactRecord fact) {
        List<BlockerNode> children = new ArrayList<BlockerNode>();
        if (!fact.isValid()) {
            children.add(BlockerNode.builder()
                    .code("FACT_INVALIDATED")
                    .entityType("FACT")
                    .entityId(fact.getFactId())
                    .status("INVALIDATED")
                    .message("Fact invalidated: " + fact.getInvalidatedBy())
                    .build());
        }
        return BlockerNode.builder()
                .code(children.isEmpty() ? "VALID" : "FACT_INVALIDATED")
                .entityType("FACT")
                .entityId(fact.getFactId())
                .status(fact.isValid() ? "VALID" : "INVALIDATED")
                .message(fact.getStatement())
                .children(children)
                .build();
    }

    private void collectLeafBlockers(BlockerNode node, List<BlockerNode> out) {
        if (node == null) {
            return;
        }
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            if (!"READY".equals(node.getCode()) && !"VALID".equals(node.getCode()) && !"RESOLVED".equals(node.getCode())) {
                out.add(node);
            }
            return;
        }
        for (BlockerNode child : node.getChildren()) {
            collectLeafBlockers(child, out);
        }
    }

    private boolean matchesActorTarget(String actorId, String targetActorId) {
        if (actorId == null || targetActorId == null) {
            return true;
        }
        if (actorId.equals(targetActorId)) {
            return true;
        }
        return ("human:" + actorId).equals(targetActorId) || targetActorId.equals("owner:" + actorId);
    }

    private boolean hasOpenEscalationForTask(HarnessState state, String taskId, String excludeEscalationId) {
        for (EscalationRecord candidate : state.getEscalations().values()) {
            if (candidate != null && !HarnessSupport.safeEquals(excludeEscalationId, candidate.getEscalationId())
                    && HarnessSupport.safeEquals(taskId, candidate.getTaskId())
                    && candidate.getStatus() == EscalationStatus.OPEN) {
                return true;
            }
        }
        return false;
    }
}
