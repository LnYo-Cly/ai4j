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
 * Collaborator enforcing {@link ExecutionReviewRecord} vs {@link ContentPinnedConsentRecord}
 * separation, human/owner-only consent signing, submission digest pinning, per-finding
 * disposition coverage, and independent {@link WitnessRunner} verification ({@link GateRunRecord}).
 */
public final class GovernanceReviewService {

    public static final String ERROR_CONSENT_AGENT_FORBIDDEN = "CONSENT_AGENT_FORBIDDEN";
    public static final String ERROR_CONSENT_DIGEST_MISMATCH = "CONSENT_DIGEST_MISMATCH";
    public static final String ERROR_CONSENT_FINDINGS_UNCOVERED = "CONSENT_FINDINGS_UNCOVERED";
    public static final String ERROR_WITNESS_FAILED = "WITNESS_FAILED";

    private final HarnessStore store;
    private final HarnessContract contract;
    private final HarnessEventJournal eventJournal;
    private final HarnessProjectionEngine projectionEngine;
    private final HarnessActor defaultActor;

    public GovernanceReviewService(HarnessStore store,
                                   HarnessContract contract,
                                   HarnessEventJournal eventJournal,
                                   HarnessProjectionEngine projectionEngine,
                                   HarnessActor defaultActor) {
        this.store = store;
        this.contract = contract == null ? HarnessContract.builder().build() : contract;
        this.eventJournal = eventJournal;
        this.projectionEngine = projectionEngine;
        this.defaultActor = HarnessSupport.normalizeActor(defaultActor, HarnessActor.agent("harness-agent"));
    }

