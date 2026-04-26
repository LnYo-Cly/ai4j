package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.cli.session.DefaultCodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.InMemoryCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.InMemorySessionEventStore;
import io.github.lnyocly.ai4j.coding.definition.CodingSessionMode;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLink;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import io.github.lnyocly.ai4j.coding.task.CodingTask;
import io.github.lnyocly.ai4j.coding.task.CodingTaskProgress;
import io.github.lnyocly.ai4j.coding.task.CodingTaskStatus;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

public class CodingTaskSessionEventBridgeTest {

    @Test
    public void shouldAppendCreatedAndUpdatedTaskEvents() throws Exception {
        DefaultCodingSessionManager sessionManager = new DefaultCodingSessionManager(
                new InMemoryCodingSessionStore(Paths.get("(memory-sessions)")),
                new InMemorySessionEventStore()
        );
        CodingTaskSessionEventBridge bridge = new CodingTaskSessionEventBridge(sessionManager);

        CodingTask task = CodingTask.builder()
                .taskId("task-1")
                .definitionName("explore")
                .parentSessionId("session-1")
                .childSessionId("child-1")
                .background(true)
                .status(CodingTaskStatus.QUEUED)
                .progress(CodingTaskProgress.builder()
                        .phase("queued")
                        .message("Task queued for execution.")
                        .percent(0)
                        .updatedAtEpochMs(System.currentTimeMillis())
                        .build())
                .createdAtEpochMs(System.currentTimeMillis())
                .build();
        CodingSessionLink link = CodingSessionLink.builder()
                .linkId("link-1")
                .taskId("task-1")
                .definitionName("explore")
                .parentSessionId("session-1")
                .childSessionId("child-1")
                .sessionMode(CodingSessionMode.FORK)
                .background(true)
                .createdAtEpochMs(System.currentTimeMillis())
                .build();

        bridge.onTaskCreated(task, link);
        bridge.onTaskUpdated(task.toBuilder()
                .status(CodingTaskStatus.COMPLETED)
                .outputText("delegate complete")
                .progress(task.getProgress().toBuilder()
                        .phase("completed")
                        .message("Delegated session completed.")
                        .percent(100)
                        .build())
                .build());

        List<SessionEvent> events = sessionManager.listEvents("session-1", null, null);
        Assert.assertEquals(2, events.size());
        Assert.assertEquals(SessionEventType.TASK_CREATED, events.get(0).getType());
        Assert.assertEquals(SessionEventType.TASK_UPDATED, events.get(1).getType());
        Assert.assertEquals("task-1", events.get(0).getPayload().get("taskId"));
        Assert.assertEquals("fork", events.get(0).getPayload().get("sessionMode"));
        Assert.assertEquals("completed", events.get(1).getPayload().get("status"));
        Assert.assertEquals("delegate complete", events.get(1).getPayload().get("output"));
    }
}
