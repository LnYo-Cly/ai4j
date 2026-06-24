package io.github.lnyocly.ai4j.agent.session;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Round-trip tests for {@link FileAgentSessionStore}. A new store instance on the same directory
 * stands in for a process restart — the snapshot must survive it, with no database required.
 */
public class FileAgentSessionStoreTest {

    private AgentSessionSnapshot snapshot(String id, long createdAt) {
        AgentSessionSnapshot snap = new AgentSessionSnapshot();
        snap.setMetadata(new AgentSessionMetadata(id, createdAt, createdAt + 1,
                new LinkedHashMap<String, Object>()));
        return snap;
    }

    @Test
    public void saveAndLoadSurvivesNewStoreInstance() throws Exception {
        Path dir = Files.createTempDirectory("ai4j-file-store-");
        new FileAgentSessionStore(dir).save(snapshot("sess-1", 100L));

        // new store instance on the same dir = "restart"
        AgentSessionSnapshot loaded = new FileAgentSessionStore(dir).load("sess-1");
        assertNotNull(loaded);
        assertEquals("sess-1", loaded.getSessionId());
        assertEquals(100L, loaded.getMetadata().getCreatedAtEpochMs());
    }

    @Test
    public void loadReturnsNullForUnknownSession() throws Exception {
        Path dir = Files.createTempDirectory("ai4j-file-store-");
        assertNull(new FileAgentSessionStore(dir).load("no-such-session"));
    }

    @Test
    public void saveIsUpsertAndDeleteAndListWork() throws Exception {
        Path dir = Files.createTempDirectory("ai4j-file-store-");
        FileAgentSessionStore store = new FileAgentSessionStore(dir);
        store.save(snapshot("s1", 1L));
        store.save(snapshot("s1", 9L)); // upsert (overwrite)
        store.save(snapshot("s2", 2L));

        assertEquals(2, store.listSessionIds().size());
        assertEquals("upsert must overwrite, not duplicate", 9L, store.load("s1").getMetadata().getCreatedAtEpochMs());

        assertTrue(store.delete("s1"));
        assertEquals(1, store.listSessionIds().size());
        assertNull("deleted session must not load", store.load("s1"));
    }
}
