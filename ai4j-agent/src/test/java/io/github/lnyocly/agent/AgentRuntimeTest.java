package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
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
import java.util.stream.Collectors;

public class AgentRuntimeTest {

    @Test
    public void test_tool_loop_and_output() throws Exception {
        Deque<AgentModelResult> queue = new ArrayDeque<>();
        queue.add(resultWithToolCall("call_1", "echo", "{}"));
        queue.add(resultWithText("done"));

        AgentModelClient modelClient = new QueueModelClient(queue);
        CountingToolExecutor toolExecutor = new CountingToolExecutor();

        AgentContext context = AgentContext.builder()
                .modelClient(modelClient)
                .toolExecutor(toolExecutor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().maxSteps(4).build())
                .model("test-model")
                .build();

        AgentResult result = new ReActRuntime().run(context, AgentRequest.builder().input("hi").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(1, toolExecutor.count);
        Assert.assertEquals(1, result.getToolCalls().size());
        AgentToolCall call = result.getToolCalls().get(0);
        Assert.assertEquals("echo", call.getName());
        Assert.assertEquals("call_1", call.getCallId());
    }

    @Test
    public void test_stream_run_publishes_reasoning_and_text_before_tool_call() throws Exception {
        AgentModelClient modelClient = new AgentModelClient() {
            private int invocation;

            @Override
            public AgentModelResult create(AgentPrompt prompt) {
                throw new UnsupportedOperationException("stream path only");
            }

            @Override
            public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                invocation++;
                if (invocation == 1) {
                    return AgentModelResult.builder()
                            .reasoningText("Need to inspect the tool output first.")
                            .outputText("I will run the tool now.")
                            .toolCalls(Arrays.asList(AgentToolCall.builder()
                                    .callId("call_1")
                                    .name("echo")
                                    .arguments("{}")
                                    .type("function_call")
                                    .build()))
                            .memoryItems(new ArrayList<Object>())
                            .build();
                }
                return resultWithText("done");
            }
        };

        AgentContext context = AgentContext.builder()
                .modelClient(modelClient)
                .toolExecutor(new CountingToolExecutor())
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().stream(true).maxSteps(4).build())
                .model("test-model")
                .build();

        List<AgentEventType> eventTypes = new ArrayList<>();
        List<String> eventMessages = new ArrayList<>();
        AgentListener listener = new AgentListener() {
            @Override
            public void onEvent(AgentEvent event) {
                if (event == null || event.getType() == null) {
                    return;
                }
                eventTypes.add(event.getType());
                eventMessages.add(event.getMessage());
            }
        };

        new ReActRuntime().runStream(context, AgentRequest.builder().input("hi").build(), listener);

