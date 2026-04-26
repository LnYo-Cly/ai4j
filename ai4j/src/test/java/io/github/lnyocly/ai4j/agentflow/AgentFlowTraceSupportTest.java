package io.github.lnyocly.ai4j.agentflow;

import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatEvent;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatRequest;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatResponse;
import io.github.lnyocly.ai4j.agentflow.support.AgentFlowSupport;
import io.github.lnyocly.ai4j.agentflow.trace.AgentFlowTraceContext;
import io.github.lnyocly.ai4j.agentflow.trace.AgentFlowTraceListener;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AgentFlowTraceSupportTest {

    @Test
    public void test_trace_listener_receives_lifecycle_events() {
        RecordingTraceListener listener = new RecordingTraceListener();
        TestAgentFlowSupport support = new TestAgentFlowSupport(config(listener));

        AgentFlowChatRequest request = AgentFlowChatRequest.builder()
                .prompt("plan a trip")
                .build();
        AgentFlowChatEvent event = AgentFlowChatEvent.builder()
                .type("message")
                .contentDelta("hello")
                .build();
        AgentFlowChatResponse response = AgentFlowChatResponse.builder()
                .content("hello world")
                .taskId("task-1")
                .build();

        AgentFlowTraceContext context = support.begin("chat", true, request);
        support.emitEvent(context, event);
        support.finish(context, response);

        Assert.assertEquals(Integer.valueOf(1), Integer.valueOf(listener.contexts.size()));
        Assert.assertEquals(Integer.valueOf(1), Integer.valueOf(listener.events.size()));
        Assert.assertEquals(Integer.valueOf(1), Integer.valueOf(listener.responses.size()));
        Assert.assertEquals(AgentFlowType.DIFY, listener.contexts.get(0).getType());
        Assert.assertEquals("chat", listener.contexts.get(0).getOperation());
        Assert.assertTrue(listener.contexts.get(0).isStreaming());
        Assert.assertEquals("task-1", listener.responses.get(0).getTaskId());
    }

    @Test
    public void test_trace_listener_receives_error_and_does_not_break_call_path() {
        RecordingTraceListener listener = new RecordingTraceListener();
        ThrowingTraceListener throwingListener = new ThrowingTraceListener();
        TestAgentFlowSupport support = new TestAgentFlowSupport(config(listener, throwingListener));

        AgentFlowTraceContext context = support.begin("workflow", false, null);
        RuntimeException failure = new RuntimeException("boom");
        support.fail(context, failure);

        Assert.assertEquals(Integer.valueOf(1), Integer.valueOf(listener.errors.size()));
        Assert.assertEquals("boom", listener.errors.get(0).getMessage());
    }

    private AgentFlowConfig config(AgentFlowTraceListener... listeners) {
        List<AgentFlowTraceListener> values = new ArrayList<AgentFlowTraceListener>();
        if (listeners != null) {
            for (AgentFlowTraceListener listener : listeners) {
                values.add(listener);
            }
        }
        return AgentFlowConfig.builder()
                .type(AgentFlowType.DIFY)
                .baseUrl("http://localhost:8080")
                .traceListeners(values)
                .build();
    }

    private static final class TestAgentFlowSupport extends AgentFlowSupport {

        private TestAgentFlowSupport(AgentFlowConfig config) {
            super(configuration(), config);
        }

        private AgentFlowTraceContext begin(String operation, boolean streaming, Object request) {
            return startTrace(operation, streaming, request);
        }

        private void emitEvent(AgentFlowTraceContext context, Object event) {
            traceEvent(context, event);
        }

        private void finish(AgentFlowTraceContext context, Object response) {
            traceComplete(context, response);
        }

        private void fail(AgentFlowTraceContext context, Throwable throwable) {
            traceError(context, throwable);
        }

        private static Configuration configuration() {
            Configuration configuration = new Configuration();
            configuration.setOkHttpClient(new OkHttpClient());
            return configuration;
        }
    }

    private static final class RecordingTraceListener implements AgentFlowTraceListener {

        private final List<AgentFlowTraceContext> contexts = new ArrayList<AgentFlowTraceContext>();
        private final List<AgentFlowChatEvent> events = new ArrayList<AgentFlowChatEvent>();
        private final List<AgentFlowChatResponse> responses = new ArrayList<AgentFlowChatResponse>();
        private final List<Throwable> errors = new ArrayList<Throwable>();

        @Override
        public void onStart(AgentFlowTraceContext context) {
            contexts.add(context);
        }

        @Override
        public void onEvent(AgentFlowTraceContext context, Object event) {
            if (event instanceof AgentFlowChatEvent) {
                events.add((AgentFlowChatEvent) event);
            }
        }

        @Override
        public void onComplete(AgentFlowTraceContext context, Object response) {
            if (response instanceof AgentFlowChatResponse) {
                responses.add((AgentFlowChatResponse) response);
            }
        }

        @Override
        public void onError(AgentFlowTraceContext context, Throwable throwable) {
            errors.add(throwable);
        }
    }

    private static final class ThrowingTraceListener implements AgentFlowTraceListener {

        @Override
        public void onStart(AgentFlowTraceContext context) {
            throw new IllegalStateException("ignored");
        }

        @Override
        public void onEvent(AgentFlowTraceContext context, Object event) {
            throw new IllegalStateException("ignored");
        }

        @Override
        public void onComplete(AgentFlowTraceContext context, Object response) {
            throw new IllegalStateException("ignored");
        }

        @Override
        public void onError(AgentFlowTraceContext context, Throwable throwable) {
            throw new IllegalStateException("ignored");
        }
    }
}
