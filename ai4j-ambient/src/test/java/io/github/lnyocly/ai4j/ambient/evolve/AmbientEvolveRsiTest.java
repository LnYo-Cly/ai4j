package io.github.lnyocly.ai4j.ambient.evolve;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.trace.TraceMetrics;
import io.github.lnyocly.ai4j.agent.trace.TraceSpan;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanStatus;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanType;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.harness.AcceptanceRecord;
import io.github.lnyocly.ai4j.harness.FileHarnessConfig;
import io.github.lnyocly.ai4j.harness.FileHarnessStore;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceContext;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceEvaluator;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceResult;
import io.github.lnyocly.ai4j.harness.HarnessAcceptanceStatus;
import io.github.lnyocly.ai4j.harness.HarnessActor;
import io.github.lnyocly.ai4j.harness.HarnessCommandGateway;
import io.github.lnyocly.ai4j.harness.HarnessRule;
import io.github.lnyocly.ai4j.harness.HarnessRuleSpec;
import io.github.lnyocly.ai4j.harness.RuleKind;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.testing.DeterministicToolExecutor;
import io.github.lnyocly.ai4j.testing.ModelFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AmbientEvolveRsiTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final ServiceKey<String> PARENT_SERVICE_KEY =
            ServiceKey.of("parent.service", String.class);

    @Test
    public void contrastiveMinerDetectsMissingPrecheckHumanSteerAndRepetitiveChainFromTraceSpans() {
        List<TraceSpan> okSpans = Arrays.asList(
                TraceSpan.builder().type(TraceSpanType.RUN).status(TraceSpanStatus.OK)
                        .startTime(100L).endTime(300L)
                        .metrics(TraceMetrics.builder().totalTokens(350L).build()).build(),
                TraceSpan.builder().type(TraceSpanType.TOOL).name("check_lock").status(TraceSpanStatus.OK)
                        .attributes(attrs("check_lock", "{\"repo\":\"ai4j\"}")).build(),
                TraceSpan.builder().type(TraceSpanType.TOOL).name("run_deploy").status(TraceSpanStatus.OK)
                        .attributes(attrs("run_deploy", "{\"repo\":\"ai4j\"}")).build()
        );
        TrajectoryRecord traceOk = TrajectoryRecord.fromTraceSpans(
                "tr-ok-1", "deploy-service", okSpans, false, null);

        TrajectoryRecord traceFail = TrajectoryRecord.builder()
                .trajectoryId("tr-fail-1")
                .taskType("deploy-service")
                .success(false)
                .steps(4)
                .totalTokens(1200L)
                .durationMillis(900L)
                .steeredByHuman(true)
                .steerFeedback("Always run check_lock before run_deploy")
                .toolSteps(Collections.singletonList(TrajectoryRecord.ToolStep.builder()
                        .toolName("run_deploy")
                        .argumentsJson("{\"repo\":\"ai4j\"}")
                        .success(false)
                        .errorMessage("lock conflict")
                        .build()))
                .build();

        TrajectoryRecord longChain1 = TrajectoryRecord.builder()
                .trajectoryId("tr-chain-1")
                .taskType("repo-report")
                .success(true)
                .steps(4)
                .totalTokens(800L)
                .durationMillis(400L)
                .toolSteps(Arrays.asList(
                        TrajectoryRecord.ToolStep.builder().toolName("fetch_git").argumentsJson("{\"repo\":\"r1\"}").build(),
                        TrajectoryRecord.ToolStep.builder().toolName("parse_logs").argumentsJson("{\"env\":\"prod\"}").build(),
                        TrajectoryRecord.ToolStep.builder().toolName("publish_summary").argumentsJson("{\"channel\":\"ops\"}").build()
                ))
                .build();

        TrajectoryContrastiveMiner miner = new TrajectoryContrastiveMiner();
        TrajectoryContrastiveMiner.MiningReport report = miner.mine(
                "prod-scope", Arrays.asList(traceOk, traceFail, longChain1), "meta-v1");

        assertEquals(3, report.getTotalTrajectories());
        assertFalse(report.getRuleProposals().isEmpty());
        assertEquals(RuleKind.REQUIRE_ACCEPTANCE_CHECK,
                report.getRuleProposals().get(0).getRuleSpec().getKind());
        assertTrue(report.getRuleProposals().get(0).getRuleSpec().getPayload().contains("check_lock"));

        assertEquals(1, report.getSkillPatches().size());
        assertTrue(report.getSkillPatches().get(0).getMarkdownContent().contains("Always run check_lock"));

        boolean foundChainDiagnosis = false;
        for (TrajectoryContrastiveMiner.ContrastiveDiagnosis diag : report.getDiagnoses()) {
            if (diag.getRootCause() == TrajectoryContrastiveMiner.RootCauseKind.REPETITIVE_TOOL_CHAIN) {
                foundChainDiagnosis = true;
                assertEquals(Arrays.asList("fetch_git", "parse_logs", "publish_summary"),
                        diag.getSuggestedToolChain());
            }
        }
        assertTrue(foundChainDiagnosis);
    }

    @Test
    public void skillCompilerCompilesMultiStepTraceAndVerifiesInIsolatedSandboxWithoutPollutingParent() throws Exception {
        Ai4jExtension parentExt = new Ai4jExtension() {
            @Override
            public ExtensionManifest manifest() {
                return ExtensionManifest.builder()
                        .id("parent-host")
                        .capability(ExtensionCapability.TOOL)
                        .build();
            }

            @Override
            public void apply(ExtensionContext context) {
                context.provide(PARENT_SERVICE_KEY, "parent-live");
                context.tools().register(
                        ExtensionToolSpec.builder().name("ping").inputSchema("{\"type\":\"object\"}").build(),
                        call -> "pong");
            }
        };
        ExtensionRegistry parentRegistry = ExtensionRegistry.of(parentExt)
                .enable("parent-host")
                .exposeTool("ping");

        List<TrajectoryRecord> traces = Arrays.asList(
                TrajectoryRecord.builder()
                        .trajectoryId("t1").taskType("release-audit").success(true).steps(3)
                        .toolSteps(Arrays.asList(
                                TrajectoryRecord.ToolStep.builder().toolName("git_diff").argumentsJson("{\"branch\":\"main\"}").build(),
                                TrajectoryRecord.ToolStep.builder().toolName("run_checks").argumentsJson("{\"suite\":\"unit\"}").build(),
                                TrajectoryRecord.ToolStep.builder().toolName("format_digest").argumentsJson("{\"target\":\"ops\"}").build()
                        )).build(),
                TrajectoryRecord.builder()
                        .trajectoryId("t2").taskType("release-audit").success(true).steps(3)
                        .toolSteps(Arrays.asList(
                                TrajectoryRecord.ToolStep.builder().toolName("git_diff").argumentsJson("{\"branch\":\"release\"}").build(),
                                TrajectoryRecord.ToolStep.builder().toolName("run_checks").argumentsJson("{\"suite\":\"unit\"}").build(),
                                TrajectoryRecord.ToolStep.builder().toolName("format_digest").argumentsJson("{\"target\":\"dev\"}").build()
                        )).build()
        );

        SkillCompiler compiler = new SkillCompiler();
        CompiledSkillCandidate compiled = compiler.compileFromTrajectories(
                "release-audit", "compiled_release_audit", traces,
                "Compiles 3-step release audit into one deterministic tool call.");

        assertEquals(3, compiled.getSteps().size());
        Tool openAiTool = compiled.toOpenAiTool();
        assertEquals("compiled_release_audit", openAiTool.getFunction().getName());
        assertTrue(openAiTool.getFunction().getParameters().getProperties().containsKey("branch"));
        assertTrue(openAiTool.getFunction().getParameters().getProperties().containsKey("target"));

        DeterministicToolExecutor delegate = new DeterministicToolExecutor()
                .on("git_diff", call -> "{\"changedFiles\":3,\"args\":" + call.getArguments() + "}")
                .on("run_checks", "{\"passed\":true}")
                .on("format_digest", call -> "Digest ready for " + call.getArguments());

        SkillCompiler.SandboxVerificationResult verification = compiler.verifyInIsolatedSandbox(
                parentRegistry,
                compiled,
                delegate,
                Collections.singletonList(SkillCompiler.SandboxCheck.builder()
                        .argumentsJson("{\"branch\":\"feature-x\",\"target\":\"ops\"}")
                        .expectedOutputContains("stepsExecuted\":3")
                        .build())
        );

        assertTrue(verification.isPassed());
        assertTrue(verification.isValidationValid());
        assertEquals(1, delegate.countFor("git_diff"));
        assertEquals(1, delegate.countFor("run_checks"));
        assertEquals(1, delegate.countFor("format_digest"));

        // Verify parent registry was completely untouched by the sandbox verification!
        assertEquals("parent-live", parentRegistry.service(PARENT_SERVICE_KEY));
        assertFalse(parentRegistry.findService(CompiledSkillCandidate.SANDBOX_CANDIDATE_KEY).isPresent());
        assertEquals(1, parentRegistry.snapshot().getTools().size());
        assertEquals("plugin__parent-host__ping", parentRegistry.snapshot().getTools().get(0).getName());
        parentRegistry.close();
    }

    @Test
    public void preCommitGoldenGateEnforcesReplayBudgetWitnessConsentAndSupportsRollbackAndMetaRsi() throws Exception {
        Path harnessDir = temporaryFolder.newFolder("rsi-harness").toPath();
        try (FileHarnessStore store = new FileHarnessStore(FileHarnessConfig.builder().directory(harnessDir).build())) {
            HarnessCommandGateway gateway = new HarnessCommandGateway(store);
            HarnessActor witness = HarnessActor.system("golden-witness");
            HarnessActor human = HarnessActor.human("owner-1");
            HarnessActor agent = HarnessActor.agent("ai4j-agent");

            PreCommitGoldenGate gate = new PreCommitGoldenGate();

            // Seed v1 baseline skill
            SkillPatchCandidate baselineV1 = SkillPatchCandidate.builder()
                    .candidateId("skill-v1")
                    .scopeKey("prod")
                    .skillName("release-audit")
                    .baseVersion("v0")
                    .targetVersion("v1")
                    .markdownContent("# v1 instructions")
                    .build();
            gate.seedBaseline("slot:release-audit", baselineV1);

            // Prepare L3 compiled candidate v2 (reduces 3 model steps -> 2 steps / 1 compiled tool call)
            CompiledSkillCandidate compiledV2 = CompiledSkillCandidate.builder()
                    .candidateId("comp-v2")
                    .scopeKey("prod")
                    .metaVersion("meta-v2")
                    .extensionId("compiled-release-audit")
                    .skillName("release-audit")
                    .toolName("compiled_release_audit")
                    .description("Compiled 1-call release audit")
                    .inputSchemaJson("{\"type\":\"object\",\"properties\":{\"branch\":{\"type\":\"string\"}},\"required\":[\"branch\"]}")
                    .steps(Arrays.asList(
                            CompiledSkillCandidate.CompiledStep.builder()
                                    .stepIndex(0).toolName("git_diff").argumentsTemplate("{\"branch\":\"{{branch}}\"}").build(),
                            CompiledSkillCandidate.CompiledStep.builder()
                                    .stepIndex(1).toolName("run_checks").argumentsTemplate("{\"suite\":\"unit\"}").build()
                    ))
                    .build();

            DeterministicToolExecutor baseTools = new DeterministicToolExecutor()
                    .on("git_diff", "{\"files\":2}")
                    .on("run_checks", "{\"passed\":true}");

            ModelFixture compiledGoldenFixture = ModelFixture.parse("{\n"
                    + "  \"name\": \"compiled-audit-v2\",\n"
                    + "  \"exchanges\": [\n"
                    + "    {\n"
                    + "      \"expectedPromptContains\": [\"compiled_release_audit\"],\n"
                    + "      \"response\": {\n"
                    + "        \"toolCalls\": [{\"name\": \"compiled_release_audit\", \"arguments\": \"{\\\"branch\\\":\\\"main\\\"}\", \"callId\": \"call_1\"}],\n"
                    + "        \"inputTokens\": 30, \"outputTokens\": 10, \"totalTokens\": 40\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"expectedPromptContains\": [\"stepsExecuted\"],\n"
                    + "      \"response\": {\n"
                    + "        \"outputText\": \"Audit passed cleanly.\",\n"
                    + "        \"inputTokens\": 40, \"outputTokens\": 10, \"totalTokens\": 50\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}");

            PreCommitGoldenGate.GoldenReplayCase goldenCase = PreCommitGoldenGate.GoldenReplayCase.builder()
                    .caseId("case-1")
                    .name("release-audit-golden")
                    .userInput("Audit branch main")
                    .fixture(compiledGoldenFixture)
                    .toolRegistry(new StaticToolRegistry(Collections.emptyList()))
                    .toolExecutor(baseTools)
                    .baselineSteps(4)
                    .baselineTokens(200L)
                    .expectedOutputContains("Audit passed cleanly.")
                    .build();

            HarnessAcceptanceEvaluator passingWitness = ctx -> HarnessAcceptanceResult.builder()
                    .checkId("rsi_golden_witness")
                    .status(HarnessAcceptanceStatus.PASS)
                    .summary("Verified digest " + ctx.getContextSnapshotRef())
                    .build();

            // A) Reject budget regression (baselineSteps=1 < candidateSteps=2)
            PreCommitGoldenGate.GateEvaluationResult budgetRejected = gate.evaluateAndPromote(
                    PreCommitGoldenGate.GateEvaluationRequest.builder()
                            .slotKey("slot:release-audit")
                            .scopeKey("prod")
                            .candidate(compiledV2)
                            .goldenCases(Collections.singletonList(goldenCase.toBuilder().baselineSteps(1).build()))
                            .maxStepRegressionRatio(0.0d)
                            .witnessEvaluator(passingWitness)
                            .witnessActor(witness)
                            .consentActor(human)
                            .sandboxDelegateExecutor(baseTools)
                            .build()
            );
            assertFalse(budgetRejected.isPromoted());
            assertEquals(PreCommitGoldenGate.GateOutcome.REJECTED_BUDGET_REGRESSION, budgetRejected.getOutcome());
            assertEquals("skill-v1", gate.getActiveCandidate("slot:release-audit").getCandidateId());

            // B) Reject agent self-consent (MUST require human!)
            PreCommitGoldenGate.GateEvaluationResult agentConsentRejected = gate.evaluateAndPromote(
                    PreCommitGoldenGate.GateEvaluationRequest.builder()
                            .slotKey("slot:release-audit")
                            .scopeKey("prod")
                            .candidate(compiledV2)
                            .goldenCases(Collections.singletonList(goldenCase))
                            .witnessEvaluator(passingWitness)
                            .witnessActor(witness)
                            .consentActor(agent)
                            .sandboxDelegateExecutor(baseTools)
                            .build()
            );
            assertEquals(PreCommitGoldenGate.GateOutcome.REJECTED_CONSENT_REQUIRED, agentConsentRejected.getOutcome());

            // C) Reject witness failure
            PreCommitGoldenGate.GateEvaluationResult witnessRejected = gate.evaluateAndPromote(
                    PreCommitGoldenGate.GateEvaluationRequest.builder()
                            .slotKey("slot:release-audit")
                            .scopeKey("prod")
                            .candidate(compiledV2)
                            .goldenCases(Collections.singletonList(goldenCase))
                            .witnessEvaluator(ctx -> HarnessAcceptanceResult.builder()
                                    .checkId("rsi_golden_witness")
                                    .status(HarnessAcceptanceStatus.FAIL)
                                    .summary("Witness detected discrepancy")
                                    .build())
                            .witnessActor(witness)
                            .consentActor(human)
                            .sandboxDelegateExecutor(baseTools)
                            .build()
            );
            assertEquals(PreCommitGoldenGate.GateOutcome.REJECTED_WITNESS_FAILED, witnessRejected.getOutcome());

            // D) Full pass with Harness witness & human consent -> PROMOTED
            PreCommitGoldenGate.GateEvaluationResult promoted = gate.evaluateAndPromote(
                    PreCommitGoldenGate.GateEvaluationRequest.builder()
                            .slotKey("slot:release-audit")
                            .scopeKey("prod")
                            .candidate(compiledV2)
                            .goldenCases(Collections.singletonList(goldenCase))
                            .witnessEvaluator(passingWitness)
                            .witnessActor(witness)
                            .consentActor(human)
                            .consentDigest(compiledV2.getDigest())
                            .harnessGateway(gateway)
                            .sandboxDelegateExecutor(baseTools)
                            .sandboxChecks(Collections.singletonList(SkillCompiler.SandboxCheck.builder()
                                    .argumentsJson("{\"branch\":\"main\"}")
                                    .expectedOutputContains("stepsExecuted\":2")
                                    .build()))
                            .build()
            );
            assertTrue(promoted.isPromoted());
            assertEquals(PreCommitGoldenGate.GateOutcome.PROMOTED, promoted.getOutcome());
            assertNotNull(promoted.getAcceptanceId());
            assertNotNull(promoted.getEvidenceId());
            assertNotNull(promoted.getDecisionId());
            assertEquals("comp-v2", gate.getActiveCandidate("slot:release-audit").getCandidateId());
            assertEquals(CandidateStatus.ACTIVE, compiledV2.getStatus());

            // Also verify L1 HarnessRuleProposal promotion + rollback revokes rule in HarnessCommandGateway
            HarnessRuleProposal l1Proposal = HarnessRuleProposal.builder()
                    .candidateId("rule-v2")
                    .scopeKey("prod")
                    .metaVersion("meta-v2")
                    .ruleSpec(HarnessRuleSpec.builder()
                            .kind(RuleKind.REQUIRE_APPROVAL_FOR_TOOL)
                            .payload("run_checks")
                            .build())
                    .rationale("Gate sensitive check tool")
                    .build();
            PreCommitGoldenGate.GateEvaluationResult l1Promoted = gate.evaluateAndPromote(
                    PreCommitGoldenGate.GateEvaluationRequest.builder()
                            .slotKey("slot:rule-run-checks")
                            .scopeKey("prod")
                            .candidate(l1Proposal)
                            .goldenCases(Collections.singletonList(PreCommitGoldenGate.GoldenReplayCase.builder()
                                    .caseId("rule-case")
                                    .userInput("Say ok")
                                    .fixture(ModelFixture.parse("{\"name\":\"simple\",\"exchanges\":[{\"response\":{\"outputText\":\"ok\",\"totalTokens\":10}}]}"))
                                    .baselineSteps(1)
                                    .baselineTokens(10L)
                                    .expectedOutputContains("ok")
                                    .build()))
                            .witnessEvaluator(passingWitness)
                            .witnessActor(witness)
                            .consentActor(human)
                            .harnessGateway(gateway)
                            .build()
            );
            assertTrue(l1Promoted.isPromoted());
            assertNotNull(l1Promoted.getPromotedRuleId());
            List<HarnessRule> activeRules = gateway.listLearnedRulesInScope("prod");
            assertEquals(1, activeRules.size());
            assertTrue(activeRules.get(0).isEnabled());

            // Instant rollback restores baseline v1 for release-audit AND revokes L1 rule in Harness
            PreCommitGoldenGate.RollbackResult rollbackSkill = gate.rollback(
                    "slot:release-audit", gateway, human, "post-promotion latency spike");
            assertEquals("comp-v2", rollbackSkill.getRolledBackCandidateId());
            assertEquals("skill-v1", rollbackSkill.getRestoredCandidateId());
            assertEquals("skill-v1", gate.getActiveCandidate("slot:release-audit").getCandidateId());
            assertEquals(CandidateStatus.ROLLED_BACK, compiledV2.getStatus());

            PreCommitGoldenGate.RollbackResult rollbackRule = gate.rollback(
                    "slot:rule-run-checks", gateway, human, "false positive approval friction");
            assertEquals(l1Promoted.getPromotedRuleId(), rollbackRule.getRevokedRuleId());
            assertFalse(gateway.listLearnedRulesInScope("prod").get(0).isEnabled());

            // MetaRsiEvaluator tracks first-pass rate & post-promotion regression rate
            MetaRsiEvaluator meta = gate.getMetaRsiEvaluator();
            MetaRsiEvaluator.MetaRsiMetrics metaV2 = meta.getMetrics("meta-v2");
            assertEquals(5, metaV2.getTotalProposed());
            assertEquals(2, metaV2.getPromotedCount());
            assertEquals(2, metaV2.getRollbackCount());
            assertEquals(1.0d, metaV2.getPostPromotionRegressionRate(), 0.001d);

            // Meta-RSI Golden Gate blocks meta-v2 due to 100% rollback regression rate
            MetaRsiEvaluator.MetaPromotionDecision metaDecision =
                    meta.evaluateMetaPromotion("meta-v1", "meta-v2", 0.1d);
            assertFalse(metaDecision.isApproved());
        }
    }

    private static Map<String, Object> attrs(String toolName, String args) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("toolName", toolName);
        map.put("arguments", args);
        return map;
    }
}
