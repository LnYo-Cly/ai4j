package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic tests for Harness 2.0 governance gates, causal ReadSet freshness,
 * Witness verification, Escalate & SupervisoryAgenda, explain(entityRef),
 * falsifiable Decision reckoning, safe-point steer, handoff, AgentBlueprint
 * diff/rollback, SquadRun budgets, VerticalKind/FactType, Todo->Task->Work,
 * and Schedule(DETECT | REMEDIATE). Zero Thread.sleep usage.
 */
public class HarnessGovernance2Test {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("ai4j-harness2-gov-");
    }

    @After
    public void tearDown() throws Exception {
        if (directory != null && Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Best effort cleanup
                        }
                    });
        }
    }

    private HarnessCommandGateway fileGateway(String name, HarnessContract contract) {
        FileHarnessStore store = new FileHarnessStore(FileHarnessConfig.builder()
                .directory(directory.resolve(name))
                .harnessId(name)
                .build());
        return new HarnessCommandGateway(store, contract, HarnessActor.agent("coder-agent"));
    }

    private HarnessCommandGateway jdbcGateway(String dbName, HarnessContract contract) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        JdbcHarnessStore store = new JdbcHarnessStore(ds, dbName);
        return new HarnessCommandGateway(store, contract, HarnessActor.agent("coder-agent"));
    }

    private static HarnessContract strictGovContract() {
        return HarnessContract.builder()
                .requiresContentPinnedConsent(true)
                .requiredWitnessGate("mvn-verify")
                .requiresFalsifiableDecisions(true)
                .requiresApprovedReview(true)
                .build();
    }

    @Test
    public void executionReviewVsContentPinnedConsentEnforcesHumanOnlyDigestAndDispositions() {
        HarnessCommandGateway gw = fileGateway("review-consent", strictGovContract());
        try {
            HarnessActor agent = HarnessActor.agent("agent-1");
            HarnessActor reviewer = HarnessActor.agent("reviewer-agent");
            HarnessActor owner = HarnessActor.owner("owner-alice");

            TaskRecord task = gw.createTask(HarnessTaskSpec.builder()
                    .taskId("task-rc-1")
                    .scopeKey("repo-a")
                    .title("Ship feature X")
                    .build(), agent);
            ExecutionRecord exec = gw.createExecution(HarnessExecutionSpec.builder()
                    .executionId("exec-rc-1")
                    .taskId(task.getTaskId())
                    .scopeKey("repo-a")
                    .build());
            ExecutionRecord claimedRc = gw.claimExecution(exec.getExecutionId(), "worker-rc", 60_000L);
            EvidenceRecord ev = gw.recordEvidence(HarnessEvidenceSpec.builder()
                    .evidenceId("ev-1")
                    .scopeKey("repo-a")
                    .taskId(task.getTaskId())
                    .executionId(exec.getExecutionId())
                    .kind("test")
                    .location("src/test")
                    .summary("unit tests green")
                    .build(), agent);
            gw.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                    .executionId(claimedRc.getExecutionId())
                    .leaseId(claimedRc.getLeaseId())
                    .fencingToken(claimedRc.getFencingToken())
                    .status(ExecutionStatus.SUCCEEDED)
                    .outputText("implemented X")
                    .build());

            // Witness must pass before completion too
            gw.witnessRerun(WitnessRunSpec.builder()
                    .taskId(task.getTaskId())
                    .executionId(exec.getExecutionId())
                    .gateName("mvn-verify")
                    .command("mvn verify")
                    .build(),
                    req -> WitnessResult.builder().exitCode(0).passed(true).output("BUILD SUCCESS").build(),
                    HarnessActor.worker("host-witness"));

            SubmissionRecord sub = gw.submitTask(task.getTaskId(), exec.getExecutionId(),
                    HarnessSubmissionSpec.builder()
                            .completionClaim("Implemented feature X cleanly")
                            .evidenceIds(Collections.singletonList(ev.getEvidenceId()))
                            .deliverables(Collections.singletonList("FeatureX.java"))
                            .build(), agent);
            bindPassAcceptance(gw, task.getTaskId(), exec.getExecutionId(), sub.getSubmissionId(), owner);
            Assert.assertNotNull(sub.getSubmissionDigest());
            Assert.assertFalse(sub.getSubmissionDigest().trim().isEmpty());

            // Technical ExecutionReview with one blocking finding
            ExecutionReviewRecord review = gw.submitExecutionReview(ExecutionReviewSpec.builder()
                    .submissionId(sub.getSubmissionId())
                    .submissionDigest(sub.getSubmissionDigest())
                    .verdict(ReviewVerdict.APPROVED)
                    .findingsSummary("One edge finding noted")
                    .findings(Collections.singletonList(ReviewFinding.builder()
                            .findingId("f-1")
                            .severity(ReviewFinding.Severity.WARN)
                            .summary("Document retry edge")
                            .blocking(true)
                            .build()))
                    .build(), reviewer);
            Assert.assertEquals(sub.getSubmissionDigest(), review.getSubmissionDigest());

            // 1) Agent principal MUST be rejected on reviewConsent
            try {
                gw.reviewConsent(ContentPinnedConsentSpec.builder()
                        .submissionId(sub.getSubmissionId())
                        .submissionDigest(sub.getSubmissionDigest())
                        .approved(true)
                        .dispositions(Collections.singletonList(FindingDisposition.builder()
                                .findingId("f-1")
                                .status(FindingDisposition.Status.RESOLVED)
                                .rationale("fixed")
                                .build()))
                        .build(), agent);
                Assert.fail("agent principal must be rejected on reviewConsent");
            } catch (HarnessValidationException expected) {
                Assert.assertTrue(expected.getMessage().contains("human")
                        || GovernanceReviewService.ERROR_CONSENT_AGENT_FORBIDDEN.equals(expected.getErrorCode()));
            }

            // 2) Wrong digest MUST be rejected
            try {
                gw.reviewConsent(ContentPinnedConsentSpec.builder()
                        .submissionId(sub.getSubmissionId())
                        .submissionDigest("sha256:bad-digest")
                        .approved(true)
                        .dispositions(Collections.singletonList(FindingDisposition.builder()
                                .findingId("f-1")
                                .status(FindingDisposition.Status.RESOLVED)
                                .build()))
                        .build(), owner);
                Assert.fail("wrong submissionDigest must be rejected");
            } catch (HarnessConflictException expected) {
                Assert.assertNotNull(expected.getMessage());
            }

            // 3) Missing finding disposition MUST be rejected
            try {
                gw.reviewConsent(ContentPinnedConsentSpec.builder()
                        .submissionId(sub.getSubmissionId())
                        .submissionDigest(sub.getSubmissionDigest())
                        .approved(true)
                        .build(), owner);
                Assert.fail("uncovered review finding must be rejected");
            } catch (HarnessConflictException expected) {
                Assert.assertTrue(expected.getMessage().contains("f-1")
                        || GovernanceReviewService.ERROR_CONSENT_FINDINGS_UNCOVERED.equals(expected.getErrorCode()));
            }

            // 4) Valid human/owner ContentPinnedConsent with matching digest & covered finding succeeds
            ContentPinnedConsentRecord consent = gw.reviewConsent(ContentPinnedConsentSpec.builder()
                    .submissionId(sub.getSubmissionId())
                    .submissionDigest(sub.getSubmissionDigest())
                    .approved(true)
                    .rationale("Acceptable with documented retry")
                    .dispositions(Collections.singletonList(FindingDisposition.builder()
                            .findingId("f-1")
                            .status(FindingDisposition.Status.ACCEPTED_RISK)
                            .rationale("covered by timeout fence")
                            .build()))
                    .build(), owner);
            Assert.assertTrue(consent.isApproved());
            Assert.assertEquals(sub.getSubmissionDigest(), consent.getSubmissionDigest());

            TaskRecord completed = gw.completeTask(task.getTaskId(), sub.getSubmissionId(), owner);
            Assert.assertEquals(TaskStatus.DONE, completed.getStatus());
        } finally {
            gw.close();
        }
    }

    @Test
    public void escalateDualQueueAgendaAndExplainBlockerTree() throws Exception {
        HarnessCommandGateway gw = jdbcGateway("gov_agenda_" + System.nanoTime(), strictGovContract());
        try {
            HarnessActor agent = HarnessActor.agent("worker-agent");
            HarnessActor human = HarnessActor.human("lead-bob");

            TaskRecord depTask = gw.createTask(HarnessTaskSpec.builder()
                    .taskId("task-dep")
                    .scopeKey("scope-1")
                    .title("Dependency task")
                    .build(), agent);
            TaskRecord mainTask = gw.createTask(HarnessTaskSpec.builder()
                    .taskId("task-main")
                    .scopeKey("scope-1")
                    .title("Main task")
                    .build(), agent);
            ExecutionRecord exec = gw.createExecution(HarnessExecutionSpec.builder()
                    .executionId("exec-main")
                    .taskId(mainTask.getTaskId())
                    .scopeKey("scope-1")
                    .build());
            ExecutionRecord claimedMain = gw.claimExecution(exec.getExecutionId(), "w1", 60_000L);
            gw.addDependency(mainTask.getTaskId(), depTask.getTaskId(), agent);

            // Agent invokes management tool to escalate and explain
            HarnessExecutionContext toolCtx = new HarnessExecutionContext(
                    gw, exec.getExecutionId(), mainTask.getTaskId(), "sess-1", "scope-1", "run-1", agent, null);
            HarnessManagementToolExecutor toolExec = new HarnessManagementToolExecutor(toolCtx);

            String escJson = toolExec.execute(AgentToolCall.builder()
                    .callId("c-esc")
                    .name(HarnessToolNames.CONTROL_REQUEST)
                    .arguments("{\"operation\":\"escalate\",\"question\":\"Use Blue or Green rollout?\","
                            + "\"options\":[\"blue\",\"green\"],\"recommend\":\"blue\","
                            + "\"reason\":\"needs release policy ruling\",\"targetActorId\":\"lead-bob\"}")
                    .build());
            Assert.assertTrue(escJson.contains("HARNESS_ESCALATED"));
            ExecutionRecord waitingExec = gw.getExecution(claimedMain.getExecutionId());
            gw.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                    .executionId(waitingExec.getExecutionId())
                    .leaseId(waitingExec.getLeaseId())
                    .fencingToken(waitingExec.getFencingToken())
                    .status(ExecutionStatus.WAITING)
                    .waitId(waitingExec.getWaitId())
                    .checkpointSummary("waiting on escalation")
                    .build());

            // Check dual-queue agenda: awaitingRuling has item, answeredPendingFollowUp empty
            SupervisoryAgenda agendaBefore = gw.projectAgenda("lead-bob", "scope-1");
            Assert.assertEquals(1, agendaBefore.getAwaitingRuling().size());
            Assert.assertEquals(0, agendaBefore.getAnsweredPendingFollowUp().size());
            String escId = agendaBefore.getAwaitingRuling().get(0).getItemId();

            // Explain must return dependency + open escalation + wait blockers
            BlockerExplanation explanation = gw.explain("task:" + mainTask.getTaskId());
            Assert.assertTrue(explanation.isBlocked());
            Assert.assertNotNull(explanation.getRoot());
            Set<String> codes = collectCodes(explanation.getRoot());
            Assert.assertTrue(codes.contains("UNSATISFIED_DEPENDENCY"));
            Assert.assertTrue(codes.contains("OPEN_WAIT"));
            Assert.assertTrue(collectEntityTypes(explanation.getRoot()).contains("ESCALATION"));

            String explainToolOutput = toolExec.execute(AgentToolCall.builder()
                    .callId("c-exp")
                    .name(HarnessToolNames.CONTROL_REQUEST)
                    .arguments("{\"operation\":\"explain\",\"entityRef\":\"task:" + mainTask.getTaskId() + "\"}")
                    .build());
            Assert.assertTrue(explainToolOutput.contains("UNSATISFIED_DEPENDENCY"));

            // Human answers escalation -> moves to answeredPendingFollowUp + wakes execution
            EscalationRecord answered = gw.answerEscalation(
                    escId, "Proceed with blue deployment", "blue", "safer canary", human);
            Assert.assertEquals(EscalationStatus.ANSWERED, answered.getStatus());
            Assert.assertEquals(ExecutionStatus.READY, gw.getExecution(exec.getExecutionId()).getStatus());

            SupervisoryAgenda agendaMid = gw.projectAgenda("lead-bob", "scope-1");
            Assert.assertEquals(0, agendaMid.getAwaitingRuling().size());
            Assert.assertEquals(1, agendaMid.getAnsweredPendingFollowUp().size());

            // Agent follows up -> queue clears to RESOLVED
            EscalationRecord resolved = gw.followUpEscalation(escId, "Switched deployment script to blue", agent);
            Assert.assertEquals(EscalationStatus.RESOLVED, resolved.getStatus());

            SupervisoryAgenda agendaAfter = gw.projectAgenda("lead-bob", "scope-1");
            Assert.assertEquals(0, agendaAfter.getAwaitingRuling().size());
            Assert.assertEquals(0, agendaAfter.getAnsweredPendingFollowUp().size());
        } finally {
            gw.close();
        }
    }

    @Test
    public void causalReadSetDetectsStaleFactsDecisionsUntilReconfirmedAndWitnessGateBlocksFailure() {
        HarnessContract contract = HarnessContract.builder()
                .requiredWitnessGate("smoke-suite")
                .requiresApprovedReview(false)
                .build();
        HarnessCommandGateway gw = fileGateway("readset-witness", contract);
        try {
            HarnessActor agent = HarnessActor.agent("agent-rs");
            HarnessActor human = HarnessActor.human("human-rs");

            TaskRecord task = gw.createTask(HarnessTaskSpec.builder()
                    .taskId("task-rs")
                    .scopeKey("scope-rs")
                    .title("Validate ReadSet & Witness")
                    .build(), agent);
            ExecutionRecord exec = gw.createExecution(HarnessExecutionSpec.builder()
                    .executionId("exec-rs")
                    .taskId(task.getTaskId())
                    .scopeKey("scope-rs")
                    .build());
            ExecutionRecord claimedRs = gw.claimExecution(exec.getExecutionId(), "worker-rs", 60_000L);
            EvidenceRecord ev = gw.recordEvidence(HarnessEvidenceSpec.builder()
                    .evidenceId("ev-rs")
                    .scopeKey("scope-rs")
                    .taskId(task.getTaskId())
                    .executionId(exec.getExecutionId())
                    .kind("log")
                    .location("ci")
                    .summary("baseline check")
                    .build(), agent);
            FactRecord fact1 = gw.recordFact(HarnessFactSpec.builder()
                    .factId("fact-rs-1")
                    .scopeKey("scope-rs")
                    .taskId(task.getTaskId())
                    .statement("API timeout is 5s")
                    .source("config")
                    .evidenceIds(Collections.singletonList(ev.getEvidenceId()))
                    .build(), agent);
            DecisionRecord dec1 = gw.proposeDecision(HarnessDecisionSpec.builder()
                    .decisionId("dec-rs-1")
                    .scopeKey("scope-rs")
                    .taskId(task.getTaskId())
                    .question("Retry strategy?")
                    .chosenOption("exponential-backoff")
                    .evidenceIds(Collections.singletonList(ev.getEvidenceId()))
                    .build(), agent);
            gw.resolveDecision(dec1.getDecisionId(), DecisionStatus.ACCEPTED, "approved", human);

            ReadSetEntry factRead = gw.recordFactRead(exec.getExecutionId(), fact1.getFactId(), agent);
            ReadSetEntry decRead = gw.recordDecisionRead(exec.getExecutionId(), dec1.getDecisionId(), agent);
            Assert.assertEquals(1L, factRead.getObservedVersion());
            Assert.assertEquals(2L, decRead.getObservedVersion());

            // Supersede read fact -> submit MUST fail with STALE_READ_SET
            FactRecord fact2 = gw.supersedeFact(fact1.getFactId(), HarnessFactSpec.builder()
                    .factId("fact-rs-2")
                    .scopeKey("scope-rs")
                    .taskId(task.getTaskId())
                    .statement("API timeout bumped to 15s")
                    .source("config")
                    .build(), "config updated", agent);
            Assert.assertEquals(fact1.getFactId(), fact2.getSupersedesFactId());

            // Explain also surfaces stale ReadSet
            BlockerExplanation exp = gw.explain("task:" + task.getTaskId());
            Assert.assertTrue(collectCodes(exp.getRoot()).contains("STALE_READ_SET"));

            try {
                gw.submitTask(task.getTaskId(), exec.getExecutionId(), HarnessSubmissionSpec.builder()
                        .completionClaim("done")
                        .evidenceIds(Collections.singletonList(ev.getEvidenceId()))
                        .build(), agent);
                Assert.fail("STALE_READ_SET expected when read fact superseded");
            } catch (HarnessConflictException expected) {
                Assert.assertEquals(KnowledgeLedgerService.ERROR_STALE_READ_SET, expected.getErrorCode());
            }

            // Reconfirm ReadSet clears fact staleness
            gw.reconfirmReadSet(exec.getExecutionId(), "acknowledged 15s timeout", agent);

            // Amend read Decision -> submit MUST fail with STALE_READ_SET until reconfirmed again
            gw.amendDecision(dec1.getDecisionId(), HarnessDecisionSpec.builder()
                    .chosenOption("jittered-backoff")
                    .rationale("prevent thundering herd")
                    .build(), "added jitter", human);

            try {
                gw.submitTask(task.getTaskId(), exec.getExecutionId(), HarnessSubmissionSpec.builder()
                        .completionClaim("done")
                        .evidenceIds(Collections.singletonList(ev.getEvidenceId()))
                        .build(), agent);
                Assert.fail("STALE_READ_SET expected when read decision amended");
            } catch (HarnessConflictException expected) {
                Assert.assertEquals(KnowledgeLedgerService.ERROR_STALE_READ_SET, expected.getErrorCode());
            }

            gw.reconfirmReadSet(exec.getExecutionId(), "adopted jittered backoff", agent);

            gw.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                    .executionId(claimedRs.getExecutionId())
                    .leaseId(claimedRs.getLeaseId())
                    .fencingToken(claimedRs.getFencingToken())
                    .status(ExecutionStatus.SUCCEEDED)
                    .outputText("ready for witness")
                    .build());

            // Explain shows MISSING_WITNESS_GATE_RUN once submitted without Witness run
            SubmissionRecord sub = gw.submitTask(task.getTaskId(), exec.getExecutionId(), HarnessSubmissionSpec.builder()
                    .completionClaim("done")
                    .evidenceIds(Collections.singletonList(ev.getEvidenceId()))
                    .build(), agent);
            bindPassAcceptance(gw, task.getTaskId(), exec.getExecutionId(), sub.getSubmissionId(), human);
            BlockerExplanation missingWitness = gw.explain("task:" + task.getTaskId());
            Assert.assertTrue(collectCodes(missingWitness.getRoot()).contains("MISSING_WITNESS_GATE_RUN"));

            // Witness failing run records exitCode/digest and blocks completion
            GateRunRecord failedRun = gw.witnessRerun(WitnessRunSpec.builder()
                    .taskId(task.getTaskId())
                    .executionId(exec.getExecutionId())
                    .submissionId(sub.getSubmissionId())
                    .gateName("smoke-suite")
                    .command("mvn test -Dtest=SmokeTest")
                    .build(),
                    req -> WitnessResult.builder().exitCode(1).passed(false).output("1 failure").build(),
                    HarnessActor.worker("host-witness"));
            Assert.assertFalse(failedRun.isPassed());
            Assert.assertEquals(1, failedRun.getExitCode());
            Assert.assertNotNull(failedRun.getDigest());

            BlockerExplanation failedWitness = gw.explain("task:" + task.getTaskId());
            Assert.assertTrue(collectCodes(failedWitness.getRoot()).contains("FAILED_WITNESS_GATE_RUN"));

            try {
                gw.completeTask(task.getTaskId(), sub.getSubmissionId(), human);
                Assert.fail("failing witness gate must block completion");
            } catch (HarnessConflictException expected) {
                Assert.assertTrue(expected.getMessage().contains("smoke-suite"));
            }

            // Passing witness rerun unblocks completion
            GateRunRecord passedRun = gw.witnessRerun(WitnessRunSpec.builder()
                    .taskId(task.getTaskId())
                    .executionId(exec.getExecutionId())
                    .submissionId(sub.getSubmissionId())
                    .gateName("smoke-suite")
                    .command("mvn test -Dtest=SmokeTest")
                    .build(),
                    req -> WitnessResult.builder().exitCode(0).passed(true).output("all green").durationMillis(42L).build(),
                    HarnessActor.worker("host-witness"));
            Assert.assertTrue(passedRun.isPassed());
            Assert.assertEquals(42L, passedRun.getDurationMillis());
            Assert.assertNotNull(passedRun.getEvidenceId());

            TaskRecord done = gw.completeTask(task.getTaskId(), sub.getSubmissionId(), human);
            Assert.assertEquals(TaskStatus.DONE, done.getStatus());
        } finally {
            gw.close();
        }
    }

    @Test
    public void falsifiableDecisionReckonAutoRevokesPromotedRules() {
        HarnessCommandGateway gw = fileGateway("falsifiable-reckon", strictGovContract());
        try {
            HarnessActor agent = HarnessActor.agent("arch-agent");
            HarnessActor owner = HarnessActor.owner("owner-1");

            // Missing rejected or claims must fail under requiresFalsifiableDecisions=true
            try {
                gw.proposeDecision(HarnessDecisionSpec.builder()
                        .decisionId("dec-bad")
                        .question("Use cache?")
                        .chosenOption("redis")
                        .build(), agent);
                Assert.fail("expected rejection for non-falsifiable decision");
            } catch (HarnessValidationException expected) {
                Assert.assertTrue(expected.getMessage().contains("rejected"));
            }

            DecisionRecord decision = gw.proposeDecision(HarnessDecisionSpec.builder()
                    .decisionId("dec-falsifiable")
                    .scopeKey("perf")
                    .question("Enable connection pooling?")
                    .chosen("hikari")
                    .rejected(Arrays.asList("unpooled-jdbc", "c3p0"))
                    .claims(Collections.singletonList(DecisionClaim.builder()
                            .claimId("c-latency")
                            .statement("p99 latency stays < 50ms")
                            .falsifiableBy("load-test p99 >= 50ms")
                            .verificationMetric("p99_ms")
                            .build()))
                    .rationale("lower tail latency")
                    .build(), agent);
            Assert.assertEquals("hikari", decision.getChosen());
            Assert.assertEquals(2, decision.getRejected().size());

            gw.resolveDecision(decision.getDecisionId(), DecisionStatus.ACCEPTED, "accepted for prod", owner);
            HarnessRule rule = gw.promoteLesson(decision.getDecisionId(), HarnessRuleSpec.builder()
                    .ruleId("rule-hikari")
                    .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK)
                    .payload("pool-verify")
                    .build(), owner);
            Assert.assertTrue(rule.isEnabled());

            // Reckon falsified -> Decision FALSIFIED + promoted rule auto-revoked!
            DecisionReckonResult reckoned = gw.reckonDecision(decision.getDecisionId(),
                    Collections.singletonList(DecisionFulfillment.builder()
                            .claimId("c-latency")
                            .satisfied(false)
                            .notes("p99 hit 120ms under burst")
                            .observer(owner)
                            .build()),
                    true,
                    "Load test falsified latency claim",
                    owner);
            Assert.assertEquals(ReckonStatus.FALSIFIED, reckoned.getDecision().getReckonStatus());
            Assert.assertEquals(DecisionStatus.FALSIFIED, reckoned.getDecision().getStatus());
            Assert.assertEquals(1, reckoned.getRevokedRuleIds().size());
            Assert.assertEquals(rule.getRuleId(), reckoned.getRevokedRuleIds().get(0));
            Assert.assertFalse(gw.getState().getLearnedRules().get(rule.getRuleId()).isEnabled());
        } finally {
            gw.close();
        }
    }

    @Test
    public void liveAgentHarnessSafePointSteerAndPortableHandoffTargetModel() {
        HarnessCommandGateway gw = fileGateway("steer-handoff", HarnessContract.builder().build());
        try {
            HarnessActor owner = HarnessActor.owner("owner-steer");
            TaskRecord task = gw.createTask(HarnessTaskSpec.builder()
                    .taskId("task-steer")
                    .scopeKey("scope-sh")
                    .title("Steered multi-step execution")
                    .build(), owner);

            final ExecutionRecord preExec = gw.createExecution(HarnessExecutionSpec.builder()
                    .executionId("exec-steer")
                    .taskId(task.getTaskId())
                    .scopeKey("scope-sh")
                    .sessionId("sess-steer")
                    .runId("run-steer")
                    .inputSummary("step one then two")
                    .build());

            // CAS version mismatch check on steer
            try {
                gw.steer(preExec.getExecutionId(), task.getTaskId(), preExec.getVersion() + 99L,
                        "bad version", owner);
                Assert.fail("mismatched expectedVersion must throw HarnessConflictException");
            } catch (HarnessConflictException expected) {
                Assert.assertNotNull(expected.getMessage());
            }

            // Live AgentHarness multi-step run: step 1 tool execution queues a safe-point steer;
            // step 2 ModelRequestHook injects [HARNESS_STEER] before calling model.
            final List<String> seenInstructions = new ArrayList<String>();
            final AtomicBoolean steeredOnTool = new AtomicBoolean(false);

            AgentModelClient twoStepModel = new AgentModelClient() {
                private int step = 0;

                @Override
                public AgentModelResult create(AgentPrompt prompt) {
                    seenInstructions.add(prompt.getInstructions());
                    if (step++ == 0) {
                        return AgentModelResult.builder()
                                .toolCalls(Collections.singletonList(AgentToolCall.builder()
                                        .callId("call-ping")
                                        .name("ping_tool")
                                        .arguments("{}")
                                        .type("function_call")
                                        .build()))
                                .memoryItems(new ArrayList<Object>())
                                .build();
                    }
                    return AgentModelResult.builder()
                            .outputText("steered final answer")
                            .toolCalls(new ArrayList<AgentToolCall>())
                            .memoryItems(new ArrayList<Object>())
                            .build();
                }

                @Override
                public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                    return create(prompt);
                }
            };

            ToolExecutor pingExecutor = call -> {
                if (steeredOnTool.compareAndSet(false, true)) {
                    ExecutionRecord current = gw.getExecution(preExec.getExecutionId());
                    gw.steer(current.getExecutionId(), task.getTaskId(), current.getVersion(),
                            "focus strictly on billing checks", owner);
                }
                return "{\"ok\":true}";
            };

            Tool pingTool = new Tool("function", new Tool.Function(
                    "ping_tool", "ping", new Tool.Function.Parameter("object",
                    Collections.<String, Tool.Function.Property>emptyMap(), Collections.<String>emptyList())));

            Agent agent = Agents.react()
                    .runtime(new ReActRuntime())
                    .modelClient(twoStepModel)
                    .model("gpt-4o-mini")
                    .toolRegistry(new StaticToolRegistry(Collections.<Object>singletonList(pingTool)))
                    .toolExecutor(pingExecutor)
                    .options(AgentOptions.builder().maxSteps(4).build())
                    .build();

            AgentHarness harness = AgentHarness.builder()
                    .agent(agent)
                    .store(gw.getStore())
                    .autoResume(false)
                    .build();

            HarnessRunResult result = harness.run(HarnessRunRequest.builder()
                    .taskId(task.getTaskId())
                    .executionId(preExec.getExecutionId())
                    .sessionId("sess-steer")
                    .scopeKey("scope-sh")
                    .input("run step 1")
                    .build());

            Assert.assertEquals(HarnessRunStatus.COMPLETED, result.getStatus());
            Assert.assertEquals("steered final answer", result.getOutputText());
            Assert.assertEquals(2, seenInstructions.size());
            Assert.assertTrue(seenInstructions.get(1).contains("[HARNESS_STEER]: focus strictly on billing checks"));
            List<SteerRecord> steers = gw.listSteers(preExec.getExecutionId());
            Assert.assertEquals(1, steers.size());
            Assert.assertEquals(SteerStatus.APPLIED, steers.get(0).getStatus());
            Assert.assertEquals(1, steers.get(0).getAppliedStep());
            harness.close();

            // Portable Handoff: export -> wrong-node claim rejected -> revoke/TTL check -> targetModel switch
            ExecutionRecord handoffExec = gw.createExecution(HarnessExecutionSpec.builder()
                    .executionId("exec-ho")
                    .taskId(task.getTaskId())
                    .scopeKey("scope-sh")
                    .sessionId("sess-ho")
                    .runId("run-ho")
                    .build());
            gw.claimExecution(handoffExec.getExecutionId(), "worker-a", 60_000L);
            HandoffRecord exported = gw.exportHandoff(
                    handoffExec.getExecutionId(), "node-b", "claude-3-7-sonnet", "offload to gpu node", 60_000L, owner);
            Assert.assertEquals(HandoffStatus.EXPORTED, exported.getStatus());
            Assert.assertEquals("claude-3-7-sonnet", exported.getTargetModel());
            Assert.assertEquals(ExecutionStatus.READY, gw.getExecution(handoffExec.getExecutionId()).getStatus());

            // Affinity mismatch rejected
            try {
                gw.claimHandoff(exported.getHandoffId(), "node-wrong", "w-wrong", 60_000L, owner);
                Assert.fail("claim on wrong targetNodeId must fail");
            } catch (HarnessConflictException expected) {
                Assert.assertEquals(ExecutionCoordinationService.ERROR_HANDOFF_AFFINITY_MISMATCH, expected.getErrorCode());
            }

            // Revoke blocks subsequent claim
            HandoffRecord revokedCase = gw.exportHandoff(
                    handoffExec.getExecutionId(), "node-c", "gpt-4o", "test revoke", 60_000L, owner);
            HandoffRecord revoked = gw.revokeHandoff(revokedCase.getHandoffId(), "superseded", owner);
            Assert.assertEquals(HandoffStatus.REVOKED, revoked.getStatus());

            // TTL expiry check deterministically without Thread.sleep
            final HandoffRecord ttlExport = gw.exportHandoff(
                    handoffExec.getExecutionId(), "node-b", "claude-3-7-sonnet", "test ttl", 60_000L, owner);
            gw.getStore().update(state -> {
                HandoffRecord r = state.getHandoffs().get(ttlExport.getHandoffId());
                r.setExpiresAtEpochMs(System.currentTimeMillis() - 5_000L);
                return state;
            });
            try {
                gw.claimHandoff(ttlExport.getHandoffId(), "node-b", "w-b", 60_000L, owner);
                Assert.fail("expired handoff must fail");
            } catch (HarnessConflictException expected) {
                Assert.assertEquals(ExecutionCoordinationService.ERROR_HANDOFF_EXPIRED, expected.getErrorCode());
            }

            // Fresh export claimed by targetNode transitions Execution.targetModel -> Agent context honors model
            HandoffRecord activeHandoff = gw.exportHandoff(
                    handoffExec.getExecutionId(), "node-b", "claude-3-7-sonnet", "live handoff", 60_000L, owner);
            HandoffRecord claimed = gw.claimHandoff(activeHandoff.getHandoffId(), "node-b", "worker-b", 0L, owner);
            Assert.assertEquals(HandoffStatus.CLAIMED, claimed.getStatus());
            ExecutionRecord claimedExec = gw.getExecution(handoffExec.getExecutionId());
            Assert.assertEquals("claude-3-7-sonnet", claimedExec.getTargetModel());
            Assert.assertEquals("node-b", claimedExec.getAssignedNodeId());
        } finally {
            gw.close();
        }
    }

    @Test
    public void blueprintsSquadBudgetTypedSchemasTodoWorkAndSchedules() {
        HarnessCommandGateway gw = jdbcGateway("gov_misc_" + System.nanoTime(), HarnessContract.builder().build());
        try {
            HarnessActor owner = HarnessActor.owner("owner-misc");
            HarnessActor agent = HarnessActor.agent("planner-agent");

            // 1) Versioned AgentBlueprint + diff + rollback
            AgentBlueprintRecord v1 = gw.registerAgentBlueprint(AgentBlueprintSpec.builder()
                    .scopeKey("team-a")
                    .name("coder-blueprint")
                    .model("gpt-4o-mini")
                    .systemPrompt("Write clean Java")
                    .allowedTools(Arrays.asList("read_file", "edit_file"))
                    .capabilities(Collections.singletonList("java8"))
                    .build(), owner);
            AgentBlueprintRecord v2 = gw.registerAgentBlueprint(AgentBlueprintSpec.builder()
                    .scopeKey("team-a")
                    .name("coder-blueprint")
                    .model("gpt-4o")
                    .systemPrompt("Write clean Java and verify")
                    .allowedTools(Arrays.asList("read_file", "write_file"))
                    .capabilities(Arrays.asList("java8", "maven"))
                    .build(), owner);
            Assert.assertEquals(1L, v1.getVersion());
            Assert.assertEquals(2L, v2.getVersion());
            Assert.assertTrue(v2.isActive());

            AgentBlueprintDiff diff = gw.diffAgentBlueprints("coder-blueprint", 1L, 2L);
            Assert.assertTrue(diff.isModelChanged());
            Assert.assertTrue(diff.isSystemPromptChanged());
            Assert.assertEquals(Collections.singletonList("write_file"), diff.getAddedTools());
            Assert.assertEquals(Collections.singletonList("edit_file"), diff.getRemovedTools());

            AgentBlueprintRecord v3 = gw.rollbackAgentBlueprint("coder-blueprint", 1L, "cost regression", owner);
            Assert.assertEquals(3L, v3.getVersion());
            Assert.assertEquals("gpt-4o-mini", v3.getModel());
            Assert.assertEquals("Write clean Java", gw.getActiveAgentBlueprint("coder-blueprint").getSystemPrompt());

            // 2) SquadRun with leaderTurnBudget enforcement & synthesis
            SquadRunRecord squad = gw.startSquadRun(SquadRunSpec.builder()
                    .scopeKey("team-a")
                    .leaderId("leader-1")
                    .objective("Audit storage layer")
                    .leaderTurnBudget(2)
                    .memberIds(Arrays.asList("m1", "m2"))
                    .build(), owner);
            SquadRunRecord t1 = gw.recordSquadLeaderTurn(squad.getSquadRunId(), "delegate", "m1",
                    "Inspect FileStore", "clean", agent);
            Assert.assertEquals(SquadRunStatus.RUNNING, t1.getStatus());
            SquadRunRecord t2 = gw.recordSquadLeaderTurn(squad.getSquadRunId(), "delegate", "m2",
                    "Inspect JdbcStore", "index gap", agent);
            Assert.assertEquals(SquadRunStatus.BUDGET_EXHAUSTED, t2.getStatus());
            Assert.assertNotNull(t2.getSynthesisReport());
            Assert.assertTrue(t2.getSynthesisReport().contains("Forced synthesis for squad"));
            Assert.assertTrue(t2.getSynthesisReport().contains("index gap"));

            try {
                gw.recordSquadLeaderTurn(squad.getSquadRunId(), "delegate", "m1", "extra turn", null, agent);
                Assert.fail("3rd turn beyond budget must throw");
            } catch (HarnessConflictException expected) {
                Assert.assertEquals(ExecutionCoordinationService.ERROR_SQUAD_BUDGET_EXHAUSTED, expected.getErrorCode());
            }

            // 3) VerticalKind<T> & FactType<T>
            VerticalKind<SampleBuildPayload> buildKind = VerticalKind.of(
                    "maven_build_report", 1, SampleBuildPayload.class,
                    "Maven build outcome", p -> p.durationMs >= 0);
            gw.registerVerticalKind(buildKind);
            try {
                gw.registerVerticalKind(VerticalKind.of("maven_build_report", 1, String.class));
                Assert.fail("mutating existing version schema must fail");
            } catch (HarnessConflictException expected) {
                Assert.assertNotNull(expected.getMessage());
            }

            Set<String> reqFields = new LinkedHashSet<String>(Arrays.asList("module", "passed"));
            FactType<Map> lintFactType = FactType.of("spotbugs_metric", 1, Map.class, reqFields, "SpotBugs metric");
            gw.registerFactType(lintFactType);

            // 4) Todo -> Task -> Work promotion & completion rollup
            TodoRecord todo = gw.createTodo("todo-1", "team-a", "Add SpotBugs check", "enforce 0 bugs", null, agent);
            WorkRecord work = gw.createWork("work-1", "team-a", "Quality Milestone", "Zero violations", null, owner);
            TaskRecord promotedTask = gw.promoteTodoToTask(todo.getTodoId(), HarnessTaskSpec.builder()
                    .taskId("task-from-todo")
                    .verticalKind(buildKind.schemaKey())
                    .build(), owner);
            gw.attachTaskToWork(work.getWorkId(), promotedTask.getTaskId(), owner);

            TypedArtifactRecord artifact = gw.recordTypedArtifact(buildKind, promotedTask.getTaskId(), null,
                    "team-a", new SampleBuildPayload("ai4j-harness", true, 120L), agent);
            SampleBuildPayload readBack = gw.readTypedArtifact(buildKind, artifact.getArtifactId());
            Assert.assertEquals("ai4j-harness", readBack.module);

            Map<String, Object> metricMap = new LinkedHashMap<String, Object>();
            metricMap.put("module", "ai4j-harness");
            metricMap.put("passed", Boolean.TRUE);
            FactRecord typedFact = gw.recordTypedFact(lintFactType, promotedTask.getTaskId(), "team-a",
                    "SpotBugs passed", metricMap, "spotbugs", Collections.<String>emptyList(), agent);
            Map decodedFact = gw.readTypedFactPayload(lintFactType, typedFact.getFactId());
            Assert.assertEquals(Boolean.TRUE, decodedFact.get("passed"));

            ExecutionRecord workExec = gw.createExecution(HarnessExecutionSpec.builder()
                    .executionId("exec-work-1")
                    .taskId(promotedTask.getTaskId())
                    .scopeKey("team-a")
                    .build());
            ExecutionRecord claimedWork = gw.claimExecution(workExec.getExecutionId(), "worker-w1", 60_000L);
            EvidenceRecord workEv = gw.recordEvidence(HarnessEvidenceSpec.builder()
                    .scopeKey("team-a").taskId(promotedTask.getTaskId())
                    .executionId(workExec.getExecutionId())
                    .kind("ci").location("gh").summary("ok").build(), agent);
            gw.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                    .executionId(claimedWork.getExecutionId())
                    .leaseId(claimedWork.getLeaseId())
                    .fencingToken(claimedWork.getFencingToken())
                    .status(ExecutionStatus.SUCCEEDED)
                    .outputText("done")
                    .build());
            SubmissionRecord workSub = gw.submitTask(promotedTask.getTaskId(), workExec.getExecutionId(),
                    HarnessSubmissionSpec.builder()
                            .completionClaim("done")
                            .evidenceIds(Collections.singletonList(workEv.getEvidenceId()))
                            .build(), agent);
            bindPassAcceptance(gw, promotedTask.getTaskId(), workExec.getExecutionId(), workSub.getSubmissionId(), owner);
            gw.reviewSubmission(workSub.getSubmissionId(), ReviewVerdict.APPROVED, "clean", "approved", owner);
            gw.completeTask(promotedTask.getTaskId(), workSub.getSubmissionId(), owner);

            WorkProgress progress = gw.getWorkProgress(work.getWorkId());
            Assert.assertTrue(progress.isAllDone());
            Assert.assertEquals(WorkStatus.DONE, progress.getStatus());

            // 5) Schedule(DETECT | REMEDIATE) idempotency & DETECT read-only guard
            ScheduleRecord detectSched = gw.createSchedule(ScheduleSpec.builder()
                    .scheduleId("sched-detect")
                    .scopeKey("team-a")
                    .name("Nightly Drift Detector")
                    .mode(ScheduleMode.DETECT)
                    .cronExpression("0 2 * * *")
                    .build(), owner);

            ScheduleRunRecord detectFail = gw.triggerSchedule(detectSched.getScheduleId(), "2026-11-23T02:00Z", "node-1",
                    ctx -> {
                        ctx.createRemediationTask(HarnessTaskSpec.builder().title("Illegal mutation").build());
                        return "unreachable";
                    }, agent);
            Assert.assertEquals(ScheduleRunStatus.FAILED, detectFail.getStatus());
            Assert.assertTrue(detectFail.getError().contains("read-only"));

            ScheduleRunRecord detectOk = gw.triggerSchedule(detectSched.getScheduleId(), "2026-11-23T03:00Z", "node-1",
                    ctx -> {
                        ctx.recordFact(HarnessFactSpec.builder().statement("Drift observed").source("detector").build());
                        ctx.emitAlert("drift detected");
                        return "observed";
                    }, agent);
            Assert.assertEquals(ScheduleRunStatus.COMPLETED, detectOk.getStatus());
            // Re-running the same tickKey is idempotent and does not execute twice
            ScheduleRunRecord duplicateTick = gw.triggerSchedule(detectSched.getScheduleId(), "2026-11-23T03:00Z", "node-2",
                    ctx -> {
                        throw new AssertionError("duplicate tickKey must be idempotent");
                    }, agent);
            Assert.assertEquals("node-1", duplicateTick.getClaimedByNodeId());

            ScheduleRecord remSched = gw.createSchedule(ScheduleSpec.builder()
                    .scheduleId("sched-remediate")
                    .scopeKey("team-a")
                    .name("Auto Remediator")
                    .mode(ScheduleMode.REMEDIATE)
                    .build(), owner);
            ScheduleRunRecord remRun = gw.triggerSchedule(remSched.getScheduleId(), "2026-11-23T04:00Z", "node-1",
                    ctx -> {
                        ctx.createRemediationTask(HarnessTaskSpec.builder().title("Fix drift").build());
                        return "remediation queued";
                    }, owner);
            Assert.assertEquals(ScheduleRunStatus.COMPLETED, remRun.getStatus());
            Assert.assertNotNull(remRun.getRemediationTaskId());

            // Verify CQRS projection & rebuild count on JdbcHarnessStore works across 2.0 entities
            int rebuilt = gw.rebuildEntitiesFromEvents();
            Assert.assertTrue(rebuilt > 0);
        } finally {
            gw.close();
        }
    }

    private static void bindPassAcceptance(HarnessCommandGateway gw,
                                           String taskId,
                                           String executionId,
                                           String submissionId,
                                           HarnessActor actor) {
        HarnessAcceptanceEvaluation eval = HarnessAcceptanceCoordinator.evaluate(gw,
                context -> HarnessAcceptanceResult.builder()
                        .checkId("default-acceptance")
                        .status(HarnessAcceptanceStatus.PASS)
                        .summary("acceptance passed")
                        .build(),
                HarnessAcceptanceContext.builder()
                        .taskId(taskId)
                        .executionId(executionId)
                        .build(),
                actor);
        gw.bindAcceptanceToSubmission(eval.getAcceptance().getAcceptanceId(), submissionId);
    }

    private static Set<String> collectCodes(BlockerNode root) {
        Set<String> result = new LinkedHashSet<String>();
        if (root == null) {
            return result;
        }
        if (root.getCode() != null) {
            result.add(root.getCode());
        }
        if (root.getChildren() != null) {
            for (BlockerNode child : root.getChildren()) {
                result.addAll(collectCodes(child));
            }
        }
        return result;
    }

    private static Set<String> collectEntityTypes(BlockerNode root) {
        Set<String> result = new LinkedHashSet<String>();
        if (root == null) {
            return result;
        }
        if (root.getEntityType() != null) {
            result.add(root.getEntityType());
        }
        if (root.getChildren() != null) {
            for (BlockerNode child : root.getChildren()) {
                result.addAll(collectEntityTypes(child));
            }
        }
        return result;
    }

    public static final class SampleBuildPayload {
        public String module;
        public boolean passed;
        public long durationMs;

        public SampleBuildPayload() {
        }

        public SampleBuildPayload(String module, boolean passed, long durationMs) {
            this.module = module;
            this.passed = passed;
            this.durationMs = durationMs;
        }
    }
}
