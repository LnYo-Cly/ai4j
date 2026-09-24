package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable end-to-end walkthrough of the refund-ticket scenario:
 * four bounded slices across "five days", a real process restart between
 * slices, three different wait types, an approval-gated tool call, and a
 * submission -> review -> acceptance -> completion chain.
 *
 * Run it as a test or directly via {@link #main(String[])}.
 */
public class RefundTicketDemoTest {

    @Test
    public void refundTicketSurvivesFourSlicesAndAProcessRestart() throws Exception {
        Path store = Files.createTempDirectory("ai4j-refund-demo-");
        runScenario(store);
    }

    public static void main(String[] args) throws Exception {
        Path store = Files.createTempDirectory("ai4j-refund-demo-");
        runScenario(store);
        System.out.println("ledger persisted at: " + store);
    }

    private static void runScenario(Path store) {
        // ---- Business tools: submitRefund is approval-gated by the contract,
        // so this executor must be reached exactly once, after approval. ----
        final AtomicInteger refundCalls = new AtomicInteger();
        ToolExecutor businessTools = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                if ("submitRefund".equals(call.getName())) {
                    refundCalls.incrementAndGet();
                    return "{\"refundId\":\"RF-88\",\"status\":\"PENDING\"}";
                }
                return "{}";
            }
        };

        // ---- Scripted model: each queued result is one model invocation.
        // Queue position survives across slices; the "model" never sees
        // process internals, only what the session/checkpoint restores. ----
        ScriptedModel model = new ScriptedModel(
                // Day 1: create the task, ask the user for the order number.
                toolCall("c1", HarnessToolNames.TASK_MANAGE,
                        "{\"operation\":\"create\",\"taskId\":\"refund-ticket-1\","
                                + "\"title\":\"Refund investigation\",\"goal\":\"Verify order and refund\"}"),
                toolCall("c2", HarnessToolNames.CONTROL_REQUEST,
                        "{\"operation\":\"wait\",\"type\":\"USER_INPUT\","
                                + "\"externalKey\":\"order-no\"}"),
                // Day 3: record what the order service said, then try to refund.
                toolCall("c3", HarnessToolNames.FACT_RECORD,
                        "{\"operation\":\"record\",\"factId\":\"fact-order-1\","
                                + "\"statement\":\"Order SO-20260920-001 exists, amount 899 CNY\","
                                + "\"source\":\"order-service\"}"),
                toolCall("c4", HarnessToolNames.EVIDENCE_RECORD,
                        "{\"evidenceId\":\"ev-order-1\",\"kind\":\"tool-output\","
                                + "\"summary\":\"order-service lookup response\","
                                + "\"contentRef\":\"order:SO-20260920-001\"}"),
                toolCall("c5", "submitRefund", "{\"orderId\":\"SO-20260920-001\"}"),
                // Day 4: approval delivered -> retry the same gated call, then
                // the refund API is async -> wait for the callback.
                toolCall("c6", "submitRefund", "{\"orderId\":\"SO-20260920-001\"}"),
                toolCall("c7", HarnessToolNames.CONTROL_REQUEST,
                        "{\"operation\":\"wait\",\"type\":\"ASYNC_OPERATION\","
                                + "\"externalKey\":\"refund-RF-88\"}"),
                // Day 5: receipt in -> record evidence, submit for review.
                toolCall("c8", HarnessToolNames.EVIDENCE_RECORD,
                        "{\"evidenceId\":\"ev-receipt-1\",\"kind\":\"callback\","
                                + "\"summary\":\"refund receipt RF-88\","
                                + "\"contentRef\":\"refund:RF-88\"}"),
                toolCall("c9", HarnessToolNames.SUBMISSION_REQUEST,
                        "{\"completionClaim\":\"Refund RF-88 completed\","
                                + "\"verificationNotes\":\"order verified, approval granted, receipt received; "
                                + "order evidence recorded under the day-3 execution\","
                                + "\"evidenceIds\":[\"ev-receipt-1\"],"   // gates require evidence of THIS execution
                                + "\"deliverables\":[\"refund RF-88\"],"
                                + "\"knownGaps\":[],\"residualRisks\":[]}"),
                text("Refund RF-88 has been issued."));

        HarnessContract contract = HarnessContract.builder()
                .taskRequiredTool("submitRefund")       // no Task, no refund tool
                .approvalRequiredTool("submitRefund") // refund needs a human gate
                .requiresApprovedReview(true)
                .requiresCompletionEvidence(true)
                .requiredAcceptanceCheck("refund-settled")
                .build();

        // ======================= DAY 1 =======================
        AgentHarness harness = AgentHarness.builder()
                .agent(newAgent(model, businessTools))
                .persistence(HarnessPersistence.file(store))
                .contract(contract)
                .autoResume(false)
                .build();

        HarnessRunResult day1 = harness.run(HarnessRunRequest.builder()
                .scopeKey("shop-A")
                .sessionId("customer-123")
                .idempotencyKey("message:day1")
                .input("The invoice amount is wrong, I want a refund")
                .build());
        Assert.assertEquals(HarnessRunStatus.WAITING, day1.getStatus());
        String userWait = day1.getWaitId();
        Assert.assertEquals(WaitType.USER_INPUT,
                harness.getGateway().getWait(userWait).getType());
        harness.close(); // <-- process dies here. State is on disk.

        // ======================= DAY 3 =======================
        // Brand-new harness instance, same store: a "different JVM".
        AgentHarness day3 = AgentHarness.builder()
                .agent(newAgent(model, businessTools))
                .persistence(HarnessPersistence.file(store))
                .contract(contract)
                .autoResume(false)
                .build();
        Assert.assertEquals(1, day3.getGateway().listOpenWaitsInScope("shop-A").size());

        HarnessRunResult day3Result = day3.deliver(userWait, "SO-20260920-001");
        Assert.assertEquals(HarnessRunStatus.WAITING, day3Result.getStatus());
        String approvalWait = day3Result.getWaitId();
        Assert.assertEquals(WaitType.APPROVAL,
                day3.getGateway().getWait(approvalWait).getType());
        Assert.assertEquals("submitRefund must not run before approval",
                0, refundCalls.get());
        day3.close(); // <-- another restart.

        // ======================= DAY 4 =======================
        AgentHarness day4 = AgentHarness.builder()
                .agent(newAgent(model, businessTools))
                .persistence(HarnessPersistence.file(store))
                .contract(contract)
                .autoResume(false)
                .build();
        Map<String, Object> approval = new HashMap<String, Object>();
        approval.put("approved", Boolean.TRUE);
        approval.put("approver", "ops-zhangsan");
        HarnessRunResult day4Result = day4.deliver(approvalWait, approval);
        Assert.assertEquals(HarnessRunStatus.WAITING, day4Result.getStatus());
        String asyncWait = day4Result.getWaitId();
        Assert.assertEquals(WaitType.ASYNC_OPERATION,
                day4.getGateway().getWait(asyncWait).getType());
        Assert.assertEquals("approved retry executes the business tool once",
                1, refundCalls.get());
        day4.close();

        // ======================= DAY 5 =======================
        AgentHarness day5 = AgentHarness.builder()
                .agent(newAgent(model, businessTools))
                .persistence(HarnessPersistence.file(store))
                .contract(contract)
                .autoResume(false)
                .build();
        Map<String, Object> callback = new HashMap<String, Object>();
        callback.put("refundId", "RF-88");
        callback.put("success", Boolean.TRUE);
        HarnessRunResult day5Result = day5.deliver(asyncWait, callback);
        Assert.assertEquals(HarnessRunStatus.COMPLETED, day5Result.getStatus());
        Assert.assertEquals("Refund RF-88 has been issued.", day5Result.getOutputText());

        // ---- Host side: review + acceptance + completion ----
        HarnessCommandGateway gateway = day5.getGateway();
        TaskRecord task = gateway.getTask("refund-ticket-1");
        Assert.assertEquals(TaskStatus.IN_REVIEW, task.getStatus());
        String submissionId = task.getSubmissionId();
        Assert.assertNotNull(submissionId);

        // An agent can never approve or complete its own work.
        try {
            gateway.reviewSubmission(submissionId, ReviewVerdict.APPROVED,
                    null, null, HarnessActor.agent("agent-a"));
            Assert.fail("agent actor must not review submissions");
        } catch (HarnessValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("not allowed"));
        }

        gateway.reviewSubmission(submissionId, ReviewVerdict.APPROVED,
                "evidence chain verified", "order + receipt both present",
                HarnessActor.human("ops-zhangsan"));

        // The contract's required check blocks completion until a PASS lands.
        try {
            gateway.completeTask(task.getTaskId(), submissionId, HarnessActor.human("ops-zhangsan"));
            Assert.fail("missing acceptance check must block completion");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("refund-settled"));
        }
        gateway.recordAcceptance(AcceptanceRecord.builder()
                .acceptanceId("acc-1").taskId(task.getTaskId())
                .executionId(task.getLastExecutionId())
                .submissionId(submissionId)
                .checkId("refund-settled")
                .status(HarnessAcceptanceStatus.PASS)
                .summary("payment service confirms RF-88 settled")
                .build());

        Assert.assertEquals(TaskStatus.DONE, gateway.completeTask(task.getTaskId(),
                submissionId, HarnessActor.human("ops-zhangsan")).getStatus());

        // ---- What the ledger kept, after four slices and three restarts ----
        Assert.assertEquals(1, gateway.listFactsInScope("shop-A").size());
        Assert.assertEquals(2, gateway.listEvidenceInScope("shop-A").size());
        Assert.assertEquals(3, gateway.getState().getWaits().size());
        Assert.assertEquals(ReviewVerdict.APPROVED,
                gateway.getState().getReviews().values().iterator().next().getVerdict());
        day5.close();
    }

    private static Agent newAgent(AgentModelClient model, ToolExecutor executor) {
        AgentContext context = AgentContext.builder()
                .modelClient(model)
                .toolRegistry(StaticToolRegistry.empty())
                .toolExecutor(executor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().maxSteps(8).build())
                .model("scripted-model")
                .build();
        return new Agent(new ReActRuntime(), context, InMemoryAgentMemory::new);
    }

    private static AgentModelResult toolCall(String callId, String name, String arguments) {
        return AgentModelResult.builder()
                .toolCalls(Collections.singletonList(AgentToolCall.builder()
                        .callId(callId).name(name).arguments(arguments)
                        .type("function_call").build()))
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    private static AgentModelResult text(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .toolCalls(new ArrayList<AgentToolCall>())
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    /** Deterministic stand-in for a real LLM: returns queued results in order. */
    private static final class ScriptedModel implements AgentModelClient {
        private final Deque<AgentModelResult> results;

        private ScriptedModel(AgentModelResult... results) {
            this.results = new ArrayDeque<AgentModelResult>(Arrays.asList(results));
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return results.isEmpty()
                    ? text("no more scripted results")
                    : results.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }
}
