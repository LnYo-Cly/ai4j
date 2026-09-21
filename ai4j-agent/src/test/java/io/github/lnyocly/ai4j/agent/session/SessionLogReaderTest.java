package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class SessionLogReaderTest {

    @Test
    public void list_read_events_search_work_over_store() {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        store.save(snapshot("s-1",
                event(1, AgentEventType.USER_INPUT, "u1"),
                event(2, AgentEventType.TOOL_RESULT, "t1"),
                event(3, AgentEventType.USER_INPUT, "u2")));
        store.save(snapshot("s-2", event(1, AgentEventType.USER_INPUT, "other")));

        SessionLogReader reader = SessionLogReader.of(store);

        Assert.assertEquals(2, reader.listSessionIds().size());
        Assert.assertNotNull(reader.read("s-1"));
        Assert.assertNull(reader.read("missing"));

        Assert.assertEquals(3, reader.events("s-1").size());
        List<AgentSessionEvent> inputs = reader.search("s-1", AgentEventType.USER_INPUT);
        Assert.assertEquals(2, inputs.size());
        Assert.assertEquals("u1", inputs.get(0).getEvent().getMessage());

        List<AgentSessionEvent> tools = reader.search("s-1", new Predicate<AgentSessionEvent>() {
            @Override
            public boolean test(AgentSessionEvent e) {
                return e.getEvent().getType() == AgentEventType.TOOL_RESULT;
            }
        });
        Assert.assertEquals(1, tools.size());
        Assert.assertEquals("t1", tools.get(0).getEvent().getMessage());
    }

    @Test
    public void project_folds_events_back_to_memory_snapshot() {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        store.save(snapshot("s-1", eventWithPayload(1, AgentEventType.USER_INPUT, "hello")));

        MemorySnapshot projected = SessionLogReader.of(store).project("s-1");
        Assert.assertNotNull(projected);
        Assert.assertEquals(1, projected.getItems().size());
    }

    @Test
    public void fork_writes_new_session_without_touching_source() {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        store.save(snapshot("s-1", event(1, AgentEventType.USER_INPUT, "u1")));

        SessionLogReader reader = SessionLogReader.of(store);
        AgentSessionSnapshot forked = reader.fork("s-1", "s-1-fork");

        Assert.assertEquals("s-1-fork", forked.getSessionId());
        Assert.assertNotNull(store.load("s-1-fork"));
        Assert.assertEquals("s-1", store.load("s-1").getSessionId());
        Assert.assertEquals(1, reader.events("s-1-fork").size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void missing_session_throws() {
        SessionLogReader.of(new InMemoryAgentSessionStore()).events("nope");
    }

    private static AgentSessionSnapshot snapshot(String sessionId, AgentSessionEvent... events) {
        AgentSessionMetadata metadata = new AgentSessionMetadata(sessionId, 1L, 1L, null);
        List<AgentSessionEvent> list = new ArrayList<AgentSessionEvent>();
        for (AgentSessionEvent e : events) {
            list.add(e);
        }
        return new AgentSessionSnapshot(metadata, null, list);
    }

    private static AgentSessionEvent event(long seq, AgentEventType type, String message) {
        AgentEvent event = AgentEvent.builder()
                .type(type)
                .message(message)
                .build();
        return new AgentSessionEvent(seq, seq, event);
    }

    private static AgentSessionEvent eventWithPayload(long seq, AgentEventType type, Object payload) {
        AgentEvent event = AgentEvent.builder()
                .type(type)
                .payload(payload)
                .build();
        return new AgentSessionEvent(seq, seq, event);
    }
}
