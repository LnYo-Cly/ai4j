package io.github.lnyocly.ai4j.ambient.evolve;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.skill.AgentSkillResolver;
import io.github.lnyocly.ai4j.agent.skill.AgentSkillScope;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.CompositeToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.RoutingToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.harness.DecisionRecord;
import io.github.lnyocly.ai4j.harness.DecisionStatus;
import io.github.lnyocly.ai4j.harness.ExecutionRecord;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceContext;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceCoordinator;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceEvaluation;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceEvaluator;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceResult;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceStatus;
import io.github.lnyocly.ai4j.harness.HarnessActor;
import io.github.lnyocly.ai4j.harness.HarnessCommandGateway;
import io.github.lnyocly.ai4j.harness.HarnessDecisionSpec;
import io.github.lnyocly.ai4j.harness.HarnessExecutionSpec;
import io.github.lnyocly.ai4j.harness.HarnessRule;
import io.github.lnyocly.ai4j.harness.HarnessRuleSpec;
import io.github.lnyocly.ai4j.harness.HarnessTaskSpec;
import io.github.lnyocly.ai4j.harness.TaskRecord;
import io.github.lnyocly.ai4j.skill.SkillDescriptor;
import io.github.lnyocly.ai4j.testing.ModelFixture;
import io.github.lnyocly.ai4j.testing.ReplayModelClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immune pre-commit gate governing promotion of L1/L2/L3 RSI candidates ({@code v2}) over
 * existing production assets ({@code v1}).
 *
 * <p>Requires all of:</p>
 * <ol>
 *   <li>Isolated sandbox validation for L3 compiled tools without polluting parent runtime.</li>
 *   <li>Full {@link ReplayModelClient} golden replay regression pass.</li>
 *   <li>Step &amp; token budget non-regression check.</li>
 *   <li>Independent non-agent Witness acceptance verification (persisted via {@link HarnessAcceptanceCoordinator} when a gateway is supplied).</li>
 *   <li>Explicit human consent ({@link HarnessActor#isHuman()}) before atomic activation.</li>
 * </ol>
 *
 * <p>Retains version lineage for instant one-step {@link #rollback(String, HarnessActor, String)}
 * and feeds {@link MetaRsiEvaluator} for post-promotion regression tracking.</p>
 */
public class PreCommitGoldenGate {

    public enum GateOutcome {
        PROMOTED,
        REJECTED_SANDBOX_FAILED,
        REJECTED_GOLDEN_REPLAY_FAILED,
        REJECTED_BUDGET_REGRESSION,
        REJECTED_WITNESS_FAILED,
        REJECTED_CONSENT_REQUIRED,
        REJECTED_CONSENT_DIGEST_MISMATCH
    }

    private final SkillCompiler skillCompiler;
    private final MetaRsiEvaluator metaRsiEvaluator;
    private final Map<String, ActiveSlot> activeSlots = new LinkedHashMap<String, ActiveSlot>();

    public PreCommitGoldenGate() {
        this(new SkillCompiler(), new MetaRsiEvaluator());
    }

    public PreCommitGoldenGate(SkillCompiler skillCompiler, MetaRsiEvaluator metaRsiEvaluator) {
        this.skillCompiler = skillCompiler == null ? new SkillCompiler() : skillCompiler;
        this.metaRsiEvaluator = metaRsiEvaluator == null ? new MetaRsiEvaluator() : metaRsiEvaluator;
    }

    public MetaRsiEvaluator getMetaRsiEvaluator() {
        return metaRsiEvaluator;
    }

    /**
     * Seeds an initial production baseline asset {@code v1} into {@code slotKey} so
     * subsequent {@code v2} promotions and rollbacks preserve full version continuity.
     */
    public synchronized void seedBaseline(String slotKey, RsiCandidate baseline) {
        String key = AmbientSupport.requireNonBlank(slotKey, "slotKey");
        if (baseline == null) {
            throw new IllegalArgumentException("baseline candidate must not be null");
        }
        baseline.setStatus(CandidateStatus.ACTIVE);
        ActiveSlot slot = activeSlots.computeIfAbsent(key, ignored -> new ActiveSlot());
        slot.active = new PromotedVersion(baseline, null);
    }

    public synchronized RsiCandidate getActiveCandidate(String slotKey) {
        ActiveSlot slot = activeSlots.get(slotKey);
        return slot == null || slot.active == null ? null : slot.active.candidate;
    }

    public synchronized GateEvaluationResult evaluateAndPromote(GateEvaluationRequest request) {
        if (request == null || request.getCandidate() == null) {
            throw new IllegalArgumentException("gate evaluation request and candidate must not be null");
        }
        String slotKey = AmbientSupport.requireNonBlank(request.getSlotKey(), "slotKey");
        RsiCandidate candidate = request.getCandidate();
        String scopeKey = AmbientSupport.trimToNull(request.getScopeKey());
        if (scopeKey == null) {
            scopeKey = AmbientSupport.trimToNull(candidate.getScopeKey()) == null
                    ? "default" : candidate.getScopeKey();
        }
        String metaVersion = candidate.getMetaVersion();
        metaRsiEvaluator.recordProposal(metaVersion, candidate);

        // 1. Isolated sandbox verification for L3 compiled skills
        if (candidate instanceof CompiledSkillCandidate) {
            CompiledSkillCandidate compiled = (CompiledSkillCandidate) candidate;
            SkillCompiler.SandboxVerificationResult sandboxRes = skillCompiler.verifyInIsolatedSandbox(
                    request.getParentRegistry(),
                    compiled,
                    request.getSandboxDelegateExecutor(),
                    request.getSandboxChecks()
            );
            if (!sandboxRes.isPassed()) {
                return reject(candidate, metaVersion, GateOutcome.REJECTED_SANDBOX_FAILED,
                        "isolated sandbox verification failed: " + sandboxRes.getFailureReason(),
                        0, 0, 0L, 0L, null, null);
            }
        }

        // 2. Golden Replay Suite using ReplayModelClient
        List<GoldenReplayCase> cases = request.getGoldenCases();
        if (cases == null || cases.isEmpty()) {
            return reject(candidate, metaVersion, GateOutcome.REJECTED_GOLDEN_REPLAY_FAILED,
                    "golden replay suite must contain at least one case",
                    0, 0, 0L, 0L, null, null);
        }

        int totalBaselineSteps = 0;
        int totalCandidateSteps = 0;
        long totalBaselineTokens = 0L;
        long totalCandidateTokens = 0L;

        for (GoldenReplayCase goldenCase : cases) {
            if (goldenCase == null || goldenCase.getFixture() == null) {
                return reject(candidate, metaVersion, GateOutcome.REJECTED_GOLDEN_REPLAY_FAILED,
                        "golden case and fixture must not be null",
                        totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                        null, null);
            }
            CaseReplayOutcome outcome = runGoldenCase(candidate, goldenCase, request.getSandboxDelegateExecutor());
            totalBaselineSteps += Math.max(0, goldenCase.getBaselineSteps());
            totalBaselineTokens += Math.max(0L, goldenCase.getBaselineTokens());
            totalCandidateSteps += outcome.getSteps();
            totalCandidateTokens += outcome.getTokens();

            if (!outcome.isPassed()) {
                return reject(candidate, metaVersion, GateOutcome.REJECTED_GOLDEN_REPLAY_FAILED,
                        "golden case '" + outcome.getCaseId() + "' failed: " + outcome.getFailureReason(),
                        totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                        null, null);
            }
        }

        // 3. Step & Token budget non-regression gate
        double stepDeltaRatio = computeDeltaRatio(totalBaselineSteps, totalCandidateSteps);
        double tokenDeltaRatio = computeDeltaRatio(totalBaselineTokens, totalCandidateTokens);

        if (totalBaselineSteps > 0) {
            double maxStepsAllowed = (double) totalBaselineSteps * (1.0d + Math.max(0.0d, request.getMaxStepRegressionRatio()));
            if ((double) totalCandidateSteps > maxStepsAllowed) {
                return reject(candidate, metaVersion, GateOutcome.REJECTED_BUDGET_REGRESSION,
                        "step budget regressed: candidate=" + totalCandidateSteps
                                + " > allowed=" + maxStepsAllowed + " (baseline=" + totalBaselineSteps + ")",
                        totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                        null, null);
            }
        }
        if (totalBaselineTokens > 0L) {
            double maxTokensAllowed = (double) totalBaselineTokens * (1.0d + Math.max(0.0d, request.getMaxTokenRegressionRatio()));
            if ((double) totalCandidateTokens > maxTokensAllowed) {
                return reject(candidate, metaVersion, GateOutcome.REJECTED_BUDGET_REGRESSION,
                        "token budget regressed: candidate=" + totalCandidateTokens
                                + " > allowed=" + maxTokensAllowed + " (baseline=" + totalBaselineTokens + ")",
                        totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                        null, null);
            }
        }

        // 4. Independent non-agent Witness verification
        HarnessActor witnessActor = request.getWitnessActor();
        HarnessAcceptanceEvaluator witnessEvaluator = request.getWitnessEvaluator();
        if (witnessEvaluator == null) {
            return reject(candidate, metaVersion, GateOutcome.REJECTED_WITNESS_FAILED,
                    "independent witness evaluator is required",
                    totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                    null, null);
        }
        if (witnessActor == null || witnessActor.isAgent()) {
            return reject(candidate, metaVersion, GateOutcome.REJECTED_WITNESS_FAILED,
                    "witness actor must be a non-agent principal",
                    totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                    null, null);
        }

        String acceptanceId = null;
        String evidenceId = null;
        HarnessCommandGateway gateway = request.getHarnessGateway();
        Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
        artifacts.put("candidateId", candidate.getCandidateId());
        artifacts.put("candidateDigest", candidate.getDigest());
        artifacts.put("tier", candidate.getTier().name());
        artifacts.put("candidateSteps", Integer.valueOf(totalCandidateSteps));
        artifacts.put("candidateTokens", Long.valueOf(totalCandidateTokens));

        if (gateway != null) {
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder()
                    .scopeKey(scopeKey)
                    .title("RSI Golden Gate Witness: " + slotKey)
                    .goal("Verify candidate " + candidate.getCandidateId() + " (" + candidate.getTargetVersion() + ")")
                    .build(), witnessActor);
            ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                    .scopeKey(scopeKey)
                    .taskId(task.getTaskId())
                    .inputSummary("PreCommitGoldenGate witness check")
                    .build(), witnessActor);
            HarnessAcceptanceContext ctx = HarnessAcceptanceContext.builder()
                    .taskId(task.getTaskId())
                    .executionId(execution.getExecutionId())
                    .contextSnapshotRef(candidate.getDigest())
                    .artifacts(artifacts)
                    .build();
            HarnessAcceptanceEvaluation evaluation = HarnessAcceptanceCoordinator.evaluate(
                    gateway, witnessEvaluator, ctx, witnessActor);
            if (evaluation.getResult() == null || !evaluation.getResult().isPassed()) {
                String summary = evaluation.getResult() == null ? "null witness result" : evaluation.getResult().getSummary();
                return reject(candidate, metaVersion, GateOutcome.REJECTED_WITNESS_FAILED,
                        "witness verification failed: " + summary,
                        totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                        evaluation.getAcceptance() == null ? null : evaluation.getAcceptance().getAcceptanceId(),
                        null);
            }
            acceptanceId = evaluation.getAcceptance().getAcceptanceId();
            evidenceId = evaluation.getEvidence() == null ? null : evaluation.getEvidence().getEvidenceId();
        } else {
            HarnessAcceptanceContext ctx = HarnessAcceptanceContext.builder()
                    .contextSnapshotRef(candidate.getDigest())
                    .artifacts(artifacts)
                    .build();
            HarnessAcceptanceResult result = witnessEvaluator.evaluate(ctx);
            if (result == null || result.getStatus() != HarnessAcceptanceStatus.PASS) {
                return reject(candidate, metaVersion, GateOutcome.REJECTED_WITNESS_FAILED,
                        "witness verification failed: " + (result == null ? "null result" : result.getSummary()),
                        totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                        null, null);
            }
        }

        // 5. Human consent requirement before activation
        HarnessActor consentActor = request.getConsentActor();
        if (consentActor == null || !consentActor.isHuman()) {
            return reject(candidate, metaVersion, GateOutcome.REJECTED_CONSENT_REQUIRED,
                    "human owner consent is required before activating candidate " + candidate.getCandidateId(),
                    totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                    acceptanceId, evidenceId);
        }
        String consentDigest = AmbientSupport.trimToNull(request.getConsentDigest());
        if (consentDigest != null && !consentDigest.equals(candidate.getDigest())) {
            return reject(candidate, metaVersion, GateOutcome.REJECTED_CONSENT_DIGEST_MISMATCH,
                    "consent digest does not match candidate digest " + candidate.getDigest(),
                    totalBaselineSteps, totalCandidateSteps, totalBaselineTokens, totalCandidateTokens,
                    acceptanceId, evidenceId);
        }

        // 6. Durable Decision / L1 Rule Promotion + Atomic Activation
        String decisionId = null;
        String promotedRuleId = null;
        if (gateway != null) {
            List<String> evidenceIds = evidenceId == null
                    ? Collections.<String>emptyList()
                    : Collections.singletonList(evidenceId);
            DecisionRecord proposed = gateway.proposeDecision(HarnessDecisionSpec.builder()
                    .scopeKey(scopeKey)
                    .question("Activate RSI candidate " + candidate.getCandidateId() + " for slot " + slotKey + "?")
                    .chosenOption(candidate.getTargetVersion() + "@" + candidate.getDigest())
                    .rationale("Golden gate & witness passed")
                    .evidenceIds(evidenceIds)
                    .build(), witnessActor);
            DecisionRecord resolved = gateway.resolveDecision(
                    proposed.getDecisionId(),
                    DecisionStatus.ACCEPTED,
                    "Approved by human consent (" + consentActor.getId() + ")",
                    consentActor);
            decisionId = resolved.getDecisionId();

            if (candidate instanceof HarnessRuleProposal) {
                HarnessRuleProposal ruleProposal = (HarnessRuleProposal) candidate;
                if (ruleProposal.getRuleSpec() != null) {
                    HarnessRule promotedRule = gateway.promoteLesson(decisionId, ruleProposal.getRuleSpec(), consentActor);
                    promotedRuleId = promotedRule.getRuleId();
                    ruleProposal.setDecisionId(decisionId);
                    ruleProposal.setPromotedRuleId(promotedRuleId);
                }
            }
        }

        ActiveSlot slot = activeSlots.computeIfAbsent(slotKey, ignored -> new ActiveSlot());
        if (slot.active != null) {
            slot.history.push(slot.active);
        }
        candidate.setStatus(CandidateStatus.ACTIVE);
        slot.active = new PromotedVersion(candidate, promotedRuleId);

        metaRsiEvaluator.recordGateResult(metaVersion, candidate.getCandidateId(), true, true,
                stepDeltaRatio, tokenDeltaRatio);

        return GateEvaluationResult.builder()
                .outcome(GateOutcome.PROMOTED)
                .promoted(true)
                .slotKey(slotKey)
                .candidateId(candidate.getCandidateId())
                .candidateDigest(candidate.getDigest())
                .decisionId(decisionId)
                .promotedRuleId(promotedRuleId)
                .acceptanceId(acceptanceId)
                .evidenceId(evidenceId)
                .baselineSteps(totalBaselineSteps)
                .candidateSteps(totalCandidateSteps)
                .baselineTokens(totalBaselineTokens)
                .candidateTokens(totalCandidateTokens)
                .stepDeltaRatio(stepDeltaRatio)
                .tokenDeltaRatio(tokenDeltaRatio)
                .reason("candidate verified and promoted")
                .build();
    }

    /**
     * Instant rollback from active {@code v2} back to the previous {@code v1} version,
     * revoking any promoted L1 {@link HarnessRule} in {@code gateway} and recording the
     * post-promotion regression in {@link MetaRsiEvaluator}.
     */
    public synchronized RollbackResult rollback(String slotKey,
                                                HarnessCommandGateway gateway,
                                                HarnessActor actor,
                                                String reason) {
        String key = AmbientSupport.requireNonBlank(slotKey, "slotKey");
        if (actor == null || actor.isAgent()) {
            throw new IllegalArgumentException("rollback requires a non-agent human or system actor");
        }
        ActiveSlot slot = activeSlots.get(key);
        if (slot == null || slot.active == null) {
            throw new IllegalStateException("no active candidate to roll back for slot: " + key);
        }
        PromotedVersion current = slot.active;
        current.candidate.setStatus(CandidateStatus.ROLLED_BACK);

        String revokedRuleId = null;
        if (gateway != null && current.promotedRuleId != null) {
            HarnessRule revoked = gateway.revokeLesson(current.promotedRuleId, actor);
            revokedRuleId = revoked.getRuleId();
        }

        PromotedVersion restored = slot.history.isEmpty() ? null : slot.history.pop();
        if (restored != null) {
            restored.candidate.setStatus(CandidateStatus.ACTIVE);
        }
        slot.active = restored;

        metaRsiEvaluator.recordPostPromotionRollback(
                current.candidate.getMetaVersion(),
                current.candidate.getCandidateId(),
                reason
        );

        return RollbackResult.builder()
                .slotKey(key)
                .rolledBackCandidateId(current.candidate.getCandidateId())
                .restoredCandidateId(restored == null ? null : restored.candidate.getCandidateId())
                .restoredCandidate(restored == null ? null : restored.candidate)
                .revokedRuleId(revokedRuleId)
                .reason(reason)
                .build();
    }

    private CaseReplayOutcome runGoldenCase(RsiCandidate candidate,
                                            GoldenReplayCase goldenCase,
                                            ToolExecutor sandboxDelegate) {
        ReplayModelClient replay = new ReplayModelClient(goldenCase.getFixture());
        replay.failWhenExhausted(true);

        AgentToolRegistry registry = goldenCase.getToolRegistry();
        ToolExecutor executor = goldenCase.getToolExecutor();
        String extraSystemPrompt = null;

        if (candidate instanceof CompiledSkillCandidate) {
            CompiledSkillCandidate compiled = (CompiledSkillCandidate) candidate;
            AgentToolRegistry compiledReg = new StaticToolRegistry(
                    Collections.<Object>singletonList(compiled.toOpenAiTool()));
            registry = registry == null ? compiledReg : new CompositeToolRegistry(compiledReg, registry);
            ToolExecutor base = executor != null ? executor : sandboxDelegate;
            if (base != null) {
                executor = new RoutingToolExecutor(
                        Collections.singletonList(RoutingToolExecutor.route(
                                Collections.singleton(compiled.getToolName()), compiled.toExecutor(base))),
                        base);
            }
        } else if (candidate instanceof SkillPatchCandidate) {
            final SkillPatchCandidate patch = (SkillPatchCandidate) candidate;
            extraSystemPrompt = patch.getMarkdownContent();
        }

        try {
            io.github.lnyocly.ai4j.agent.AgentBuilder builder = Agents.react()
                    .modelClient(replay)
                    .model(AmbientSupport.trimToNull(goldenCase.getModelName()) == null
                            ? "golden-replay-model" : goldenCase.getModelName());
            if (registry != null) {
                builder.toolRegistry(registry);
            }
            if (executor != null) {
                builder.toolExecutor(executor);
            }
            if (extraSystemPrompt != null) {
                builder.systemPrompt(extraSystemPrompt);
            }
            if (candidate instanceof SkillPatchCandidate) {
                final SkillDescriptor descriptor = ((SkillPatchCandidate) candidate).toSkillDescriptor(null);
                builder.skillResolver(new AgentSkillResolver() {
                    @Override
                    public AgentSkillScope resolve(AgentRequest req) {
                        return AgentSkillScope.builder()
                                .providedSkills(Collections.singletonList(descriptor))
                                .enabledSkillNames(Collections.singletonList(descriptor.getName()))
                                .build();
                    }
                });
            }

            Agent agent = builder.build();
            AgentResult result = agent.newSession().run(
                    AgentRequest.builder()
                            .input(goldenCase.getUserInput() == null ? "run" : goldenCase.getUserInput())
                            .build());

            if (goldenCase.getExpectedOutputContains() != null) {
                String out = result == null ? null : result.getOutputText();
                if (out == null || !out.contains(goldenCase.getExpectedOutputContains())) {
                    return CaseReplayOutcome.fail(goldenCase.getCaseId(),
                            "output did not contain expected text '" + goldenCase.getExpectedOutputContains() + "'",
                            stepsOf(result, replay), tokensOf(result));
                }
            }
            return CaseReplayOutcome.pass(goldenCase.getCaseId(),
                    stepsOf(result, replay), tokensOf(result));
        } catch (Exception e) {
            return CaseReplayOutcome.fail(goldenCase.getCaseId(), e.getMessage(),
                    replay.getInvocationCount(), 0L);
        }
    }

    private GateEvaluationResult reject(RsiCandidate candidate,
                                        String metaVersion,
                                        GateOutcome outcome,
                                        String reason,
                                        int baselineSteps,
                                        int candidateSteps,
                                        long baselineTokens,
                                        long candidateTokens,
                                        String acceptanceId,
                                        String evidenceId) {
        candidate.setStatus(CandidateStatus.GATE_REJECTED);
        double stepDelta = computeDeltaRatio(baselineSteps, candidateSteps);
        double tokenDelta = computeDeltaRatio(baselineTokens, candidateTokens);
        metaRsiEvaluator.recordGateResult(metaVersion, candidate.getCandidateId(), false, false,
                stepDelta, tokenDelta);
        return GateEvaluationResult.builder()
                .outcome(outcome)
                .promoted(false)
                .candidateId(candidate.getCandidateId())
                .candidateDigest(candidate.getDigest())
                .acceptanceId(acceptanceId)
                .evidenceId(evidenceId)
                .baselineSteps(baselineSteps)
                .candidateSteps(candidateSteps)
                .baselineTokens(baselineTokens)
                .candidateTokens(candidateTokens)
                .stepDeltaRatio(stepDelta)
                .tokenDeltaRatio(tokenDelta)
                .reason(reason)
                .build();
    }

    private static int stepsOf(AgentResult result, ReplayModelClient replay) {
        if (result != null && result.getSteps() != null && result.getSteps().intValue() > 0) {
            return result.getSteps().intValue();
        }
        return replay.getInvocationCount();
    }

    private static long tokensOf(AgentResult result) {
        if (result == null) {
            return 0L;
        }
        if (result.getTotalTokens() != null) {
            return result.getTotalTokens().longValue();
        }
        long in = result.getInputTokens() == null ? 0L : result.getInputTokens().longValue();
        long out = result.getOutputTokens() == null ? 0L : result.getOutputTokens().longValue();
        return in + out;
    }

    private static double computeDeltaRatio(long baseline, long candidate) {
        if (baseline <= 0L) {
            return 0.0d;
        }
        return (double) (candidate - baseline) / (double) baseline;
    }

    private static final class ActiveSlot {
        private PromotedVersion active;
        private final Deque<PromotedVersion> history = new ArrayDeque<PromotedVersion>();
    }

    private static final class PromotedVersion {
        private final RsiCandidate candidate;
        private final String promotedRuleId;

        private PromotedVersion(RsiCandidate candidate, String promotedRuleId) {
            this.candidate = candidate;
            this.promotedRuleId = promotedRuleId;
        }
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoldenReplayCase {
        private String caseId;
        private String name;
        private String modelName;
        private String userInput;
        private ModelFixture fixture;
        private AgentToolRegistry toolRegistry;
        private ToolExecutor toolExecutor;
        private int baselineSteps;
        private long baselineTokens;
        @Builder.Default
        private boolean mustPass = true;
        private String expectedOutputContains;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GateEvaluationRequest {
        private String slotKey;
        private String scopeKey;
        private RsiCandidate candidate;
        @Builder.Default
        private List<GoldenReplayCase> goldenCases = new ArrayList<GoldenReplayCase>();
        @Builder.Default
        private double maxStepRegressionRatio = 0.0d;
        @Builder.Default
        private double maxTokenRegressionRatio = 0.0d;
        private HarnessAcceptanceEvaluator witnessEvaluator;
        private HarnessActor witnessActor;
        private HarnessActor consentActor;
        private String consentDigest;
        private HarnessCommandGateway harnessGateway;
        private ExtensionRegistry parentRegistry;
        private ToolExecutor sandboxDelegateExecutor;
        @Builder.Default
        private List<SkillCompiler.SandboxCheck> sandboxChecks = new ArrayList<SkillCompiler.SandboxCheck>();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GateEvaluationResult {
        private GateOutcome outcome;
        private boolean promoted;
        private String slotKey;
        private String candidateId;
        private String candidateDigest;
        private String decisionId;
        private String promotedRuleId;
        private String acceptanceId;
        private String evidenceId;
        private int baselineSteps;
        private int candidateSteps;
        private long baselineTokens;
        private long candidateTokens;
        private double stepDeltaRatio;
        private double tokenDeltaRatio;
        private String reason;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackResult {
        private String slotKey;
        private String rolledBackCandidateId;
        private String restoredCandidateId;
        private RsiCandidate restoredCandidate;
        private String revokedRuleId;
        private String reason;
    }

    @Data
    private static class CaseReplayOutcome {
        private final String caseId;
        private final boolean passed;
        private final String failureReason;
        private final int steps;
        private final long tokens;

        private static CaseReplayOutcome pass(String caseId, int steps, long tokens) {
            return new CaseReplayOutcome(caseId, true, null, steps, tokens);
        }

        private static CaseReplayOutcome fail(String caseId, String reason, int steps, long tokens) {
            return new CaseReplayOutcome(caseId, false, reason, steps, tokens);
        }
    }
}
