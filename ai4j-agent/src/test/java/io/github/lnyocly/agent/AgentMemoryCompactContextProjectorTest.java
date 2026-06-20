package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionRequest;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.compact.CompactPolicyMemoryCompressor;
import io.github.lnyocly.ai4j.agent.compact.CompactResult;
import io.github.lnyocly.ai4j.agent.compact.StructuredSummaryCompactPolicy;
import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.context.ContextProjection;
import io.github.lnyocly.ai4j.agent.context.ContextReport;
import io.github.lnyocly.ai4j.agent.context.DefaultContextProjector;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.session.InMemoryAgentSessionStore;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AgentMemoryCompactContextProjectorTest {

    @Test
    public void defaultContextProjectorKeepsPinnedPrefixAndRecentTail() {
        DefaultContextProjector projector = new DefaultContextProjector();
        List<Object> source = Arrays.<Object>asList("system-summary", "old-1", "old-2", "recent-1", "recent-2");

        ContextProjection projection = projector.project(source, ContextBudget.builder()
                .maxItems(3)
                .pinnedPrefixItems(1)
                .build());

        Assert.assertEquals(Arrays.<Object>asList("system-summary", "recent-1", "recent-2"), projection.getItems());
        ContextReport report = projection.getReport();
        Assert.assertEquals(5, report.getSourceItemCount());
        Assert.assertEquals(3, report.getProjectedItemCount());
        Assert.assertEquals(2, report.getDroppedItemCount());
        Assert.assertTrue(report.isItemLimitApplied());
        Assert.assertFalse(report.isCharacterLimitApplied());
    }

    @Test
    public void runtimeUsesContextProjectorForPromptItemsAndPublishesReport() throws Exception {
        CapturingModelClient modelClient = new CapturingModelClient("projected-answer");
        final List<AgentEvent> events = new ArrayList<AgentEvent>();
        AgentEventPublisher publisher = new AgentEventPublisher();
        publisher.addListener(new AgentListener() {
            @Override
            public void onEvent(AgentEvent event) {
                events.add(event);
            }
        });
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .memorySupplier(InMemoryAgentMemory::new)
                .options(AgentOptions.builder().maxSteps(1).build())
                .model("test-model")
                .contextProjector(new DefaultContextProjector())
                .contextBudget(ContextBudget.builder().maxItems(2).pinnedPrefixItems(0).build())
                .eventPublisher(publisher)
                .build();
        AgentSession session = agent.newSession();
        session.getContext().getMemory().addUserInput("old-1");
        session.getContext().getMemory().addUserInput("old-2");
        session.getContext().getMemory().addUserInput("recent-before-run");

        AgentResult result = session.run("current-input");

        Assert.assertEquals("projected-answer", result.getOutputText());
        Assert.assertNotNull(modelClient.lastPrompt);
        Assert.assertEquals(2, modelClient.lastPrompt.getItems().size());
        Assert.assertTrue(String.valueOf(modelClient.lastPrompt.getItems().get(0)).contains("recent-before-run"));
        Assert.assertTrue(String.valueOf(modelClient.lastPrompt.getItems().get(1)).contains("current-input"));
        AgentEvent compressEvent = firstEvent(events, AgentEventType.MEMORY_COMPRESS);
        Assert.assertNotNull(compressEvent);
        Assert.assertTrue(compressEvent.getPayload() instanceof ContextReport);
        Assert.assertEquals(0L, compressEvent.getStep().longValue());
        Assert.assertEquals(2, ((ContextReport) compressEvent.getPayload()).getProjectedItemCount());
        Assert.assertTrue(hasType(session.getEventLog().getEvents(), AgentEventType.MEMORY_COMPRESS));
    }


    @Test
    public void codeActRuntimeAlsoUsesContextProjectorForPromptItems() throws Exception {
        CapturingModelClient modelClient = new CapturingModelClient("{\"type\":\"final\",\"output\":\"codeact-answer\"}");
        Agent agent = Agents.codeAct()
                .modelClient(modelClient)
                .memorySupplier(InMemoryAgentMemory::new)
                .codeExecutor(new NoopCodeExecutor())
                .options(AgentOptions.builder().maxSteps(1).build())
                .model("test-model")
                .contextProjector(new DefaultContextProjector())
                .contextBudget(ContextBudget.maxItems(1))
                .build();
        AgentSession session = agent.newSession();
        session.getContext().getMemory().addUserInput("old-codeact-context");

        AgentResult result = session.run("current-codeact-input");

        Assert.assertEquals("codeact-answer", result.getOutputText());
        Assert.assertNotNull(modelClient.lastPrompt);
        Assert.assertEquals(1, modelClient.lastPrompt.getItems().size());
        Assert.assertTrue(String.valueOf(modelClient.lastPrompt.getItems().get(0)).contains("current-codeact-input"));
        Assert.assertTrue(hasType(session.getEventLog().getEvents(), AgentEventType.MEMORY_COMPRESS));
    }

    @Test
    public void structuredSummaryCompactPolicyProducesMemorySummaryAndReport() {
        MemorySnapshot snapshot = MemorySnapshot.from(
                Arrays.<Object>asList("goal", "done", "pending", "decision", "recent"),
                "previous summary"
        );

        CompactResult result = new StructuredSummaryCompactPolicy(ContextBudget.builder()
                .maxItems(2)
                .pinnedPrefixItems(1)
                .build()).compact(snapshot);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getMemory());
        Assert.assertEquals(Arrays.<Object>asList("goal", "recent"), result.getMemory().getItems());
        Assert.assertTrue(result.getSummary().contains("AI4J_COMPACT_SUMMARY"));
        Assert.assertTrue(result.getSummary().contains("previous summary"));
        Assert.assertEquals(result.getSummary(), result.getMemory().getSummary());
        Assert.assertNotNull(result.getContextReport());
        Assert.assertEquals(3, result.getContextReport().getDroppedItemCount());
    }

    @Test
    public void compactPolicyMemoryCompressorKeepsLastStructuredResult() {
        CompactPolicyMemoryCompressor compressor = new CompactPolicyMemoryCompressor(
                new StructuredSummaryCompactPolicy(ContextBudget.maxItems(1))
        );
        MemorySnapshot compressed = compressor.compress(MemorySnapshot.from(Arrays.<Object>asList("one", "two", "three"), null));

        Assert.assertEquals(1, compressed.getItems().size());
        Assert.assertEquals("three", compressed.getItems().get(0));
        Assert.assertNotNull(compressor.getLastResult());
        Assert.assertEquals(2, compressor.getLastResult().getContextReport().getDroppedItemCount());
    }

    @Test
    public void sessionCompactStoresStateInSnapshotAndResume() throws Exception {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        Agent agent = Agents.react()
                .modelClient(new CapturingModelClient("ok"))
                .memorySupplier(InMemoryAgentMemory::new)
                .options(AgentOptions.builder().maxSteps(1).build())
                .model("test-model")
                .sessionStore(store)
                .build();
        AgentSession session = agent.newSession();
        session.getContext().getMemory().addUserInput("goal");
        session.getContext().getMemory().addUserInput("old");
        session.getContext().getMemory().addUserInput("recent");

        session.compact(new StructuredSummaryCompactPolicy(ContextBudget.builder()
                .maxItems(2)
                .pinnedPrefixItems(1)
                .build()));
        session.save();

        CompactResult last = session.getLastCompactResult();
        Assert.assertNotNull(last);
        List<Object> compactedItems = session.snapshot().getMemory().getItems();
        Assert.assertEquals(2, compactedItems.size());
        Assert.assertTrue(String.valueOf(compactedItems.get(0)).contains("goal"));
        Assert.assertTrue(String.valueOf(compactedItems.get(1)).contains("recent"));
        AgentSessionSnapshot storedSnapshot = store.load(session.getSessionId());
        Assert.assertNotNull(storedSnapshot.getCompactResult());
        Assert.assertEquals(1, storedSnapshot.getCompactResult().getContextReport().getDroppedItemCount());

        AgentSession resumed = agent.resumeSession(session.getSessionId());
        Assert.assertNotNull(resumed.getLastCompactResult());
        List<Object> resumedItems = resumed.snapshot().getMemory().getItems();
        Assert.assertEquals(2, resumedItems.size());
        Assert.assertTrue(String.valueOf(resumedItems.get(0)).contains("goal"));
        Assert.assertTrue(String.valueOf(resumedItems.get(1)).contains("recent"));
    }

    private static AgentEvent firstEvent(List<AgentEvent> events, AgentEventType type) {
        for (AgentEvent event : events) {
            if (event != null && event.getType() == type) {
                return event;
            }
        }
        return null;
    }

    private static boolean hasType(List<io.github.lnyocly.ai4j.agent.session.AgentSessionEvent> events, AgentEventType type) {
        for (io.github.lnyocly.ai4j.agent.session.AgentSessionEvent event : events) {
            if (event != null && event.getEvent() != null && event.getEvent().getType() == type) {
                return true;
            }
        }
        return false;
    }

    private static class NoopCodeExecutor implements CodeExecutor {
        @Override
        public CodeExecutionResult execute(CodeExecutionRequest request) {
            return CodeExecutionResult.builder().result("noop").build();
        }
    }

    private static class CapturingModelClient implements AgentModelClient {
        private final String output;
        private AgentPrompt lastPrompt;

        private CapturingModelClient(String output) {
            this.output = output;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            this.lastPrompt = prompt;
            return result();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            this.lastPrompt = prompt;
            return result();
        }

        private AgentModelResult result() {
            return AgentModelResult.builder()
                    .outputText(output)
                    .memoryItems(new ArrayList<Object>())
                    .toolCalls(new ArrayList<AgentToolCall>())
                    .build();
        }
    }
}