        int reasoningIndex = eventTypes.indexOf(AgentEventType.MODEL_REASONING);
        int responseIndex = eventTypes.indexOf(AgentEventType.MODEL_RESPONSE);
        int toolCallIndex = eventTypes.indexOf(AgentEventType.TOOL_CALL);
        Assert.assertTrue("missing reasoning event: " + eventTypes, reasoningIndex >= 0);
        Assert.assertTrue("missing response event: " + eventTypes, responseIndex >= 0);
        Assert.assertTrue("missing tool call event: " + eventTypes, toolCallIndex >= 0);
        Assert.assertTrue("reasoning should arrive before tool call: " + eventTypes, reasoningIndex < toolCallIndex);
        Assert.assertTrue("text should arrive before tool call: " + eventTypes, responseIndex < toolCallIndex);
        Assert.assertTrue(eventMessages.stream().filter(msg -> msg != null).collect(Collectors.toList())
                .contains("Need to inspect the tool output first."));
        Assert.assertTrue(eventMessages.stream().filter(msg -> msg != null).collect(Collectors.toList())
                .contains("I will run the tool now."));
    }

    @Test
    public void test_invalid_tool_call_is_reported_without_execution() throws Exception {
        Deque<AgentModelResult> queue = new ArrayDeque<>();
        queue.add(AgentModelResult.builder()
                .reasoningText("Need to inspect the shell call.")
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name("bash")
                        .arguments("{\"action\":\"exec\"}")
                        .build()))
                .memoryItems(new ArrayList<Object>())
                .build());
        queue.add(resultWithText("done"));

        CountingToolExecutor toolExecutor = new CountingToolExecutor();
        AgentContext context = AgentContext.builder()
                .modelClient(new QueueModelClient(queue))
                .toolExecutor(toolExecutor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().stream(true).maxSteps(4).build())
                .model("test-model")
                .build();

        List<AgentEvent> events = new ArrayList<AgentEvent>();
        AgentListener listener = new AgentListener() {
            @Override
            public void onEvent(AgentEvent event) {
                if (event != null) {
                    events.add(event);
                }
            }
        };

        new ReActRuntime().runStream(context, AgentRequest.builder().input("hi").build(), listener);

        Assert.assertEquals(0, toolExecutor.count);
        Assert.assertTrue(events.stream().anyMatch(event -> event.getType() == AgentEventType.TOOL_CALL));
        Assert.assertTrue(events.stream().anyMatch(event -> {
            if (event.getType() != AgentEventType.TOOL_RESULT || !(event.getPayload() instanceof io.github.lnyocly.ai4j.agent.tool.AgentToolResult)) {
                return false;
            }
            io.github.lnyocly.ai4j.agent.tool.AgentToolResult result =
                    (io.github.lnyocly.ai4j.agent.tool.AgentToolResult) event.getPayload();
            return result.getOutput() != null && result.getOutput().contains("bash exec requires a non-empty command");
        }));
    }

    @Test
    public void test_stream_run_publishes_model_retry_event() throws Exception {
        AgentModelClient modelClient = new AgentModelClient() {
            @Override
            public AgentModelResult create(AgentPrompt prompt) {
                throw new UnsupportedOperationException("stream path only");
            }

            @Override
            public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                listener.onRetry("Timed out waiting for first model stream event after 30000 ms", 2, 3,
                        new RuntimeException("Timed out waiting for first model stream event after 30000 ms"));
                return resultWithText("done");
            }
        };

        AgentContext context = AgentContext.builder()
                .modelClient(modelClient)
                .toolExecutor(new CountingToolExecutor())
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().stream(true).maxSteps(4).build())
                .model("test-model")
                .build();

        List<AgentEvent> events = new ArrayList<AgentEvent>();
        AgentListener listener = new AgentListener() {
            @Override
            public void onEvent(AgentEvent event) {
                if (event != null) {
                    events.add(event);
                }
            }
        };

        new ReActRuntime().runStream(context, AgentRequest.builder().input("hi").build(), listener);

        Assert.assertTrue(events.stream().anyMatch(event -> event.getType() == AgentEventType.MODEL_RETRY));
        Assert.assertTrue(events.stream().anyMatch(event ->
                event.getType() == AgentEventType.MODEL_RETRY
                        && event.getMessage() != null
                        && event.getMessage().contains("Timed out waiting for first model stream event")));
    }

    @Test
    public void test_zero_max_steps_means_unlimited() throws Exception {
        Deque<AgentModelResult> queue = new ArrayDeque<>();
        queue.add(resultWithToolCall("call_1", "echo", "{}"));
        queue.add(resultWithToolCall("call_2", "echo", "{}"));
        queue.add(resultWithText("done"));

        CountingToolExecutor toolExecutor = new CountingToolExecutor();
        AgentContext context = AgentContext.builder()
                .modelClient(new QueueModelClient(queue))
                .toolExecutor(toolExecutor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().maxSteps(0).build())
                .model("test-model")
                .build();

        AgentResult result = new ReActRuntime().run(context, AgentRequest.builder().input("hi").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(2, toolExecutor.count);
        Assert.assertEquals(2, result.getToolCalls().size());
    }

    @Test
    public void test_tool_loop_executes_workspace_style_tools_and_pairs_next_turn_outputs() throws Exception {
        Deque<AgentModelResult> queue = new ArrayDeque<>();
        queue.add(resultWithToolCallAndMemory("read-call", "read_file", "{\"path\":\"a.txt\"}"));
        queue.add(resultWithToolCallAndMemory("write-call", "write_file", "{\"path\":\"a.txt\",\"content\":\"ok\"}"));
        queue.add(resultWithToolCallAndMemory("edit-call", "edit", "{\"path\":\"a.txt\",\"old_string\":\"o\",\"new_string\":\"k\"}"));
        queue.add(resultWithToolCallAndMemory("bash-call", "bash", "{\"command\":\"echo ok\"}"));
        queue.add(resultWithText("done"));

        QueueModelClient modelClient = new QueueModelClient(queue);
        CountingToolExecutor toolExecutor = new CountingToolExecutor();
        AgentContext context = AgentContext.builder()
                .modelClient(modelClient)
                .toolExecutor(toolExecutor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().maxSteps(0).build())
                .model("test-model")
                .build();

        AgentResult result = new ReActRuntime().run(context,
                AgentRequest.builder().input("inspect and update the workspace").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(Arrays.asList("read_file", "write_file", "edit", "bash"), toolExecutor.names);
        Assert.assertEquals(5, modelClient.prompts.size());
        for (int i = 0; i < 4; i++) {
            String callId = result.getToolCalls().get(i).getCallId();
            Assert.assertTrue("missing assistant tool call for " + callId,
                    containsAssistantToolCall(modelClient.prompts.get(i + 1).getItems(), callId));
            Assert.assertTrue("missing tool result for " + callId,
                    containsToolOutput(modelClient.prompts.get(i + 1).getItems(), callId));
        }
    }

    @Test
    public void test_missing_call_id_is_reconciled_before_tool_output_is_added() throws Exception {
        Deque<AgentModelResult> queue = new ArrayDeque<>();
        queue.add(resultWithToolCallAndMemory(null, "echo", "{}"));
        queue.add(resultWithText("done"));

        QueueModelClient modelClient = new QueueModelClient(queue);
        AgentContext context = AgentContext.builder()
                .modelClient(modelClient)
                .toolExecutor(new CountingToolExecutor())
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().maxSteps(0).build())
                .model("test-model")
                .build();

        AgentResult result = new ReActRuntime().run(context,
                AgentRequest.builder().input("run echo").build());

        Assert.assertEquals("tool_step_0_0", result.getToolCalls().get(0).getCallId());
        Assert.assertTrue(containsAssistantToolCall(modelClient.prompts.get(1).getItems(), "tool_step_0_0"));
        Assert.assertTrue(containsToolOutput(modelClient.prompts.get(1).getItems(), "tool_step_0_0"));
    }

    private boolean containsAssistantToolCall(List<Object> items, String callId) {
        if (items == null) {
            return false;
        }
        for (Object item : items) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            if (!"message".equals(map.get("type")) || !"assistant".equals(map.get("role"))) {
                continue;
            }
            Object rawCalls = map.get("tool_calls");
            if (!(rawCalls instanceof List)) {
                continue;
            }
            for (Object rawCall : (List<?>) rawCalls) {
                if (rawCall instanceof Map && callId.equals(((Map<?, ?>) rawCall).get("id"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsToolOutput(List<Object> items, String callId) {
        if (items == null) {
            return false;
        }
        for (Object item : items) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            if ("function_call_output".equals(map.get("type"))
                    && callId.equals(map.get("call_id"))) {
                return true;
            }
        }
        return false;
    }

    private AgentModelResult resultWithText(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>())
                .build();
    }

    private AgentModelResult resultWithToolCall(String callId, String name, String arguments) {
        List<AgentToolCall> calls = Arrays.asList(AgentToolCall.builder()
                .callId(callId)
                .name(name)
                .arguments(arguments)
                .type("function_call")
                .build());
        return AgentModelResult.builder()
                .toolCalls(calls)
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    private AgentModelResult resultWithToolCallAndMemory(String callId, String name, String arguments) {
        AgentToolCall call = AgentToolCall.builder()
                .callId(callId)
                .name(name)
                .arguments(arguments)
                .type("function")
                .build();
        return AgentModelResult.builder()
                .toolCalls(Arrays.asList(call))
                .memoryItems(Arrays.<Object>asList(
                        AgentInputItem.assistantToolCallsMessage("", Arrays.asList(call))))
                .build();
    }

    private static class QueueModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> queue;
        private final List<AgentPrompt> prompts = new ArrayList<>();

        private QueueModelClient(Deque<AgentModelResult> queue) {
            this.queue = queue;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            prompts.add(prompt);
            return queue.isEmpty() ? AgentModelResult.builder().build() : queue.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            prompts.add(prompt);
            return queue.isEmpty() ? AgentModelResult.builder().build() : queue.poll();
        }
    }

    private static class CountingToolExecutor implements ToolExecutor {
        private int count = 0;
        private final List<String> names = new ArrayList<>();

        @Override
        public String execute(AgentToolCall call) {
            count += 1;
            names.add(call == null ? null : call.getName());
            return "{\"ok\":true}";
        }
    }
}
