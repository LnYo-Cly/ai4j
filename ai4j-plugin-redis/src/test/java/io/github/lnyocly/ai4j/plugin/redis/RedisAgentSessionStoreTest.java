package io.github.lnyocly.ai4j.plugin.redis;

import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.session.AgentSessionMetadata;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class RedisAgentSessionStoreTest {

    @Test
    public void savesLoadsListsDeletesAndExpiresSessions() {
        InMemoryRedisClientAdapter client = new InMemoryRedisClientAdapter();
        RedisAgentSessionStore store = RedisAgentSessionStore.builder()
                .client(client)
                .keyPrefix("app:sessions:")
                .ttlSeconds(60L)
                .build();

        client.set("unrelated:key", "keep-me");

        store.save(snapshot("sess-b", 10L));
        store.save(snapshot("sess-a", 20L));
        store.save(snapshot("sess-a", 30L)); // upsert

        AgentSessionSnapshot loadedA = store.load("sess-a");
        Assert.assertNotNull(loadedA);
        Assert.assertEquals("sess-a", loadedA.getSessionId());
        Assert.assertEquals(30L, loadedA.getMetadata().getCreatedAtEpochMs());
        Assert.assertEquals("hello", loadedA.getMemory().getItems().get(0));

        List<String> ids = store.listSessionIds();
        Assert.assertEquals(Arrays.asList("sess-a", "sess-b"), ids);

        Assert.assertTrue(store.delete("sess-b"));
        Assert.assertFalse(store.delete("sess-b"));
        Assert.assertNull(store.load("sess-b"));

        // Logical TTL expiration without Thread.sleep
        client.advanceSeconds(61L);
        Assert.assertNull(store.load("sess-a"));
        Assert.assertTrue(store.listSessionIds().isEmpty());
        Assert.assertEquals("keep-me", client.get("unrelated:key"));
    }

    private static AgentSessionSnapshot snapshot(String sessionId, long createdAt) {
        AgentSessionSnapshot snap = new AgentSessionSnapshot();
        snap.setMetadata(new AgentSessionMetadata(
                sessionId,
                createdAt,
                createdAt + 1L,
                new LinkedHashMap<String, Object>()
        ));
        snap.setMemory(MemorySnapshot.from(Collections.<Object>singletonList("hello"), "summary"));
        return snap;
    }
}
