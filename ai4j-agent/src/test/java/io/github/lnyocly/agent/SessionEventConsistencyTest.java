package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.compact.CompactPolicy;
import io.github.lnyocly.ai4j.agent.compact.CompactResult;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.session.SessionEventProjector;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Asserts the session event contract: every memory mutation emits an event, so folding the
 * event log reconstructs the exact memory state. If a new mutation path forgets to publish,
 * these tests fail.
 */
public class SessionEventConsistencyTest {

    @Test
    public void projection_matches_memory_after_tool_loop() throws Exception {
        Deque<AgentModelResult> queue = new ArrayDeque<>();
        queue.add(resultWithToolCall("call_1", "echo", "{}"));
        queue.add(resultWithText("done"));

        Agent agent = Agents.react()
                .modelClient(new QueueModelClient(queue))
                .toolExecutor(echoExecutor())
                .memorySupplier(new java.util.function.Supplier<AgentMemory>() {
                    @Override
                    public AgentMemory get() {
                        return new InMemoryAgentMemory();
                    }
                })
                .options(AgentOptions.builder().maxSteps(4).build())
                .model("test-model")
                .build();

        AgentSession session = agent.newSession();
        AgentResult result = session.run(AgentRequest.builder().input("hi").build());

        Assert.assertEquals("done", result.getOutputText());
        assertProjectionMatches(session);
    }

    @Test
    public void projection_matches_memory_after_compact() throws Exception {
        Deque<AgentModelResult> queue = new ArrayDeque<>();
        queue.add(resultWithToolCall("call_1", "echo", "{}"));
        queue.add(resultWithText("done"));

        Agent agent = Agents.react()
                .modelClient(new QueueModelClient(queue))
                .toolExecutor(echoExecutor())
                .memorySupplier(new java.util.function.Supplier<AgentMemory>() {
                    @Override
                    public AgentMemory get() {
                        return new InMemoryAgentMemory();
                    }
                })
                .options(AgentOptions.builder().maxSteps(4).build())
                .model("test-model")
                .build();

        AgentSession session = agent.newSession();
        session.run(AgentRequest.builder().input("hi").build());

        session.compact(new CompactPolicy() {
            @Override
            public boolean shouldCompact(MemorySnapshot snapshot) {
                return true;
            }

            @Override
            public CompactResult compact(MemorySnapshot snapshot) {
                List<Object> kept = new ArrayList<>();
                if (snapshot.getItems() != null && !snapshot.getItems().isEmpty()) {
                    kept.add(snapshot.getItems().get(snapshot.getItems().size() - 1));
                }
                return CompactResult.builder()
                        .memory(MemorySnapshot.from(kept, "compressed-summary"))
                        .summary("compressed-summary")
                        .build();
            }
        });

        AgentMemory memory = session.getContext().getMemory();
        Assert.assertEquals("compressed-summary", memory.getSummary());
        Assert.assertEquals(1, memory.snapshot().getItems().size());
        assertProjectionMatches(session);
    }

    @Test
    public void projection_matches_memory_after_replace_tool_output() throws Exception {
        Deque<AgentModelResult> queue = new ArrayDeque<>();
        queue.add(resultWithToolCall("call_1", "echo", "{}"));
        queue.add(resultWithText("done"));

        Agent agent = Agents.react()
                .modelClient(new QueueModelClient(queue))
                .toolExecutor(echoExecutor())
                .memorySupplier(new java.util.function.Supplier<AgentMemory>() {
                    @Override
                    public AgentMemory get() {
                        return new InMemoryAgentMemory();
                    }
                })
                .options(AgentOptions.builder().maxSteps(4).build())
                .model("test-model")
                .build();

        AgentSession session = agent.newSession();
        session.run(AgentRequest.builder().input("hi").build());

        Assert.assertTrue(session.replaceToolOutput("call_1", "patched-output"));
        assertProjectionMatches(session);
        Assert.assertTrue(containsToolOutput(
                SessionEventProjector.deriveItems(session.getEventLog().getEvents()),
                "call_1", "patched-output"));
    }

    @Test
    public void projection_matches_memory_after_snapshot_restore() throws Exception {
        Deque<AgentModelResult> queue = new ArrayDeque<>();
        queue.add(resultWithToolCall("call_1", "echo", "{}"));
        queue.add(resultWithText("done"));

        Agent agent = Agents.react()
                .modelClient(new QueueModelClient(queue))
                .toolExecutor(echoExecutor())
                .memorySupplier(new java.util.function.Supplier<AgentMemory>() {
                    @Override
                    public AgentMemory get() {
                        return new InMemoryAgentMemory();
                    }
                })
                .options(AgentOptions.builder().maxSteps(4).build())
                .model("test-model")
                .build();

        AgentSession first = agent.newSession();
        first.run(AgentRequest.builder().input("hi").build());

        AgentSession resumed = agent.newSession(first.snapshot());
        MemorySnapshot resumedMemory = resumed.getContext().getMemory().snapshot();
        MemorySnapshot projected = SessionEventProjector.deriveSnapshot(resumed.getEventLog().getEvents());
        Assert.assertEquals(resumedMemory.getItems(), projected.getItems());
        Assert.assertEquals(resumedMemory.getSummary(), projected.getSummary());
    }

    private void assertProjectionMatches(AgentSession session) {
        AgentMemory memory = session.getContext().getMemory();
        MemorySnapshot expected = memory.snapshot();
        MemorySnapshot projected = SessionEventProjector.deriveSnapshot(session.getEventLog().getEvents());
        Assert.assertEquals(expected.getItems(), projected.getItems());
        Assert.assertEquals(expected.getSummary(), projected.getSummary());
    }

    private boolean containsToolOutput(List<Object> items, String callId, String output) {
        for (Object item : items) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            if ("function_call_output".equals(map.get("type"))
                    && callId.equals(map.get("call_id"))
                    && output.equals(map.get("output"))) {
                return true;
            }
        }
        return false;
    }

    private ToolExecutor echoExecutor() {
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "echo-output";
            }
        };
    }

    private AgentModelResult resultWithText(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>())
                .build();
    }

    private AgentModelResult resultWithToolCall(String callId, String name, String arguments) {
        AgentToolCall call = AgentToolCall.builder()
                .callId(callId)
                .name(name)
                .arguments(arguments)
                .type("function_call")
                .build();
        return AgentModelResult.builder()
                .toolCalls(Arrays.asList(call))
                .memoryItems(Arrays.<Object>asList(
                        AgentInputItem.assistantToolCallsMessage("", Arrays.asList(call))))
                .build();
    }

    private static final class QueueModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> queue;

        private QueueModelClient(Deque<AgentModelResult> queue) {
            this.queue = queue;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return queue.isEmpty() ? resultWithTextStatic("done") : queue.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt,
                                             io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static AgentModelResult resultWithTextStatic(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>())
                .build();
    }
}
