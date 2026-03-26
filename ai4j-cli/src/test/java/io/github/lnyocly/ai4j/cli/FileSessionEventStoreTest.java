package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileSessionEventStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldAppendListAndDeleteSessionEvents() throws Exception {
        Path eventDir = temporaryFolder.newFolder("session-events").toPath();
        FileSessionEventStore store = new FileSessionEventStore(eventDir);

        store.append(SessionEvent.builder()
                .sessionId("session-alpha")
                .type(SessionEventType.SESSION_CREATED)
                .timestamp(100L)
                .summary("created")
                .build());
        store.append(SessionEvent.builder()
                .sessionId("session-alpha")
                .type(SessionEventType.USER_MESSAGE)
                .timestamp(200L)
                .summary("user")
                .build());
        store.append(SessionEvent.builder()
                .sessionId("session-alpha")
                .type(SessionEventType.ASSISTANT_MESSAGE)
                .timestamp(300L)
                .summary("assistant")
                .build());

        List<SessionEvent> recent = store.list("session-alpha", 2, null);
        List<SessionEvent> offset = store.list("session-alpha", 1, 1L);

        assertEquals(2, recent.size());
        assertEquals(SessionEventType.USER_MESSAGE, recent.get(0).getType());
        assertEquals(SessionEventType.ASSISTANT_MESSAGE, recent.get(1).getType());

        assertEquals(1, offset.size());
        assertEquals(SessionEventType.USER_MESSAGE, offset.get(0).getType());

        store.delete("session-alpha");
        assertTrue(store.list("session-alpha", 10, null).isEmpty());
    }
}
