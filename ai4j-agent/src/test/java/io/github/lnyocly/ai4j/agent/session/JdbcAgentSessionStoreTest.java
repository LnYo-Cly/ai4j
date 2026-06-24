package io.github.lnyocly.ai4j.agent.session;

import org.junit.Test;

import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * H2 round-trip tests for {@link JdbcAgentSessionStore}. A new store instance on the same jdbc URL
 * stands in for a process restart — the snapshot must survive it.
 */
public class JdbcAgentSessionStoreTest {

    private String jdbcUrl(String suffix) {
        return "jdbc:h2:mem:ai4j_session_store_" + suffix + ";MODE=MYSQL;DB_CLOSE_DELAY=-1";
    }

    private AgentSessionSnapshot snapshot(String id, long createdAt) {
        AgentSessionSnapshot snap = new AgentSessionSnapshot();
        snap.setMetadata(new AgentSessionMetadata(id, createdAt, createdAt + 1,
                new LinkedHashMap<String, Object>()));
        return snap;
    }

    @Test
    public void saveAndLoadSurvivesNewStoreInstance() {
        String url = jdbcUrl("persist");
        new JdbcAgentSessionStore(url).save(snapshot("sess-1", 100L));

        // new store instance = "restart"
        AgentSessionSnapshot loaded = new JdbcAgentSessionStore(url).load("sess-1");
        assertNotNull(loaded);
        assertEquals("sess-1", loaded.getSessionId());
        assertEquals(100L, loaded.getMetadata().getCreatedAtEpochMs());
    }

    @Test
    public void loadReturnsNullForUnknownSession() {
        assertNull(new JdbcAgentSessionStore(jdbcUrl("miss")).load("no-such-session"));
    }

    @Test
    public void saveIsUpsertAndDeleteAndListWork() {
        String url = jdbcUrl("upsert");
        JdbcAgentSessionStore store = new JdbcAgentSessionStore(url);
        store.save(snapshot("s1", 1L));
        store.save(snapshot("s1", 9L)); // upsert: same id, new content
        store.save(snapshot("s2", 2L));

        assertEquals(2, store.listSessionIds().size());
        assertEquals("upsert must replace, not duplicate", 9L, store.load("s1").getMetadata().getCreatedAtEpochMs());

        assertTrue(store.delete("s1"));
        assertEquals(1, store.listSessionIds().size());
        assertNull("deleted session must not load", store.load("s1"));
    }
}
