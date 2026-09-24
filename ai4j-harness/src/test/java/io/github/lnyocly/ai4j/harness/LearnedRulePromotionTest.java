package io.github.lnyocly.ai4j.harness;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Covers the LEARN reflow: an ACCEPTED decision promoted into a durable
 * {@link HarnessRule} that additively constrains future executions.
 */
public class LearnedRulePromotionTest {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("ai4j-harness-learn-");
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
                            // Best effort cleanup of the test directory.
                        }
                    });
        }
    }

    private HarnessCommandGateway gateway(HarnessContract contract) {
        return new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder().directory(directory).build()),
                contract, HarnessActor.agent("agent"));
    }

    private HarnessCommandGateway lenientGateway() {
        return gateway(HarnessContract.builder()
                .requiresApprovedReview(false)
                .requiresCompletionEvidence(false)
                .build());
    }

    private ExecutionRecord successfulExecution(HarnessCommandGateway gateway, String taskId, String scopeKey) {
        ExecutionRecord created = gateway.createExecution(HarnessExecutionSpec.builder()
                .taskId(taskId).scopeKey(scopeKey).build());
        ExecutionRecord claimed = gateway.claimExecution(created.getExecutionId(), "worker", 10000L);
        return gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                .executionId(created.getExecutionId())
                .leaseId(claimed.getLeaseId()).fencingToken(claimed.getFencingToken())
                .status(ExecutionStatus.SUCCEEDED).outputText("ok").build());
    }

    private DecisionRecord acceptedDecision(HarnessCommandGateway gateway, String scopeKey) {
        DecisionRecord proposed = gateway.proposeDecision(HarnessDecisionSpec.builder()
                .scopeKey(scopeKey)
                .question("Should refunds check dispute status first?")
                .chosenOption("yes")
                .build(), HarnessActor.agent("agent"));
        return gateway.resolveDecision(proposed.getDecisionId(), DecisionStatus.ACCEPTED,
                "chargeback incident", HarnessActor.human("ops-lead"));
    }

    @Test
    public void agentCanProposeButNeverPromote() {
        HarnessCommandGateway gateway = lenientGateway();
        try {
            DecisionRecord decision = acceptedDecision(gateway, "s");
            HarnessRuleSpec spec = HarnessRuleSpec.builder()
                    .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("dispute-check").build();
            try {
                gateway.promoteLesson(decision.getDecisionId(), spec, HarnessActor.agent("agent"));
                Assert.fail("an agent must not promote its own lessons");
            } catch (HarnessValidationException expected) {
                Assert.assertTrue(expected.getMessage().contains("not allowed"));
            }
        } finally { gateway.close(); }
    }

    @Test
    public void systemPromotionIsDeniedByDefaultAndOptedIn() {
        HarnessCommandGateway gateway = lenientGateway();
        try {
            DecisionRecord decision = acceptedDecision(gateway, "s");
            try {
                gateway.promoteLesson(decision.getDecisionId(), HarnessRuleSpec.builder()
                                .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("c").build(),
                        HarnessActor.system("cron"));
                Assert.fail("system promotion is off by default");
            } catch (HarnessValidationException expected) {
                // expected
            }
        } finally { gateway.close(); }

        HarnessCommandGateway open = gateway(HarnessContract.builder()
                .requiresApprovedReview(false).requiresCompletionEvidence(false)
                .allowSystemPromotion(true).build());
        try {
            DecisionRecord decision = acceptedDecision(open, "s");
            HarnessRule rule = open.promoteLesson(decision.getDecisionId(), HarnessRuleSpec.builder()
                            .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("c").build(),
                    HarnessActor.system("cron"));
            Assert.assertTrue(rule.isEnabled());
            Assert.assertEquals("system", rule.getPromotedBy().getKind());
        } finally { open.close(); }
    }

    @Test
    public void promoteRequiresAcceptedDecision() {
        HarnessCommandGateway gateway = lenientGateway();
        try {
            DecisionRecord proposed = gateway.proposeDecision(HarnessDecisionSpec.builder()
                    .question("q").chosenOption("a").build(), HarnessActor.agent("agent"));
            try {
                gateway.promoteLesson(proposed.getDecisionId(), HarnessRuleSpec.builder()
                                .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("c").build(),
                        HarnessActor.human("ops"));
                Assert.fail("PROPOSED decisions cannot be promoted");
            } catch (HarnessConflictException expected) {
                Assert.assertTrue(expected.getMessage().contains("ACCEPTED"));
            }
            try {
                gateway.promoteLesson("missing", HarnessRuleSpec.builder()
                                .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("c").build(),
                        HarnessActor.human("ops"));
                Assert.fail("unknown decision must be rejected");
            } catch (HarnessValidationException expected) {
                Assert.assertTrue(expected.getMessage().contains("not found"));
            }
        } finally { gateway.close(); }
    }

    @Test
    public void promotionDedupesIdenticalEnabledRule() {
        HarnessCommandGateway gateway = lenientGateway();
        try {
            DecisionRecord decision = acceptedDecision(gateway, "s");
            HarnessRuleSpec spec = HarnessRuleSpec.builder()
                    .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("dispute-check").build();
            HarnessRule first = gateway.promoteLesson(decision.getDecisionId(), spec, HarnessActor.human("ops"));
            HarnessRule again = gateway.promoteLesson(decision.getDecisionId(), spec, HarnessActor.human("ops"));
            Assert.assertEquals(first.getRuleId(), again.getRuleId());
            Assert.assertEquals(1, gateway.listLearnedRulesInScope("s").size());
            Assert.assertEquals(decision.getDecisionId(), first.getDecisionId());
            Assert.assertEquals("s", first.getScopeKey());
        } finally { gateway.close(); }
    }

    @Test
    public void learnedAcceptanceCheckBlocksCompletionUntilPassed() {
        HarnessCommandGateway gateway = lenientGateway();
        try {
            DecisionRecord decision = acceptedDecision(gateway, "s");
            gateway.promoteLesson(decision.getDecisionId(), HarnessRuleSpec.builder()
                            .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("dispute-check").build(),
                    HarnessActor.human("ops"));

            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().scopeKey("s").title("refund").build());
            ExecutionRecord execution = successfulExecution(gateway, task.getTaskId(), "s");
            SubmissionRecord submission = gateway.submitTask(task.getTaskId(), execution.getExecutionId(),
                    HarnessSubmissionSpec.builder().completionClaim("refunded").build());
            try {
                gateway.completeTask(task.getTaskId(), submission.getSubmissionId(), HarnessActor.human("reviewer"));
                Assert.fail("learned check must block completion");
            } catch (HarnessConflictException expected) {
                Assert.assertTrue(expected.getMessage().contains("dispute-check"));
            }

            gateway.recordAcceptance(AcceptanceRecord.builder()
                    .acceptanceId("acc-1").taskId(task.getTaskId())
                    .executionId(execution.getExecutionId())
                    .submissionId(submission.getSubmissionId())
                    .checkId("dispute-check").status(HarnessAcceptanceStatus.PASS).build());
            Assert.assertEquals(TaskStatus.DONE, gateway.completeTask(task.getTaskId(),
                    submission.getSubmissionId(), HarnessActor.human("reviewer")).getStatus());
        } finally { gateway.close(); }
    }

    @Test
    public void learnedRulesTightenButNeverLoosenTheContract() {
        HarnessContract contract = HarnessContract.builder()
                .requiresApprovedReview(false)
                .requiresCompletionEvidence(false)
                .requiredAcceptanceCheck("static-check")
                .build();
        HarnessCommandGateway gateway = gateway(contract);
        try {
            DecisionRecord decision = acceptedDecision(gateway, null);
            gateway.promoteLesson(decision.getDecisionId(), HarnessRuleSpec.builder()
                            .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("learned-check").build(),
                    HarnessActor.human("ops"));

            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().title("t").build());
            ExecutionRecord execution = successfulExecution(gateway, task.getTaskId(), null);
            SubmissionRecord submission = gateway.submitTask(task.getTaskId(), execution.getExecutionId(),
                    HarnessSubmissionSpec.builder().completionClaim("done").build());
            // Only the learned check is satisfied: the static one still blocks.
            gateway.recordAcceptance(AcceptanceRecord.builder()
                    .acceptanceId("acc-learned").taskId(task.getTaskId())
                    .executionId(execution.getExecutionId())
                    .submissionId(submission.getSubmissionId())
                    .checkId("learned-check").status(HarnessAcceptanceStatus.PASS).build());
            try {
                gateway.completeTask(task.getTaskId(), submission.getSubmissionId(), HarnessActor.human("reviewer"));
                Assert.fail("the static check must still be enforced");
            } catch (HarnessConflictException expected) {
                Assert.assertTrue(expected.getMessage().contains("static-check"));
            }
        } finally { gateway.close(); }
    }

    @Test
    public void presetScopedRuleOnlyAppliesToMatchingTasks() {
        HarnessCommandGateway gateway = lenientGateway();
        try {
            DecisionRecord decision = acceptedDecision(gateway, "s");
            gateway.promoteLesson(decision.getDecisionId(), HarnessRuleSpec.builder()
                            .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("dispute-check")
                            .preset("refund").build(),
                    HarnessActor.human("ops"));

            // An FAQ task in the same scope is unaffected.
            TaskRecord faq = gateway.createTask(HarnessTaskSpec.builder().scopeKey("s").title("faq").build());
            ExecutionRecord faqExec = successfulExecution(gateway, faq.getTaskId(), "s");
            SubmissionRecord faqSub = gateway.submitTask(faq.getTaskId(), faqExec.getExecutionId(),
                    HarnessSubmissionSpec.builder().completionClaim("answered").build());
            Assert.assertEquals(TaskStatus.DONE, gateway.completeTask(faq.getTaskId(),
                    faqSub.getSubmissionId(), HarnessActor.human("reviewer")).getStatus());

            // A refund task in the same scope is blocked by the rule.
            Map<String, Object> metadata = new LinkedHashMap<String, Object>();
            metadata.put(HarnessContract.PRESET_METADATA_KEY, "refund");
            TaskRecord refund = gateway.createTask(HarnessTaskSpec.builder().scopeKey("s")
                    .title("refund").metadata(metadata).build());
            ExecutionRecord refundExec = successfulExecution(gateway, refund.getTaskId(), "s");
            SubmissionRecord refundSub = gateway.submitTask(refund.getTaskId(), refundExec.getExecutionId(),
                    HarnessSubmissionSpec.builder().completionClaim("refunded").build());
            try {
                gateway.completeTask(refund.getTaskId(), refundSub.getSubmissionId(), HarnessActor.human("reviewer"));
                Assert.fail("preset-scoped rule must block the refund task");
            } catch (HarnessConflictException expected) {
                Assert.assertTrue(expected.getMessage().contains("dispute-check"));
            }
        } finally { gateway.close(); }
    }

    @Test
    public void learnedToolRulesMergeAtGateway() {
        HarnessCommandGateway gateway = lenientGateway();
        try {
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().scopeKey("s").title("t").build());
            ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                    .taskId(task.getTaskId()).scopeKey("s").build());
            Assert.assertFalse(gateway.toolRequiresApproval(execution.getExecutionId(), "refund"));
            Assert.assertFalse(gateway.toolRequiresTask(execution.getExecutionId(), "refund"));

            DecisionRecord decision = acceptedDecision(gateway, "s");
            gateway.promoteLesson(decision.getDecisionId(), HarnessRuleSpec.builder()
                            .kind(RuleKind.REQUIRE_APPROVAL_FOR_TOOL).payload("refund").build(),
                    HarnessActor.human("ops"));
            gateway.promoteLesson(decision.getDecisionId(), HarnessRuleSpec.builder()
                            .kind(RuleKind.REQUIRE_TASK_FOR_TOOL).payload("chargeback").build(),
                    HarnessActor.human("ops"));

            Assert.assertTrue(gateway.toolRequiresApproval(execution.getExecutionId(), "refund"));
            Assert.assertTrue(gateway.toolRequiresTask(execution.getExecutionId(), "chargeback"));
            Assert.assertFalse(gateway.toolRequiresApproval(execution.getExecutionId(), "lookup"));

            // Revoking removes the rule from evaluation but keeps the record.
            HarnessRule rule = gateway.listLearnedRulesInScope("s").get(0);
            gateway.revokeLesson(rule.getRuleId(), HarnessActor.human("ops"));
            Assert.assertFalse(gateway.listLearnedRulesInScope("s").get(0).isEnabled());
            Assert.assertEquals("ops", gateway.listLearnedRulesInScope("s").get(0).getRevokedBy().getId());
        } finally { gateway.close(); }
    }

    @Test
    public void learnedRulesSurviveStoreRestart() {
        HarnessCommandGateway first = lenientGateway();
        DecisionRecord decision = acceptedDecision(first, "s");
        first.promoteLesson(decision.getDecisionId(), HarnessRuleSpec.builder()
                        .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("dispute-check").build(),
                HarnessActor.human("ops"));
        first.close();

        HarnessCommandGateway reopened = lenientGateway();
        try {
            List<HarnessRule> rules = reopened.listLearnedRulesInScope("s");
            Assert.assertEquals(1, rules.size());
            Assert.assertEquals("dispute-check", rules.get(0).getPayload());
            Assert.assertEquals(decision.getDecisionId(), rules.get(0).getDecisionId());
        } finally { reopened.close(); }
    }

    @Test
    public void learnedRulesRespectScopeVisibility() {
        HarnessCommandGateway gateway = lenientGateway();
        try {
            DecisionRecord inA = acceptedDecision(gateway, "scope-A");
            gateway.promoteLesson(inA.getDecisionId(), HarnessRuleSpec.builder()
                            .kind(RuleKind.REQUIRE_ACCEPTANCE_CHECK).payload("check-a").build(),
                    HarnessActor.human("ops"));
            Assert.assertEquals(1, gateway.listLearnedRulesInScope("scope-A").size());
            Assert.assertTrue(gateway.listLearnedRulesInScope("scope-B").isEmpty());
        } finally { gateway.close(); }
    }
}
