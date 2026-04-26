package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.coding.process.BashProcessLogChunk;
import io.github.lnyocly.ai4j.coding.process.BashProcessStatus;
import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CodingSessionTest {

    private static final String STUB_TOOL = "stub_tool";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldSnapshotAndCompactSessionMemory() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session workspace")
                .build();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(new CompactionAwareModelClient())
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        CodingSession session = agent.newSession();
        try {
            session.run("Inspect the repository layout.");
            session.run("Now summarize the current progress.");

            CodingSessionSnapshot before = session.snapshot();
            CodingSessionCompactResult compactResult = session.compact();
            CodingSessionSnapshot after = session.snapshot();

            assertTrue(before.getMemoryItemCount() >= 2);
            assertNotNull(compactResult.getSummary());
            assertNotNull(compactResult.getCheckpoint());
            assertTrue(compactResult.getSummary().contains("## Goal"));
            assertEquals(before.getMemoryItemCount(), compactResult.getBeforeItemCount());
            assertEquals(after.getMemoryItemCount(), compactResult.getAfterItemCount());
            assertTrue(after.getMemoryItemCount() <= before.getMemoryItemCount());
            assertTrue(compactResult.getEstimatedTokensBefore() > 0);
            assertEquals(workspaceRoot.toString(), after.getWorkspaceRoot());
            assertEquals("Continue the coding task.", after.getCheckpointGoal());
            assertEquals("manual", after.getLastCompactMode());
            assertTrue(compactResult.getStrategy().contains("checkpoint"));
            assertEquals(compactResult.getEstimatedTokensBefore(), after.getLastCompactTokensBefore());
            assertEquals(compactResult.getEstimatedTokensAfter(), after.getLastCompactTokensAfter());
        } finally {
            session.close();
        }
    }

    @Test
    public void shouldExportAndRestoreSessionState() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-restore").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session restore workspace")
                .build();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(new CompactionAwareModelClient())
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        CodingSession first = agent.newSession("resume-session", null);
        CodingSessionState state;
        try {
            first.run("Inspect the repository layout.");
            first.run("Now summarize the current progress.");
            state = first.exportState();
        } finally {
            first.close();
        }

        CodingSession resumed = agent.newSession(state);
        try {
            CodingSessionSnapshot snapshot = resumed.snapshot();
            assertEquals("resume-session", resumed.getSessionId());
            assertEquals(workspaceRoot.toString(), snapshot.getWorkspaceRoot());
            assertTrue(snapshot.getMemoryItemCount() >= 2);
            resumed.run("Continue from previous context.");
            assertTrue(resumed.snapshot().getMemoryItemCount() > snapshot.getMemoryItemCount());
        } finally {
            resumed.close();
        }
    }

    @Test
    public void shouldRestoreProcessMetadataAsReadOnlySnapshots() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-process-restore").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session process restore workspace")
                .build();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(new CompactionAwareModelClient())
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        CodingSessionState seededState = CodingSessionState.builder()
                .sessionId("process-seeded")
                .workspaceRoot(workspaceRoot.toString())
                .memorySnapshot(firstMemorySnapshot())
                .processCount(1)
                .processSnapshots(Collections.singletonList(StoredProcessSnapshot.builder()
                        .processId("proc_demo")
                        .command("echo ready && sleep 10")
                        .workingDirectory(workspaceRoot.toString())
                        .status(BashProcessStatus.STOPPED)
                        .startedAt(System.currentTimeMillis())
                        .endedAt(System.currentTimeMillis())
                        .lastLogOffset(42L)
                        .lastLogPreview("[stdout] ready")
                        .restored(false)
                        .controlAvailable(true)
                        .build()))
                .build();

        try (CodingSession resumed = agent.newSession(seededState)) {
            CodingSessionSnapshot snapshot = resumed.snapshot();
            assertEquals(1, snapshot.getProcessCount());
            assertEquals(0, snapshot.getActiveProcessCount());
            assertEquals(1, snapshot.getRestoredProcessCount());
            assertTrue(snapshot.getProcesses().get(0).isRestored());
            assertFalse(snapshot.getProcesses().get(0).isControlAvailable());
        }
    }

    @Test
    public void shouldReadPreviewLogsFromRestoredProcessSnapshots() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-process-logs").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session restored process logs workspace")
                .build();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(new CompactionAwareModelClient())
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        CodingSessionState seededState = CodingSessionState.builder()
                .sessionId("process-log-seeded")
                .workspaceRoot(workspaceRoot.toString())
                .memorySnapshot(firstMemorySnapshot())
                .processCount(1)
                .processSnapshots(Collections.singletonList(StoredProcessSnapshot.builder()
                        .processId("proc_demo")
                        .command("npm run dev")
                        .workingDirectory(workspaceRoot.toString())
                        .status(BashProcessStatus.STOPPED)
                        .startedAt(System.currentTimeMillis())
                        .endedAt(System.currentTimeMillis())
                        .lastLogOffset(30L)
                        .lastLogPreview("[stdout] server ready\n")
                        .restored(false)
                        .controlAvailable(true)
                        .build()))
                .build();

        try (CodingSession resumed = agent.newSession(seededState)) {
            BashProcessLogChunk logs = resumed.processLogs("proc_demo", null, 200);
            assertNotNull(logs);
            assertEquals("proc_demo", logs.getProcessId());
            assertTrue(logs.getContent().contains("server ready"));
            assertEquals(BashProcessStatus.STOPPED, logs.getStatus());
        }
    }

    @Test
    public void shouldAutoCompactWhenTokenBudgetExceeded() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-auto").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session auto compact workspace")
                .build();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(new CompactionAwareModelClient())
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .codingOptions(CodingAgentOptions.builder()
                        .autoCompactEnabled(true)
                        .compactContextWindowTokens(120)
                        .compactReserveTokens(20)
                        .compactKeepRecentTokens(40)
                        .compactSummaryMaxOutputTokens(120)
                        .build())
                .build();

        CodingSession session = agent.newSession();
        try {
            session.run("Please write a very long analysis about the workspace state and repeat details many times.");
            CodingSessionCompactResult autoResult = session.drainLastAutoCompactResult();
            CodingSessionSnapshot snapshot = session.snapshot();

            assertNotNull(autoResult);
            assertTrue(autoResult.isAutomatic());
            assertTrue(autoResult.getSummary().contains("## Goal"));
            assertTrue(snapshot.getEstimatedContextTokens() <= autoResult.getEstimatedTokensBefore());
            assertNull(session.drainLastAutoCompactError());
            assertEquals("auto", snapshot.getLastCompactMode());
            assertTrue(autoResult.getStrategy().contains("checkpoint"));
            assertEquals("Continue the coding task.", snapshot.getCheckpointGoal());
        } finally {
            session.close();
        }
    }

    @Test
    public void shouldMicroCompactOversizedToolResultsBeforeFullCheckpointCompaction() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-micro").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session micro compact workspace")
                .build();

        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(toolCallResult(STUB_TOOL, "call-1"));
        modelClient.enqueue(assistantResult("Tool work finished for this step."));

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .toolRegistry(singleToolRegistry(STUB_TOOL))
                .toolExecutor(largeToolExecutor())
                .codingOptions(CodingAgentOptions.builder()
                        .autoCompactEnabled(true)
                        .compactContextWindowTokens(700)
                        .compactReserveTokens(120)
                        .compactKeepRecentTokens(200)
                        .toolResultMicroCompactEnabled(true)
                        .toolResultMicroCompactKeepRecent(0)
                        .toolResultMicroCompactMaxTokens(120)
                        .build())
                .build();

        try (CodingSession session = agent.newSession()) {
            session.run("Run the stub tool and continue.");

            CodingSessionCompactResult autoResult = session.drainLastAutoCompactResult();
            MemorySnapshot memorySnapshot = session.exportState().getMemorySnapshot();

            assertNotNull(autoResult);
            assertEquals("tool-result-micro", autoResult.getStrategy());
            assertTrue(autoResult.getCompactedToolResultCount() > 0);
            assertTrue(autoResult.getEstimatedTokensAfter() < autoResult.getEstimatedTokensBefore());
            assertTrue(String.valueOf(memorySnapshot.getItems()).contains("tool result compacted to save context"));
            assertEquals(0, modelClient.getSummaryPromptCount());
        }
    }

    @Test
    public void shouldOpenAutoCompactCircuitBreakerAfterRepeatedFailures() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-breaker").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session auto compact breaker workspace")
                .build();

        FailingCompactionModelClient modelClient = new FailingCompactionModelClient();
        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .codingOptions(CodingAgentOptions.builder()
                        .autoCompactEnabled(true)
                        .compactContextWindowTokens(160)
                        .compactReserveTokens(40)
                        .compactKeepRecentTokens(40)
                        .compactSummaryMaxOutputTokens(80)
                        .autoCompactMaxConsecutiveFailures(3)
                        .build())
                .build();

        try (CodingSession session = agent.newSession()) {
            session.run("First prompt.");
            assertNotNull(session.drainLastAutoCompactError());
            session.run("Second prompt.");
            assertNotNull(session.drainLastAutoCompactError());
            session.run("Third prompt.");
            Exception thirdError = session.drainLastAutoCompactError();

            CodingSessionSnapshot afterFailures = session.snapshot();
            assertNotNull(thirdError);
            assertTrue(thirdError.getMessage().contains("circuit breaker opened"));
            assertEquals(3, afterFailures.getAutoCompactFailureCount());
            assertTrue(afterFailures.isAutoCompactCircuitBreakerOpen());
            assertEquals(3, modelClient.getSummaryPromptCount());

            session.run("Fourth prompt.");
            Exception pausedError = session.drainLastAutoCompactError();

            assertNotNull(pausedError);
            assertTrue(pausedError.getMessage().contains("paused after 3 consecutive failures"));
            assertEquals(3, modelClient.getSummaryPromptCount());
        }
    }

    @Test
    public void shouldRetryCompactSummaryAfterPromptTooLong() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-ptl-retry").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session prompt-too-long retry workspace")
                .build();

        PromptTooLongOnceCompactionModelClient modelClient = new PromptTooLongOnceCompactionModelClient(3200);
        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        try (CodingSession session = agent.newSession()) {
            session.run("Turn one.");
            session.run("Turn two.");
            session.run("Turn three.");

            CodingSessionCompactResult compactResult = session.compact();

            assertNotNull(compactResult);
            assertEquals(2, modelClient.getSummaryPromptCount());
            assertEquals(1, modelClient.getPromptTooLongFailures());
            assertTrue(compactResult.getSummary().contains("Compaction retry"));
            assertFalse(compactResult.getSummary().contains("Compaction fallback"));
        }
    }

    @Test
    public void shouldFallbackWhenPromptTooLongRetriesAreExhausted() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-ptl-fallback").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session prompt-too-long fallback workspace")
                .build();

        AlwaysPromptTooLongCompactionModelClient modelClient = new AlwaysPromptTooLongCompactionModelClient();
        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        try (CodingSession session = agent.newSession()) {
            session.run("Turn one.");
            session.run("Turn two.");
            session.run("Turn three.");

            CodingSessionCompactResult compactResult = session.compact();

            assertNotNull(compactResult);
            assertEquals(4, modelClient.getSummaryPromptCount());
            assertTrue(compactResult.isFallbackSummary());
            assertTrue(compactResult.getSummary().contains("Compaction fallback"));
            assertTrue(compactResult.getSummary().contains("Compaction retry"));
        }
    }

    @Test
    public void shouldUseSessionMemoryFallbackWhenCheckpointAlreadyExists() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-memory-fallback").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session memory fallback workspace")
                .build();

        FirstSummaryThenFailingCompactionModelClient modelClient = new FirstSummaryThenFailingCompactionModelClient();
        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        try (CodingSession session = agent.newSession()) {
            session.run("Create the initial checkpoint.");
            CodingSessionCompactResult first = session.compact();

            session.run("Follow-up change after the checkpoint already exists.");
            CodingSessionCompactResult second = session.compact();

            assertNotNull(first);
            assertNotNull(second);
            assertFalse(first.isFallbackSummary());
            assertTrue(second.isFallbackSummary());
            assertEquals("checkpoint-delta", second.getStrategy());
            assertTrue(second.isCheckpointReused());
            assertTrue(second.getSummary().contains("Session-memory fallback"));
            assertTrue(second.getSummary().contains("Previous compacted context retained."));
            assertTrue(second.getSummary().contains("Latest user delta: Follow-up change after the checkpoint already exists."));
        }
    }

    @Test
    public void shouldReuseExistingCheckpointForAggressiveFallback() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-aggressive-memory-fallback").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session aggressive memory fallback workspace")
                .build();

        FirstSummaryThenAggressiveFailingCompactionModelClient modelClient = new FirstSummaryThenAggressiveFailingCompactionModelClient();
        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .codingOptions(CodingAgentOptions.builder()
                        .autoCompactEnabled(false)
                        .compactContextWindowTokens(260)
                        .compactReserveTokens(80)
                        .compactKeepRecentTokens(800)
                        .compactSummaryMaxOutputTokens(120)
                        .build())
                .build();

        try (CodingSession session = agent.newSession()) {
            session.run("Create the initial checkpoint.");
            CodingSessionCompactResult first = session.compact();

            session.run("Make a very large follow-up change that should trigger aggressive compaction.");
            CodingSessionCompactResult second = session.compact();

            assertNotNull(first);
            assertNotNull(second);
            assertTrue(second.isFallbackSummary());
            assertEquals("aggressive-checkpoint-delta", second.getStrategy());
            assertTrue(second.isCheckpointReused());
            assertTrue(second.getSummary().contains("Session-memory fallback"));
            assertTrue(second.getSummary().contains("Created the initial checkpoint."));
            assertTrue(second.getSummary().contains("Latest user delta: Make a very large follow-up change that should trigger aggressive compaction."));
            assertEquals(3, modelClient.getSummaryPromptCount());
        }
    }

    @Test
    public void shouldExposeCheckpointDeltaStrategyWhenReusingPreviousCheckpoint() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-delta").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session delta compact workspace")
                .build();

        CompactionAwareModelClient modelClient = new CompactionAwareModelClient();
        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        try (CodingSession session = agent.newSession()) {
            session.run("Initial workspace analysis.");
            CodingSessionCompactResult first = session.compact();

            session.run("Apply another change after the first compact.");
            CodingSessionCompactResult second = session.compact();

            assertNotNull(first);
            assertNotNull(second);
            assertEquals("checkpoint", first.getStrategy());
            assertEquals("checkpoint-delta", second.getStrategy());
            assertFalse(first.isCheckpointReused());
            assertTrue(second.isCheckpointReused());
            assertTrue(second.getDeltaItemCount() > 0);
        }
    }

    @Test
    public void shouldPreserveLatestCompactMetadataAcrossExportAndRestore() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-compact-restore").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session compact restore workspace")
                .build();

        CompactionAwareModelClient modelClient = new CompactionAwareModelClient();
        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        CodingSessionState exportedState;
        CodingSessionCompactResult compactResult;
        try (CodingSession session = agent.newSession()) {
            session.run("Inspect the repository layout.");
            session.run("Summarize the current progress.");
            compactResult = session.compact();
            exportedState = session.exportState();
        }

        assertNotNull(exportedState);
        assertNotNull(exportedState.getLatestCompactResult());
        assertEquals(compactResult.getStrategy(), exportedState.getLatestCompactResult().getStrategy());

        try (CodingSession resumed = agent.newSession(exportedState)) {
            CodingSessionSnapshot snapshot = resumed.snapshot();

            assertEquals("manual", snapshot.getLastCompactMode());
            assertEquals(compactResult.getStrategy(), snapshot.getLastCompactStrategy());
            assertEquals(compactResult.getEstimatedTokensBefore(), snapshot.getLastCompactTokensBefore());
            assertEquals(compactResult.getEstimatedTokensAfter(), snapshot.getLastCompactTokensAfter());
            assertTrue(snapshot.getLastCompactSummary().contains("## Goal"));
        }
    }

    @Test
    public void shouldPreserveAutoCompactBreakerAcrossExportAndRestore() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-breaker-restore").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session breaker restore workspace")
                .build();

        FailingCompactionModelClient modelClient = new FailingCompactionModelClient();
        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .codingOptions(CodingAgentOptions.builder()
                        .autoCompactEnabled(true)
                        .compactContextWindowTokens(160)
                        .compactReserveTokens(40)
                        .compactKeepRecentTokens(40)
                        .compactSummaryMaxOutputTokens(80)
                        .autoCompactMaxConsecutiveFailures(3)
                        .build())
                .build();

        CodingSessionState exportedState;
        try (CodingSession session = agent.newSession()) {
            session.run("First prompt.");
            assertNotNull(session.drainLastAutoCompactError());
            session.run("Second prompt.");
            assertNotNull(session.drainLastAutoCompactError());
            session.run("Third prompt.");
            assertNotNull(session.drainLastAutoCompactError());

            exportedState = session.exportState();
            assertEquals(3, exportedState.getAutoCompactFailureCount());
            assertTrue(exportedState.isAutoCompactCircuitBreakerOpen());
        }

        try (CodingSession resumed = agent.newSession(exportedState)) {
            CodingSessionSnapshot snapshot = resumed.snapshot();

            assertEquals(3, snapshot.getAutoCompactFailureCount());
            assertTrue(snapshot.isAutoCompactCircuitBreakerOpen());

            resumed.run("Fourth prompt.");
            Exception pausedError = resumed.drainLastAutoCompactError();

            assertNotNull(pausedError);
            assertTrue(pausedError.getMessage().contains("paused after 3 consecutive failures"));
            assertEquals(3, modelClient.getSummaryPromptCount());
        }
    }

    @Test
    public void shouldClearPendingLoopArtifactsAfterManualCompact() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-session-manual-compact-cleanup").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit coding session manual compact cleanup workspace")
                .build();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(new CompactionAwareModelClient())
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .build();

        try (CodingSession session = agent.newSession()) {
            session.run("Inspect the repository layout.");
            session.recordLoopDecision(io.github.lnyocly.ai4j.coding.loop.CodingLoopDecision.builder()
                    .turnNumber(1)
                    .continueLoop(false)
                    .summary("stale loop state")
                    .build());

            session.compact();

            assertTrue(session.drainLoopDecisions().isEmpty());
            assertTrue(session.drainAutoCompactResults().isEmpty());
            assertTrue(session.drainAutoCompactErrors().isEmpty());
        }
    }

    private static final class CompactionAwareModelClient implements AgentModelClient {

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (prompt != null && prompt.getSystemPrompt() != null
                    && prompt.getSystemPrompt().contains("context checkpoint summaries")) {
                return AgentModelResult.builder()
                        .outputText("{\n"
                                + "  \"goal\": \"Continue the coding task.\",\n"
                                + "  \"constraints\": [\"Preserve exact file paths.\"],\n"
                                + "  \"progress\": {\n"
                                + "    \"done\": [\"Compacted old messages.\"],\n"
                                + "    \"inProgress\": [\"Continue with the latest kept context.\"],\n"
                                + "    \"blocked\": []\n"
                                + "  },\n"
                                + "  \"keyDecisions\": [\"**Compaction**: Summarized older messages into a checkpoint.\"],\n"
                                + "  \"nextSteps\": [\"Continue the coding task from the latest workspace state.\"],\n"
                                + "  \"criticalContext\": [\"Keep using the same workspace and session.\"]\n"
                                + "}")
                        .build();
            }
            String text = findLastUserText(prompt);
            if (text.contains("very long analysis")) {
                String output = repeat("long-output-", 80);
                return AgentModelResult.builder()
                        .outputText(output)
                        .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", output)))
                        .build();
            }
            return AgentModelResult.builder()
                    .outputText("echo")
                    .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", "echo")))
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = create(prompt);
            if (listener != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }

        private String findLastUserText(AgentPrompt prompt) {
            if (prompt == null || prompt.getItems() == null || prompt.getItems().isEmpty()) {
                return "";
            }
            Object last = prompt.getItems().get(prompt.getItems().size() - 1);
            return last == null ? "" : String.valueOf(last);
        }

        private String repeat(String text, int times) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < times; i++) {
                builder.append(text);
            }
            return builder.toString();
        }
    }

    private MemorySnapshot firstMemorySnapshot() {
        return MemorySnapshot.from(Collections.<Object>singletonList(AgentInputItem.userMessage("hello")), null);
    }

    private AgentToolRegistry singleToolRegistry(String toolName) {
        Tool.Function function = new Tool.Function();
        function.setName(toolName);
        function.setDescription("Stub tool for testing");
        return new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", function)));
    }

    private ToolExecutor largeToolExecutor() {
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return repeat("tool-output-", 260);
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

    private String findLastUserText(AgentPrompt prompt) {
        if (prompt == null || prompt.getItems() == null || prompt.getItems().isEmpty()) {
            return "";
        }
        Object last = prompt.getItems().get(prompt.getItems().size() - 1);
        return last == null ? "" : String.valueOf(last);
    }

    private static final class QueueModelClient implements AgentModelClient {

        private final Deque<AgentModelResult> results = new ArrayDeque<AgentModelResult>();
        private int summaryPromptCount;

        private void enqueue(AgentModelResult result) {
            results.addLast(result);
        }

        private int getSummaryPromptCount() {
            return summaryPromptCount;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (prompt != null && prompt.getSystemPrompt() != null
                    && prompt.getSystemPrompt().contains("context checkpoint summaries")) {
                summaryPromptCount += 1;
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

    private final class FailingCompactionModelClient implements AgentModelClient {

        private int summaryPromptCount;

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (prompt != null && prompt.getSystemPrompt() != null
                    && prompt.getSystemPrompt().contains("context checkpoint summaries")) {
                summaryPromptCount += 1;
                throw new IllegalStateException("summary model unavailable");
            }
            String output = repeat("long-output-", 40);
            return AgentModelResult.builder()
                    .outputText(output)
                    .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", output)))
                    .build();
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

        private int getSummaryPromptCount() {
            return summaryPromptCount;
        }
    }

    private final class PromptTooLongOnceCompactionModelClient implements AgentModelClient {

        private final int promptTooLongThreshold;
        private int summaryPromptCount;
        private int promptTooLongFailures;

        private PromptTooLongOnceCompactionModelClient(int promptTooLongThreshold) {
            this.promptTooLongThreshold = promptTooLongThreshold;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (prompt != null && prompt.getSystemPrompt() != null
                    && prompt.getSystemPrompt().contains("context checkpoint summaries")) {
                summaryPromptCount += 1;
                String text = findLastUserText(prompt);
                if (promptTooLongFailures == 0 && text.length() >= promptTooLongThreshold) {
                    promptTooLongFailures += 1;
                    throw new IllegalStateException("Prompt too long for compact summary.");
                }
                return AgentModelResult.builder()
                        .outputText("{\n"
                                + "  \"goal\": \"Continue the coding task.\",\n"
                                + "  \"constraints\": [\"Retry summary when needed.\"],\n"
                                + "  \"progress\": {\n"
                                + "    \"done\": [\"Recovered compaction after a prompt-too-long retry.\"],\n"
                                + "    \"inProgress\": [\"Continue from compacted context.\"],\n"
                                + "    \"blocked\": []\n"
                                + "  },\n"
                                + "  \"keyDecisions\": [\"Use retry slicing when the summary prompt exceeds context.\"],\n"
                                + "  \"nextSteps\": [\"Continue from the compacted checkpoint.\"],\n"
                                + "  \"criticalContext\": [\"Retry path succeeded.\"]\n"
                                + "}")
                        .build();
            }
            String output = repeat("session-output-", 90);
            return AgentModelResult.builder()
                    .outputText(output)
                    .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", output)))
                    .build();
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

        private int getSummaryPromptCount() {
            return summaryPromptCount;
        }

        private int getPromptTooLongFailures() {
            return promptTooLongFailures;
        }
    }

    private final class AlwaysPromptTooLongCompactionModelClient implements AgentModelClient {

        private int summaryPromptCount;

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (prompt != null && prompt.getSystemPrompt() != null
                    && prompt.getSystemPrompt().contains("context checkpoint summaries")) {
                summaryPromptCount += 1;
                throw new IllegalStateException("Maximum context length exceeded for compact summary.");
            }
            String output = repeat("session-output-", 90);
            return AgentModelResult.builder()
                    .outputText(output)
                    .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", output)))
                    .build();
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

        private int getSummaryPromptCount() {
            return summaryPromptCount;
        }
    }

    private final class FirstSummaryThenFailingCompactionModelClient implements AgentModelClient {

        private int summaryPromptCount;

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (prompt != null && prompt.getSystemPrompt() != null
                    && prompt.getSystemPrompt().contains("context checkpoint summaries")) {
                summaryPromptCount += 1;
                if (summaryPromptCount == 1) {
                    return AgentModelResult.builder()
                            .outputText("{\n"
                                    + "  \"goal\": \"Continue the coding task.\",\n"
                                    + "  \"constraints\": [\"Preserve exact file paths.\"],\n"
                                    + "  \"progress\": {\n"
                                    + "    \"done\": [\"Created the initial checkpoint.\"],\n"
                                    + "    \"inProgress\": [\"Wait for the next delta.\"],\n"
                                    + "    \"blocked\": []\n"
                                    + "  },\n"
                                    + "  \"keyDecisions\": [\"Use checkpoint + delta updates.\"],\n"
                                    + "  \"nextSteps\": [\"Continue from the next user change.\"],\n"
                                    + "  \"criticalContext\": [\"Checkpoint already exists before the next compact.\"]\n"
                                    + "}")
                            .build();
                }
                throw new IllegalStateException("summary model unavailable");
            }
            return AgentModelResult.builder()
                    .outputText("echo")
                    .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", "echo")))
                    .build();
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

    private final class FirstSummaryThenAggressiveFailingCompactionModelClient implements AgentModelClient {

        private int summaryPromptCount;

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (prompt != null && prompt.getSystemPrompt() != null
                    && prompt.getSystemPrompt().contains("context checkpoint summaries")) {
                summaryPromptCount += 1;
                if (summaryPromptCount == 1 || summaryPromptCount == 2) {
                    return AgentModelResult.builder()
                            .outputText("{\n"
                                    + "  \"goal\": \"Continue the coding task.\",\n"
                                    + "  \"constraints\": [\"Preserve exact file paths.\"],\n"
                                    + "  \"progress\": {\n"
                                    + "    \"done\": [\"Created the initial checkpoint.\"],\n"
                                    + "    \"inProgress\": [\"Handle the recent large delta.\"],\n"
                                    + "    \"blocked\": []\n"
                                    + "  },\n"
                                    + "  \"keyDecisions\": [\"Use checkpoint + delta updates.\"],\n"
                                    + "  \"nextSteps\": [\"Continue from the next user change.\"],\n"
                                    + "  \"criticalContext\": [\"Checkpoint already exists before aggressive compact.\"]\n"
                                    + "}")
                            .build();
                }
                throw new IllegalStateException("summary model unavailable");
            }
            String text = findLastUserText(prompt);
            if (text.contains("very large follow-up change")) {
                String output = repeat("large-follow-up-output-", 240);
                return AgentModelResult.builder()
                        .outputText(output)
                        .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", output)))
                        .build();
            }
            return AgentModelResult.builder()
                    .outputText("echo")
                    .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", "echo")))
                    .build();
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

        private int getSummaryPromptCount() {
            return summaryPromptCount;
        }
    }
}
