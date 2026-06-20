package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.session.AgentSessionEvent;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.session.InMemoryAgentSessionStore;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AgentSessionRuntimeContainerTest {

    @Test
    public void newSessionCreatesIsolatedMetadataAndMemory() throws Exception {
        Agent agent = testAgent();

        io.github.lnyocly.ai4j.agent.AgentSession first = agent.newSession();
        io.github.lnyocly.ai4j.agent.AgentSession second = agent.newSession();

        Assert.assertNotNull(first.getSessionId());
        Assert.assertNotNull(second.getSessionId());
        Assert.assertNotEquals(first.getSessionId(), second.getSessionId());

        first.putMetadata("project", "alpha");
        Map<String, Object> attrs = first.getMetadataAttributes();
        attrs.put("project", "mutated");

        Assert.assertEquals("alpha", first.getMetadata("project"));
        Assert.assertNull(second.getMetadata("project"));

        first.run("hello");
        Assert.assertTrue(first.snapshot().getMemory().getItems().size() > 0);
        Assert.assertEquals(0, second.snapshot().getMemory().getItems().size());
    }

    @Test
    public void sessionEventLogRecordsRuntimeEvents() throws Exception {
        Agent agent = testAgent();
        io.github.lnyocly.ai4j.agent.AgentSession session = agent.newSession();

        AgentResult result = session.run(AgentRequest.builder().input("hi").build());

        Assert.assertEquals("session-answer", result.getOutputText());
        List<AgentSessionEvent> events = session.getEventLog().getEvents();
        Assert.assertTrue(events.size() >= 5);
        Assert.assertEquals(1L, events.get(0).getSequence());
        Assert.assertTrue(hasType(events, AgentEventType.STEP_START));
        Assert.assertTrue(hasType(events, AgentEventType.MODEL_REQUEST));
        Assert.assertTrue(hasType(events, AgentEventType.MODEL_RESPONSE));
        Assert.assertTrue(hasType(events, AgentEventType.FINAL_OUTPUT));
        Assert.assertTrue(hasType(events, AgentEventType.STEP_END));
    }

    @Test
    public void snapshotAndRestorePreserveMemoryMetadataAndEvents() throws Exception {
        Agent agent = testAgent();
        io.github.lnyocly.ai4j.agent.AgentSession session = agent.newSession();
        session.putMetadata("owner", "runtime");
        session.run("remember this");

        AgentSessionSnapshot snapshot = session.snapshot();
        long updatedAt = snapshot.getMetadata().getUpdatedAtEpochMs();

        io.github.lnyocly.ai4j.agent.AgentSession restored = agent.newSession(snapshot);

        Assert.assertEquals(session.getSessionId(), restored.getSessionId());
        Assert.assertEquals("runtime", restored.getMetadata("owner"));
        Assert.assertEquals(updatedAt, restored.getMetadata().getUpdatedAtEpochMs());
        Assert.assertEquals(snapshot.getEvents().size(), restored.getEventLog().getEvents().size());
        Assert.assertEquals(snapshot.getMemory().getItems().size(), restored.snapshot().getMemory().getItems().size());
    }

    @Test
    public void sessionStoreCanSaveAndResume() throws Exception {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        Agent agent = testAgent(store);
        io.github.lnyocly.ai4j.agent.AgentSession session = agent.newSession();
        session.putMetadata("ticket", "P0-A");
        session.run("save me");

        session.save();

        Assert.assertEquals(Arrays.asList(session.getSessionId()), store.listSessionIds());
        io.github.lnyocly.ai4j.agent.AgentSession resumed = agent.resumeSession(session.getSessionId());
        Assert.assertEquals(session.getSessionId(), resumed.getSessionId());
        Assert.assertEquals("P0-A", resumed.getMetadata("ticket"));
        Assert.assertEquals(session.getEventLog().getEvents().size(), resumed.getEventLog().getEvents().size());
        Assert.assertEquals(session.snapshot().getMemory().getItems().size(), resumed.snapshot().getMemory().getItems().size());
    }

    @Test
    public void sessionSnapshotUsesDefensiveCopies() throws Exception {
        Agent agent = testAgent();
        io.github.lnyocly.ai4j.agent.AgentSession session = agent.newSession();
        session.putMetadata("safe", "yes");
        session.run("copy");

        AgentSessionSnapshot snapshot = session.snapshot();
        snapshot.getMetadata().putAttribute("safe", "no");
        List<AgentSessionEvent> copiedEvents = snapshot.getEvents();
        copiedEvents.get(0).getEvent().setMessage("mutated");
        copiedEvents.clear();
        MemorySnapshot memory = snapshot.getMemory();
        memory.getItems().clear();

        Assert.assertEquals("yes", session.getMetadata("safe"));
        Assert.assertTrue(session.getEventLog().getEvents().size() > 0);
        Assert.assertNotEquals("mutated", session.getEventLog().getEvents().get(0).getEvent().getMessage());
        Assert.assertTrue(session.snapshot().getMemory().getItems().size() > 0);
    }

    private static boolean hasType(List<AgentSessionEvent> events, AgentEventType type) {
        for (AgentSessionEvent event : events) {
            if (event != null && event.getEvent() != null && event.getEvent().getType() == type) {
                return true;
            }
        }
        return false;
    }

    private static Agent testAgent() {
        return testAgent(null);
    }

    private static Agent testAgent(InMemoryAgentSessionStore store) {
        return Agents.react()
                .modelClient(new StaticModelClient())
                .memorySupplier(InMemoryAgentMemory::new)
                .options(AgentOptions.builder().maxSteps(2).build())
                .model("test-model")
                .sessionStore(store)
                .build();
    }

    private static class StaticModelClient implements AgentModelClient {
        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return result();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return result();
        }

        private AgentModelResult result() {
            return AgentModelResult.builder()
                    .outputText("session-answer")
                    .memoryItems(new ArrayList<Object>())
                    .toolCalls(new ArrayList<AgentToolCall>())
                    .build();
        }
    }
}
