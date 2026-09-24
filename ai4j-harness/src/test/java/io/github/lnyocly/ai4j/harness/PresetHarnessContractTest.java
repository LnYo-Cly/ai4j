package io.github.lnyocly.ai4j.harness;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PresetHarnessContractTest {

    private TaskRecord taskWithPreset(String preset) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        if (preset != null) {
            metadata.put(HarnessContract.PRESET_METADATA_KEY, preset);
        }
        return TaskRecord.builder().taskId("t").metadata(metadata).build();
    }

    @Test
    public void routesTaskAwareQueriesToMatchingPreset() {
        PresetHarnessContract contract = PresetHarnessContract.builder()
                .preset("refund", p -> p.requiresApprovedReview(true)
                        .requiredAcceptanceCheck("payment-verified"))
                .preset("faq", p -> p.requiresApprovedReview(false)
                        .requiresCompletionEvidence(false))
                .base(HarnessContract.builder().requiresApprovedReview(true).build())
                .build();
        Assert.assertTrue(contract.requiresApprovedReview(taskWithPreset("refund"), null));
        Assert.assertFalse(contract.requiresApprovedReview(taskWithPreset("faq"), null));
        Assert.assertTrue(contract.requiredAcceptanceChecks(taskWithPreset("refund"), null)
                .contains("payment-verified"));
        Assert.assertTrue(contract.requiredAcceptanceChecks(taskWithPreset("faq"), null).isEmpty());
    }

    @Test
    public void taskWithoutPresetFallsBackToBase() {
        PresetHarnessContract contract = PresetHarnessContract.builder()
                .preset("faq", p -> p.requiresApprovedReview(false))
                .base(HarnessContract.builder().requiresApprovedReview(false).build())
                .build();
        Assert.assertFalse(contract.requiresApprovedReview(taskWithPreset(null), null));
        Assert.assertFalse(contract.requiresApprovedReview(taskWithPreset("  "), null));
        Assert.assertFalse(contract.requiresApprovedReview(null, null));
    }

    @Test
    public void unknownPresetFailsClosedByDefault() {
        PresetHarnessContract contract = PresetHarnessContract.builder()
                .preset("refund", p -> p.requiresApprovedReview(false))
                .build();
        List<GateResult> results = contract.evaluateCompletion(taskWithPreset("refun"), null, null);
        Assert.assertEquals(1, results.size());
        Assert.assertFalse(results.get(0).isPassed());
        Assert.assertTrue(results.get(0).getReason().contains("refun"));
        // Strictest defaults still apply alongside the failing gate.
        Assert.assertTrue(contract.requiresApprovedReview(taskWithPreset("refun"), null));
        Assert.assertTrue(contract.requiresCompletionEvidence(taskWithPreset("refun"), null));
    }

    @Test
    public void unknownPresetCanFallBackToBase() {
        PresetHarnessContract contract = PresetHarnessContract.builder()
                .onUnknownPreset(UnknownPresetPolicy.FALLBACK_TO_BASE)
                .base(HarnessContract.builder().requiresApprovedReview(false).build())
                .build();
        Assert.assertFalse(contract.requiresApprovedReview(taskWithPreset("typo"), null));
    }

    @Test
    public void toolLevelAndActorPolicyStayOnBase() {
        HarnessContract base = HarnessContract.builder()
                .approvalRequiredTool("refund")
                .requiresApprovedReview(false)
                .allowSystemApproval(false)
                .build();
        PresetHarnessContract contract = PresetHarnessContract.builder()
                .preset("faq", p -> p.requiresApprovedReview(false))
                .base(base)
                .build();
        // A preset must not loosen harness-wide tool or actor policy.
        Assert.assertTrue(contract.requiresApprovalForTool("refund"));
        Assert.assertFalse(contract.mayApprove(HarnessActor.agent("bot"), null));
        Assert.assertFalse(contract.mayApprove(HarnessActor.system("cron"), null));
        Assert.assertTrue(contract.mayApprove(HarnessActor.human("op"), null));
        Assert.assertFalse(contract.mayPromoteLesson(HarnessActor.system("cron")));
        Assert.assertTrue(contract.mayPromoteLesson(HarnessActor.human("op")));
    }

    @Test
    public void perPresetCompletionGatesEvaluate() {
        final HarnessGate refundGate = new HarnessGate() {
            @Override
            public String getName() {
                return "refund-reconciled";
            }

            @Override
            public GateResult evaluate(TaskRecord task, SubmissionRecord submission, HarnessState state) {
                return GateResult.fail("refund-reconciled", "not reconciled");
            }
        };
        PresetHarnessContract contract = PresetHarnessContract.builder()
                .preset("refund", p -> p.completionGate(refundGate))
                .preset("faq", p -> p.requiresApprovedReview(false))
                .build();
        List<GateResult> refund = contract.evaluateCompletion(taskWithPreset("refund"), null, null);
        Assert.assertEquals(1, refund.size());
        Assert.assertFalse(refund.get(0).isPassed());
        Assert.assertEquals("refund-reconciled", refund.get(0).getName());
        // faq preset has no gates: default pass.
        List<GateResult> faq = contract.evaluateCompletion(taskWithPreset("faq"), null, null);
        Assert.assertEquals(1, faq.size());
        Assert.assertTrue(faq.get(0).isPassed());
    }

    @Test
    public void customPresetKeyIsRespected() {
        PresetHarnessContract contract = PresetHarnessContract.builder()
                .presetKey(task -> task == null || task.getTags() == null || task.getTags().isEmpty()
                        ? null : task.getTags().get(0))
                .preset("ops", p -> p.requiresApprovedReview(false))
                .base(HarnessContract.builder().requiresApprovedReview(true).build())
                .build();
        TaskRecord tagged = TaskRecord.builder().taskId("t")
                .tags(Collections.singletonList("ops")).build();
        Assert.assertFalse(contract.requiresApprovedReview(tagged, null));
        Assert.assertTrue(contract.requiresApprovedReview(taskWithPreset("ops"), null));
    }
}
