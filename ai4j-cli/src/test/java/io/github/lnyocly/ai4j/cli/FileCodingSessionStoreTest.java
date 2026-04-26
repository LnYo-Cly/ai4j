package io.github.lnyocly.ai4j.cli.session;

import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.coding.CodingSessionCheckpoint;
import io.github.lnyocly.ai4j.coding.CodingSessionCompactResult;
import io.github.lnyocly.ai4j.coding.CodingSessionState;
import io.github.lnyocly.ai4j.coding.process.BashProcessStatus;
import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FileCodingSessionStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldSaveLoadAndListSessions() throws Exception {
        Path sessionDir = temporaryFolder.newFolder("session-store").toPath();
        FileCodingSessionStore store = new FileCodingSessionStore(sessionDir);

        StoredCodingSession saved = store.save(StoredCodingSession.builder()
                .sessionId("session-alpha")
                .rootSessionId("session-alpha")
                .provider("zhipu")
                .protocol("chat")
                .model("GLM-4.5-Flash")
                .workspace("workspace-a")
                .summary("alpha summary")
                .memoryItemCount(2)
                .processCount(1)
                .activeProcessCount(0)
                .restoredProcessCount(1)
                .state(CodingSessionState.builder()
                        .sessionId("session-alpha")
                        .workspaceRoot("workspace-a")
                        .memorySnapshot(MemorySnapshot.from(
                                Arrays.<Object>asList(
                                        AgentInputItem.userMessage("hello"),
                                        AgentInputItem.message("assistant", "world")
                                ),
                                null
                        ))
                        .processCount(1)
                        .checkpoint(CodingSessionCheckpoint.builder()
                                .goal("Continue session-alpha")
                                .doneItems(Collections.singletonList("Saved workspace state"))
                                .build())
                        .latestCompactResult(CodingSessionCompactResult.builder()
                                .sessionId("session-alpha")
                                .beforeItemCount(12)
                                .afterItemCount(3)
                                .summary("checkpoint summary")
                                .automatic(false)
                                .strategy("checkpoint-delta")
                                .deltaItemCount(5)
                                .checkpointReused(true)
                                .checkpoint(CodingSessionCheckpoint.builder()
                                        .goal("Continue session-alpha")
                                        .nextSteps(Collections.singletonList("Resume the next coding step"))
                                        .build())
                                .build())
                        .autoCompactFailureCount(2)
                        .autoCompactCircuitBreakerOpen(true)
                        .processSnapshots(Collections.singletonList(StoredProcessSnapshot.builder()
                                .processId("proc_demo")
                                .command("echo ready")
                                .workingDirectory("workspace-a")
                                .status(BashProcessStatus.STOPPED)
                                .startedAt(System.currentTimeMillis())
                                .endedAt(System.currentTimeMillis())
                                .lastLogOffset(10L)
                                .lastLogPreview("[stdout] ready")
                                .restored(true)
                                .controlAvailable(false)
                                .build()))
                        .build())
                .build());

        StoredCodingSession loaded = store.load("session-alpha");
        List<StoredCodingSession> sessions = store.list();

        assertNotNull(saved.getStorePath());
        assertNotNull(loaded);
        assertEquals("session-alpha", loaded.getSessionId());
        assertEquals("session-alpha", loaded.getRootSessionId());
        assertEquals("alpha summary", loaded.getSummary());
        assertEquals(1, loaded.getProcessCount());
        assertEquals(1, loaded.getRestoredProcessCount());
        assertNotNull(loaded.getState().getCheckpoint());
        assertNotNull(loaded.getState().getLatestCompactResult());
        assertEquals("checkpoint-delta", loaded.getState().getLatestCompactResult().getStrategy());
        assertEquals(2, loaded.getState().getAutoCompactFailureCount());
        assertTrue(loaded.getState().isAutoCompactCircuitBreakerOpen());
        assertEquals(1, loaded.getState().getProcessSnapshots().size());
        assertEquals(1, sessions.size());
        assertEquals("session-alpha", sessions.get(0).getSessionId());
        assertTrue(sessions.get(0).getUpdatedAtEpochMs() >= sessions.get(0).getCreatedAtEpochMs());
    }
}