    public ExecutionReviewRecord submitExecutionReview(ExecutionReviewSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("execution review specification is required");
        }
        final String submissionId = HarnessSupport.requireText(spec.getSubmissionId(), "submission id");
        if (spec.getVerdict() == null) {
            throw new HarnessValidationException("execution review verdict is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<ExecutionReviewRecord>() {
            @Override
            public ExecutionReviewRecord apply(HarnessState state) {
                SubmissionRecord submission = state.getSubmissions().get(submissionId);
                if (submission == null) {
                    throw new HarnessValidationException("submission not found: " + submissionId);
                }
                TaskRecord task = HarnessSupport.requireTask(state, submission.getTaskId());
                if (!submissionId.equals(task.getSubmissionId())) {
                    throw new HarnessConflictException("submission is no longer the current task submission: " + submissionId);
                }
                if (effectiveActor.isAgent()
                        && submission.getSubmitter() != null
                        && HarnessSupport.safeEquals(
                                HarnessSupport.actorKey(effectiveActor, defaultActor),
                                HarnessSupport.actorKey(submission.getSubmitter(), defaultActor))) {
                    throw new HarnessValidationException(
                            "an Agent cannot submit an ExecutionReview for its own submission; an independent reviewer is required");
                }
                String actualDigest = ensureSubmissionDigest(submission);
                String requestedDigest = HarnessSupport.trimToNull(spec.getSubmissionDigest());
                if (requestedDigest != null && !requestedDigest.equals(actualDigest)) {
                    throw new HarnessConflictException(ERROR_CONSENT_DIGEST_MISMATCH,
                            "execution review submissionDigest mismatch: expected "
                                    + actualDigest + " but got " + requestedDigest);
                }
                List<ReviewFinding> findings = normalizeFindings(spec.getFindings());
                String reviewId = HarnessSupport.valueOrGenerated(spec.getReviewId(), "exrev_");
                ExecutionReviewRecord review = ExecutionReviewRecord.builder()
                        .reviewId(reviewId)
                        .submissionId(submissionId)
                        .taskId(submission.getTaskId())
                        .executionId(submission.getExecutionId())
                        .scopeKey(task.getScopeKey())
                        .submissionDigest(actualDigest)
                        .reviewer(HarnessSupport.copyActor(effectiveActor))
                        .verdict(spec.getVerdict())
                        .findingsSummary(spec.getFindingsSummary())
                        .findings(findings)
                        .rationale(spec.getRationale())
                        .createdAtEpochMs(HarnessSupport.now())
                        .build();
                state.getExecutionReviews().put(reviewId, review);
                if (spec.getVerdict() == ReviewVerdict.CHANGES_REQUESTED) {
                    task.setStatus(TaskStatus.ACTIVE);
                    task.setUpdatedAtEpochMs(HarnessSupport.now());
                    task.setVersion(task.getVersion() + 1L);
                }
                eventJournal.addEvent(state, "execution_review.recorded", reviewId, effectiveActor,
                        HarnessSupport.mapOf("submissionId", submissionId,
                                "verdict", spec.getVerdict().name(),
                                "findingCount", findings.size(),
                                "submissionDigest", actualDigest));
                return review.copy();
            }
        });
    }

    public ContentPinnedConsentRecord reviewConsent(ContentPinnedConsentSpec spec, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("content-pinned consent specification is required");
        }
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent() || !effectiveActor.isHumanOrOwner()) {
            throw new HarnessValidationException(ERROR_CONSENT_AGENT_FORBIDDEN,
                    "protocol violation: only a human or owner principal may sign ContentPinnedConsent, rejected actor "
                            + effectiveActor.getKind() + ":" + effectiveActor.getId());
        }
        final String submissionId = HarnessSupport.requireText(spec.getSubmissionId(), "submission id");
        final String pinnedDigest = HarnessSupport.requireText(spec.getSubmissionDigest(), "submissionDigest");
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<ContentPinnedConsentRecord>() {
            @Override
            public ContentPinnedConsentRecord apply(HarnessState state) {
                SubmissionRecord submission = state.getSubmissions().get(submissionId);
                if (submission == null) {
                    throw new HarnessValidationException("submission not found: " + submissionId);
                }
                TaskRecord task = HarnessSupport.requireTask(state, submission.getTaskId());
                if (!submissionId.equals(task.getSubmissionId())) {
                    throw new HarnessConflictException("submission is no longer the current task submission: " + submissionId);
                }
                String actualDigest = ensureSubmissionDigest(submission);
                if (!pinnedDigest.equals(actualDigest)) {
                    throw new HarnessConflictException(ERROR_CONSENT_DIGEST_MISMATCH,
                            "ContentPinnedConsent submissionDigest mismatch: expected "
                                    + actualDigest + " but got " + pinnedDigest);
                }
                List<FindingDisposition> dispositions = normalizeDispositions(spec.getDispositions());
                if (spec.isApproved()) {
                    List<String> uncovered = findUncoveredFindings(state, submissionId, dispositions);
                    if (!uncovered.isEmpty()) {
                        throw new HarnessConflictException(ERROR_CONSENT_FINDINGS_UNCOVERED,
                                "ContentPinnedConsent must cover every review finding disposition; uncovered: "
                                        + uncovered);
                    }
                }
                long now = HarnessSupport.now();
                String consentId = HarnessSupport.valueOrGenerated(spec.getConsentId(), "consent_");
                ContentPinnedConsentRecord consent = ContentPinnedConsentRecord.builder()
                        .consentId(consentId)
                        .submissionId(submissionId)
                        .taskId(submission.getTaskId())
                        .executionId(submission.getExecutionId())
                        .scopeKey(task.getScopeKey())
                        .submissionDigest(pinnedDigest)
                        .consenter(HarnessSupport.copyActor(effectiveActor))
                        .approved(spec.isApproved())
                        .rationale(spec.getRationale())
                        .dispositions(dispositions)
                        .consentedAtEpochMs(now)
                        .version(1L)
                        .build();
                state.getConsents().put(consentId, consent);
                eventJournal.addEvent(state, "consent.recorded", consentId, effectiveActor,
                        HarnessSupport.mapOf("submissionId", submissionId,
                                "approved", spec.isApproved(),
                                "submissionDigest", pinnedDigest,
                                "dispositionCount", dispositions.size()));
                return consent.copy();
            }
        });
    }

    public List<ExecutionReviewRecord> listExecutionReviews(String submissionId) {
        String subKey = HarnessSupport.trimToNull(submissionId);
        HarnessState state = store.load();
        if (state == null || state.getExecutionReviews() == null) {
            return Collections.emptyList();
        }
        List<ExecutionReviewRecord> result = new ArrayList<ExecutionReviewRecord>();
        for (ExecutionReviewRecord review : state.getExecutionReviews().values()) {
            if (review != null && (subKey == null || subKey.equals(review.getSubmissionId()))) {
                result.add(review.copy());
            }
        }
        Collections.sort(result, new Comparator<ExecutionReviewRecord>() {
            @Override
            public int compare(ExecutionReviewRecord left, ExecutionReviewRecord right) {
                return Long.compare(left.getCreatedAtEpochMs(), right.getCreatedAtEpochMs());
            }
        });
        return result;
    }

    public List<ContentPinnedConsentRecord> listConsents(String submissionId) {
        String subKey = HarnessSupport.trimToNull(submissionId);
        HarnessState state = store.load();
        if (state == null || state.getConsents() == null) {
            return Collections.emptyList();
        }
        List<ContentPinnedConsentRecord> result = new ArrayList<ContentPinnedConsentRecord>();
        for (ContentPinnedConsentRecord consent : state.getConsents().values()) {
            if (consent != null && (subKey == null || subKey.equals(consent.getSubmissionId()))) {
                result.add(consent.copy());
            }
        }
        Collections.sort(result, new Comparator<ContentPinnedConsentRecord>() {
            @Override
            public int compare(ContentPinnedConsentRecord left, ContentPinnedConsentRecord right) {
                return Long.compare(left.getConsentedAtEpochMs(), right.getConsentedAtEpochMs());
            }
        });
        return result;
    }

    public GateRunRecord witnessRerun(WitnessRunSpec spec, WitnessRunner runner, HarnessActor actor) {
        if (spec == null) {
            throw new HarnessValidationException("witness run specification is required");
        }
        if (runner == null) {
            throw new HarnessValidationException("witness runner is required");
        }
        final String gateName = HarnessSupport.requireText(spec.getGateName(), "witness gate name");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        if (effectiveActor.isAgent()) {
            throw new HarnessValidationException(
                    "an Agent cannot execute an independent Witness gate run; host/system/human witness principal required");
        }

        WitnessRequest request = WitnessRequest.builder()
                .gateName(gateName)
                .taskId(HarnessSupport.trimToNull(spec.getTaskId()))
                .executionId(HarnessSupport.trimToNull(spec.getExecutionId()))
                .submissionId(HarnessSupport.trimToNull(spec.getSubmissionId()))
                .command(spec.getCommand())
                .workspaceRef(spec.getWorkspaceRef())
                .parameters(HarnessSupport.copyMap(spec.getParameters()))
                .build();

        long startedAt = System.currentTimeMillis();
        WitnessResult rawResult;
        try {
            rawResult = runner.run(request);
            if (rawResult == null) {
                rawResult = WitnessResult.builder()
                        .exitCode(1)
                        .passed(false)
                        .summary("witness runner returned null result")
                        .build();
            }
        } catch (Exception error) {
            rawResult = WitnessResult.builder()
                    .exitCode(1)
                    .passed(false)
                    .output(error.getMessage())
                    .summary("witness runner threw: " + error.getMessage())
                    .build();
        }
        long elapsed = rawResult.getDurationMillis() >= 0L
                ? rawResult.getDurationMillis() : Math.max(0L, System.currentTimeMillis() - startedAt);
        final WitnessResult result = rawResult;
        final long durationMillis = elapsed;
        String computedDigest = HarnessSupport.trimToNull(result.getDigest());
        if (computedDigest == null) {
            Map<String, Object> digestInput = new LinkedHashMap<String, Object>();
            digestInput.put("gateName", gateName);
            digestInput.put("command", spec.getCommand());
            digestInput.put("exitCode", result.getExitCode());
            digestInput.put("passed", result.isPassed());
            digestInput.put("output", result.getOutput());
            digestInput.put("artifacts", result.getArtifacts());
            computedDigest = HarnessDigests.sha256Hex(HarnessJson.toJson(digestInput));
        }
        final String digest = computedDigest;

        return HarnessSupport.write(store, new HarnessSupport.StateCommand<GateRunRecord>() {
            @Override
            public GateRunRecord apply(HarnessState state) {
                String taskId = HarnessSupport.trimToNull(spec.getTaskId());
                String executionId = HarnessSupport.trimToNull(spec.getExecutionId());
                String submissionId = HarnessSupport.trimToNull(spec.getSubmissionId());
                String scopeKey = null;
                if (submissionId != null) {
                    SubmissionRecord submission = state.getSubmissions().get(submissionId);
                    if (submission == null) {
                        throw new HarnessValidationException("submission not found: " + submissionId);
                    }
                    if (taskId == null) taskId = submission.getTaskId();
                    if (executionId == null) executionId = submission.getExecutionId();
                }
                if (executionId != null) {
                    ExecutionRecord execution = HarnessSupport.requireExecution(state, executionId);
                    if (taskId == null) taskId = execution.getTaskId();
                    scopeKey = execution.getScopeKey();
                }
                if (taskId != null) {
                    TaskRecord task = HarnessSupport.requireTask(state, taskId);
                    HarnessSupport.assertCompatibleScope(scopeKey, task.getScopeKey(),
                            "witness task and execution scopes must match");
                    if (scopeKey == null) scopeKey = task.getScopeKey();
                }

                long now = HarnessSupport.now();
                String gateRunId = HarnessSupport.valueOrGenerated(spec.getGateRunId(), "gaterun_");
                String evidenceId = "evidence_" + gateRunId;
                String summary = HarnessSupport.trimToNull(result.getSummary());
                if (summary == null) {
                    summary = "Witness gate '" + gateName + "' " + (result.isPassed() ? "PASSED" : "FAILED")
                            + " (exitCode=" + result.getExitCode() + ", digest=" + digest + ")";
                }

                EvidenceRecord evidence = EvidenceRecord.builder()
                        .evidenceId(evidenceId)
                        .scopeKey(scopeKey)
                        .taskId(taskId)
                        .executionId(executionId)
                        .kind("witness-gate-run")
                        .location(HarnessSupport.trimToNull(result.getLocation()) != null
                                ? result.getLocation() : spec.getCommand())
                        .summary(summary)
                        .contentRef(gateRunId)
                        .createdAtEpochMs(now)
                        .provenance(HarnessSupport.provenance(effectiveActor, defaultActor,
                                executionId, null, "witness", gateRunId))
                        .build();
                state.getEvidence().put(evidenceId, evidence);

                GateRunRecord gateRun = GateRunRecord.builder()
                        .gateRunId(gateRunId)
                        .scopeKey(scopeKey)
                        .taskId(taskId)
                        .executionId(executionId)
                        .submissionId(submissionId)
                        .gateName(gateName)
                        .command(spec.getCommand())
                        .exitCode(result.getExitCode())
                        .passed(result.isPassed())
                        .digest(digest)
                        .durationMillis(durationMillis)
                        .evidenceId(evidenceId)
                        .summary(summary)
                        .witnessActor(HarnessSupport.copyActor(effectiveActor))
                        .createdAtEpochMs(now)
                        .build();
                state.getGateRuns().put(gateRunId, gateRun);

                String gateId = HarnessSupport.valueOrGenerated(null, "gate_");
                state.getGates().put(gateId, GateRecord.builder()
                        .gateId(gateId)
                        .taskId(taskId)
                        .executionId(executionId)
                        .submissionId(submissionId)
                        .name(gateName)
                        .status(result.isPassed() ? GateStatus.PASS : GateStatus.FAIL)
                        .reason(summary)
                        .evaluatedAtEpochMs(now)
                        .build());

                eventJournal.addEvent(state, "witness.gate_run", gateRunId, effectiveActor,
                        HarnessSupport.mapOf("gateName", gateName, "exitCode", result.getExitCode(),
                                "passed", result.isPassed(), "digest", digest,
                                "durationMillis", durationMillis, "evidenceId", evidenceId));
                return gateRun.copy();
            }
        });
    }

    public List<GateRunRecord> listGateRuns(String taskId) {
        String key = HarnessSupport.trimToNull(taskId);
        HarnessState state = store.load();
        if (state == null || state.getGateRuns() == null) {
            return Collections.emptyList();
        }
        List<GateRunRecord> result = new ArrayList<GateRunRecord>();
        for (GateRunRecord run : state.getGateRuns().values()) {
            if (run != null && (key == null || key.equals(run.getTaskId()))) {
                result.add(run.copy());
            }
        }
        Collections.sort(result, new Comparator<GateRunRecord>() {
            @Override
            public int compare(GateRunRecord left, GateRunRecord right) {
                return Long.compare(left.getCreatedAtEpochMs(), right.getCreatedAtEpochMs());
            }
        });
        return result;
    }

    public List<GateRunRecord> listGateRunsForExecution(String executionId) {
        String key = HarnessSupport.requireText(executionId, "execution id");
        HarnessState state = store.load();
        if (state == null || state.getGateRuns() == null) {
            return Collections.emptyList();
        }
        List<GateRunRecord> result = new ArrayList<GateRunRecord>();
        for (GateRunRecord run : state.getGateRuns().values()) {
            if (run != null && key.equals(run.getExecutionId())) {
                result.add(run.copy());
            }
        }
        return result;
    }

    /**
     * Validates review vs content-pinned consent separation and required witness gate runs
     * before task completion. Preserves legacy 1.x reviewSubmission flow when neither
     * ExecutionReview nor ContentPinnedConsent is used and contract does not require consent.
     */
    public void validateCompletionGovernance(HarnessState state,
                                             TaskRecord task,
                                             SubmissionRecord submission) {
        String submissionId = submission.getSubmissionId();
        boolean hasExecReview = hasExecutionReviews(state, submissionId);
        boolean hasConsents = hasConsents(state, submissionId);
        boolean consentMode = contract.requiresContentPinnedConsent(task, submission)
                || hasExecReview || hasConsents;

        if (consentMode) {
            ContentPinnedConsentRecord consent = latestConsentForSubmission(state, submissionId);
            if (consent == null) {
                throw new HarnessValidationException(
                        "ContentPinnedConsent signed by a human/owner principal is required before completion");
            }
            if (!consent.isApproved()) {
                throw new HarnessConflictException("latest ContentPinnedConsent rejected the submission");
            }
            String actualDigest = ensureSubmissionDigest(submission);
            if (!HarnessSupport.safeEquals(actualDigest, consent.getSubmissionDigest())) {
                throw new HarnessConflictException(ERROR_CONSENT_DIGEST_MISMATCH,
                        "ContentPinnedConsent submissionDigest does not match current submission: expected "
                                + actualDigest + " but consent pinned " + consent.getSubmissionDigest());
            }
            List<String> uncovered = findUncoveredFindings(state, submissionId, consent.getDispositions());
            if (!uncovered.isEmpty()) {
                throw new HarnessConflictException(ERROR_CONSENT_FINDINGS_UNCOVERED,
                        "ContentPinnedConsent dispositions do not cover all review findings: " + uncovered);
            }
        } else if (contract.requiresApprovedReview(task, submission)
                && !hasApprovedLegacyReview(state, submissionId)) {
            throw new HarnessValidationException("an approved review is required before completion");
        }

        Set<String> requiredWitnesses = contract.requiredWitnessGates(task, submission);
        if (requiredWitnesses != null && !requiredWitnesses.isEmpty()) {
            for (String gateName : requiredWitnesses) {
                String normalizedGate = HarnessSupport.trimToNull(gateName);
                if (normalizedGate == null) {
                    continue;
                }
                GateRunRecord latest = latestGateRun(state, task.getTaskId(),
                        submission.getExecutionId(), submissionId, normalizedGate);
                if (latest == null) {
                    throw new HarnessConflictException(ERROR_WITNESS_FAILED,
                            "required Witness gate run is missing: " + normalizedGate);
                }
                if (!latest.isPassed()) {
                    throw new HarnessConflictException(ERROR_WITNESS_FAILED,
                            "required Witness gate run failed: " + normalizedGate + " (" + latest.getSummary() + ")");
                }
                if (latest.getEvidenceId() == null || state.getEvidence().get(latest.getEvidenceId()) == null) {
                    throw new HarnessConflictException(ERROR_WITNESS_FAILED,
                            "required Witness gate run evidence is missing for: " + normalizedGate);
                }
            }
        }
    }

    public static List<String> findUncoveredFindings(HarnessState state,
                                                     String submissionId,
                                                     List<FindingDisposition> dispositions) {
        Map<String, FindingDisposition> dispositionMap = new LinkedHashMap<String, FindingDisposition>();
        if (dispositions != null) {
            for (FindingDisposition d : dispositions) {
                if (d != null && HarnessSupport.trimToNull(d.getFindingId()) != null) {
                    dispositionMap.put(d.getFindingId().trim(), d);
                }
            }
        }
        Set<String> requiredFindingIds = new LinkedHashSet<String>();
        if (state != null && state.getExecutionReviews() != null) {
            for (ExecutionReviewRecord review : state.getExecutionReviews().values()) {
                if (review == null || !HarnessSupport.safeEquals(submissionId, review.getSubmissionId())
                        || review.getFindings() == null) {
                    continue;
                }
                for (ReviewFinding finding : review.getFindings()) {
                    if (finding != null && HarnessSupport.trimToNull(finding.getFindingId()) != null) {
                        requiredFindingIds.add(finding.getFindingId().trim());
                    }
                }
            }
        }
        List<String> uncovered = new ArrayList<String>();
        for (String findingId : requiredFindingIds) {
            FindingDisposition disposition = dispositionMap.get(findingId);
            if (disposition == null || !disposition.isResolvedDisposition()) {
                uncovered.add(findingId);
            }
        }
        return uncovered;
    }

    public static boolean hasExecutionReviews(HarnessState state, String submissionId) {
        if (state == null || state.getExecutionReviews() == null) {
            return false;
        }
        for (ExecutionReviewRecord review : state.getExecutionReviews().values()) {
            if (review != null && HarnessSupport.safeEquals(submissionId, review.getSubmissionId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasConsents(HarnessState state, String submissionId) {
        if (state == null || state.getConsents() == null) {
            return false;
        }
        for (ContentPinnedConsentRecord consent : state.getConsents().values()) {
            if (consent != null && HarnessSupport.safeEquals(submissionId, consent.getSubmissionId())) {
                return true;
            }
        }
        return false;
    }

    public static ContentPinnedConsentRecord latestConsentForSubmission(HarnessState state, String submissionId) {
        if (state == null || state.getConsents() == null) {
            return null;
        }
        ContentPinnedConsentRecord latest = null;
        for (ContentPinnedConsentRecord candidate : state.getConsents().values()) {
            if (candidate == null || !HarnessSupport.safeEquals(submissionId, candidate.getSubmissionId())) {
                continue;
            }
            if (latest == null || candidate.getConsentedAtEpochMs() >= latest.getConsentedAtEpochMs()) {
                latest = candidate;
            }
        }
        return latest;
    }

    public static boolean hasApprovedLegacyReview(HarnessState state, String submissionId) {
        if (state == null || state.getReviews() == null) {
            return false;
        }
        ReviewRecord latest = null;
        for (ReviewRecord review : state.getReviews().values()) {
            if (review == null || !HarnessSupport.safeEquals(submissionId, review.getSubmissionId())) {
                continue;
            }
            if (latest == null || review.getCreatedAtEpochMs() >= latest.getCreatedAtEpochMs()) {
                latest = review;
            }
        }
        return latest != null && ReviewVerdict.APPROVED.equals(latest.getVerdict());
    }

    public static GateRunRecord latestGateRun(HarnessState state,
                                              String taskId,
                                              String executionId,
                                              String submissionId,
                                              String gateName) {
        if (state == null || state.getGateRuns() == null) {
            return null;
        }
        GateRunRecord latest = null;
        for (GateRunRecord candidate : state.getGateRuns().values()) {
            if (candidate == null || !gateName.equals(HarnessSupport.trimToNull(candidate.getGateName()))) {
                continue;
            }
            boolean matchesSubmission = submissionId != null && submissionId.equals(candidate.getSubmissionId());
            boolean matchesExecution = executionId != null && executionId.equals(candidate.getExecutionId())
                    && candidate.getSubmissionId() == null;
            boolean matchesTask = taskId != null && taskId.equals(candidate.getTaskId())
                    && candidate.getExecutionId() == null && candidate.getSubmissionId() == null;
            if (!matchesSubmission && !matchesExecution && !matchesTask) {
                continue;
            }
            if (latest == null || candidate.getCreatedAtEpochMs() >= latest.getCreatedAtEpochMs()) {
                latest = candidate;
            }
        }
        return latest;
    }

    private static String ensureSubmissionDigest(SubmissionRecord submission) {
        String existing = HarnessSupport.trimToNull(submission.getSubmissionDigest());
        if (existing != null) {
            return existing;
        }
        String computed = HarnessDigests.computeSubmissionDigest(submission);
        submission.setSubmissionDigest(computed);
        return computed;
    }

    private static List<ReviewFinding> normalizeFindings(List<ReviewFinding> raw) {
        List<ReviewFinding> result = new ArrayList<ReviewFinding>();
        if (raw == null) {
            return result;
        }
        int idx = 1;
        for (ReviewFinding item : raw) {
            if (item == null) {
                continue;
            }
            String findingId = HarnessSupport.trimToNull(item.getFindingId());
            if (findingId == null) {
                findingId = "finding-" + idx;
            }
            idx++;
            result.add(item.toBuilder()
                    .findingId(findingId)
                    .severity(item.getSeverity() == null ? ReviewFinding.Severity.WARN : item.getSeverity())
                    .build());
        }
        return result;
    }

    private static List<FindingDisposition> normalizeDispositions(List<FindingDisposition> raw) {
        List<FindingDisposition> result = new ArrayList<FindingDisposition>();
        if (raw == null) {
            return result;
        }
        for (FindingDisposition item : raw) {
            if (item == null || HarnessSupport.trimToNull(item.getFindingId()) == null) {
                continue;
            }
            result.add(item.toBuilder()
                    .findingId(item.getFindingId().trim())
                    .status(item.getStatus() == null ? FindingDisposition.Status.RESOLVED : item.getStatus())
                    .build());
        }
        return result;
    }
}
