package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionRequest;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.compact.StructuredSummaryCompactPolicy;
import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.lifecycle.AgentLifecycleHookError;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEventType;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public class AgentPluginLifecycleHooksTest {

    @Test
    public void shouldDispatchLifecycleHooksInReactRuntime() throws Exception {
        RecordingLifecycleHook hook = new RecordingLifecycleHook("hook.recorder");
        ExtensionRegistry registry = ExtensionRegistry.of(new LifecycleExtension(hook))
                .enable("lifecycle-pack");
        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .name("echo")
                        .arguments("{}")
                        .callId("call-1")
                        .build()))
                .build());
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("done")
                .toolCalls(new ArrayList<AgentToolCall>())
                .memoryItems(new ArrayList<Object>())
                .build());

        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .extensions(registry)
                .toolExecutor(new EchoToolExecutor())
                .options(AgentOptions.builder().maxSteps(4).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("hi").build());

        Assert.assertEquals("done", result.getOutputText());
        assertOrder(hook.types,
                AgentLifecycleEventType.BEFORE_TURN,
                AgentLifecycleEventType.BEFORE_MODEL_REQUEST,
                AgentLifecycleEventType.AFTER_MODEL_RESPONSE,
                AgentLifecycleEventType.BEFORE_TOOL_CALL,
                AgentLifecycleEventType.AFTER_TOOL_CALL,
                AgentLifecycleEventType.AFTER_TURN,
                AgentLifecycleEventType.BEFORE_TURN,
                AgentLifecycleEventType.BEFORE_MODEL_REQUEST,
                AgentLifecycleEventType.AFTER_MODEL_RESPONSE,
                AgentLifecycleEventType.AFTER_TURN);
        Assert.assertTrue(hook.runtimes.contains("react"));
    }

    @Test
    public void shouldRecordLifecycleHookErrorsAndContinue() throws Exception {
        FailingLifecycleHook hook = new FailingLifecycleHook("hook.fail");
        ExtensionRegistry registry = ExtensionRegistry.of(new LifecycleExtension(hook))
                .enable("lifecycle-pack");
        final List<AgentEvent> events = new ArrayList<AgentEvent>();
        AgentEventPublisher publisher = new AgentEventPublisher();
        publisher.addListener(new AgentListener() {
            public void onEvent(AgentEvent event) {
                events.add(event);
            }
        });

        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("still done")
                .toolCalls(new ArrayList<AgentToolCall>())
                .memoryItems(new ArrayList<Object>())
                .build());

        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .extensions(registry)
                .eventPublisher(publisher)
                .options(AgentOptions.builder().maxSteps(1).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("hi").build());

        Assert.assertEquals("still done", result.getOutputText());
        AgentEvent error = firstEvent(events, AgentEventType.ERROR);
        Assert.assertNotNull(error);
        Assert.assertTrue(error.getPayload() instanceof AgentLifecycleHookError);
        AgentLifecycleHookError payload = (AgentLifecycleHookError) error.getPayload();
        Assert.assertEquals("hook.fail", payload.getHookName());
        Assert.assertNotNull(payload.getEvent());
    }

    @Test
    public void shouldDispatchLifecycleHooksInCodeActRuntime() throws Exception {
        RecordingLifecycleHook hook = new RecordingLifecycleHook("hook.codeact");
        ExtensionRegistry registry = ExtensionRegistry.of(new LifecycleExtension(hook))
                .enable("lifecycle-pack");
        QueueModelClient modelClient = new QueueModelClient();
        modelClient.enqueue(AgentModelResult.builder()
                .outputText("{\"type\":\"code\",\"language\":\"js\",\"code\":\"return 'ok';\"}")
                .toolCalls(new ArrayList<AgentToolCall>())
                .memoryItems(new ArrayList<Object>())
                .build());

        Agent agent = Agents.codeAct()
                .modelClient(modelClient)
                .model("test-model")
                .extensions(registry)
                .codeExecutor(new StaticCodeExecutor("codeact done"))
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("run code").build());

        Assert.assertEquals("codeact done", result.getOutputText());
        Assert.assertTrue(hook.types.contains(AgentLifecycleEventType.BEFORE_TURN));
        Assert.assertTrue(hook.types.contains(AgentLifecycleEventType.BEFORE_MODEL_REQUEST));
        Assert.assertTrue(hook.types.contains(AgentLifecycleEventType.AFTER_MODEL_RESPONSE));
        Assert.assertTrue(hook.types.contains(AgentLifecycleEventType.BEFORE_TOOL_CALL));
        Assert.assertTrue(hook.types.contains(AgentLifecycleEventType.AFTER_TOOL_CALL));
        Assert.assertTrue(hook.types.contains(AgentLifecycleEventType.AFTER_TURN));
        Assert.assertTrue(hook.runtimes.contains("codeact"));
    }

    @Test
    public void shouldDispatchLifecycleHookOnSessionCompact() {
        RecordingLifecycleHook hook = new RecordingLifecycleHook("hook.compact");
        ExtensionRegistry registry = ExtensionRegistry.of(new LifecycleExtension(hook))
                .enable("lifecycle-pack");
        QueueModelClient modelClient = new QueueModelClient();
        Agent agent = Agents.react()
                .modelClient(modelClient)
                .model("test-model")
                .extensions(registry)
                .options(AgentOptions.builder().maxSteps(1).build())
                .build();
        AgentSession session = agent.newSession();
        session.getContext().getMemory().addUserInput("goal");
        session.getContext().getMemory().addUserInput("recent");

        session.compact(new StructuredSummaryCompactPolicy(ContextBudget.maxItems(1)));

        Assert.assertTrue(hook.types.contains(AgentLifecycleEventType.ON_COMPACT));
        AgentLifecycleEvent event = lastEvent(hook.events, AgentLifecycleEventType.ON_COMPACT);
        Assert.assertNotNull(event);
        Assert.assertEquals(session.getSessionId(), event.getSessionId());
        Assert.assertEquals("session", event.getRuntime());
        Assert.assertNotNull(event.getPayload());
    }

    private static AgentEvent firstEvent(List<AgentEvent> events, AgentEventType type) {
        for (AgentEvent event : events) {
            if (event != null && event.getType() == type) {
                return event;
            }
        }
        return null;
    }

    private static AgentLifecycleEvent lastEvent(List<AgentLifecycleEvent> events, AgentLifecycleEventType type) {
        AgentLifecycleEvent result = null;
        for (AgentLifecycleEvent event : events) {
            if (event != null && event.getType() == type) {
                result = event;
            }
        }
        return result;
    }

    private static void assertOrder(List<AgentLifecycleEventType> actual, AgentLifecycleEventType... expected) {
        Assert.assertEquals("event count: " + actual, expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals("event index " + i + ": " + actual, expected[i], actual.get(i));
        }
    }

    private static class LifecycleExtension implements Ai4jExtension {
        private final AgentLifecycleHook hook;

        private LifecycleExtension(AgentLifecycleHook hook) {
            this.hook = hook;
        }

        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("lifecycle-pack")
                    .name("Lifecycle Pack")
                    .version("1.0.0")
                    .vendor("tests")
                    .capability(ExtensionCapability.LIFECYCLE)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.lifecycle().register(hook);
        }
    }

    private static class RecordingLifecycleHook implements AgentLifecycleHook {
        private final String name;
        private final List<AgentLifecycleEvent> events = new ArrayList<AgentLifecycleEvent>();
        private final List<AgentLifecycleEventType> types = new ArrayList<AgentLifecycleEventType>();
        private final List<String> runtimes = new ArrayList<String>();

        private RecordingLifecycleHook(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public void onEvent(AgentLifecycleEvent event) {
            events.add(event);
            types.add(event.getType());
            runtimes.add(event.getRuntime());
        }
    }

    private static class FailingLifecycleHook implements AgentLifecycleHook {
        private final String name;

        private FailingLifecycleHook(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public void onEvent(AgentLifecycleEvent event) {
            throw new IllegalStateException("boom");
        }
    }

    private static class QueueModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> results = new ArrayDeque<AgentModelResult>();

        private void enqueue(AgentModelResult result) {
            results.addLast(result);
        }

        public AgentModelResult create(AgentPrompt prompt) {
            return results.isEmpty()
                    ? AgentModelResult.builder().outputText("").toolCalls(new ArrayList<AgentToolCall>()).build()
                    : results.removeFirst();
        }

        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static class EchoToolExecutor implements ToolExecutor {
        public String execute(AgentToolCall call) {
            return "echo:" + (call == null ? "" : call.getArguments());
        }
    }

    private static class StaticCodeExecutor implements CodeExecutor {
        private final String result;

        private StaticCodeExecutor(String result) {
            this.result = result;
        }

        public CodeExecutionResult execute(CodeExecutionRequest request) {
            return CodeExecutionResult.builder()
                    .result(result)
                    .build();
        }
    }
}
