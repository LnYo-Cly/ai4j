package io.github.lnyocly.ai4j.coding.loop;

import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgentRequest;
import io.github.lnyocly.ai4j.coding.CodingAgentResult;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.CodingSessionCheckpoint;
import io.github.lnyocly.ai4j.coding.CodingSessionCompactResult;
import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.process.BashProcessStatus;
import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodingAgentLoopControllerTest {

    private static final String STUB_TOOL = "stub_tool";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldContinueWithHiddenInstructionsWithoutAddingExtraUserMessage() throws Exception {
        InspectableQueueModelClient modelClient = new InspectableQueueModelClient();
        modelClient.enqueue(toolCallResult(STUB_TOOL, "call-1"));
        modelClient.enqueue(assistantResult("Continuing with remaining work."));
        modelClient.enqueue(assistantResult("Completed the requested change."));

        try (CodingSession session = newAgent(modelClient, okToolExecutor(), defaultOptions()).newSession()) {
            CodingAgentResult result = session.run(CodingAgentRequest.builder().input("Implement the requested change.").build());

            assertEquals(CodingStopReason.COMPLETED, result.getStopReason());
            assertEquals(2, result.getTurns());
            assertTrue(result.isAutoContinued());
            assertEquals(1, result.getAutoFollowUpCount());
            assertEquals(1, countUserMessages(session.exportState().getMemorySnapshot()));
            assertTrue(modelClient.getPrompts().size() >= 3);
            assertTrue(modelClient.getPrompts().get(2).getInstructions().contains("Internal continuation. This is not a new user message."));
            assertTrue(modelClient.getPrompts().get(2).getInstructions().contains("Continuation reason: CONTINUE_AFTER_TOOL_WORK."));
        }
    }

    @Test
    public void shouldStopWhenMaxAutoFollowUpsIsReached() throws Exception {
        InspectableQueueModelClient modelClient = new InspectableQueueModelClient();
        modelClient.enqueue(toolCallResult(STUB_TOOL, "call-1"));
        modelClient.enqueue(assistantResult("Continuing with remaining work."));
        modelClient.enqueue(toolCallResult(STUB_TOOL, "call-2"));
        modelClient.enqueue(assistantResult("Continuing with remaining work."));

        CodingAgentOptions options = defaultOptions().toBuilder()
                .maxAutoFollowUps(1)
                .build();

        try (CodingSession session = newAgent(modelClient, okToolExecutor(), options).newSession()) {
            CodingAgentResult result = session.run("Keep working until you are done.");
            List<CodingLoopDecision> decisions = session.drainLoopDecisions();

            assertEquals(CodingStopReason.MAX_AUTO_FOLLOWUPS_REACHED, result.getStopReason());
            assertEquals(2, result.getTurns());
            assertTrue(result.isAutoContinued());
            assertEquals(1, result.getAutoFollowUpCount());
            assertEquals(2, result.getToolResults().size());
            assertEquals(2, decisions.size());
            assertTrue(decisions.get(0).isContinueLoop());
            assertEquals(CodingLoopDecision.CONTINUE_AFTER_TOOL_WORK, decisions.get(0).getContinueReason());
            assertFalse(decisions.get(1).isContinueLoop());
            assertEquals(CodingStopReason.MAX_AUTO_FOLLOWUPS_REACHED, decisions.get(1).getStopReason());
        }
    }

    @Test
    public void shouldStopWhenApprovalIsRejected() throws Exception {
        InspectableQueueModelClient modelClient = new InspectableQueueModelClient();
        modelClient.enqueue(toolCallResult(STUB_TOOL, "call-1"));
        modelClient.enqueue(assistantResult("Approval required before proceeding."));

        try (CodingSession session = newAgent(modelClient, approvalRejectedToolExecutor(), defaultOptions()).newSession()) {
            CodingAgentResult result = session.run("Run the protected operation.");
            List<CodingLoopDecision> decisions = session.drainLoopDecisions();

            assertEquals(CodingStopReason.BLOCKED_BY_APPROVAL, result.getStopReason());
            assertFalse(result.isAutoContinued());
            assertEquals(1, result.getTurns());
            assertEquals(1, decisions.size());
            assertTrue(decisions.get(0).isBlocked());
            assertEquals(CodingStopReason.BLOCKED_BY_APPROVAL, decisions.get(0).getStopReason());
        }
    }

    @Test
    public void shouldStopAfterToolWorkWhenAssistantAlreadyReturnedFinalAnswer() throws Exception {
        InspectableQueueModelClient modelClient = new InspectableQueueModelClient();
        modelClient.enqueue(toolCallResult(STUB_TOOL, "call-1"));
        modelClient.enqueue(assistantResult("# ai4j"));

        try (CodingSession session = newAgent(modelClient, okToolExecutor(), defaultOptions()).newSession()) {
            CodingAgentResult result = session.run("Read README.md and answer with the first heading.");
            List<CodingLoopDecision> decisions = session.drainLoopDecisions();

            assertEquals(CodingStopReason.COMPLETED, result.getStopReason());
            assertFalse(result.isAutoContinued());
            assertEquals(1, result.getTurns());
            assertEquals("# ai4j", result.getOutputText());
            assertEquals(1, decisions.size());
            assertFalse(decisions.get(0).isContinueLoop());
            assertEquals(CodingStopReason.COMPLETED, decisions.get(0).getStopReason());
        }
    }

    @Test
    public void shouldReanchorContinuationPromptFromCheckpointAfterCompaction() throws Exception {
        InspectableQueueModelClient modelClient = new InspectableQueueModelClient();
        modelClient.enqueue(assistantResult("Continuing with remaining work. " + repeat("checkpoint-pressure-", 80)));
        modelClient.enqueue(assistantResult("Completed the requested change."));

        CodingAgentOptions options = defaultOptions().toBuilder()
                .autoCompactEnabled(true)
                .compactContextWindowTokens(220)
                .compactReserveTokens(60)
                .compactKeepRecentTokens(60)
                .compactSummaryMaxOutputTokens(120)
                .continueAfterCompact(true)
                .build();

        try (CodingSession session = newAgent(modelClient, okToolExecutor(), options).newSession()) {
            CodingAgentResult result = session.run("Keep working until the task is done.");

            assertEquals(CodingStopReason.COMPLETED, result.getStopReason());
            assertEquals(2, result.getTurns());
            assertTrue(result.isAutoContinued());
            assertTrue(modelClient.getPrompts().size() >= 3);
            String continuationInstructions = findContinuationInstructions(modelClient.getPrompts());

            assertTrue(continuationInstructions.contains("A context compaction was just applied."));
            assertTrue(continuationInstructions.contains("Compaction strategy:"));
            assertTrue(continuationInstructions.contains("checkpoint"));
            assertTrue(continuationInstructions.contains("Checkpoint goal: Continue the coding task."));
            assertTrue(continuationInstructions.contains("Checkpoint next steps:"));
        }
    }

    @Test
    public void shouldInjectConstraintsBlockedAndProcessesIntoContinuationPrompt() {
        CodingLoopDecision decision = CodingLoopDecision.builder()
                .turnNumber(2)
                .continueLoop(true)
                .continueReason(CodingLoopDecision.CONTINUE_AFTER_COMPACTION)
                .compactApplied(true)
                .build();
        CodingSessionCompactResult compactResult = CodingSessionCompactResult.builder()
                .strategy("checkpoint-delta")
                .checkpointReused(true)
                .checkpoint(CodingSessionCheckpoint.builder()
                        .goal("Finish the current implementation.")
                        .splitTurn(true)
                        .constraints(Collections.singletonList("Keep exact file paths."))
                        .blockedItems(Collections.singletonList("Wait for approval before deleting files."))
                        .nextSteps(Collections.singletonList("Run the next code change step."))
                        .criticalContext(Collections.singletonList("The workspace already contains partial edits."))
                        .inProgressItems(Collections.singletonList("Refine the compact continuation path."))
                        .processSnapshots(Collections.singletonList(StoredProcessSnapshot.builder()
                                .processId("proc_demo")
                                .command("npm run dev")
                                .status(BashProcessStatus.RUNNING)
                                .restored(true)
                                .build()))
                        .build())
                .build();

        String prompt = CodingContinuationPrompt.build(
                decision,
                CodingAgentResult.builder()
                        .outputText("Continue from the checkpoint.")
                        .build(),
                compactResult,
                3
        );

        assertTrue(prompt.contains("Compaction strategy: checkpoint-delta."));
        assertTrue(prompt.contains("The existing checkpoint was updated with new delta context."));
        assertTrue(prompt.contains("This checkpoint came from a split-turn compaction."));
        assertTrue(prompt.contains("Checkpoint constraints:"));
        assertTrue(prompt.contains("Keep exact file paths."));
        assertTrue(prompt.contains("Checkpoint blocked items:"));
        assertTrue(prompt.contains("Wait for approval before deleting files."));
        assertTrue(prompt.contains("Checkpoint process snapshots:"));
        assertTrue(prompt.contains("proc_demo [RUNNING] npm run dev (restored snapshot)"));
    }

    private CodingAgent newAgent(InspectableQueueModelClient modelClient,
                                 ToolExecutor toolExecutor,
                                 CodingAgentOptions options) throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("loop-controller-workspace").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit loop controller workspace")
                .build();
        return CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .codingOptions(options)
                .toolRegistry(singleToolRegistry(STUB_TOOL))
                .toolExecutor(toolExecutor)
                .build();
    }

    private CodingAgentOptions defaultOptions() {
        return CodingAgentOptions.builder()
                .autoCompactEnabled(false)
                .autoContinueEnabled(true)
                .maxAutoFollowUps(2)
                .maxTotalTurns(6)
                .build();
    }

    private AgentToolRegistry singleToolRegistry(String toolName) {
        Tool.Function function = new Tool.Function();
        function.setName(toolName);
        function.setDescription("Stub tool for testing");
        return new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", function)));
    }

    private ToolExecutor okToolExecutor() {
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "{\"ok\":true}";
            }
        };
    }

    private ToolExecutor approvalRejectedToolExecutor() {
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "[approval-rejected] protected action";
            }
        };
    }

    private AgentModelResult toolCallResult(String toolName, String callId) {
        return AgentModelResult.builder()
                .toolCalls(Collections.singletonList(AgentToolCall.builder()
                        .name(toolName)
                        .arguments("{}")
                        .callId(callId)
                        .type("function")
                        .build()))
                .memoryItems(Collections.<Object>emptyList())
                .build();
    }

    private AgentModelResult assistantResult(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", text)))
                .build();
    }

    private String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(text);
        }
        return builder.toString();
    }

    private static AgentModelResult compactSummaryResult() {
        return AgentModelResult.builder()
                .outputText("{\n"
                        + "  \"goal\": \"Continue the coding task.\",\n"
                        + "  \"constraints\": [\"Preserve exact file paths.\"],\n"
                        + "  \"progress\": {\n"
                        + "    \"done\": [\"Compacted older context.\"],\n"
                        + "    \"inProgress\": [\"Resume from the checkpoint.\"],\n"
                        + "    \"blocked\": []\n"
                        + "  },\n"
                        + "  \"keyDecisions\": [\"Use the compacted checkpoint for continuation.\"],\n"
                        + "  \"nextSteps\": [\"Continue with the next concrete implementation step.\"],\n"
                        + "  \"criticalContext\": [\"Recent messages were compacted before auto-continuation.\"]\n"
                        + "}")
                .build();
    }

    private String findContinuationInstructions(List<AgentPrompt> prompts) {
        if (prompts == null) {
            return "";
        }
        for (AgentPrompt prompt : prompts) {
            if (prompt != null
                    && prompt.getInstructions() != null
                    && prompt.getInstructions().contains("Internal continuation.")) {
                return prompt.getInstructions();
            }
        }
        return "";
    }

    private int countUserMessages(MemorySnapshot snapshot) {
        if (snapshot == null || snapshot.getItems() == null) {
            return 0;
        }
        int count = 0;
        for (Object item : snapshot.getItems()) {
            if (!(item instanceof Map)) {
                continue;
            }
            Object role = ((Map<?, ?>) item).get("role");
            if ("user".equals(role)) {
                count += 1;
            }
        }
        return count;
    }

    private static final class InspectableQueueModelClient implements AgentModelClient {

        private final Deque<AgentModelResult> results = new ArrayDeque<AgentModelResult>();
        private final List<AgentPrompt> prompts = new ArrayList<AgentPrompt>();

        private void enqueue(AgentModelResult result) {
            results.addLast(result);
        }

        private List<AgentPrompt> getPrompts() {
            return prompts;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            prompts.add(prompt == null ? null : prompt.toBuilder().build());
            if (prompt != null
                    && prompt.getSystemPrompt() != null
                    && prompt.getSystemPrompt().contains("context checkpoint summaries")) {
                return compactSummaryResult();
            }
            AgentModelResult result = results.removeFirst();
            return result == null ? AgentModelResult.builder().build() : result;
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = create(prompt);
            if (listener != null && result != null && result.getOutputText() != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }
    }
}
